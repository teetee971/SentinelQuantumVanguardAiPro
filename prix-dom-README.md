# Module Prix DOM - Surveillance des Prix OPMR

## Description

Le module Prix DOM permet de surveiller les prix des produits alimentaires dans les territoires d'outre-mer français via l'API OPMR (Observatoire des prix et des marges) du gouvernement français.

## Utilisation

### Import du module

```javascript
import { fetchPrixDOM } from './prix-dom.js';
```

### Fonction principale

#### `fetchPrixDOM(produit, territoire)`

Récupère les prix d'un produit dans un territoire donné.

**Paramètres:**
- `produit` (string) : Le nom du produit à rechercher (ex: "pain", "lait", "riz")
- `territoire` (string) : Le territoire d'outre-mer (ex: "Martinique", "Guadeloupe", "Guyane", "Réunion")

**Retour:**
- Array d'objets contenant :
  - `enseigne` : Nom de l'enseigne/magasin
  - `prix` : Prix du produit en euros
  - `date` : Date de relevé du prix

**Exemple:**
```javascript
const prix = await fetchPrixDOM('pain', 'Martinique');
console.log(prix);
// [
//   { enseigne: "Super U", prix: "1.20", date: "2024-01-15" },
//   { enseigne: "Carrefour", prix: "1.35", date: "2024-01-15" }
// ]
```

## Gestion des erreurs

La fonction gère automatiquement les erreurs réseau et d'API :
- En cas d'erreur, retourne un tableau vide `[]`
- Affiche les erreurs dans la console avec le préfixe "Erreur OPMR:"

## Intégration avec Sentinel

Ce module s'intègre parfaitement dans l'écosystème Sentinel Quantum Vanguard AI Pro en tant que module de surveillance OSINT spécialisé dans le monitoring économique des territoires d'outre-mer.

## Territoires supportés

- Martinique
- Guadeloupe  
- Guyane
- Réunion
- Mayotte
- Saint-Pierre-et-Miquelon
- Nouvelle-Calédonie
- Polynésie française

## Source des données

Les données proviennent de l'API officielle du gouvernement français :
`https://data.economie.gouv.fr/api/records/1.0/search/`

## Exemples avancés

Consultez le fichier `exemples/prix-dom-usage.js` pour des exemples d'utilisation avancée incluant :
- Surveillance multi-produits
- Détection d'alertes de variation de prix
- Calculs de prix moyens

## Conformité et sécurité

Ce module respecte les principes de sécurité de Sentinel :
- Pas de stockage de données sensibles
- Gestion appropriée des erreurs
- API publique du gouvernement français
- Aucune donnée personnelle collectée