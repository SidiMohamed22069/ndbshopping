from django.urls import path

from . import views

app_name = "catalog"

urlpatterns = [
    path("produits/", views.product_list, name="product_list"),
    path("produits/<int:product_id>/", views.product_detail, name="product_detail"),
    path("produits/<int:product_id>/avis/", views.review_add, name="review_add"),
    path("produits/<int:product_id>/avis/supprimer/", views.review_delete, name="review_delete"),
    path("produits/<int:product_id>/favoris/ajouter/", views.favorite_add, name="favorite_add"),
    path("produits/<int:product_id>/favoris/retirer/", views.favorite_remove, name="favorite_remove"),
    path("categories/", views.categories, name="categories"),
    path("categories/<int:category_id>/", views.product_list, name="category_products"),
    path("actualites/", views.publication_list, name="publication_list"),
    path("actualites/<int:publication_id>/", views.publication_detail, name="publication_detail"),
]
