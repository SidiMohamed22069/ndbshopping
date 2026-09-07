import json
from decimal import Decimal, InvalidOperation

from django.contrib import messages
from django.shortcuts import redirect, render
from django.urls import reverse
from django.utils.translation import gettext as _
from django.utils.translation import gettext_lazy as _lazy
from django.views.decorators.http import require_http_methods, require_POST

from core.decorators import admin_required_api
from core.media_upload import json_error, json_ok, media_initial_json, upload_and_respond, validate_image, validate_video
from core.utils import (
    ETAT_CHOICES,
    VILLE_CHOICES,
    flatten_categories,
    normalize_category_image,
    normalize_product_images,
    page_from_request,
)
from services import api_client

CATEGORY_TYPES = [
    ("PRODUIT", _lazy("Produit")),
    ("HOTEL", _lazy("Hôtel")),
    ("VOITURE", _lazy("Voiture")),
    ("SERVICE", _lazy("Service")),
    ("AUTRE", _lazy("Autre")),
]
ATTR_TYPES = [
    ("TEXTE", _lazy("Texte")),
    ("NOMBRE", _lazy("Nombre")),
    ("DATE", _lazy("Date")),
    ("BOOLEEN", _lazy("Oui / Non")),
]
PRODUCT_STATUSES = [("BROUILLON", _lazy("Brouillon")), ("PUBLIE", _lazy("Publié"))]
PRODUCT_FILTER_STATUSES = [
    ("BROUILLON", _lazy("Brouillon")),
    ("PUBLIE", _lazy("Publié")),
    ("EN_ATTENTE", _lazy("En attente")),
    ("REJETE", _lazy("Refusé")),
    ("VENDU", _lazy("Vendu")),
    ("ARCHIVE", _lazy("Archivé")),
]
PRODUCT_SOURCES = [
    ("MANUEL", _lazy("Manuel")),
    ("FACEBOOK", "Facebook"),
    ("ALIBABA", "Alibaba"),
    ("ALIEXPRESS", "AliExpress"),
    ("AMAZON", "Amazon"),
    ("AUTRE", _lazy("Autre")),
]
ORDER_STATUSES = [
    ("EN_ATTENTE", _lazy("En attente")),
    ("CONFIRMEE", _lazy("Confirmée")),
    ("EN_LIVRAISON", _lazy("En livraison")),
    ("LIVREE", _lazy("Livrée")),
    ("ANNULEE", _lazy("Annulée")),
]
NEGOTIATION_STATUSES = [
    ("PENDING", _lazy("En attente")),
    ("COUNTER_OFFER", _lazy("Contre-offre")),
    ("ACCEPTED", _lazy("Acceptée")),
    ("REJECTED", _lazy("Refusée")),
]
PUB_STATUSES = [("BROUILLON", _lazy("Brouillon")), ("PUBLIE", _lazy("Publié"))]

IMAGE_MAX_BYTES = 5 * 1024 * 1024
IMAGE_CONTENT_TYPES = {"image/jpeg", "image/jpg", "image/png", "image/webp"}


def _token(request) -> str:
    return request.jwt_token


def _posted_file(request, field_name: str):
    upload = request.FILES.get(field_name)
    if upload and getattr(upload, "size", 0):
        return upload
    return None


def _posted_image(request):
    return _posted_file(request, "image")


def _is_local_media_url(value: str | None) -> bool:
    """True si la valeur pointe vers un fichier /media/ stocké par le backend."""
    v = (value or "").strip().lower()
    if not v:
        return False
    markers = ("/media/products/", "/media/categories/", "/media/publications/")
    if any(marker in v for marker in markers):
        return True
    return v.startswith(("products/", "categories/", "publications/"))


def _source_url_for_form(product: dict | None) -> str:
    """sourceUrl d'import (Facebook/Alibaba), jamais l'URL d'une image uploadée."""
    if not product:
        return ""
    source = (product.get("sourceUrl") or "").strip()
    if not source or _is_local_media_url(source):
        return ""
    source_path = source.split("?")[0]
    source_name = source_path.rsplit("/", 1)[-1]
    for img in product.get("images") or []:
        image_url = (img.get("url") or "").strip()
        if not image_url:
            continue
        image_path = image_url.split("?")[0]
        if source in (image_url, image_path) or source_path == image_path:
            return ""
        if source_name and source_name == image_path.rsplit("/", 1)[-1]:
            return ""
    return source


def _validate_image(upload) -> str | None:
    if upload.size > IMAGE_MAX_BYTES:
        return _("Image trop volumineuse (5 Mo maximum).")
    content_type = (upload.content_type or "").lower()
    name = (upload.name or "").lower()
    if content_type not in IMAGE_CONTENT_TYPES and not name.endswith((".jpg", ".jpeg", ".png", ".webp")):
        return _("Format non accepté. Utilisez JPG, PNG ou WebP.")
    return None


def _upload_ok_with_url(result) -> bool:
    if not result.ok:
        return False
    if not isinstance(result.data, dict) or not result.data:
        return True
    return bool(result.data.get("url") or result.data.get("imageUrl") or result.data.get("id"))


