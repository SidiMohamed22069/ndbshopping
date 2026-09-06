import logging

from django.conf import settings

from cart.utils import cart_quantity
from core.utils import flatten_categories, normalize_category_image
from services import api_client

logger = logging.getLogger(__name__)


def storefront(request):
    """Contexte partagé : nav, panier, URLs médias, auth session."""
    try:
        categories_result = api_client.get_categories()
    except Exception:
        logger.exception("Échec GET /categories (contexte storefront)")
        categories_result = api_client.ApiResult(ok=False, status=0, data=None, error=str(api_client.UNAVAILABLE))

    if not categories_result.ok:
        logger.error(
            "GET /categories a échoué (status=%s): %s",
            categories_result.status,
            categories_result.error,
        )

    categories = categories_result.data if categories_result.ok and isinstance(categories_result.data, list) else []
    try:
        categories = [normalize_category_image(cat) for cat in categories if isinstance(cat, dict)]
    except Exception:
        logger.exception("Impossible de normaliser les catégories du storefront")
        categories = []

    featured = []
    if not request.path.startswith("/admin-ndb/"):
        try:
            featured_result = api_client.get_featured_publications()
        except Exception:
            logger.exception("Échec GET /publications/mises-en-avant")
        else:
            if featured_result.ok and isinstance(featured_result.data, list):
                featured = featured_result.data
            elif not featured_result.ok:
                logger.error(
                    "GET /publications/mises-en-avant a échoué (status=%s): %s",
                    featured_result.status,
                    featured_result.error,
                )

    token = request.session.get("jwt_token")
    unread_notifications_client = 0
    if token:
        try:
            unread_result = api_client.get_client_unread_count(token)
        except Exception:
            logger.exception("Échec GET /notifications/count-non-lues (contexte storefront)")
        else:
            if unread_result.ok and isinstance(unread_result.data, dict):
                unread_notifications_client = unread_result.data.get("count") or 0

    return {
        "nav_categories": categories,
        "nav_categories_flat": flatten_categories(categories),
        "featured_publications": featured,
        "cart_count": cart_quantity(request.session),
        "is_authenticated_api": bool(token),
        "is_admin_api": request.session.get("user_role") == "ADMIN",
        "user_nom": request.session.get("user_nom") or "",
        "current_user_id": request.session.get("user_id"),
        "unread_notifications_client": unread_notifications_client,
        "MEDIA_BACKEND_URL": settings.MEDIA_BACKEND_URL.rstrip("/"),
        "PUBLIC_BACKEND_HOST": settings.PUBLIC_BACKEND_HOST,
        "api_unavailable": not categories_result.ok,
    }


def admin_badges(request):
    """Badges admin : notifications non lues + produits EN_ATTENTE + négociations en attente."""
    empty = {"unread_notifications": 0, "pending_products_count": 0, "pending_negotiations_count": 0}
    if not request.path.startswith("/admin-ndb/"):
        return empty
    token = request.session.get("jwt_token")
    if not token or request.session.get("user_role") != "ADMIN":
        return empty
    unread = 0
    result = api_client.admin_unread_count(token)
    if result.ok and isinstance(result.data, dict):
        unread = result.data.get("count") or 0
    pending = 0
    pending_result = api_client.admin_get_products(token, statut="EN_ATTENTE", page=0, size=1)
    if pending_result.ok and isinstance(pending_result.data, dict):
        pending = pending_result.data.get("totalElements") or 0
    pending_negotiations = 0
    negotiations_result = api_client.admin_negotiations_awaiting_count(token)
    if negotiations_result.ok and isinstance(negotiations_result.data, dict):
        pending_negotiations = negotiations_result.data.get("count") or 0
    return {
        "unread_notifications": unread,
        "pending_products_count": pending,
        "pending_negotiations_count": pending_negotiations,
    }
