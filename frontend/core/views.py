import logging

from django.contrib import messages
from django.shortcuts import render
from django.views.decorators.http import require_GET

from core.utils import normalize_product_images
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