def _upload_image_if_present(request, upload_fn, entity_id) -> str | None:
    """Envoie le fichier `image` s'il est présent. Retourne un message d'erreur, sinon None."""
    upload = _posted_image(request)
    if not upload or not entity_id:
        return None
    error = _validate_image(upload)
    if error:
        return error
    upload.seek(0)
    result = upload_fn(_token(request), entity_id, upload)
    if _upload_ok_with_url(result):
        return None
    return result.error or _("Impossible d'enregistrer l'image.")


def _media_form_extra(product) -> dict:
    normalized = normalize_product_images(product) if isinstance(product, dict) else product
    return {
        "product": normalized,
        "media_url_ns": "adminpanel",
        "media_initial_json": media_initial_json(normalized if isinstance(normalized, dict) else None),
    }


def _find_in_tree(nodes, cid):
    for n in nodes or []:
        if str(n.get("id")) == str(cid):
            return n
        found = _find_in_tree(n.get("children") or [], cid)
        if found:
            return found
    return None


# ---------------------------------------------------------------------------
# Dashboard
# ---------------------------------------------------------------------------

@admin_required_api
@require_http_methods(["GET"])
def dashboard(request):
    token = _token(request)
    pending = api_client.admin_get_orders(token, statut="EN_ATTENTE", page=0, size=5)
    recent = api_client.admin_get_orders(token, page=0, size=8)
    notifs = api_client.admin_get_notifications(token, lu=False, page=0, size=8)
    unread = api_client.admin_unread_count(token)

    pending_data = pending.data if pending.ok else {}
    recent_data = recent.data if recent.ok else {}
    notifs_data = notifs.data if notifs.ok else {}
    unread_count = unread.data.get("count", 0) if unread.ok and isinstance(unread.data, dict) else 0

    for result in (pending, recent, notifs, unread):
        if not result.ok:
            messages.error(request, result.error or api_client.UNAVAILABLE)
            break

    return render(
        request,
        "adminpanel/dashboard.html",
        {
            "pending_count": pending_data.get("totalElements", 0) if isinstance(pending_data, dict) else 0,
            "pending_orders": pending_data.get("content") or [] if isinstance(pending_data, dict) else [],
            "recent_orders": recent_data.get("content") or [] if isinstance(recent_data, dict) else [],
            "notifications": notifs_data.get("content") or [] if isinstance(notifs_data, dict) else [],
            "unread_count": unread_count,
        },
    )


# ---------------------------------------------------------------------------
# Catégories
# ---------------------------------------------------------------------------

@admin_required_api
@require_http_methods(["GET"])
def category_list(request):
    result = api_client.get_categories()
    tree = result.data if result.ok and isinstance(result.data, list) else []
    if not result.ok:
        messages.error(request, result.error or api_client.UNAVAILABLE)
    tree = [normalize_category_image(cat) for cat in tree if isinstance(cat, dict)]
    return render(request, "adminpanel/categories/list.html", {"categories": tree})


@admin_required_api
@require_POST
def category_reorder(request):
    try:
        payload = json.loads(request.body.decode() or "{}")
    except json.JSONDecodeError:
        return json_error(_("Données invalides."))
    raw_ids = payload.get("ordreIds")
    if not isinstance(raw_ids, list) or not raw_ids:
        return json_error(_("La liste des catégories est vide."))
    ordre_ids = []
    for raw in raw_ids:
        try:
            ordre_ids.append(int(raw))
        except (TypeError, ValueError):
            return json_error(_("Données invalides."))
    result = api_client.admin_reorder_categories(_token(request), ordre_ids)
    if result.ok:
        return json_ok()
    return json_error(result.error or _("Impossible d'enregistrer l'ordre."), result.status or 400)


def _category_payload(request) -> dict:
    parent = request.POST.get("parentId") or None
    return {
        "nom": (request.POST.get("nom") or "").strip(),
        "type": request.POST.get("type") or "PRODUIT",
        "parentId": int(parent) if parent else None,
    }


@admin_required_api
@require_http_methods(["GET", "POST"])
def category_create(request):
    cats = api_client.get_categories()
    flat = flatten_categories(cats.data if cats.ok else [])
    if request.method == "POST":
        payload = _category_payload(request)
        result = api_client.admin_create_category(_token(request), payload)
        if result.ok:
            entity_id = (result.data or {}).get("id") if isinstance(result.data, dict) else None
            img_error = _upload_image_if_present(
                request, api_client.admin_upload_category_image, entity_id
            )
            if img_error:
                messages.warning(
                    request,
                    _("Catégorie créée, mais l'image n'a pas pu être enregistrée : ") + img_error,
                )
                if entity_id:
                    return redirect("adminpanel:category_edit", category_id=entity_id)
            elif entity_id and _posted_image(request):
                messages.success(request, _("Catégorie créée. Image enregistrée."))
                return redirect("adminpanel:category_edit", category_id=entity_id)
            else:
                messages.success(request, _("Catégorie créée."))
            return redirect("adminpanel:category_list")
        messages.error(request, result.error or _("Création impossible."))
    return render(
        request,
        "adminpanel/categories/form.html",
        {"category": None, "parents": flat, "types": CATEGORY_TYPES},
    )


