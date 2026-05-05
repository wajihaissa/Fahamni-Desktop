# Configuration Google Maps

Ce projet utilise Google Maps pour verifier et completer la localisation des salles.

## Variables utilisees

Le code lit ces variables :

- `GOOGLE_MAPS_API_KEY`
- `GOOGLE_MAPS_REGION_CODE`
- `GOOGLE_MAPS_LANGUAGE_CODE`

Valeurs conseillees pour ce projet :

```text
GOOGLE_MAPS_REGION_CODE=tn
GOOGLE_MAPS_LANGUAGE_CODE=fr
```

## 1. Creer le projet Google Cloud

1. Ouvrir `https://console.cloud.google.com/`
2. Creer un projet Google Cloud dedie a ce module
3. Activer la facturation sur ce projet

## 2. Activer les APIs necessaires

Dans `APIs & Services > Library`, activer :

1. `Places API`
2. `Geocoding API`

Note :
- le code appelle `Autocomplete (New)` via `https://places.googleapis.com/v1/places:autocomplete`
- le code appelle aussi `https://maps.googleapis.com/maps/api/geocode/json`

## 3. Creer une cle API

1. Aller dans `APIs & Services > Credentials`
2. Cliquer sur `Create credentials > API key`
3. Copier la cle generee

## 4. Restreindre la cle

Pour cette application desktop Java qui appelle les web services Google depuis la machine locale :

1. Ouvrir la cle creee
2. Dans `API restrictions`, choisir `Restrict key`
3. Autoriser uniquement :
   - `Places API`
   - `Geocoding API`

Application restriction :

- en developpement local, vous pouvez temporairement laisser `None`
- si vous avez plus tard un serveur fixe pour les appels, passez a une restriction `IP addresses`

Important :
- ne reutilisez pas cette cle pour d'autres apps
- ne mettez jamais la cle dans le code source

## 5. Configurer la machine locale

### Option A. Variables d'environnement Windows persistantes

Dans PowerShell :

```powershell
[Environment]::SetEnvironmentVariable("GOOGLE_MAPS_API_KEY", "VOTRE_CLE", "User")
[Environment]::SetEnvironmentVariable("GOOGLE_MAPS_REGION_CODE", "tn", "User")
[Environment]::SetEnvironmentVariable("GOOGLE_MAPS_LANGUAGE_CODE", "fr", "User")
```

Puis :

1. Fermer IntelliJ
2. Reouvrir IntelliJ
3. Relancer l'application

### Option B. Variables seulement pour la session courante

Dans PowerShell :

```powershell
$env:GOOGLE_MAPS_API_KEY="VOTRE_CLE"
$env:GOOGLE_MAPS_REGION_CODE="tn"
$env:GOOGLE_MAPS_LANGUAGE_CODE="fr"
```

Ensuite, lancer l'application depuis le meme terminal.

### Option C. IntelliJ IDEA

Dans IntelliJ :

1. `Run > Edit Configurations`
2. Choisir la configuration qui lance l'application
3. Dans `Environment variables`, ajouter :

```text
GOOGLE_MAPS_API_KEY=VOTRE_CLE;GOOGLE_MAPS_REGION_CODE=tn;GOOGLE_MAPS_LANGUAGE_CODE=fr
```

Note :
- comme `.idea/` est ignore dans ce repo, cette configuration reste locale a chaque collegue
- il faut que chacun mette sa propre cle, ou la cle de l'equipe transmise hors Git

## 6. Verification dans l'application

1. Ouvrir le backoffice des salles
2. Saisir une localisation generale, par exemple :
   - `ESPRIT Ariana`
   - `Ariana, Tunisia`
3. Cliquer sur `Verifier / completer`
4. Choisir une suggestion Google Maps
5. Verifier que le champ `Localisation` est remplace par une adresse normalisee

## 7. Ce qu'il faut partager sur Git

A partager :

- le code source
- ce fichier de documentation
- les noms des variables d'environnement

A ne pas partager :

- la vraie valeur de `GOOGLE_MAPS_API_KEY`
- les captures de console contenant la cle
- une configuration IntelliJ contenant la cle

## 8. Processus recommande pour l'equipe

1. Un membre cree le projet Google Cloud
2. Il active `Places API` et `Geocoding API`
3. Il cree une cle API dediee au projet desktop
4. Il partage la cle aux collegues via un canal prive
   - groupe prive
   - message direct
   - gestionnaire de secrets
5. Chaque collegue configure sa machine localement
6. Personne ne commit la cle dans Git

## 9. Limite fonctionnelle actuelle

La version actuelle :

- valide et complete l'adresse generale
- affiche les coordonnees dans l'interface
- ne stocke pas encore `latitude/longitude` en base

Les details internes du campus restent geres par l'application :

- batiment
- etage
- bloc
- numero de salle

## 10. Conformite Google Maps

La documentation Google Places indique que l'affichage de predictions sans carte implique des exigences d'attribution Google.

Si vous gardez cette fonctionnalite pour une version finale, prevoyez d'ajouter l'attribution visuelle Google demandee par leur documentation.

## Liens officiels

- Getting started: `https://developers.google.com/maps/get-started`
- Places API: `https://developers.google.com/maps/documentation/places/web-service`
- Autocomplete (New): `https://developers.google.com/maps/documentation/places/web-service/place-autocomplete`
- Geocoding API: `https://developers.google.com/maps/documentation/geocoding/requests-geocoding`
- Security guidance: `https://developers.google.com/maps/api-security-best-practices`
