#!/bin/bash
# Script pour ajouter 3 produits dans chaque catégorie (Vêtements, Hôtels, Voitures)
# À exécuter sur le VPS, depuis n'importe quel dossier.

set -e

API="http://127.0.0.1:8085/api"
TELEPHONE="37565537"
PASSWORD="password123"

echo "=== 1. Connexion admin ==="
LOGIN_RESPONSE=$(curl -s -X POST "$API/auth/register-or-login" \
  -H "Content-Type: application/json" \
  -d "{\"nom\":\"Administrateur\",\"telephone\":\"$TELEPHONE\",\"password\":\"$PASSWORD\"}")

echo "$LOGIN_RESPONSE"

# Si la réponse contient directement un token, on le récupère.
TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo ""
  echo "Aucun token direct reçu — un code OTP a probablement été envoyé par SMS au $TELEPHONE."
  read -p "Entrez le code OTP reçu : " OTP_CODE
  VERIFY_RESPONSE=$(curl -s -X POST "$API/auth/verify-otp" \
    -H "Content-Type: application/json" \
    -d "{\"telephone\":\"$TELEPHONE\",\"code\":\"$OTP_CODE\"}")
  echo "$VERIFY_RESPONSE"
  TOKEN=$(echo "$VERIFY_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
fi

if [ -z "$TOKEN" ]; then
  echo "ERREUR : impossible de récupérer un token. Vérifiez les identifiants ou le code OTP."
  exit 1
fi

echo ""
echo "Token récupéré avec succès."
echo ""

echo "=== 2. Récupération des catégories existantes ==="
CATEGORIES=$(curl -s "$API/categories")
echo "$CATEGORIES"
echo ""
echo "Repérez ci-dessus les 'id' exacts de Vêtements, Hôtels, Voitures, et modifiez"
echo "les variables CATEGORY_VETEMENTS / CATEGORY_HOTELS / CATEGORY_VOITURES ci-dessous si besoin."
echo ""

# Ajustez ces IDs si différents de ceux observés précédemment en base (1, 2, 3)
CATEGORY_VETEMENTS=1
CATEGORY_HOTELS=2
CATEGORY_VOITURES=3

read -p "Appuyez sur Entrée pour continuer avec ces IDs (Vêtements=$CATEGORY_VETEMENTS, Hôtels=$CATEGORY_HOTELS, Voitures=$CATEGORY_VOITURES), ou Ctrl+C pour annuler et corriger le script..."

create_product() {
  local nom="$1"
  local description="$2"
  local prix="$3"
  local stock="$4"
  local categoryId="$5"

  RESPONSE=$(curl -s -X POST "$API/admin/products" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{
      \"nom\": \"$nom\",
      \"description\": \"$description\",
      \"prix\": $prix,
      \"stock\": $stock,
      \"categoryId\": $categoryId,
      \"statut\": \"PUBLIE\",
      \"sourceOrigine\": \"MANUEL\"
    }")
  echo "-> $nom : $RESPONSE"
  echo ""
}

echo "=== 3. Création des produits — Vêtements ==="
create_product "Boubou traditionnel homme" "Boubou brodé, coupe traditionnelle mauritanienne, plusieurs coloris disponibles." 8500 15 "$CATEGORY_VETEMENTS"
create_product "Chemise homme manches longues" "Chemise en coton, coupe droite, idéale pour le quotidien à Nouadhibou." 3200 25 "$CATEGORY_VETEMENTS"
create_product "Robe été femme" "Robe légère en tissu respirant, parfaite pour la chaleur côtière." 4500 20 "$CATEGORY_VETEMENTS"

echo "=== 4. Création des produits — Hôtels ==="
create_product "Hôtel El Medina" "Chambre double avec vue sur mer, petit-déjeuner inclus, wifi gratuit." 15000 5 "$CATEGORY_HOTELS"
create_product "Hôtel Nouadhibou Palace" "Chambre standard climatisée, proche du port et du centre-ville." 12000 8 "$CATEGORY_HOTELS"
create_product "Auberge du Port" "Chambre simple économique, idéale pour un court séjour." 6000 10 "$CATEGORY_HOTELS"

echo "=== 5. Création des produits — Voitures ==="
create_product "Toyota Hilux 2020" "Pick-up 4x4, excellent état, faible kilométrage, révisé récemment." 4500000 1 "$CATEGORY_VOITURES"
create_product "Peugeot 206 occasion" "Citadine fiable, entretien à jour, idéale pour la ville." 1200000 1 "$CATEGORY_VOITURES"
create_product "Renault Duster 2019" "SUV compact, bon état général, climatisation fonctionnelle." 3200000 1 "$CATEGORY_VOITURES"

echo ""
echo "=== Terminé ==="
echo "9 produits créés (3 par catégorie), statut PUBLIE — visibles immédiatement sur le catalogue public."
echo "Pensez à ajouter une image à chacun depuis l'espace admin (https://ndbshopping.duckdns.org/admin-ndb/produits/)"
echo "puisque ce script ne gère pas l'upload d'image (nécessite des fichiers image sur le disque)."