@admin_required_api
@require_http_methods(["GET", "POST"])
def category_edit(request, category_id):
    cats = api_client.get_categories()
    tree = cats.data if cats.ok else []
    category = _find_in_tree(tree, category_id)
    if not category:
        messages.error(request, _("Catégorie introuvable."))
        return redirect("adminpanel:category_list")
    flat = [c for c in flatten_categories(tree) if str(c["id"]) != str(category_id)]
    if request.method == "POST":
        payload = _category_payload(request)
        result = api_client.admin_update_category(_token(request), category_id, payload)
        if result.ok:
            img_error = _upload_image_if_present(
                request, api_client.admin_upload_category_image, category_id
            )
            if img_error:
                messages.warning(
                    request,
                    _("Catégorie mise à jour, mais l'image n'a pas pu être enregistrée : ") + img_error,
                )
                return redirect("adminpanel:category_edit", category_id=category_id)
            if _posted_image(request):
                messages.success(request, _("Catégorie mise à jour. Image enregistrée."))
                return redirect("adminpanel:category_edit", category_id=category_id)
            messages.success(request, _("Catégorie mise à jour."))
            return redirect("adminpanel:category_list")
        messages.error(request, result.error or _("Mise à jour impossible."))
    return render(
        request,
        "adminpanel/categories/form.html",
        {"category": category, "parents": flat, "types": CATEGORY_TYPES},
    )


@admin_required_api
@require_POST
def category_delete(request, category_id):
    result = api_client.admin_delete_category(_token(request), category_id)
    if result.ok:
        messages.success(request, _("Catégorie supprimée."))
    else:
        messages.error(request, result.error or _("Suppression impossible."))
    return redirect("adminpanel:category_list")


@admin_required_api
@require_http_methods(["GET", "POST"])
def category_attributes(request, category_id):
    cats = api_client.get_categories()
    category = _find_in_tree(cats.data if cats.ok else [], category_id)
    attrs = api_client.get_category_attributes(category_id)
    attributes = attrs.data if attrs.ok and isinstance(attrs.data, list) else []
    if request.method == "POST":
        payload = {
            "nomAttribut": (request.POST.get("nomAttribut") or "").strip(),
            "typeValeur": request.POST.get("typeValeur") or "TEXTE",
        }
        result = api_client.admin_add_attribute(_token(request), category_id, payload)
        if result.ok:
            messages.success(request, _("Attribut ajouté."))
            return redirect("adminpanel:category_attributes", category_id=category_id)
        messages.error(request, result.error or _("Ajout impossible."))
    return render(
        request,
        "adminpanel/categories/attributes.html",
        {
            "category": category,
            "attributes": attributes,
            "attr_types": ATTR_TYPES,
            "category_id": category_id,
        },
    )


@admin_required_api
@require_POST
def category_attribute_delete(request, category_id, attribute_id):
    result = api_client.admin_delete_attribute(_token(request), category_id, attribute_id)
    if result.ok:
        messages.success(request, _("Attribut supprimé."))
    else:
        messages.error(request, result.error or _("Suppression impossible."))
    return redirect("adminpanel:category_attributes", category_id=category_id)


# ---------------------------------------------------------------------------
# Produits
# ---------------------------------------------------------------------------

def _product_payload(request, existing: dict | None = None) -> dict:
    attributs = []
    for key in request.POST:
        if key.startswith("attr_"):
            raw_id = key[5:]
            try:
                attributs.append(
                    {
                        "attributeDefinitionId": int(raw_id),
                        "valeur": request.POST.get(key) or "",
                    }
                )
            except ValueError:
                continue
    category_id = request.POST.get("categoryId")
    stock = request.POST.get("stock") or 0
    source_url = (request.POST.get("sourceUrl") or "").strip() or None
    if source_url and _is_local_media_url(source_url):
        source_url = None
    prix_raw = (request.POST.get("prix") or "").strip()
    if prix_raw:
        prix = prix_raw
    elif existing and existing.get("prix") is not None:
        # Le champ n'a pas été retransmis (ex: soumission hors formulaire) :
        # on garde le prix actuel du produit au lieu de l'écraser par 0.
        prix = str(existing["prix"])
    else:
        prix = "0"
    return {
        "nom": (request.POST.get("nom") or "").strip(),
        "description": (request.POST.get("description") or "").strip() or None,
        "prix": prix,
        "stock": int(stock) if str(stock).isdigit() else 0,
        "categoryId": int(category_id) if category_id else None,
        "sourceOrigine": request.POST.get("sourceOrigine") or "MANUEL",
        "sourceUrl": source_url,
        "statut": request.POST.get("statut") or "BROUILLON",
        "ville": (request.POST.get("ville") or "").strip() or None,
        "etat": (request.POST.get("etat") or "").strip() or None,
        "attributs": attributs,
    }


def _product_form_context(product, categories_flat) -> dict:
    extra = _media_form_extra(product)
    return {
        "product": extra["product"],
        "source_url": _source_url_for_form(extra["product"] if isinstance(extra["product"], dict) else None),
        "product_attrs_json": json.dumps((extra["product"] or {}).get("attributs") or []) if extra["product"] else "[]",
        "categories_flat": categories_flat,
        "statuses": PRODUCT_STATUSES,
        "sources": PRODUCT_SOURCES,
        "ville_choices": VILLE_CHOICES,
        "etat_choices": ETAT_CHOICES,
        "media_url_ns": extra["media_url_ns"],
        "media_initial_json": extra["media_initial_json"],
    }


