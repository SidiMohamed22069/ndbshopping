# NDB SHOPPING — Backend (Spring Boot)

API REST de la marketplace **NDB SHOPPING** (Nouadhibou).

Stack : Java 21, Spring Boot 3.4, PostgreSQL, Redis, JWT, WebSocket (STOMP), SMS OTP Chinguisoft.

## Prérequis

- Docker et Docker Compose
- (optionnel) JDK 21 + Maven, pour lancer l'API hors Docker

## Démarrage avec Docker

```bash
cp .env.example .env
# Éditez .env : JWT_SECRET (min. 32 caractères) et, en production, les clés Chinguisoft

docker compose up --build
```

Services :

| Service    | URL / port        |
|------------|-------------------|
| API        | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:5432    |
| Redis      | localhost:6379    |
| Images     | http://localhost:8080/media/** |

Compte admin de démo (créé au premier démarrage) :

- Téléphone : `20000001`
- Mot de passe : `admin123`
- Rôle : `ADMIN` (déjà vérifié)

## Configuration SMS (Chinguisoft)

Variables dans `.env` (jamais dans le code) :

```
SMS_VALIDATION_KEY=...
SMS_TOKEN=...
SMS_MOCK_ENABLED=false
```

En local, `SMS_MOCK_ENABLED=true` évite d'appeler Chinguisoft : le code OTP est toujours `123456`.

En production, laissez `SMS_MOCK_ENABLED=false` et renseignez les vraies clés. L'API appelle :

`POST https://chinguisoft.com/api/sms/validation/{SMS_VALIDATION_KEY}`

avec le header `Validation-token`. Le code renvoyé est stocké 10 minutes dans Redis (`otp:{telephone}`).

Si le solde Chinguisoft passe sous le seuil (défaut : 10), une notification admin `SOLDE_SMS_BAS` est créée.

## Authentification (téléphone + OTP)

```bash
# 1. Inscription ou connexion → envoi OTP
curl -X POST http://localhost:8080/api/auth/register-or-login \
  -H "Content-Type: application/json" \
  -d '{"nom":"Test User","telephone":"20000002","password":"test1234"}'

# 2. Vérification OTP (en mock : 123456) → JWT
curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"telephone":"20000002","code":"123456"}'
```

Le numéro doit être au format mauritanien : **8 chiffres commençant par 2, 3 ou 4**, sans indicatif +222.

Utilisez ensuite : `Authorization: Bearer <token>`.

Endpoints `/api/admin/**` : JWT + rôle `ADMIN`.

## Notifications admin (WebSocket)

Pas de WhatsApp. À chaque commande :

1. Une ligne est persistée dans `notifications`
2. Un message STOMP est poussé sur `/topic/admin-notifications`

Connexion (SockJS) : `http://localhost:8080/ws`

## Import produits

- **URL** `POST /api/admin/products/import/url` : stub MVP (brouillon + `sourceUrl`). Pas de scraping Facebook (CGU Meta).
- **CSV** `POST /api/admin/products/import/csv` : colonnes `nom,description,prix,stock,categoryId`. Produits créés en `BROUILLON`.

## Images

Upload admin : `POST /api/admin/products/{id}/images` (multipart, jpg/png/webp, max 5 Mo).

Stockage local : `UPLOAD_DIR` (défaut Docker `/app/uploads`). Servies en dev via `/media/**`. En production, préférez Nginx pour servir ce dossier.

## Lancer sans Docker (dev)

PostgreSQL et Redis doivent tourner (vous pouvez ne lancer que ces services : `docker compose up postgres redis`).

```bash
export DB_HOST=localhost DB_NAME=ndbshopping DB_USERNAME=ndbshopping DB_PASSWORD=changeme
export REDIS_HOST=localhost JWT_SECRET=changeme_super_secret_key_min_32_chars_ici
export SMS_MOCK_ENABLED=true
./mvnw spring-boot:run
```

Si le wrapper Maven n'est pas présent : `mvn spring-boot:run`.

## CORS

`FRONTEND_URL` (défaut `http://localhost:8000` pour Django). Plusieurs origines : séparées par des virgules.
