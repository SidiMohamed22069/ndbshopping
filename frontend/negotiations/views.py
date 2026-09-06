from decimal import Decimal, InvalidOperation

from django.contrib import messages
from django.shortcuts import redirect, render
from django.utils.translation import gettext as _
from django.views.decorators.http import require_http_methods

from core.decorators import login_required_api
from core.utils import normalize_product_images, page_from_request
from services import api_client


def _parse_price(raw: str):
    try:
        return str(Decimal((raw or "").strip()))
    except (InvalidOperation, ValueError):
        return None


@login_required_api
@require_http_methods(["GET", "POST"])
def start(request, product_id):
    """Propose un prix pour un produit, ou rejoint la négociation déjà ouverte."""
    active = api_client.get_active_negotiation_for_product(request.jwt_token, product_id)
    if active.ok and isinstance(active.data, dict):
        return redirect("negotiations:detail", negotiation_id=active.data["id"])

    product_result = api_client.get_product(product_id)
    if not product_result.ok or not isinstance(product_result.data, dict):
        messages.error(request, product_result.error or _("Produit introuvable."))
        return redirect("catalog:product_list")
    product = normalize_product_images(product_result.data)

    if request.method == "POST":
        price = _parse_price(request.POST.get("proposed_price"))
        message = (request.POST.get("message") or "").strip()
        if price is None:
            messages.error(request, _("Indiquez un prix valide."))
        else:
            result = api_client.start_negotiation(request.jwt_token, product_id, price, message or None)
            if result.ok and isinstance(result.data, dict):
                messages.success(request, _("Votre proposition a été envoyée à l'équipe NDB Shopping."))
                return redirect("negotiations:detail", negotiation_id=result.data["id"])
            messages.error(request, result.error or _("Impossible d'envoyer la proposition."))

    return render(request, "negotiations/start.html", {"product": product})


@login_required_api
@require_http_methods(["GET"])
def list_mine(request):
    page = page_from_request(request)
    result = api_client.get_my_negotiations(request.jwt_token, page=page - 1, size=10)
    items, pagination = [], None
    if result.ok and isinstance(result.data, dict):
        items = result.data.get("content") or []
        pagination = result.data
    else:
        messages.error(request, result.error or api_client.UNAVAILABLE)
    return render(
        request,
        "negotiations/list.html",
        {"negotiations": items, "pagination": pagination, "page": page},
    )


@login_required_api
@require_http_methods(["GET", "POST"])
def detail(request, negotiation_id):
    if request.method == "POST":
        action = request.POST.get("action")
        if action == "message":
            text = (request.POST.get("message") or "").strip()
            if not text:
                messages.error(request, _("Message vide."))
            else:
                result = api_client.add_negotiation_message(request.jwt_token, negotiation_id, text)
                if not result.ok:
                    messages.error(request, result.error or _("Message non envoyé."))
        elif action == "offer":
            price = _parse_price(request.POST.get("proposed_price"))
            message = (request.POST.get("message") or "").strip()
            if price is None:
                messages.error(request, _("Indiquez un prix valide."))
            else:
                result = api_client.add_negotiation_offer(request.jwt_token, negotiation_id, price, message or None)
                if result.ok:
                    messages.success(request, _("Votre contre-offre a été envoyée."))
                else:
                    messages.error(request, result.error or _("Impossible d'envoyer la contre-offre."))
        elif action == "accept":
            result = api_client.accept_negotiation(request.jwt_token, negotiation_id)
            if result.ok:
                messages.success(request, _("Prix accepté ! Vous pouvez l'ajouter au panier."))
            else:
                messages.error(request, result.error or _("Impossible d'accepter cette offre."))
        elif action == "reject":
            message = (request.POST.get("message") or "").strip()
            result = api_client.reject_negotiation(request.jwt_token, negotiation_id, message or None)
            if result.ok:
                messages.success(request, _("Négociation refusée."))
            else:
                messages.error(request, result.error or _("Impossible de refuser."))
        return redirect("negotiations:detail", negotiation_id=negotiation_id)

    result = api_client.get_negotiation(request.jwt_token, negotiation_id)
    if not result.ok or not isinstance(result.data, dict):
        messages.error(request, result.error or _("Négociation introuvable."))
        return redirect("negotiations:list")
    negotiation = result.data
    can_accept = negotiation.get("derniereActionPar") != "USER"
    return render(
        request,
        "negotiations/detail.html",
        {"negotiation": negotiation, "can_accept": can_accept},
    )