@admin_required_api
@require_http_methods(["GET"])
def product_list(request):
    page = page_from_request(request)
    statut = request.GET.get("statut") or ""
    category_id = request.GET.get("category") or ""
    q = (request.GET.get("q") or "").strip()
    result = api_client.admin_get_products(
        _token(request),
        statut=statut or None,
        category_id=category_id or None,
        q=q or None,
        page=page - 1,
        size=20,
    )
    products, pagination = [], None
    if result.ok and isinstance(result.data, dict):
        products = result.data.get("content") or []
        pagination = result.data
    else:
        messages.error(request, result.error or api_client.UNAVAILABLE)
    cats = api_client.get_categories()
    return render(
        request,
        "adminpanel/products/list.html",
        {
            "products": products,
            "pagination": pagination,
            "page": page,
            "statut": statut,
            "category_id": category_id,
            "q": q,
            "statuses": PRODUCT_FILTER_STATUSES,
            "categories_flat": flatten_categories(cats.data if cats.ok else []),
        },
    )


def _attach_import_images(request, product_id) -> list[str]:
    """Rapatrie côté backend les images sélectionnées lors de l'aperçu d'import.

    `import_image_urls` est un JSON (liste d'URLs) rempli par le JS de l'onglet
    "Importer" après extraction. Retourne les messages d'erreur des images qui
    n'ont pas pu être téléchargées (le produit reste créé malgré tout).
    """
    raw = request.POST.get("import_image_urls") or ""
    if not raw.strip() or not product_id:
        return []
    try:
        urls = json.loads(raw)
    except json.JSONDecodeError:
        return []
    if not isinstance(urls, list):
        return []
    token = _token(request)
    errors = []
    for url in urls[:6]:
        if not isinstance(url, str) or not url.strip():
            continue
        result = api_client.admin_import_product_image(token, product_id, url.strip())
        if not result.ok:
            errors.append(result.error or url)
    return errors


@admin_required_api
@require_http_methods(["GET", "POST"])
def product_create(request):
    cats = api_client.get_categories()
    flat = flatten_categories(cats.data if cats.ok else [])
    if request.method == "POST":
        payload = _product_payload(request)
        result = api_client.admin_create_product(_token(request), payload)
        if result.ok and isinstance(result.data, dict):
            entity_id = result.data.get("id")
            image_errors = _attach_import_images(request, entity_id)
            messages.success(request, _("Produit créé."))
            if image_errors:
                messages.warning(
                    request,
                    _("Produit créé, mais certaines images importées n'ont pas pu être enregistrées : ")
                    + "; ".join(image_errors),
                )
            if entity_id:
                return redirect("adminpanel:product_edit", product_id=entity_id)
        messages.error(request, result.error or _("Création impossible."))
    return render(
        request,
        "adminpanel/products/form.html",
        _product_form_context(None, flat),
    )


@admin_required_api
@require_POST
def product_import_extract(request):
    """Proxy AJAX vers POST /admin/products/import/preview (utilisé par l'onglet "Importer" du formulaire d'ajout)."""
    url = (request.POST.get("url") or "").strip()
    if not url:
        return json_error(_("Collez une URL."))
    result = api_client.admin_import_preview(_token(request), url)
    if not result.ok:
        return json_error(result.error or _("Extraction impossible."), result.status or 400)
    return json_ok(result.data)


@admin_required_api
@require_http_methods(["GET", "POST"])
def product_edit(request, product_id):
    token = _token(request)
    prod = api_client.admin_get_product(token, product_id)
    if not prod.ok:
        messages.error(request, prod.error or _("Produit introuvable."))
        return redirect("adminpanel:product_list")
    cats = api_client.get_categories()
    flat = flatten_categories(cats.data if cats.ok else [])
    if request.method == "POST":
        payload = _product_payload(request, existing=prod.data if isinstance(prod.data, dict) else None)
        result = api_client.admin_update_product(token, product_id, payload)
        if result.ok:
            messages.success(request, _("Produit mis à jour."))
            return redirect("adminpanel:product_edit", product_id=product_id)
        messages.error(request, result.error or _("Mise à jour impossible."))
    return render(
        request,
        "adminpanel/products/form.html",
        _product_form_context(prod.data, flat),
    )


@admin_required_api
@require_POST
def product_delete(request, product_id):
    result = api_client.admin_delete_product(_token(request), product_id)
    if result.ok:
        messages.success(request, _("Produit supprimé."))
    else:
        messages.error(request, result.error or _("Suppression impossible."))
    return redirect("adminpanel:product_list")


