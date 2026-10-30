# RSS by Fabrice Heuvrard

Application Android de veille RSS, alternative locale à Feedly : tout est
stocké sur le téléphone, sans serveur à exploiter et sans publicité.

Lot MVP du cahier des charges (oct. 2026).

## Fonctions livrées en v1.0.0

- Abonnement à un flux **RSS 2.0 / RDF, Atom ou JSON Feed** par URL.
- **Détection automatique** du flux d'un site (balises `alternate`, puis
  `/feed`, `/rss.xml`, `/atom.xml`, …).
- Abonnement par **partage d'un lien** depuis le navigateur.
- **Dossiers** illimités, déplacement des sources, compteurs de non-lus par
  source, par dossier et au total.
- **Import et export OPML** (dossiers conservés, attributs en minuscules
  tolérés). Un OPML de 300 sources s'importe sans attendre 300 requêtes.
- Trois vues : **liste, cartes, magazine**. Thème clair, sombre ou système.
- Lecteur intégré : HTML nettoyé, **JavaScript désactivé**, images en
  `https`, liens ouverts dans le navigateur externe.
- Lu / non lu, « lire plus tard » (favoris), **balayage** vers la droite pour
  basculer le statut, ouverture directe dans le navigateur, partage.
- **Collecte à chaque lancement**, actualisation manuelle (tirer pour
  actualiser ou bouton), et **collecte périodique optionnelle** de 15 min à
  24 h (WorkManager, Wi-Fi uniquement possible, batterie faible exclue).
- **Recherche plein texte** (SQLite FTS4) sur les titres et le texte des
  articles.
- **Lecture hors connexion** : le texte intégral est conservé localement, avec
  plafond d'archive réglable (5 000 à 100 000 articles).
- Requêtes conditionnelles (`ETag`, `If-Modified-Since`) et User-Agent
  identifiable, comme demandé dans les exigences non fonctionnelles.
- **Mise à jour intégrée** : l'application lit la release GitHub `latest`
  (manifeste `version.json` puis API), télécharge l'APK et propose
  l'installation.

## Architecture

| Couche | Choix |
| --- | --- |
| Langage | Kotlin 2.0, coroutines + Flow |
| Interface | Jetpack Compose, Material 3 |
| Architecture | MVVM, injection manuelle (`AppContainer`) |
| Base locale | Room 2.6 (SQLite) + table FTS4 |
| Réglages | DataStore Preferences |
| Tâches de fond | WorkManager (période minimale 15 min) |
| Réseau | OkHttp, 4 collectes en parallèle |
| Analyse | jsoup (XML tolérant + HTML), `org.json` (JSON Feed) |

Le dépôt `FeedRepository` ne dépend pas d'Android : l'analyse des flux, l'OPML
et la recherche sont testés sur JVM.

## Tests

```bash
./gradlew testDebugUnitTest          # 32 tests, dont l'analyse RSS/Atom/JSON,
                                     # l'OPML, la détection de flux, l'extraction
                                     # du texte, la mise à jour et un garde-fou
                                     # sur res/xml/file_paths.xml
./gradlew assembleRelease            # APK signé
```

## Onglet IA (v1.1.0)

L'onglet IA sépare deux choses :

- **Sans modèle** : regroupement des dépêches identiques publiées par plusieurs
  sources (titre normalisé, préfixes `DIRECT :`, `(AFP)` ignorés) et classement
  par thème par mots-clés. Ces blocs s'affichent même hors connexion.
- **Avec modèle local** : résumé d'un article (bouton dans le lecteur) et
  synthèse des articles non lus, envoyés à un serveur **Ollama** du réseau
  domestique. Adresse et modèle se règlent dans Réglages, section
  « Intelligence artificielle » (défaut `http://192.168.0.162:11434`,
  `ministral-3:8b`). Aucun texte d'article ne part vers un service extérieur.

## Flux en erreur (v1.4.0)

Chaque échec de collecte est daté : `errorSince` (premier échec de la série) et
`errorCount` (échecs consécutifs), remis à zéro dès qu'une collecte réussit
(migration Room 1 → 2, sans perte des articles déjà stockés). Le paramétrage est
découpé en **deux onglets** : « Réglages » et « Flux en erreur (n) ». Le second
liste les sources fautives avec l'erreur, l'adresse, le nombre de jours, un
bouton **Réessayer** par source et **Tout réessayer** dans la barre du haut.
Le tiroir garde un raccourci vers cet onglet, avec le compteur en pastille.
Une source n'est signalée comme « à examiner » qu'après **8 jours** d'échec
consécutif (`EtatSource.SEUIL_PROPOSITION_JOURS`) : une panne d'une heure ne
déclenche rien, et aucune suppression n'est automatique.

## Barre du bas et actions rapides (v1.4.0)

La barre du bas porte **Articles**, **IA** et **Ajouter** (le « + » remplace la
bulle flottante qui masquait le bas de la liste). L'entrée **IA** est verte
quand le serveur Ollama répond à l'API, rouge quand il est injoignable, teinte
neutre avant le premier test (test au lancement, à chaque changement d'adresse et
à l'ouverture de l'onglet).

Le menu **⋮** de la liste d'articles propose **Tout marquer comme lu** et
**Tout réessayer les flux en erreur** : ces actions ne sont plus seulement dans
le tiroir.

## Lecture et doublons (v1.5.0)

- **Se désabonner depuis l'article** : menu ⋮ du lecteur, confirmation explicite,
  puis retour à l'accueil ; la source et ses articles déjà téléchargés sont
  retirés de l'app (le site du flux n'est pas touché).
- **Doublons masqués** : un même article publié par plusieurs sources
  n'apparaît qu'une fois dans la liste (même adresse après nettoyage des
  paramètres de traçage, ou même titre significatif d'au moins 20 caractères,
  préfixes `AFP`, `DIRECT :` ignorés). `Regroupement.dedoublonner` est testé sur
  JVM ; l'onglet IA garde la liste brute pour afficher qui a publié quoi.
- **Couleurs de lecture** : fond orange clair pour un article non lu, vert clair
  une fois lu (teintes adaptées au thème sombre), en liste comme en cartes.
- **Bascule Tous / Non lus** en haut de la liste, avec le compteur de non lus.

## Signature et mise à jour

Le keystore de release vit hors du dépôt (`/root/.secrets/keystores-android/`)
et ses quatre valeurs sont passées en secrets GitHub. **Toute nouvelle version
doit être signée avec la même clé**, sinon l'installation par-dessus échoue.

Le dépôt doit rester **public** : les assets d'une release privée répondent 404
sans authentification, et l'application n'a pas de jeton.

Avant chaque livraison : incrémenter `versionCode` et `versionName` dans
`app/build.gradle.kts`.
