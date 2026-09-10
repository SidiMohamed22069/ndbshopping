from importlib import import_module
from unittest.mock import patch

from django.conf import settings
from django.test import TestCase
from django.urls import reverse

from services.api_client import ApiResult


class ForgotPasswordFlowTests(TestCase):
    """Flux 'Mot de passe oublié' côté Django : le code de réinitialisation
    (généré par le backend Spring, sans SMS payant) doit s'afficher sur l'écran
    de confirmation, et le nouveau mot de passe doit être transmis au backend
    pour être haché (BCrypt) en base."""

    PHONE = "24001234"

    def test_get_forgot_password_shows_form(self):
        response = self.client.get(reverse("accounts:forgot_password"))
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "telephone")

    @patch("accounts.views.api_client.request_password_reset")
    def test_valid_phone_shows_code_on_confirmation_screen(self, mock_request):
        mock_request.return_value = ApiResult(
            ok=True,
            status=200,
            data={"message": "Code généré", "code": "123456", "expiresAt": "2026-09-10T12:15:00Z"},
        )
        response = self.client.post(reverse("accounts:forgot_password"), {"telephone": self.PHONE})
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "123456")
        self.assertEqual(self.client.session.get("reset_phone"), self.PHONE)

    @patch("accounts.views.api_client.request_password_reset")
    def test_unknown_phone_shows_error_and_does_not_set_session(self, mock_request):
        mock_request.return_value = ApiResult(ok=False, status=404, data=None, error="Aucun compte avec ce numéro.")
        response = self.client.post(
            reverse("accounts:forgot_password"), {"telephone": "29990000"}, follow=True
        )
        self.assertEqual(response.status_code, 200)
        self.assertIsNone(self.client.session.get("reset_phone"))

    def test_confirm_page_redirects_to_forgot_password_without_session(self):
        response = self.client.get(reverse("accounts:password_reset_confirm"))
        self.assertRedirects(response, reverse("accounts:forgot_password"))

    def _with_reset_phone(self):
        """SESSION_ENGINE=signed_cookies encode toute la session dans le cookie
        lui-même : contrairement à un backend DB, `self.client.session` fige le
        cookie sur l'état lu à cet instant, donc les mutations faites après coup
        + `save()` ne sont jamais renvoyées au client de test. Il faut construire
        le cookie une fois la valeur posée."""
        engine = import_module(settings.SESSION_ENGINE)
        session = engine.SessionStore()
        session["reset_phone"] = self.PHONE
        session.save()
        self.client.cookies[settings.SESSION_COOKIE_NAME] = session.session_key

    @patch("accounts.views.api_client.confirm_password_reset")
    def test_mismatched_passwords_are_rejected_without_calling_api(self, mock_confirm):
        self._with_reset_phone()
        response = self.client.post(
            reverse("accounts:password_reset_confirm"),
            {"code": "123456", "new_password": "abcdef", "confirm_password": "different"},
        )
        self.assertEqual(response.status_code, 200)
        mock_confirm.assert_not_called()

    @patch("accounts.views.api_client.confirm_password_reset")
    def test_successful_reset_redirects_to_login_and_clears_session(self, mock_confirm):
        self._with_reset_phone()
        mock_confirm.return_value = ApiResult(
            ok=True, status=200, data={"message": "Mot de passe réinitialisé avec succès."}
        )
        response = self.client.post(
            reverse("accounts:password_reset_confirm"),
            {"code": "123456", "new_password": "abcdef", "confirm_password": "abcdef"},
        )
        self.assertRedirects(response, reverse("accounts:login"))
        mock_confirm.assert_called_once_with(self.PHONE, "123456", "abcdef")
        self.assertIsNone(self.client.session.get("reset_phone"))

    @patch("accounts.views.api_client.confirm_password_reset")
    def test_wrong_code_shows_error_and_keeps_session_for_retry(self, mock_confirm):
        self._with_reset_phone()
        mock_confirm.return_value = ApiResult(ok=False, status=400, data=None, error="Code incorrect.")
        response = self.client.post(
            reverse("accounts:password_reset_confirm"),
            {"code": "000000", "new_password": "abcdef", "confirm_password": "abcdef"},
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.client.session.get("reset_phone"), self.PHONE)