@admin_required_api
@require_http_methods(["GET", "POST"])
def product_images(request, product_id):
    token = _token(request)
    if request.method == "POST":
        upload = _posted_file(request, "file")
        if not upload:
            messages.error(request, _("Choisissez une image (jpg, png, webp — 5 Mo max)."))
        else:
            error = _validate_image(upload)
            if error:
                messages.error(request, error)
            else:
                upload.seek(0)
                result = api_client.admin_upload_product_image(token, product_id, upload)
                if _upload_ok_with_url(result):
                    messages.success(request, _("Image ajoutée."))
                else:
                    messages.error(request, result.error or _("Upload impossible."))
        return redirect("adminpanel:product_images", product_id=product_id)

    prod = api_client.admin_get_product(token, product_id)
    if not prod.ok:
        messages.error(request, prod.error or _("Produit introuvable."))
        return redirect("adminpanel:product_list")
    extra = _media_form_extra(prod.data if isinstance(prod.data, dict) else None)
    return render(request, "adminpanel/products/images.html", extra)


@admin_required_api
@require_POST
def product_media_image_add(request, product_id):
    return upload_and_respond(
        request, product_id, "file", validate_image, api_client.admin_upload_product_image
    )


@admin_required_api
@require_POST
def product_media_image_delete(request, product_id, image_id):
    result = api_client.admin_delete_product_image(_token(request), product_id, image_id)
    if result.ok:
        return json_ok()
    return json_error(result.error or _("Suppression impossible."), result.status or 400)


@admin_required_api
@require_POST
def product_media_video_add(request, product_id):
    return upload_and_respond(
        request, product_id, "video", validate_video, api_client.upload_product_video
    )


@admin_required_api
@require_POST
def product_media_video_delete(request, product_id, video_id):
    result = api_client.delete_product_video(_token(request), product_id, video_id)
    if result.ok:
        return json_ok()
    return json_error(result.error or _("Suppression impossible."), result.status or 400)


@admin_required_api
@require_POST
def product_image_delete(request, product_id, image_id):
    result = api_client.admin_delete_product_image(_token(request), product_id, image_id)
    if result.ok:
        messages.success(request, _("Image supprimée."))
    else:
        messages.error(request, result.error or _("Suppression impossible."))
    return redirect("adminpanel:product_images", product_id=product_id)


def _submitter_names(token: str, user_ids) -> dict:
    needed = {str(uid) for uid in user_ids if uid is not None}
    names = {}
    if not needed:
        return names
    page = 0
    while page < 20 and needed:
        result = api_client.admin_get_users(token, page=page, size=50)
        if not result.ok or not isinstance(result.data, dict):
            break
        for user in result.data.get("content") or []:
            uid = user.get("id")
            key = str(uid) if uid is not None else ""
            if key in needed:
                names[key] = user.get("nom") or user.get("telephone") or key
                needed.discard(key)
        if page + 1 >= (result.data.get("totalPages") or 1):
            break
        page += 1
    return names


@admin_required_api
@require_http_methods(["GET"])
def product_pending(request):
    page = page_from_request(request)
    token = _token(request)
    result = api_client.admin_get_products(token, statut="EN_ATTENTE", page=page - 1, size=20)
    products, pagination = [], None
    if result.ok and isinstance(result.data, dict):
        products = [
            normalize_product_images(p) for p in (result.data.get("content") or []) if isinstance(p, dict)
        ]
        pagination = result.data
        names = _submitter_names(token, [p.get("soumisParUserId") for p in products])
        for product in products:
            uid = product.get("soumisParUserId")
            product["soumisParNom"] = names.get(str(uid)) if uid is not None else "—"
            if uid is not None and not product["soumisParNom"]:
                product["soumisParNom"] = f"#{uid}"
    else:
        messages.error(request, result.error or api_client.UNAVAILABLE)
    return render(
        request,
        "adminpanel/products/pending.html",
        {"products": products, "pagination": pagination, "page": page},
    )


@admin_required_api
@require_POST
def product_mark_sold(request, product_id):
    result = api_client.mark_product_sold(_token(request), product_id)
    if result.ok:
        messages.success(request, _("Produit marqué comme vendu."))
    else:
        messages.error(request, result.error or _("Action impossible."))
    return redirect("adminpanel:product_list")


@admin_required_api
@require_POST
def product_archive(request, product_id):
    result = api_client.archive_product(_token(request), product_id)
    if result.ok:
        messages.success(request, _("Produit archivé."))
    else:
        messages.error(request, result.error or _("Action impossible."))
    return redirect("adminpanel:product_list")


@admin_required_api
@require_POST
def product_reactivate(request, product_id):
    result = api_client.reactivate_product(_token(request), product_id)
    if result.ok:
        messages.success(request, _("Produit remis en ligne."))
    else:
        messages.error(request, result.error or _("Action impossible."))
    return redirect("adminpanel:product_list")


@admin_required_api
@require_POST
def product_validate(request, product_id):
    result = api_client.admin_validate_product(_token(request), product_id)
    if result.ok:
        messages.success(request, _("Produit validé. Il est maintenant en ligne."))
    else:
        messages.error(request, result.error or _("Validation impossible."))
    return redirect("adminpanel:product_pending")


@admin_required_api
@require_POST
def product_reject(request, product_id):
    raison = (request.POST.get("raison") or "").strip()
    if not raison:
        messages.error(request, _("Indiquez une raison de rejet."))
        return redirect("adminpanel:product_pending")
    result = api_client.admin_reject_product(_token(request), product_id, raison)
    if result.ok:
        messages.success(request, _("Produit rejeté."))
    else:
        messages.error(request, result.error or _("Rejet impossible."))
    return redirect("adminpanel:product_pending")


