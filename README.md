<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# CoproFirst pour Android

Application Android native de [CoproFirst](https://github.com/philippe-tressard/coprofirst),
le logiciel libre de gestion de copropriété — Kotlin et Jetpack Compose.

## Ce que fait l'app

L'app suit les écrans et les choix de l'[app iOS](https://github.com/philippe-tressard/coprofirst-ios), aux couleurs de la charte de CoproFirst — celles de la résidence dès qu'elle est reconnue, au clair comme au sombre.

- **Accueil** : « Entrer dans ma résidence », « Découvrir la démo » ou « En savoir plus
  sur CoproFirst ».
- **En savoir plus** : la page de coprofirst.fr raccourcie — ce que fait CoproFirst, un
  aperçu du fil d'une résidence (trois cartes inventées, rendues par l'app), six fonctions —, avec « Entrer dans ma résidence » et « Essayer la démo » collés en bas. Les
  textes sont ceux du site : `contenu.json`, relu sur coprofirst.fr une fois par lancement
  (sans cookie ni donnée de l'utilisateur) et gardé pour le suivant, la copie embarquée à
  défaut (recopiée à l'octet par `scripts/importer-vitrine.sh`) — jamais réécrits dans l'app.
- **La démo** : trois rôles (conseil syndical, copropriétaire, locataire) ; un toucher
  ouvre la connexion à la démo, l'identifiant public du rôle pré-rempli, et dit où lire
  son mot de passe (« Voir les identifiants publics », sur coprofirst.fr).
- **Connectez-vous à votre espace** : l'adresse de la résidence (celle de la fiche
  d'arrivée) se reconnaît pendant la saisie ; une coche paraît dans le champ et le nom
  de la résidence dessous. Sinon, une phrase dit pourquoi (adresse injoignable, pas une
  résidence CoproFirst, résidence à mettre à jour). La dernière résidence ouverte est
  pré-remplie ; elle reste sur l'appareil seul. La démonstration ouvre cet écran à son
  adresse. L'icône du QR code, dans le champ, lit celui de la fiche d'arrivée avec la
  caméra (demandée à ce moment-là, l'image lue sur l'appareil, ni gardée ni envoyée) ;
  une adresse scannée se confirme toujours avant le premier mot de passe.
- **Liens d'application** : un lien vers la démo ou 5Hostachy (une affaire, les
  actualités, l'accueil, la connexion) ouvre l'app — la résidence ouverte si c'est la
  sienne, sinon sa connexion, à son adresse ; une adresse jamais vue se confirme avant
  le premier mot de passe. L'écran visé (une affaire, les affaires, une page) s'ouvre
  ensuite dans sa résidence — le fil si le compte ne voit pas la page ; les liens à jeton
  (vérifier une adresse, choisir un mot de passe) restent au navigateur. Android
  n'associe le lien à l'app que si l'installation sert `assetlinks.json`
  (coprofirst#2788).
- **Se connecter** : adresse électronique et mot de passe, sous la résidence. Avant le
  premier mot de passe envoyé à une adresse jamais vue, « Vérifiez l'adresse » montre
  l'hôte en entier et dit s'il est reconnu comme une installation CoproFirst ; sinon,
  il faut cocher « Je connais cette adresse… » pour continuer. « Mot de passe oublié ? »
  et « Demander l'accès » ouvrent le site de la résidence. Le courriel de la dernière
  connexion réussie est retenu, chiffré par le Keystore, et pré-rempli ensuite.
- **Session gardée** (Android 11 et plus) : après la première connexion, « Ouvrir avec
  votre empreinte ? » propose de garder la session sur l'appareil ; la feuille dit ce qui
  la protège vraiment (biométrie, verrouillage de l'appareil, ou rien). Au lancement
  suivant, l'écran de retour rouvre la dernière résidence par la biométrie, le mot de
  passe à côté. Une session expirée se renouvelle d'elle-même (POST /auth/refresh, un
  seul à la fois par hôte) ; si le serveur refuse aussi le renouvellement, l'app quitte la
  résidence une fois et ouvre la connexion pré-remplie, « Votre session a expiré.
  Connectez-vous de nouveau. », la session gardée effacée. Se déconnecter ferme la session sur le serveur et efface la session
  gardée ; « Oublier toutes les résidences » ferme celle de chacune et efface tout — une
  résidence qui ne répond pas est dite sur l'écran de connexion.
- **Hors ligne** : pour une résidence dont la session est gardée, la dernière lecture du
  fil, des affaires, du profil, de l'annuaire et des documents ou photos déjà ouverts est
  gardée sur l'appareil, chiffrée, sept jours au plus. Sans réseau, la biométrie passée,
  elle s'ouvre en consultation : « Hors ligne — à jour au… » sous la barre de chaque page
  (Fil, Affaires, Annuaire, Profil, Ma résidence, fiche d'une affaire), et les gestes qui
  ont besoin du serveur restent en place, grisés — le lecteur d'écran dit pourquoi ; rien
  d'autre ne part (« Vous êtes hors ligne… »). Le réseau revenu, la session se rouvre seule
  et tout se relit. L'appareil qui perd le réseau, l'app ouverte : « ce qui est affiché
  peut dater ».
- **Installation suspendue** : quand la résidence est suspendue (consultation seule), un
  bandeau le dit dès la connexion et sous la barre de chaque page ; un geste refusé dit
  pourquoi avec la phrase du serveur, comme un sixième compte refusé en mode limité.
- **La résidence ouverte** : la barre du bas, dans l'ordre et avec les libellés réglés
  par l'administration de la résidence, filtrée selon le compte (Espace CS, Prestataires,
  Délégations, Paramétrage ne paraissent qu'à ceux qui y ont droit) ; « Plus » pour les
  autres pages, Profil et À propos. Chaque page du site a son écran dans l'app ; les
  onglets du Paramétrage qu'elle ne porte pas renvoient au site.
  En haut à droite, le nom de la résidence (« où suis-je ») ouvre « Résidences » : celle où
  l'on est, ses rôles et « Se déconnecter » ; les autres résidences de l'appareil, chacune
  avec ses rôles, qu'un toucher ouvre sans se déconnecter d'ici ; « Une autre résidence ».
- **Fil d'activité** (la première page) : en tête, son logement, qui ouvre « Mes lots &
  accès » ; puis ce qui se passe dans la résidence, jour par
  jour (« Aujourd'hui », « Hier », puis la date), le plus récent en tête. Chaque carte
  dit sa rubrique dans sa teinte, l'heure, la nature d'une affaire, le délai et les dates
  d'une affaire datée, le titre et le début du texte, qui la lit et son lieu, ses pièces
  jointes ; une urgence porte un liseré rouge. Un toucher ouvre le résumé (avec « Voir
  sur le site » quand le site a une page), le document (dans l'app, comme ceux de Ma
  résidence), la fiche d'une affaire (celle de Mes affaires), ou la fiche d'un sondage : voter
  (une réponse, sa précision si elle en demande une, un commentaire, sous la charte de
  respect), les résultats quand le serveur les montre (avant le vote, au-dessus des choix,
  « provisoires » tant que le sondage est ouvert), puis les commentaires — en publier,
  supprimer les siens (confirmé), signaler ceux des autres au conseil syndical (motif
  obligatoire). Tirer la liste la relit ; un fil vide le dit.
- **Profil** (Plus › Profil) : la résidence, le statut et les rôles du compte ; les
  informations personnelles (le nom tel qu'il sera affiché se lit sous la saisie ; une
  nouvelle adresse demande le mot de passe et ne vaut qu'une fois confirmée) ; le mot de passe ; une demande de changement de profil ou de
  bâtiment au conseil syndical ; vos droits RGPD (politique de confidentialité, refus,
  export et effacement des statistiques de navigation) ; **la demande de suppression du
  compte** — ce qui sera effacé est dit d'abord, le mot de passe la confirme,
  l'administration de la résidence la traite, et elle se retire tant qu'elle n'est pas
  traitée. Le thème de l'appareil (Automatique, Clair, Sombre) ; « Se déconnecter » et « Oublier
  toutes les résidences », au bas du Profil. La saisie d'une affaire, Essentielle ou
  Complète, sur le compte ; « Ce que j'affiche » : n'afficher que les contenus de mon ou
  mes bâtiments, et les notifications par e-mail et dans l'application, de mon ou mes
  bâtiments et des autres — une case jamais réglée prend le défaut du serveur et le dit.
- **Notifications** : les alertes du compte, « Aujourd'hui » (les 24 dernières heures)
  puis « Plus anciennes », chacune avec l'icône de son type (affaire, document, sondage…),
  dont le nom se lit au lecteur d'écran. Un toucher marque lu ; « Tout marquer lu » en haut, le
  nombre de non lues sous le titre ; « Supprimer les lues » en tête de la liste ;
  « Modifier » pour en choisir plusieurs et les supprimer — toute suppression se confirme,
  elle est définitive ; les lues supprimées, un avis le dit. Balayer vers la droite marque
  lu, vers la gauche supprime. Tirer la liste la relit. La cloche, en haut de chaque page
  de la résidence, ouvre la liste en feuille ; un point y dit qu'une alerte attend (le
  nombre, au lecteur d'écran). Pas de notification push pour l'instant ; les alertes du syndic sont prévues (#280).
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
- **Prestataires** (conseil syndical) : fiches, contrats et consommations ; la moyenne des
  avis sur chaque carte (« Excellent · 4,33/5 ») ; les documents d'un contrat s'ouvrent dans
  l'app, comme ceux de Ma résidence ; les avis du conseil, par exercice et par
  intervention, chacun dans la teinte de son état. « ⭐ Noter » un prestataire, depuis sa fiche
  ou la carte d'un contrat, en choisissant l'affaire jugée (la recherche de la page Affaires ;
  l'ajouter comme intervenant se confirme), en cochant ses points forts et faibles — ils
  composent la phrase du commentaire, montrée avant l'envoi, que suit le texte libre ; « Modifier cet avis » pour son auteur, « Retirer
  cet avis » pour le conseil, chacun selon le droit servi. Créer, corriger ou archiver une
  fiche, un contrat ou un relevé restent sur le site.
- **Délégations aidant** (Plus › Délégations) : les délégations que le compte voit, leur
  état et leur période ; « Accepter » et « Révoquer » (confirmé) quand le serveur les ouvre
  au compte ; au conseil syndical, « Nouvelle délégation » (personne aidée, proche aidant,
  motif, date de fin facultative). Une délégation est en lecture seule : l'aidant consulte,
  sans agir au nom de la personne aidée.
- **Mes affaires** : Liste, Kanban (au conseil et aux copropriétaires) et Archives. La
  liste cherche au serveur (et propose les archives trouvées), se filtre par suivi, par
  nature et par « ce qui me concerne » ; un appui long copie le numéro, partage le lien
  ou archive, si le serveur le permet. Au Kanban, « Déplacer
  vers… » change l'état — clore prévient quand des affaires liées pourraient être
  fusionnées, ce qui se fait sur le site. Les Archives se rangent par année. La fiche montre le fil de l'affaire (la
  demande, la suite, les comptes rendus, la synthèse) et ses messages ; en bas, ses gestes
  selon ce que le serveur ouvre au compte : copier ou partager le lien, Répondre, Ajouter
  une suite (un commentaire ou un nouvel état), Modifier (seuls les champs changés
  partent), épingler ou marquer urgente, archiver.
  Chaque entrée de la suite se corrige ou se supprime (« ⋯ »), si le serveur le permet ;
  son propre message aussi, tant que l'affaire n'est pas close (un texte mis en forme sur
  le site se modifie depuis le site).
  « Nouvelle affaire » (le « + » du haut) : catégorie, titre, description, lieu, urgence —
  les photos et ce que le conseil désigne se règlent sur le site. « Modifier » règle aussi
  ses prestataires (ajouter, retirer, réordonner), selon le droit servi.
  En saisie Essentielle (Profil), ce qui n'est pas essentiel pour la catégorie se range
  dans « Plus de détails », replié — à la création comme à la modification.
- **Ma résidence** : la Fiche (photo, immatriculation, lots, assurance et syndic — qui
  ouvre l'annuaire du syndic —, composition par bâtiment ; règles, plans, règlement,
  comptes-rendus d'AG et diagnostics, chacun s'ouvrant dans l'app — un PDF page à page, une
  image entière, « Partager » et « Ouvrir dans une autre app » ; à côté de la page en fenêtre
  large ; un autre type de fichier, dans l'app de l'appareil, #274) ; aux
  propriétaires et au conseil, le Carnet d'entretien (par équipement, visites en retard
  en tête, « À venir » sur un contrat qui n'a pas commencé, filtre par périmètre) et le Règlement (poser une question à l'assistant, lire
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
- **Espace CS** (conseil syndical) : six onglets, ceux que le serveur ouvre au compte.
  **Comptes & accès** — la file de travail du conseil : les comptes à valider, puis les
  demandes de badges et de télécommandes, chacune approuvée (un nouvel arrivant, les codes
  remis) ou rejetée. **Badges & télécommandes** — qui détient quoi, en consultation (la
  recherche par code, porteur ou lot, puis le type) ; enregistrer ou retirer un accès reste
  au site. **Reporting** — le suivi des affaires en lecture (chiffres, répartitions
  croisées, la plus ancienne en tête), celui des entretiens périodiques, puis les
  renouvellements ; toucher une affaire l'ouvre. **Annonces Hall** — créer une affiche, puis
  celles en cours et archivées avec leurs gestes ; les photos, l'assistant, l'aperçu,
  WhatsApp et le PDF restent au site. **Annuaire CS & Syndic** — les fiches du conseil puis
  du syndic, à corriger, ajouter, retirer (et, pour le syndic, ordonner). **Courriels
  affaires** — ce que la relève a fait de chaque message reçu à l'adresse des affaires, et
  pourquoi ; « Affaire #… » ouvre l'affaire rejointe.
- **Paramétrage** (administration et opérateur de la plateforme), en partie :
  **À traiter** (la file des comptes et des commandes d'accès, celle de l'Espace CS, puis
  les demandes de changement de profil : approuver, ou rejeter avec un motif facultatif),
  **Utilisateurs** (recherche, type, état du compte, compteurs ; la fiche d'un compte,
  ses rattachements, et les gestes que le serveur offre : interdire ou réautoriser la
  communauté, ajouter ou retirer un rôle, rejouer l'auto-match des lots, supprimer —
  chacun confirmé s'il le faut, puis annoncé), **Télémétrie** (quatre vues — jour, 30 jours, 12 mois, 10 ans —, le filtre « sans le
  gestionnaire du site » quand il change quelque chose, les nombres clés, le graphe,
  « Accès : site ou application » — les vues par canal —, les pages les plus vues avec leur
  accès, et les utilisateurs actifs) et **Maintenance** (à l'opérateur, la
  santé des tâches planifiées et leurs échecs récents ; à l'administration, le dernier
  export de la copropriété). Les autres onglets sont « Sur le site », chacun avec son
  lien ; lancer une tâche ou un export reste au site, et l'écran le dit.

- **Mesure d'audience** : comme sur le site, la résidence compte les écrans vus (sous le
  chemin de la page équivalente du site, canal « android »), pour ses statistiques de
  fréquentation ; jamais si le compte l'a refusée (Profil › Vos droits), ni hors d'une
  résidence ouverte. Aucun identifiant d'appareil, rien chez l'éditeur.
- **À propos** (Plus) : la résidence et la mention de son pied de page (©, texte libre,
  version de l'app) ; ses mentions légales et sa politique de confidentialité, lues dans
  l'app (« Logiciel libre » sous les mentions) ; le site de CoproFirst, le code du serveur
  et celui de l'app (AGPL §13) ; chaque composant tiers embarqué et sa licence entière —
  la liste est confrontée au build à ce que l'app embarque vraiment.
- **Écran** : portrait au téléphone ; sur tablette, pliable déplié ou fenêtre de bureau,
  toutes les orientations et le multi-fenêtre (comme l'iPad). Dès que la fenêtre est large
  (600 dp, quel que soit l'appareil), une barre latérale liste toutes les pages, puis
  Profil et À propos, à la place de la barre du bas ; passer de l'une à l'autre garde la
  page ouverte. Le fil, « Mes affaires » (Liste, Archives), « Ma résidence » (l'avis d'une
  question du règlement, l'annuaire du syndic, l'affaire d'une entrée du carnet), les petites
  annonces, les Courriels et le Reporting de l'Espace CS, et les documents (Ma résidence,
  le fil, les contrats des prestataires) y tiennent la liste et la fiche côte à côte : la carte ouverte se souligne, « Aucune sélection » tant que rien n'est
  choisi ; la fiche ouverte reste ouverte à la rotation, au pli, au multi-fenêtre (#69).
  Un pliable déplié en livre devient un **carnet** (#242, comme l'iOS) : les intercalaires
  sur la tranche droite remplacent les barres, la liste se tient à gauche du pli et la
  fiche à droite ; le Fil s'y lit une journée par double page (l'éphéméride en tête, « Veille »
  et « Lendemain » au pied de la page, ou balayer la page loin des bords) ; il rouvre la résidence où on l'avait refermée (marque-page gardé sur
  l'appareil seul, effacé à la déconnexion ; un téléphone ordinaire n'en garde aucun).
- **Montre Wear OS** (`montre/`, en construction, #207) : la même app Google Play, pour
  la cloche de la dernière résidence ouverte, en lecture seule — pendant de l'Apple Watch.
  Elle se construit et se teste, mais **la liaison au téléphone est éteinte**
  (`LiaisonMontre.ACTIF`, un seul drapeau pour les deux côtés) : le téléphone ne demande ni
  ne transmet rien, et la montre dit seulement « Ouvrez CoproFirst sur le téléphone pour
  relier la montre ». Ce qui suit est prêt derrière la liaison, qui s'allumera quand le
  serveur, le canal et la section « Sécurité des données » seront réunis. Les écrans : la cloche (le nom
  de la résidence ; les non lues, urgentes devant ; « Déjà lues », repliée ; chaque ligne
  avec l'icône de son type, son titre sur deux lignes et son âge relatif ; « Aucune alerte ») et le détail
  (titre, date, le corps en entier rendu comme sur le téléphone, et « Continuer sur le
  téléphone » quand l'alerte vise une page : la page s'ouvre sur le téléphone).
  Reliée par le téléphone (la dernière résidence ouverte), la montre lira la cloche avec un
  jeton de montre qui ne lit qu'elle, gardé chiffré ; il se retire à la déconnexion. La tuile dit
  « 2 nouvelles alertes · dont 1 urgente », sans titre ni résidence, et ouvre la cloche ;
  la complication du cadran montre le logo, avec un point s'il y a du non lu, sans nombre.
  Sans réseau, l'app, la tuile et la complication lisent la dernière lecture gardée,
  chiffrée, datée (« Hors ligne — à jour au … à … »), comme le téléphone.
  Ce que la montre partage avec le téléphone vit dans
  `partage/` (#206) : le client HTTP, les formats du site, l'alerte reçue, le découpage du
  texte riche — écrits une fois.

Ce qui reste à faire se lit dans les issues ouvertes du dépôt.

## Construire

Prérequis : JDK 21, SDK Android avec la plateforme 37.2.

```bash
brew install openjdk@21
brew install --cask android-commandlinetools
sdkmanager "platform-tools" "platforms;android-37.2"
```

```bash
bash scripts/pipeline.sh   # sous le verrou commun du poste
```

`JAVA_HOME` et `ANDROID_HOME` (ou `local.properties`, `sdk.dir=…`) doivent désigner
le JDK 21 et le SDK.

La release pour Google Play (les AAB du téléphone et de la montre, signés par la clé de téléversement, au poste seulement,
jamais en CI) : `bash scripts/construire-release.sh` — la clé, son rangement et Play
App Signing sont dans `docs/signature-android.md`.

## Contrat d'API

`contrat/openapi.json` est la pièce jointe de la release de coprofirst au plancher
(`contrat/plancher.env`) ; sa provenance est dans `contrat/PROVENANCE.md`. Le
réimporter : `bash scripts/importer-contrat.sh` (`gh` authentifié). Chaque jour, le
workflow « Concordance avec CoproFirst » (`scripts/concordance.py`) vérifie l'écart au
plancher — pièces identiques, contrat servi par chaque installation connue
(`INSTALLATIONS`) — et dit si une release plus récente permettrait de le monter.

## Licence

AGPL-3.0-or-later, avec des permissions additionnelles (article 7) : la distribution par
les magasins d'applications Android, et la liaison avec une bibliothèque de Google Play
services, nommée, et elle seule — Wearable (lien avec la montre), décision du 16/11/2026,
restreinte le 17/11/2026 au retrait du client push (Firebase Cloud Messaging n'y est plus ;
son retour pour les alertes du syndic, #280, demandera de rouvrir ce point).
Voir `LICENSE` et `LICENSES/` ;
`scripts/verifier_dependances_google.py` refuse toute autre bibliothèque de ces familles.
Conformité [REUSE](https://reuse.software) : `reuse lint`.
