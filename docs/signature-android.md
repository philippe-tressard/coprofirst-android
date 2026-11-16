<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Signer la release pour Google Play

L'app se publie avec **Play App Signing** : Google garde la clé de l'app et re-signe
ce qu'on lui envoie. Nous ne gardons que la **clé de téléversement**, qui prouve que
l'AAB vient de nous. Perdue ou compromise, elle se remplace par une demande à Google
(Play Console › Intégrité de l'app), sans changer d'app.

**Au poste seulement.** Ni la clé ni son mot de passe n'entrent dans le dépôt, qui est
public, ni dans la CI : `scripts/verifier-depot.sh` refuse un mot de passe écrit dans un
fichier de build et toute signature dans `.github/`.

## 1. Créer la clé (une fois, par Philippe)

Dans un terminal du Mac. `keytool` demande le mot de passe (le même pour le magasin et
la clé, format PKCS12), puis l'identité du certificat.

```bash
mkdir -p ~/.coprofirst-android && chmod 700 ~/.coprofirst-android
```

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore ~/.coprofirst-android/televersement.jks -alias televersement -keyalg RSA -keysize 4096 -validity 10000
```

```bash
chmod 600 ~/.coprofirst-android/televersement.jks
```

Puis ranger le mot de passe dans le trousseau macOS (la commande le demande, rien ne
s'affiche ni ne reste dans l'historique) :

```bash
security add-generic-password -s coprofirst-android-televersement -a "$USER" -w
```

**Sauvegarde** : une copie du fichier `.jks` et de son mot de passe dans le gestionnaire
de mots de passe de Philippe. Jamais dans un dossier synchronisé en clair.

Un autre emplacement de la clé : `COPROFIRST_CLE_TELEVERSEMENT=/chemin/vers/la.jks`.

## 2. Construire l'AAB signé

Depuis la copie principale, sur `main` à jour et sans modification :

```bash
bash scripts/construire-release.sh --verifier
```

```bash
bash scripts/construire-release.sh
```

Le script refuse une branche, un arbre modifié ou un `main` décalé d'`origin`. Il
affiche la version (`versionCode` = nombre de commits de `main`, toujours croissant ;
`versionName` = `<coprofirst.version.base>.<versionCode>`), le chemin de l'AAB
(`app/build/outputs/bundle/release/app-release.aab`), l'empreinte SHA-256 du certificat
de téléversement et celle du fichier.

## 3. Première fois dans la Play Console

À la création de l'app, choisir **Play App Signing** (« Laisser Google gérer la clé de
l'app ») et téléverser le premier AAB : son certificat devient le certificat de
téléversement. Vérifier que l'empreinte affichée par la console (Intégrité de l'app ›
Signature de l'app) est celle que le script a imprimée.

Le téléversement, la piste de test et la publication sont des gestes de Philippe :
aucune session ne publie.