@admin_required_api
@require_http_methods(["GET", "POST"])
def product_import(request):
    """Import CSV en masse. L'import unitaire par URL (scraping) se fait depuis
    l'onglet "Importer" du formulaire d'ajout de produit (voir `product_create`)."""
    csv_result = None
    if request.method == "POST":
        upload = request.FILES.get("file")
        if not upload:
            messages.error(request, _("Choisissez un fichier CSV."))
        else:
            result = api_client.admin_import_csv(_token(request), upload)
            if result.ok:
                csv_result = result.data
                imported = csv_result.get("imported", 0) if isinstance(csv_result, dict) else 0
                messages.success(request, _("Import CSV terminé : %(n)s produit(s).") % {"n": imported})
            else:
                messages.error(request, result.error or _("Import CSV impossible."))
    return render(
        request,
        "adminpanel/products/import.html",
        {"csv_result": csv_result},
    )


# ---------------------------------------------------------------------------
# Commandes
# ---------------------------------------------------------------------------

@admin_required_api
@require_http_methods(["GET"])
def order_list(request):
    page = page_from_request(request)
    statut = request.GET.get("statut") or ""
    result = api_client.admin_get_orders(
        _token(request),
        statut=statut or None,
        page=page - 1,
        size=20,
    )
    orders, pagination = [], None
    if result.ok and isinstance(result.data, dict):
        orders = result.data.get("content") or []
        pagination = result.data
    else:
        messages.error(request, result.error or api_client.UNAVAILABLE)
    return render(
        request,
        "adminpanel/orders/list.html",
        {
            "orders": orders,
            "pagination": pagination,
            "page": page,
            "statut": statut,
            "statuses": ORDER_STATUSES,
        },
    )


def _find_order(token, order_id):
    page = 0
    while page < 20:
        result = api_client.admin_get_orders(token, page=page, size=50)
        if not result.ok or not isinstance(result.data, dict):
            return None, result
        for order in result.data.get("content") or []:
            if str(order.get("id")) == str(order_id):
                return order, result
        total = result.data.get("totalPages") or 1
        page += 1
        if page >= total:
            break
    return None, None


@admin_required_api
@require_http_methods(["GET", "POST"])
def order_detail(request, order_id):
    token = _token(request)
    if request.method == "POST":
        statut = request.POST.get("statut")
        result = api_client.admin_update_order_status(token, order_id, statut)
        if result.ok:
            messages.success(request, _("Statut mis à jour."))
            return redirect("adminpanel:order_detail", order_id=order_id)
        messages.error(request, result.error or _("Mise à jour impossible."))

    order, lookup = _find_order(token, order_id)
    if not order:
        if lookup is not None and not lookup.ok:
            messages.error(request, lookup.error or api_client.UNAVAILABLE)
        else:
            messages.error(request, _("Commande introuvable."))
        return redirect("adminpanel:order_list")
    return render(
        request,
        "adminpanel/orders/detail.html",
        {"order": order, "statuses": ORDER_STATUSES},
    )


# ---------------------------------------------------------------------------
# Négociations de prix
# ---------------------------------------------------------------------------

@admin_required_api
@require_http_methods(["GET"])
def negotiation_list(request):
    page = page_from_request(request)
    statut = request.GET.get("statut") or ""
    result = api_client.admin_get_negotiations(_token(request), statut=statut or None, page=page - 1, size=20)
    negotiations, pagination = [], None
    if result.ok and isinstance(result.data, dict):
        negotiations = result.data.get("content") or []
        pagination = result.data
    else:
        messages.error(request, result.error or api_client.UNAVAILABLE)
    return render(
        request,
        "adminpanel/negotiations/list.html",
        {
            "negotiations": negotiations,
            "pagination": pagination,
            "page": page,
            "statut": statut,
            "statuses": NEGOTIATION_STATUSES,
        },
    )


@admin_required_api
@require_http_methods(["GET", "POST"])
def negotiation_detail(request, negotiation_id):
    token = _token(request)
    if request.method == "POST":
        action = request.POST.get("action")
        if action == "message":
            text = (request.POST.get("message") or "").strip()
            if not text:
                messages.error(request, _("Message vide."))
            else:
                result = api_client.admin_add_negotiation_message(token, negotiation_id, text)
                if not result.ok:
                    messages.error(request, result.error or _("Message non envoyé."))
        elif action == "offer":
            price_raw = (request.POST.get("proposed_price") or "").strip()
            message = (request.POST.get("message") or "").strip()
            try:
                price = str(Decimal(price_raw))
            except InvalidOperation:
                messages.error(request, _("Indiquez un prix valide."))
            else:
                result = api_client.admin_add_negotiation_offer(token, negotiation_id, price, message or None)
                if result.ok:
                    messages.success(request, _("Contre-offre envoyée."))
                else:
                    messages.error(request, result.error or _("Impossible d'envoyer la contre-offre."))
        elif action == "accept":
            result = api_client.admin_accept_negotiation(token, negotiation_id)
            if result.ok:
                messages.success(request, _("Prix validé."))
            else:
                messages.error(request, result.error or _("Impossible de valider ce prix."))
        elif action == "reject":
            message = (request.POST.get("message") or "").strip()
            result = api_client.admin_reject_negotiation(token, negotiation_id, message or None)
            if result.ok:
                messages.success(request, _("Négociation refusée."))
            else:
                messages.error(request, result.error or _("Impossible de refuser."))
        return redirect("adminpanel:negotiation_detail", negotiation_id=negotiation_id)

    result = api_client.admin_get_negotiation(token, negotiation_id)
    if not result.ok or not isinstance(result.data, dict):
        messages.error(request, result.error or _("Négociation introuvable."))
        return redirect("adminpanel:negotiation_list")
    negotiation = result.data
    can_accept = negotiation.get("derniereActionPar") != "ADMIN"
    return render(
        request,
        "adminpanel/negotiations/detail.html",
        {"negotiation": negotiation, "can_accept": can_accept},
    )


