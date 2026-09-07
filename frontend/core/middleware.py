"""
Attache à chaque requête les infos d'auth provenant de la session Django
(JWT Spring Boot), sans passer par django.contrib.auth.
"""
import time
import uuid

from services import api_client


class ApiAuthMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        request.jwt_token = request.session.get("jwt_token")
        request.user_role = request.session.get("user_role")
        request.user_nom = request.session.get("user_nom")
        request.user_id = request.session.get("user_id")
        request.is_authenticated_api = bool(request.jwt_token)
        request.is_admin_api = request.user_role == "ADMIN"
        return self.get_response(request)


class VisitorTrackingMiddleware:
    """Fréquentation temps réel (barre de stats du header) : un cookie visiteur
    anonyme stable + un ping throttlé vers l'API Spring Boot, qui tient à jour
    le "vu pour la dernière fois" en base (voir VisitorAnalyticsService côté
    backend). N'a pas besoin de session/DB Django : tout l'état "qui est en
    ligne" vit dans le backend, Django ne fait que déclencher le ping.

    Ne doit jamais casser ni ralentir sensiblement une page : le ping est
    throttlé (au plus un appel réseau toutes les `HEARTBEAT_THROTTLE_SECONDS`
    par visiteur) et `api_client.send_visitor_heartbeat` absorbe déjà toute
    erreur réseau (timeout court, cf. services/api_client.py).
    """

    VISITOR_COOKIE = "ndb_visitor_id"
    BEAT_COOKIE = "ndb_last_beat"
    VISITOR_COOKIE_MAX_AGE = 60 * 60 * 24 * 365  # 1 an
    HEARTBEAT_THROTTLE_SECONDS = 45

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        response = self.get_response(request)
        try:
            self._track(request, response)
        except Exception:
            # La fréquentation est un bonus d'affichage : une erreur ici ne doit
            # jamais faire échouer la page.
            pass
        return response

    def _track(self, request, response):
        visitor_id = request.COOKIES.get(self.VISITOR_COOKIE)
        is_new_visitor_cookie = not visitor_id
        if is_new_visitor_cookie:
            visitor_id = uuid.uuid4().hex

        user_id = getattr(request, "user_id", None)
        authenticated = bool(getattr(request, "is_authenticated_api", False))
        # "user:{id}" reste stable si le même compte se reconnecte ailleurs ;
        # "anon:{cookie}" tant qu'il n'est pas connecté.
        visitor_key = f"user:{user_id}" if authenticated and user_id else f"anon:{visitor_id}"

        now = time.time()
        due_for_heartbeat = True
        last_beat_raw = request.COOKIES.get(self.BEAT_COOKIE)
        if last_beat_raw:
            try:
                due_for_heartbeat = (now - float(last_beat_raw)) >= self.HEARTBEAT_THROTTLE_SECONDS
            except ValueError:
                due_for_heartbeat = True

        if due_for_heartbeat:
            api_client.send_visitor_heartbeat(visitor_key, authenticated)
            response.set_cookie(
                self.BEAT_COOKIE,
                str(now),
                max_age=self.HEARTBEAT_THROTTLE_SECONDS * 4,
                httponly=True,
                samesite="Lax",
            )

        if is_new_visitor_cookie:
            response.set_cookie(
                self.VISITOR_COOKIE,
                visitor_id,
                max_age=self.VISITOR_COOKIE_MAX_AGE,
                httponly=True,
                samesite="Lax",
            )
