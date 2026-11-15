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
  ouvertes. Le parcours de référence est l'app iOS (`philippe-tressard/coprofirst-ios`,
  `Parite/PARITE.md`) : on en reprend les parcours, jamais le code.
- **Identifiant** `fr.coprofirst.app`, aligné sur l'iOS ; il se fige à la première
  publication. Signature **au poste** seulement, jamais en CI. Aucune publication
  (Play Console, piste de test ouverte) sans demande explicite de Philippe.
- **Dépôt** : `philippe-tressard/coprofirst-android`, public, branche `main`. Je
  fusionne mes PR moi-même (champ `fusion` du registre), CI verte, puis compte rendu.
  Les commits portent l'adresse *noreply* de GitHub, réglée dans la configuration
  **locale** du clone.

## Ce qui est propre à ce projet

- **Licence** : AGPL-3.0-or-later **avec une permission additionnelle** (GPL §7)
  pour les magasins Android — `LICENSES/LicenseRef-CoproFirst-Magasins.txt`. Chaque
  fichier se déclare `AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins`.
  `LICENSE` se réassemble par `bash scripts/verifier-depot.sh --ecrire-licence`.
- **Le logo** de CoproFirst n'est **pas** intégré : sa licence (`LicenseRef-CoproFirst-Logo`)
  a été élargie à l'app iOS seule (coprofirst#2474). D'ici son élargissement à
  Android, pas d'icône (dispense de lint `MissingApplicationIcon`, à retirer avec elle).
- **Ce dépôt et coprofirst ne partagent que le contrat d'API publié.** Couleurs,
  tailles, formats : à **générer** depuis les sources de coprofirst ; d'ici là, les
  couleurs du système (Material You). Un besoin côté serveur devient un ticket dans
  coprofirst, jamais une modification de `/Volumes/Data/Dev/5hostachy` d'ici.
- **Installation** : aucun annuaire — démo, adresse saisie, QR, lien d'application ;
  l'énumération des installations est interdite (spec `multi-coproprietes.md`
  §4.1 bis de coprofirst).
- **Réseau** : un seul client HTTP, `session/ClientHttp.kt` (aucune redirection
  suivie, clair refusé par le manifeste) ; aucune requête ni route écrite à la main —
  `verifier-depot.sh` le refuse. Le plancher du contrat (`VERSION_CONTRAT_PLANCHER`)
  est généré depuis `contrat/openapi.json` ; une installation qui sert un contrat plus
  ancien ne s'ouvre pas.
- **Sauvegarde** : rien ne quitte l'appareil (`res/xml/regles_extraction.xml`,
  `regles_sauvegarde.xml`) — la session se relit par l'API.
- **Contrôles** : `bash scripts/verifier-depot.sh` (500 lignes, encodage, modes,
  syntaxe, LICENSE, couleurs et requêtes écrites à la main) et `reuse lint`, rejoués par le hook
  pre-commit (armé par `git config core.hooksPath .githooks`) et par la CI ;
  `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:testDebugUnitTest :client-api:test`
  (lint en erreurs).
