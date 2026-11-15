<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# CoproFirst pour Android

Application Android native de [CoproFirst](https://github.com/philippe-tressard/coprofirst),
le logiciel libre de gestion de copropriété — Kotlin et Jetpack Compose.

## Ce que fait l'app

L'app suit les écrans et les choix de l'[app iOS](https://github.com/philippe-tressard/coprofirst-ios).

- **Accueil** : « Entrer dans ma résidence » ou « Découvrir la démo ».
- **Connectez-vous à votre espace** : l'adresse de la résidence (celle de la fiche
  d'arrivée) se reconnaît pendant la saisie ; une coche paraît dans le champ et le nom
  de la résidence dessous. Sinon, une phrase dit pourquoi (adresse injoignable, pas une
  résidence CoproFirst, résidence à mettre à jour). La dernière résidence ouverte est
  pré-remplie ; elle reste sur l'appareil seul. La démonstration ouvre cet écran à son
  adresse.

Pas encore : la connexion au compte et les personnages de la démo, le QR code de la
fiche d'arrivée, les liens d'application (tickets ouverts).

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
