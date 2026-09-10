from importlib import import_module
from unittest.mock import patch

from django.conf import settings
from django.test import TestCase
from django.urls import reverse

from services.api_client import ApiResult


def _admin_session(client):
    """Construit le cookie de session APRES avoir posé les clés : le projet
    utilise SESSION_ENGINE=signed_cookies (session encodée dans le cookie lui-
    même), donc `client.session` (qui fige le cookie sur l'état courant) suivi
    de mutations + `save()` ne suffit pas comme pour un backend DB classique."""
    engine = import_module(settings.SESSION_ENGINE)
    session = engine.SessionStore()
    session["jwt_token"] = "fake-admin-token"
    session["user_id"] = 1
    session["user_role"] = "ADMIN"
    session.save()
    client.cookies[settings.SESSION_COOKIE_NAME] = session.session_key


class FeedbackListViewTests(TestCase):
    """Vérifie que les avis (connectés et anonymes) remontent dans l'admin."""

    def setUp(self):
        _admin_session(self.client)

    @patch("adminpanel.views.api_client.admin_get_feedbacks")
    def test_anonymous_and_member_feedback_both_rendered(self, mock_get_feedbacks):
        mock_get_feedbacks.return_value = ApiResult(
            ok=True,
            status=200,
            data={
                "content": [
                    {
                        "id": 1,
                        "anonymous": True,
                        "userId": None,
                        "userNom": None,
                        "category": "BUG",
                        "message": "Site lent sur mobile",
                        "statut": "NOUVEAU",
                        "createdAt": "2026-09-10T10:00:00Z",
                    },
                    {
                        "id": 2,
                        "anonymous": False,
                        "userId": 42,
                        "userNom": "Fatimetou",
                        "category": "SUGGESTION",
                        "message": "Ajoutez un mode sombre",
                        "statut": "NOUVEAU",
                        "createdAt": "2026-09-10T11:00:00Z",
                    },
                ],
                "totalElements": 2,
                "totalPages": 1,
                "number": 0,
            },
        )
        response = self.client.get(reverse("adminpanel:feedback_list"), HTTP_ACCEPT_LANGUAGE="fr")
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "Site lent sur mobile")
        self.assertContains(response, "Ajoutez un mode sombre")
        self.assertContains(response, "Visiteur anonyme")
        self.assertContains(response, "Fatimetou")


class ProductListViewTests(TestCase):
    """Vérifie que les produits soumis par un utilisateur remontent dans la liste
    globale admin avec le détail du vendeur, et que les actions de modération
    (Valider/Rejeter/Modifier/Supprimer) sont disponibles."""

    def setUp(self):
        _admin_session(self.client)

    @patch("adminpanel.views.api_client.admin_get_users")
    @patch("adminpanel.views.api_client.get_categories")
    @patch("adminpanel.views.api_client.admin_get_products")
    def test_user_submitted_product_appears_with_seller_and_moderation_actions(
        self, mock_get_products, mock_get_categories, mock_get_users
    ):
        mock_get_products.return_value = ApiResult(
            ok=True,
            status=200,
            data={
                "content": [
                    {
                        "id": 10,
                        "nom": "Canape soumis par un client",
                        "categoryNom": "Meubles",
                        "prix": 25000,
                        "statut": "EN_ATTENTE",
                        "soumisParUserId": 42,
                    },
                    {
                        "id": 11,
                        "nom": "Produit ajoute par admin",
                        "categoryNom": "Meubles",
                        "prix": 5000,
                        "statut": "PUBLIE",
                        "soumisParUserId": None,
                    },
                ],
                "totalElements": 2,
                "totalPages": 1,
                "number": 0,
            },
        )
        mock_get_categories.return_value = ApiResult(ok=True, status=200, data=[])
        mock_get_users.return_value = ApiResult(
            ok=True,
            status=200,
            data={
                "content": [{"id": 42, "nom": "Fatimetou", "telephone": "22200000000"}],
                "totalPages": 1,
            },
        )

        response = self.client.get(reverse("adminpanel:product_list"))

        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "Canape soumis par un client")
        self.assertContains(response, "Fatimetou")
        self.assertContains(response, "Produit ajoute par admin")
        self.assertContains(response, reverse("adminpanel:product_validate", args=[10]))
        self.assertContains(response, reverse("adminpanel:product_reject", args=[10]))
        self.assertContains(response, reverse("adminpanel:product_edit", args=[10]))
        self.assertContains(response, reverse("adminpanel:product_delete", args=[10]))
