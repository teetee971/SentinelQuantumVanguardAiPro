# API Backend Requirements

## Vue d'ensemble

Le frontend MVP attend les endpoints suivants de l'API backend. Toutes les requêtes utilisent JSON et incluent le token d'authentification (si disponible) dans le header `Authorization: Bearer <token>`.

## Base URL

Configuration via variable d'environnement : `VITE_API_BASE_URL`

Exemple : `https://api.example.com/api`

## Authentication

### POST /auth/login
Connexion utilisateur

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "token": "jwt_token_here",
  "user": {
    "id": "user123",
    "name": "John Doe",
    "email": "user@example.com",
    "region": "metropole"
  }
}
```

### POST /auth/register
Inscription utilisateur

**Request Body:**
```json
{
  "name": "John Doe",
  "email": "user@example.com",
  "password": "password123",
  "region": "metropole"
}
```

**Response (201 Created):**
```json
{
  "token": "jwt_token_here",
  "user": {
    "id": "user123",
    "name": "John Doe",
    "email": "user@example.com",
    "region": "metropole"
  }
}
```

## Products

### GET /products/search
Recherche de produits

**Query Parameters:**
- `q` (required): Terme de recherche
- `region` (optional): Code région (metropole, guadeloupe, martinique, guyane, reunion, mayotte)

**Example:** `/products/search?q=lait&region=metropole`

**Response (200 OK):**
```json
{
  "products": [
    {
      "id": "prod123",
      "name": "Lait demi-écrémé 1L",
      "category": "Produits laitiers",
      "image": "https://example.com/images/lait.jpg",
      "minPrice": 1.20
    }
  ],
  "total": 15,
  "page": 1
}
```

### GET /products/:id
Détails d'un produit

**Response (200 OK):**
```json
{
  "id": "prod123",
  "name": "Lait demi-écrémé 1L",
  "category": "Produits laitiers",
  "description": "Lait demi-écrémé UHT",
  "image": "https://example.com/images/lait.jpg",
  "brand": "Marque X"
}
```

### GET /products/:id/prices
Prix d'un produit par région

**Query Parameters:**
- `region` (optional): Code région

**Example:** `/products/prod123/prices?region=metropole`

**Response (200 OK):**
```json
{
  "productId": "prod123",
  "region": "metropole",
  "prices": [
    {
      "retailer": "Carrefour",
      "price": 1.25,
      "lastUpdated": "2024-12-20T10:00:00Z"
    },
    {
      "retailer": "Leclerc",
      "price": 1.18,
      "lastUpdated": "2024-12-20T09:30:00Z"
    },
    {
      "retailer": "Auchan",
      "price": 1.30,
      "lastUpdated": "2024-12-20T08:00:00Z"
    }
  ]
}
```

## Price Comparison

### GET /prices/compare
Comparer les prix de plusieurs produits

**Query Parameters:**
- `products[]` (required, multiple): IDs des produits à comparer
- `region` (optional): Code région

**Example:** `/prices/compare?products[]=prod123&products[]=prod456&region=guadeloupe`

**Response (200 OK):**
```json
{
  "region": "guadeloupe",
  "comparisons": [
    {
      "productId": "prod123",
      "productName": "Lait demi-écrémé 1L",
      "prices": [
        {
          "retailer": "Carrefour",
          "price": 1.45
        }
      ]
    }
  ]
}
```

## User Management

### GET /user/profile
Récupérer le profil utilisateur (authentifié)

**Headers:**
```
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "id": "user123",
  "name": "John Doe",
  "email": "user@example.com",
  "region": "metropole",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

### PUT /user/profile
Mettre à jour le profil utilisateur (authentifié)

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "name": "John Smith",
  "email": "newmail@example.com",
  "region": "reunion"
}
```

**Response (200 OK):**
```json
{
  "id": "user123",
  "name": "John Smith",
  "email": "newmail@example.com",
  "region": "reunion",
  "updatedAt": "2024-12-20T12:00:00Z"
}
```

### PUT /user/region
Mettre à jour uniquement la région (authentifié)

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "region": "martinique"
}
```

**Response (200 OK):**
```json
{
  "region": "martinique",
  "updatedAt": "2024-12-20T12:00:00Z"
}
```

## Retailers

### GET /retailers
Liste des enseignes/magasins

**Query Parameters:**
- `region` (optional): Filtrer par région

**Example:** `/retailers?region=guadeloupe`

**Response (200 OK):**
```json
{
  "retailers": [
    {
      "id": "retailer1",
      "name": "Carrefour",
      "logo": "https://example.com/logos/carrefour.png",
      "regions": ["metropole", "guadeloupe", "martinique"]
    },
    {
      "id": "retailer2",
      "name": "Leclerc",
      "logo": "https://example.com/logos/leclerc.png",
      "regions": ["metropole", "reunion"]
    }
  ]
}
```

## Error Responses

Tous les endpoints peuvent retourner les erreurs suivantes:

### 400 Bad Request
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request parameters"
  }
}
```

### 401 Unauthorized
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Authentication required"
  }
}
```

### 404 Not Found
```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Resource not found"
  }
}
```

### 500 Internal Server Error
```json
{
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "An internal error occurred"
  }
}
```

## CORS Configuration

Le backend doit autoriser les requêtes depuis le domaine frontend:

```
Access-Control-Allow-Origin: https://votre-frontend.pages.dev
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
Access-Control-Allow-Credentials: true
```

## Codes Région Supportés

- `metropole` - Métropole
- `guadeloupe` - Guadeloupe
- `martinique` - Martinique
- `guyane` - Guyane
- `reunion` - Réunion
- `mayotte` - Mayotte

## Notes d'Implémentation

1. **Authentication**: JWT tokens recommandés
2. **Rate Limiting**: Implémenter une limitation pour éviter les abus
3. **Pagination**: Les listes devraient supporter la pagination (même si non utilisée dans MVP)
4. **Cache**: Mettre en cache les prix pour améliorer les performances
5. **Timestamps**: Utiliser ISO 8601 format (UTC)
6. **Images**: URLs complètes avec HTTPS
7. **Validation**: Valider toutes les entrées utilisateur
8. **Security**: HTTPS obligatoire, tokens sécurisés, sanitization des données

## Priorisation des Endpoints

**Phase 1 (MVP Critique):**
- POST /auth/login
- POST /auth/register
- GET /products/search
- GET /products/:id/prices

**Phase 2 (MVP Complet):**
- GET /products/:id
- GET /user/profile
- PUT /user/profile
- GET /retailers

**Phase 3 (Améliorations):**
- GET /prices/compare
- PUT /user/region
