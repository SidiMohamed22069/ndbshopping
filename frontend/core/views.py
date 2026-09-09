import logging

from django.contrib import messages
from django.shortcuts import render
from django.utils.translation import gettext as _
from django.views.decorators.http import require_GET, require_POST

from core.media_upload import json_error, json_ok
from core.utils import FEEDBACK_CATEGORY_CHOICES, get_client_ip, normalize_product_images
from services import api_client

logger = logging.getLogger(__name__)


@require_GET
def home(request):
    products = []
    try:
        products_result = api_client.get_products(page=0, size=12)
    except Exception:
        logger.exception("Échec des appels API sur la page d'accueil")
        messages.error(request, api_client.UNAVAILABLE)
        return render(
            request,
            "core/home.html",
            {"featured_products": [], "latest_products": []},
        )

    if products_result.ok and isinstance(products_result.data, dict):
        try:
            products = [
                normalize_product_images(p)
                for p in (products_result.data.get("content") or [])
                if isinstance(p, dict)
            ]
        except Exception:
            logger.exception("Impossible de normaliser les produits de la page d'accueil")
            messages.error(request, api_client.UNAVAILABLE)
    elif not products_result.ok:
        logger.error(
            "GET /products a échoué sur l'accueil (status=%s): %s",
            products_result.status,
            products_result.error,
        )
        if products_result.status != 0:
            messages.error(request, products_result.error or api_client.UNAVAILABLE)

    return render(
        request,
        "core/home.html",
        {
            # Pas de statut "vedette" côté produit : on met en avant les plus récents,
            # le reste alimente la grille "Dernières annonces".
            "featured_products": products[:6],
            "latest_products": products[6:],
        },
    )


@require_GET
def help_page(request):
    return render(request, "core/help.html")


def page_not_found(request, exception):
    return render(request, "404.html", status=404)


def server_error(request):
    return render(request, "500.html", status=500)


@require_GET
def category_attributes_json(request, category_id):
    """
    Proxy JSON pour le formulaire admin produits (évite les soucis CORS en local).
    Le JS appelle cette URL Django, qui relaie GET /api/categories/{id}/attributes.
    """
    from django.http import JsonResponse

    result = api_client.get_category_attributes(category_id)
    if not result.ok:
        return JsonResponse({"error": result.error}, status=result.status or 503)
    return JsonResponse(result.data, safe=False)


@require_POST
def feedback_submit(request):
    """Boîte à idées : widget global (base.html), accessible à 100% des visiteurs
    sans connexion. Rattaché au compte si connecté (request.jwt_token), sinon
    déposé anonymement — voir UserFeedbackService côté backend."""
    category = (request.POST.get("category") or "").strip().upper()
    message = (request.POST.get("message") or "").strip()
    contact_info = (request.POST.get("contact_info") or "").strip()

    if category not in FEEDBACK_CATEGORY_CHOICES:
        return json_error(_("Choisissez une catégorie."))
    if not message:
        return json_error(_("Le message est obligatoire."))

    result = api_client.create_feedback(
        category=category,
        message=message,
        contact_info=contact_info or None,
        token=request.jwt_token,
        client_ip=get_client_ip(request),
    )
    if result.ok:
        return json_ok()
    return json_error(result.error or str(api_client.UNAVAILABLE), status=result.status or 503)
