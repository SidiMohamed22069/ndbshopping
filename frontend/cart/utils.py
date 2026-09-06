"""
Panier invité : liste [{ "product_id": int, "quantite": int, "negotiation_id": int|None }, ...]
dans la session.

Les prix ne sont JAMAIS stockés ici : l'affichage du total rappelle
GET /products/{id} pour le tarif à jour (ou GET /negotiations/{id} si la ligne
est liée à une négociation acceptée). Une fois connecté, on synchronise vers
POST /api/cart/sync (le backend remplace le panier serveur et revalide le
prix négocié côté serveur).
"""
from services import api_client

CART_SESSION_KEY = "cart"


def get_cart(session) -> list[dict]:
    cart = session.get(CART_SESSION_KEY)
    if not isinstance(cart, list):
        return []
    cleaned = []
    for item in cart:
        try:
            negotiation_id = item.get("negotiation_id")
            cleaned.append(
                {
                    "product_id": int(item["product_id"]),
                    "quantite": max(1, int(item["quantite"])),
                    "negotiation_id": int(negotiation_id) if negotiation_id else None,
                }
            )
        except (KeyError, TypeError, ValueError):
            continue
    return cleaned


def save_cart(session, cart: list[dict]) -> None:
    session[CART_SESSION_KEY] = cart
    session.modified = True


def cart_quantity(session) -> int:
    return sum(item["quantite"] for item in get_cart(session))


def add_item(session, product_id: int, quantite: int = 1) -> list[dict]:
    cart = get_cart(session)
    for item in cart:
        if item["product_id"] == product_id:
            item["quantite"] += max(1, quantite)
            save_cart(session, cart)
            return cart
    cart.append({"product_id": product_id, "quantite": max(1, quantite), "negotiation_id": None})
    save_cart(session, cart)
    return cart


def add_negotiated_item(session, product_id: int, negotiation_id: int, quantite: int = 1) -> list[dict]:
    """Ajoute (ou met à jour) la ligne panier pour qu'elle utilise le prix négocié accepté."""
    cart = get_cart(session)
    for item in cart:
        if item["product_id"] == product_id:
            item["negotiation_id"] = negotiation_id
            item["quantite"] = max(item["quantite"], max(1, quantite))
            save_cart(session, cart)
            return cart
    cart.append({"product_id": product_id, "quantite": max(1, quantite), "negotiation_id": negotiation_id})
    save_cart(session, cart)
    return cart


def update_item(session, product_id: int, quantite: int) -> list[dict]:
    cart = get_cart(session)
    if quantite <= 0:
        cart = [i for i in cart if i["product_id"] != product_id]
    else:
        found = False
        for item in cart:
            if item["product_id"] == product_id:
                item["quantite"] = quantite
                found = True
                break
        if not found:
            cart.append({"product_id": product_id, "quantite": quantite, "negotiation_id": None})
    save_cart(session, cart)
    return cart


def clear_negotiation(session, product_id: int) -> list[dict]:
    """Repasse une ligne au prix catalogue (négociation devenue invalide entre-temps)."""
    cart = get_cart(session)
    for item in cart:
        if item["product_id"] == product_id:
            item["negotiation_id"] = None
            break
    save_cart(session, cart)
    return cart


def remove_item(session, product_id: int) -> list[dict]:
    cart = [i for i in get_cart(session) if i["product_id"] != product_id]
    save_cart(session, cart)
    return cart


def clear_cart(session) -> None:
    session[CART_SESSION_KEY] = []
    session.modified = True


def to_sync_payload(session) -> list[dict]:
    """Format attendu par CartSyncRequest : productId + quantite (+ negotiationId si négocié)."""
    payload = []
    for i in get_cart(session):
        line = {"productId": i["product_id"], "quantite": i["quantite"]}
        if i.get("negotiation_id"):
            line["negotiationId"] = i["negotiation_id"]
        payload.append(line)
    return payload


def sync_if_authenticated(request) -> None:
    """Pousse le panier session vers le backend si un JWT est présent."""
    token = request.session.get("jwt_token")
    if not token:
        return
    api_client.sync_cart(token, to_sync_payload(request.session))
