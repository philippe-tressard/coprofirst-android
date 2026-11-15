<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# CoproFirst pour Android

Application Android native de [CoproFirst](https://github.com/philippe-tressard/coprofirst),
le logiciel libre de gestion de copropriété — Kotlin et Jetpack Compose.

## Ce que fait l'app

L'app suit les écrans et les choix de l'[app iOS](https://github.com/philippe-tressard/coprofirst-ios), aux couleurs de la charte de CoproFirst — celles de la résidence dès qu'elle est reconnue, au clair comme au sombre.

- **Accueil** : « Entrer dans ma résidence » ou « Découvrir la démo ».
- **Connectez-vous à votre espace** : l'adresse de la résidence (celle de la fiche
  d'arrivée) se reconnaît pendant la saisie ; une coche paraît dans le champ et le nom
  de la résidence dessous. Sinon, une phrase dit pourquoi (adresse injoignable, pas une
  résidence CoproFirst, résidence à mettre à jour). La dernière résidence ouverte est
  pré-remplie ; elle reste sur l'appareil seul. La démonstration ouvre cet écran à son
  adresse.
- **Se connecter** : adresse électronique et mot de passe, sous la résidence. Avant le
  premier mot de passe envoyé à une adresse jamais vue, « Vérifiez l'adresse » montre
  l'hôte en entier et dit s'il est reconnu comme une installation CoproFirst ; sinon,
  il faut cocher « Je connais cette adresse… » pour continuer. « Mot de passe oublié ? »
  et « Demander l'accès » ouvrent le site de la résidence. La session reste en mémoire
  le temps de l'usage.
- **La résidence ouverte** : la barre du bas, dans l'ordre et avec les libellés réglés
  par l'administration de la résidence, filtrée selon le compte (Espace CS, Prestataires,
  Délégations, Paramétrage ne paraissent qu'à ceux qui y ont droit) ; « Plus » pour les
  autres pages, Profil et À propos. Les écrans des pages arrivent
  menu par menu : en attendant, chaque page dit « Bientôt ».
- **Profil** (Plus › Profil) : la résidence, le statut et les rôles du compte ; les
  informations personnelles (une nouvelle adresse demande le mot de passe et ne vaut
  qu'une fois confirmée) ; le mot de passe ; une demande de changement de profil ou de
  bâtiment au conseil syndical ; vos droits RGPD (politique de confidentialité, refus,
  export et effacement des statistiques de navigation) ; **la demande de suppression du
  compte** — ce qui sera effacé est dit d'abord, le mot de passe la confirme,
  l'administration de la résidence la traite, et elle se retire tant qu'elle n'est pas
  traitée. Le thème de l'appareil (Automatique, Clair, Sombre) ; « Se déconnecter » et « Oublier
  toutes les résidences », au bas du Profil. La saisie d'une affaire et les préférences
  de notification se règlent encore sur le site (#47).
- **Notifications** : les alertes du compte, « Aujourd'hui » (les 24 dernières heures)
  puis « Plus anciennes ». Un toucher marque lu ; « Tout marquer lu » en haut, avec le
  nombre de non lues ; « Supprimer les lues » en tête de la liste ; « Modifier » pour en
  choisir plusieurs et les supprimer — toute suppression se confirme, elle est
  définitive. Balayer vers la droite marque lu, vers la gauche supprime. Tirer la liste
  la relit. La cloche de chaque écran et les notifications push viendront ensuite.
- **Mes lots & accès** (onglet « Accès ») : ses lots et leurs caractéristiques (vue du
  locataire, du bailleur, ou de tous les autres) ; ses badges Vigik et télécommandes,
  « Signaler perdu », « Nouvel accès » (pour un de ses lots) et « Déclarer un accès » ;
  ce que le bailleur a confié au locataire, et, au bailleur, la vue par locataire avec
  « Tout récupérer » ; la gestion locative en lecture (baux actifs, archives, inventaire).
  Le tableau des charges, créer ou terminer un bail et tenir l'inventaire restent sur le
  site, et l'écran le dit.
- **Délégations aidant** (Plus › Délégations) : les délégations que le compte voit, leur
  état et leur période ; « Accepter » et « Révoquer » (confirmé) quand le serveur les ouvre
  au compte ; au conseil syndical, « Nouvelle délégation » (personne aidée, proche aidant,
  motif, date de fin facultative). Une délégation est en lecture seule : l'aidant consulte,
  sans agir au nom de la personne aidée.
- **Paramétrage** (administration et opérateur de la plateforme), en partie :
  **Télémétrie** (quatre vues — jour, 30 jours, 12 mois, 10 ans —, le filtre « sans le
  gestionnaire du site » quand il change quelque chose, les nombres clés, le graphe, les
  pages les plus vues et les utilisateurs actifs) et **Maintenance** (à l'opérateur, la
  santé des tâches planifiées et leurs échecs récents ; à l'administration, le dernier
  export de la copropriété). Les autres onglets sont « Sur le site », chacun avec son
  lien ; lancer une tâche ou un export reste au site, et l'écran le dit.

Pas encore : les écrans des pages, le courriel retenu et la
biométrie, les personnages de la démo, le QR code, les liens d'application (tickets
ouverts).

## Construire

Prérequis : JDK 21, SDK Android avec la plateforme 37.2.

```bash
brew install openjdk@21
brew install --cask android-commandlinetools
sdkmanager "platform-tools" "platforms;android-37.2"
```

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :client-api:test
```

`JAVA_HOME` et `ANDROID_HOME` (ou `local.properties`, `sdk.dir=…`) doivent désigner
le JDK 21 et le SDK.

## Contrat d'API

`contrat/openapi.json` est la pièce jointe de la release de coprofirst au plancher
(`contrat/plancher.env`) ; sa provenance est dans `contrat/PROVENANCE.md`. Le
réimporter : `bash scripts/importer-contrat.sh` (`gh` authentifié).

## Licence

AGPL-3.0-or-later, avec une permission additionnelle (article 7) qui autorise la
distribution par les magasins d'applications Android : voir `LICENSE` et `LICENSES/`.
Conformité [REUSE](https://reuse.software) : `reuse lint`.
