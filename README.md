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
- **Fil d'activité** (la première page) : ce qui se passe dans la résidence, jour par
  jour (« Aujourd'hui », « Hier », puis la date), le plus récent en tête. Chaque carte
  dit sa rubrique dans sa teinte, l'heure, la nature d'une affaire, le délai et les dates
  d'une affaire datée, le titre et le début du texte, qui la lit et son lieu, ses pièces
  jointes ; une urgence porte un liseré rouge. Un toucher ouvre le résumé (avec « Voir
  sur le site » quand le site a une page), le document, ou la fiche d'un sondage : voter
  (une réponse, sa précision si elle en demande une, un commentaire, sous la charte de
  respect), les résultats quand le serveur les montre, puis les commentaires — en publier,
  supprimer les siens (confirmé), signaler ceux des autres au conseil syndical (motif
  obligatoire). L'affaire ne s'ouvre pas encore dans l'app. Tirer la liste la relit ; un
  fil vide le dit.
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
- **FAQ** : les questions de la résidence par catégorie, une réponse dépliée à la fois ;
  « Partager le lien » d'une question ; « Créer une affaire » et le Manuel utilisateur. La rédaction reste au site,
  et l'écran ne le dit qu'à qui le serveur l'ouvre.
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
- **Mes affaires** : Liste, Kanban (au conseil et aux copropriétaires) et Archives. La
  liste cherche au serveur (et propose les archives trouvées), se filtre par suivi, par
  nature et par « ce qui me concerne » ; un appui long copie le numéro ou partage le
  lien (archiver attend un correctif du client d'API, #99). Au Kanban, « Déplacer
  vers… » change l'état — clore prévient quand des affaires liées pourraient être
  fusionnées, ce qui se fait sur le site. Les Archives se rangent par année. La fiche montre le fil de l'affaire (la
  demande, la suite, les comptes rendus, la synthèse) et ses messages, en lecture ; la
  saisie et les gestes de la fiche suivent (#16).
- **Ma résidence** : la Fiche (photo, immatriculation, lots, assurance et syndic — qui
  ouvre l'annuaire du syndic —, composition par bâtiment ; règles, plans, règlement,
  comptes-rendus d'AG et diagnostics, chacun s'ouvrant dans l'app de l'appareil) ; aux
  propriétaires et au conseil, le Carnet d'entretien (par équipement, visites en retard
  en tête, filtre par périmètre) et le Règlement (poser une question à l'assistant, lire
  son avis, ses fondements et les extraits qu'il cite). Le bilan de l'exercice se lit
  encore sur le site.
- **Annuaire** : le conseil syndical (par bâtiment, président et gestionnaire mis en
  avant, groupe WhatsApp) et le syndic (espace client, contacts) — un toucher appelle ou
  écrit.
- **Communauté** : les rubriques que le site ouvre au compte (un gestionnaire n'a que
  l'Avis ; un compte suspendu lit le motif du serveur). **Sondages** en cours puis
  archivés, chacun ouvrant sa fiche (vote, résultats, commentaires) ; « Créer,
  modifier ou stopper un sondage se fait sur le site » ne se dit qu'à qui le serveur le
  permet. **Boîte à idées** : les plus soutenues d'abord, un filtre par état, le cœur pour
  voter, « + » pour proposer une idée. **Petites annonces** : type, catégorie, tri ;
  l'annonce ouverte (photos, contact, état pour son auteur, suppression à qui le serveur
  l'ouvre), « + » pour en déposer une — les photos s'ajoutent sur le site. **Avis** sur
  l'application : filtre par note, réponse de l'équipe ; donner son avis, le corriger,
  l'archiver ou le restaurer. Une idée ou une annonce ouverte porte le fil des réponses
  (répondre, supprimer la sienne, signaler celles des autres) et « Signaler » au conseil
  syndical ; chaque contenu dit qui le lit et où.

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