# ---------------------------------------------------------------------------
# Publications
# ---------------------------------------------------------------------------

def _publication_payload(request) -> dict:
    produit = request.POST.get("produitLieId") or None
    return {
        "titre": (request.POST.get("titre") or "").strip(),
        "contenu": (request.POST.get("contenu") or "").strip(),
        "produitLieId": int(produit) if produit else None,
        "statut": request.POST.get("statut") or "BROUILLON",
        "misEnAvant": request.POST.get("misEnAvant") in ("on", "true", "1"),
    }


@admin_required_api
@require_http_methods(["GET"])
def publication_list(request):
    page = page_from_request(request)
    result = api_client.admin_get_publications(_token(request), page=page - 1, size=20)
    publications, pagination = [], None
    if result.ok and isinstance(result.data, dict):
        publications = result.data.get("content") or []
        pagination = result.data
    else:
        messages.error(request, result.error or api_client.UNAVAILABLE)
    return render(
        request,
        "adminpanel/publications/list.html",
        {"publications": publications, "pagination": pagination, "page": page},
    )


@admin_required_api
@require_http_methods(["GET", "POST"])
def publication_create(request):
    if request.method == "POST":
        result = api_client.admin_create_publication(_token(request), _publication_payload(request))
        if result.ok:
            entity_id = (result.data or {}).get("id") if isinstance(result.data, dict) else None
            img_error = _upload_image_if_present(
                request, api_client.admin_upload_publication_image, entity_id
            )
            if img_error:
                messages.warning(
                    request,
                    _("Publication créée, mais l'image n'a pas pu être enregistrée : ") + img_error,
                )
                if entity_id:
                    return redirect("adminpanel:publication_edit", publication_id=entity_id)
            elif entity_id and _posted_image(request):
                messages.success(request, _("Publication créée. Image enregistrée."))
            else:
                messages.success(request, _("Publication créée."))
            return redirect("adminpanel:publication_list")
        messages.error(request, result.error or _("Création impossible."))
    return render(
        request,
        "adminpanel/publications/form.html",
        {"publication": None, "statuses": PUB_STATUSES},
    )


@admin_required_api
@require_http_methods(["GET", "POST"])
def publication_edit(request, publication_id):
    # Pas de GET /admin/publications/{id} : on cherche dans la liste paginée.
    found = None
    page = 0
    while page < 20:
        listing = api_client.admin_get_publications(_token(request), page=page, size=50)
        if not listing.ok:
            messages.error(request, listing.error or api_client.UNAVAILABLE)
            return redirect("adminpanel:publication_list")
        for pub in listing.data.get("content") or []:
            if str(pub.get("id")) == str(publication_id):
                found = pub
                break
        if found:
            break
        if page + 1 >= (listing.data.get("totalPages") or 1):
            break
        page += 1
    if not found:
        messages.error(request, _("Publication introuvable."))
        return redirect("adminpanel:publication_list")

    if request.method == "POST":
        result = api_client.admin_update_publication(
            _token(request), publication_id, _publication_payload(request)
        )
        if result.ok:
            img_error = _upload_image_if_present(
                request, api_client.admin_upload_publication_image, publication_id
            )
            if img_error:
                messages.warning(
                    request,
                    _("Publication mise à jour, mais l'image n'a pas pu être enregistrée : ") + img_error,
                )
                return redirect("adminpanel:publication_edit", publication_id=publication_id)
            if _posted_image(request):
                messages.success(request, _("Publication mise à jour. Image enregistrée."))
            else:
                messages.success(request, _("Publication mise à jour."))
            return redirect("adminpanel:publication_list")
        messages.error(request, result.error or _("Mise à jour impossible."))
    return render(
        request,
        "adminpanel/publications/form.html",
        {"publication": found, "statuses": PUB_STATUSES},
    )


@admin_required_api
@require_POST
def publication_delete(request, publication_id):
    result = api_client.admin_delete_publication(_token(request), publication_id)
    if result.ok:
        messages.success(request, _("Publication supprimée."))
    else:
        messages.error(request, result.error or _("Suppression impossible."))
    return redirect("adminpanel:publication_list")


# ---------------------------------------------------------------------------
# Notifications
# ---------------------------------------------------------------------------

