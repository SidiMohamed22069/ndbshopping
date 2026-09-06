from django.urls import path

from . import views

app_name = "negotiations"

urlpatterns = [
    path("produit/<int:product_id>/proposer/", views.start, name="start"),
    path("", views.list_mine, name="list"),
    path("<int:negotiation_id>/", views.detail, name="detail"),
]
