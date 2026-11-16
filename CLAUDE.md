<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# CLAUDE.md — CoproFirst-Android

## ⚠️ Socle commun d'abord

Bonnes pratiques valables pour tous les projets : `~/.claude/standards/`
(routage : `~/.claude/CLAUDE.md` §1). Ce fichier ne contient que l'instanciation
CoproFirst-Android ; les garde-fous non négociables sont dans `~/.claude/projets.json`,
affichés à chaque session. Déclencheurs du projet : `.claude/coprofirst-android-preflight.md`.

## Stack et état

- **Application Android native en Kotlin et Jetpack Compose**, français seul.
  Décision du 15/11/2026 (Philippe) : l'app Android rejoint l'iOS, qui restait
  « iOS seul » au 01/11 (coprofirst#2460) — native, pas une WebView, comme l'iOS.
- **Gradle** (wrapper épinglé, empreinte de la distribution vérifiée) ; versions dans
  `gradle/libs.versions.toml`, la seule liste. JDK 21. `compileSdk` 37.2,
  `targetSdk` 37, `minSdk` 26.
- **Client d'API** : module `client-api`, généré à chaque build par
  openapi-generator (Kotlin, kotlinx.serialization) depuis `contrat/openapi.json`,
  la pièce jointe de la release de coprofirst au **plancher** (`contrat/plancher.env`),
  recopiée à l'octet par `bash scripts/importer-contrat.sh` ; la CI rejoue
  `--verifier`. Rien de généré n'est versionné. Les chemins s'entendent sous `/api`.
- **État** : il se lit dans `README.md` (« Ce que fait l'app ») et les issues
  ouvertes.
- **L'app iOS est la référence fonctionnelle** (consigne de Philippe, 15/11/2026) :
  l'Android tend vers elle — mêmes écrans, mêmes parcours, mêmes libellés, mêmes choix
  (`philippe-tressard/coprofirst-ios`, `Parite/PARITE.md`). Une question technique se
  tranche par le choix que l'iOS a fait ; on en reprend le comportement, jamais le code.
  Une divergence (contrainte d'Android, étape intermédiaire) se **déclare** dans le
  commentaire du code et dans la PR, avec le ticket qui la résorbe.
- **Version** : elle se DÉDUIT au build (`app/build.gradle.kts`) — code = nombre de
  commits, nom = `coprofirst.version.base` (gradle.properties) + `.` + code. **Aucune PR
  ne la touche** (pas de commit « Version x.y.z ») : plusieurs sessions en parallèle se
  la disputaient. La base ne change que sur une décision de sortie ; `verifier-depot.sh`
  refuse une version écrite à la main.
- **Identifiant** `fr.coprofirst.app`, aligné sur l'iOS ; il se fige à la première
  publication. Signature **au poste** seulement, jamais en CI. Aucune publication
  (Play Console, piste de test ouverte) sans demande explicite de Philippe.
- **Dépôt** : `philippe-tressard/coprofirst-android`, public, branche `main`. Je
  fusionne mes PR moi-même (champ `fusion` du registre), CI verte, puis compte rendu.
  Les commits portent l'adresse *noreply* de GitHub, réglée dans la configuration
  **locale** du clone.

## Ce qui est propre à ce projet

- **Licence** : AGPL-3.0-or-later **avec des permissions additionnelles** (GPL §7) —
  les magasins Android, et la liaison avec Firebase Messaging et Wearable seulement
  (point 4, 16/11/2026 ; `verifier_dependances_google.py` refuse toute autre bibliothèque
  `com.google.android.gms`/`com.google.firebase`) — `LICENSES/LicenseRef-CoproFirst-Magasins.txt`. Chaque
  fichier se déclare `AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins`.
  `LICENSE` se réassemble par `bash scripts/verifier-depot.sh --ecrire-licence`.
- **Le logo** de CoproFirst est intégré (icône adaptative, médaillon de l'accueil)
  sous sa licence propre `LicenseRef-CoproFirst-Logo`, élargie à l'app Android
  (usage 5, coprofirst#2743, à partir de `LOGO` de `contrat/plancher.env`). Le dessin
  et la licence sont recopiés à l'identique (`scripts/importer-logo.sh`, vérifié en
  CI) ; le format d'Android en est généré au build (`scripts/generer_logo.py`, qui
  refuse tout élément qu'il ne sait pas rendre) — jamais redessiné ni recoloré.
- **Ce dépôt et coprofirst ne partagent que ce que coprofirst publie** avec chaque
  release : le contrat (`openapi.json`), les tables pour les clients
  (`tables-clients.json`) et les constantes (`constantes-clients.json`, depuis v3.102.0),
  recopiés à l'octet dans `contrat/` — jamais le source de coprofirst. Les **palettes** se
  génèrent au build (`scripts/generer_charte.py`, testé par `verifier-depot.sh`) : la
  charte `seine`, celle de la résidence dès qu'elle est reconnue (`site_palette`), clair
  et sombre — `ThemeCoproFirst` les donne à Material, `LocalCouleurs` pour le reste.
  Le bouton plein s'écrit une fois, `commun/Boutons.kt` (`BoutonPlein`, `BoutonPleinDestructif` ; `verifier-depot.sh` le tient). Un besoin côté serveur devient un ticket dans
  coprofirst, jamais une modification de `/Volumes/Data/Dev/5hostachy` d'ici.
- **Installation** : aucun annuaire — démo, adresse saisie, QR, lien d'application ;
  l'énumération des installations est interdite (spec `multi-coproprietes.md`
  §4.1 bis de coprofirst).
- **Téléphone et montre partagent**, sans recopie (#206) : `:partage` (Kotlin pur : client
  HTTP, formats, alerte reçue, lecture du HTML) et `:partage-android` (ce qui demande
  Android : le chiffrement au Keystore, le rendu du texte riche `TexteDuSite`, les
  constantes du serveur générées). SDK, Java et lint de tout module Android :
  `reglagesCommuns` du build racine ; le périmètre de `verifier-depot.sh` se lit dans
  `settings.gradle.kts`.
- **Composants communs** : leur API est `docs/COMPOSANTS.md` — le contrat des écrans ;
  un écran la consomme sans la modifier, un besoin devient un ticket. Un écran = un
  paquet `fonctions/<menu>` qui s'inscrit lui-même (`Inscription.kt`, ses tables par
  `scripts/generer_tables_<menu>.py`) : il ne touche ni la navigation ni le build (#109).
- **Réseau** : un seul client HTTP, `ClientHttp.partage` (module `:partage`, que la montre
  partage aussi, #206 ; `verifier-depot.sh` refuse tout autre client) (aucune redirection
  suivie, clair refusé par le manifeste), décliné par hôte avec ses cookies en mémoire
  (`SessionsParHote`) ; aucune requête ni route écrite à la main — `verifier-depot.sh`
  le refuse, sauf dans `PreuveHote.kt` (le fichier d'association n'est pas une route de
  l'API) et `commun/pieces/FichierProtege.kt` (les fichiers de `/uploads/`, servis
  derrière la session hors du contrat ; l'hôte de la session seul) — dispenses nommées.
- **Mot de passe** : un seul chemin, la garde de l'hôte (`GardeHote`, comme l'iOS) —
  un hôte jamais vu se confirme avant tout envoi. Le plancher du contrat (`VERSION_CONTRAT_PLANCHER`)
  est généré depuis `contrat/openapi.json` ; une installation qui sert un contrat plus
  ancien ne s'ouvre pas.
- **Sauvegarde** : rien ne quitte l'appareil (`ressources-communes/xml/regles_extraction.xml`,
  `regles_sauvegarde.xml`, communes au téléphone et à la montre) — la session se relit par l'API.
- **Contrôles** : `bash scripts/verifier-depot.sh` (500 lignes, encodage, modes,
  syntaxe, LICENSE, couleurs et requêtes écrites à la main) et `reuse lint`, rejoués par le hook
  pre-commit (armé par `git config core.hooksPath .githooks`) et par la CI ;
  `bash scripts/pipeline.sh` (la CI hors émulateur, lint en erreurs ; `pipeline.sh <tâches>` pour une partie) —
  **au poste, aucun `./gradlew` lourd en direct** : il passe par `pipeline.sh`, sous le verrou commun ; tests d'interface (`app/src/androidTest`, serveur local en HTTPS,
  repères = identifiants d'accessibilité de l'iOS ; tout toucher passe par `toucher`,
  `tests-interface-communs/…/commun/Gestes.kt`, commun au téléphone et à la montre (#216), qui amène l'élément à l'écran avant de toucher — un
  `performClick` direct se perd hors de l'écran, `verifier-depot.sh` le refuse ; règle
  `junit4.v2.createComposeRule`, effets au fil principal — la v1 est refusée) en CI, job « Interface », et au poste
  par `bash scripts/tests-interface.sh` — une seule tâche lourde au poste à la fois
  (pas d'émulateur pendant une suite xcodebuild ou un autre émulateur), émulateur
  arrêté à la fin. Le poste est partagé : Gradle y tourne à deux workers et sur un
  démon commun aux worktrees (`gradle.properties`) — jamais `--no-daemon` au poste ;
  la CI relève la borne (`--max-workers=4`). Chaque job Gradle de la CI se prépare par
  `.github/actions/preparer-android` (image d'émulateur en cache, téléchargements réessayés,
  #232 ; `scripts/verifier_ci.py` le tient).