@admin_required_api
@require_http_methods(["GET"])
def notification_list(request):
    page = page_from_request(request)
    lu_param = request.GET.get("lu")
    lu = None
    if lu_param == "0":
        lu = False
    elif lu_param == "1":
        lu = True
    result = api_client.admin_get_notifications(_token(request), lu=lu, page=page - 1, size=20)
    notifications, pagination = [], None
    if result.ok and isinstance(result.data, dict):
        notifications = result.data.get("content") or []
        pagination = result.data
    else:
        messages.error(request, result.error or api_client.UNAVAILABLE)
    return render(
        request,
        "adminpanel/notifications/list.html",
        {
            "notifications": notifications,
            "pagination": pagination,
            "page": page,
            "lu": lu_param or "",
        },
    )


@admin_required_api
@require_POST
def notification_read(request, notification_id):
    result = api_client.admin_mark_notification_read(_token(request), notification_id)
    if result.ok:
        messages.success(request, _("Notification marquée comme lue."))
    else:
        messages.error(request, result.error or _("Action impossible."))
    next_url = request.POST.get("next") or reverse("adminpanel:notification_list")
    if next_url.startswith("/") and not next_url.startswith("//"):
        return redirect(next_url)
    return redirect("adminpanel:notification_list")


# ---------------------------------------------------------------------------
# Comptes utilisateurs
# ---------------------------------------------------------------------------

USER_ROLES = [
    ("ADMIN", _lazy("Admin")),
    ("USER", _lazy("Utilisateur")),
    ("SELLER", _lazy("Vendeur")),
]
VALID_ROLES = {code for code, _label in USER_ROLES}


@admin_required_api
@require_http_methods(["GET"])
def user_list(request):
    page = page_from_request(request)
    role = request.GET.get("role") or ""
    if role not in VALID_ROLES:
        role = ""
    result = api_client.admin_get_users(
        _token(request),
        role=role or None,
        page=page - 1,
        size=20,
    )
    users, pagination = [], None
    if result.ok and isinstance(result.data, dict):
        users = result.data.get("content") or []
        pagination = result.data
    else:
        messages.error(request, result.error or api_client.UNAVAILABLE)
    return render(
        request,
        "adminpanel/users/list.html",
        {
            "users": users,
            "pagination": pagination,
            "page": page,
            "role": role,
            "roles": USER_ROLES,
            "current_user_id": request.user_id,
        },
    )


@admin_required_api
@require_http_methods(["GET", "POST"])
def user_create(request):
    form = {
        "nom": "",
        "telephone": "",
        "role": "USER",
    }
    if request.method == "POST":
        form["nom"] = (request.POST.get("nom") or "").strip()
        form["telephone"] = (request.POST.get("telephone") or "").strip()
        form["role"] = request.POST.get("role") or "USER"
        password = request.POST.get("password") or ""
        if form["role"] not in VALID_ROLES:
            messages.error(request, _("Rôle invalide."))
        elif not form["nom"] or not form["telephone"] or not password:
            messages.error(request, _("Nom, téléphone et mot de passe sont obligatoires."))
        else:
            result = api_client.admin_create_user(
                _token(request),
                {
                    "nom": form["nom"],
                    "telephone": form["telephone"],
                    "password": password,
                    "role": form["role"],
                },
            )
            if result.ok:
                messages.success(request, _("Compte créé."))
                return redirect("adminpanel:user_list")
            messages.error(request, result.error or _("Création impossible."))
    return render(
        request,
        "adminpanel/users/form.html",
        {"form": form, "roles": USER_ROLES},
    )


@admin_required_api
@require_POST
def user_update_role(request, user_id):
    if str(user_id) == str(request.user_id):
        messages.error(request, _("Vous ne pouvez pas modifier votre propre rôle."))
        return redirect("adminpanel:user_list")
    new_role = request.POST.get("role") or ""
    if new_role not in VALID_ROLES:
        messages.error(request, _("Rôle invalide."))
        return redirect("adminpanel:user_list")
    result = api_client.admin_update_user_role(_token(request), user_id, new_role)
    if result.ok:
        messages.success(request, _("Rôle mis à jour."))
    else:
        messages.error(request, result.error or _("Mise à jour du rôle impossible."))
    return redirect("adminpanel:user_list")


@admin_required_api
@require_POST
def user_toggle_status(request, user_id):
    if str(user_id) == str(request.user_id):
        messages.error(request, _("Vous ne pouvez pas modifier le statut de votre propre compte."))
        return redirect("adminpanel:user_list")
    actuel = request.POST.get("actif") == "1"
    result = api_client.admin_update_user_status(_token(request), user_id, not actuel)
    if result.ok:
        messages.success(request, _("Compte bloqué.") if actuel else _("Compte débloqué."))
    else:
        messages.error(request, result.error or _("Mise à jour du statut impossible."))
    return redirect("adminpanel:user_list")


@admin_required_api
@require_POST
def user_delete(request, user_id):
    if str(user_id) == str(request.user_id):
        messages.error(request, _("Vous ne pouvez pas supprimer votre propre compte."))
        return redirect("adminpanel:user_list")
    result = api_client.admin_delete_user(_token(request), user_id)
    if result.ok:
        messages.success(request, _("Compte supprimé."))
    else:
        messages.error(request, result.error or _("Suppression impossible."))
    return redirect("adminpanel:user_list")
