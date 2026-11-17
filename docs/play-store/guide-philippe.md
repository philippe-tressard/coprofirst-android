<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Google Play : le chemin de Philippe, du compte à la production

**Décision du 16/11/2026** : compte développeur **personnel**, puis **test fermé avec 12
copropriétaires pendant 14 jours**, puis la production, le même jour que l'iOS (#12).
Chaque étape ci-dessous est un geste de Philippe ; ce dépôt prépare le reste. Les règles
de Google changent : relire son aide (« Exigences de test pour les nouveaux comptes de
développeur personnels ») au moment de chaque étape.

## 1. Le compte (une fois, ~1 h, puis quelques jours de vérification)

1. D'abord, sur le compte Google dédié à CoproFirst (créé le 16/11/2026) :
   **activer la validation en deux étapes**, avant toute inscription. Son adresse
   ne sert qu'à se connecter à la console et ne s'écrit pas dans ce dépôt.
2. <https://play.google.com/console/signup> avec **ce compte** ; type
   **« Pour vous-même »** (personnel) ; frais d'inscription uniques (25 $). Le contact
   affiché sur la fiche est une autre adresse, celle du site (`fiche.md`, « Coordonnées »).
3. **Vérifications d'identité** demandées par Google : pièce d'identité officielle, nom
   et adresse identiques à ceux de la pièce, adresse e-mail et téléphone de contact
   vérifiés, et un **appareil Android** : l'app Play Console installée, avec le même
   compte, pour vérifier l'accès à un vrai téléphone.
4. Nom du développeur affiché sur la fiche : **« Philippe TRESSARD »**, comme la fiche
   de l'App Store — un seul vendeur sur les deux magasins.
5. **Statut de professionnel** (règlement européen sur les services numériques) :
   **non professionnel** tant que CoproFirst ne rapporte rien (ni vente, ni abonnement,
   ni publicité). Se déclarer professionnel rend l'adresse et le téléphone **publics**
   sur la fiche.
6. Le téléphone de contact ne se valide (SMS ou appel) qu'**après** l'approbation de
   l'identité : le bouton reste grisé jusque-là.

Compte validé le 17/11/2026.

## 2. L'app dans la console

1. Créer l'app :
   - nom **CoproFirst**, langue par défaut **Français (France) – fr-FR**, appli,
     **sans frais** ;
   - **nom du package `fr.coprofirst.app`**, demandé dès la création et **définitif** :
     c'est l'`applicationId` du build (`build.gradle.kts`, téléphone et montre) ;
   - **protection automatique : Désactiver**. Elle ajoute au binaire du code fermé de
     Google, hors des deux bibliothèques que permet `LicenseRef-CoproFirst-Magasins`
     (point 4), et une restriction de redistribution que nous choisirions — le point 3c
     l'interdit. Elle ajouterait aussi des échanges avec Google à déclarer dans
     « Sécurité des données » ;
   - déclarations : règlement du programme, **Signature d'application Play** (Google
     garde la clé de l'app) et lois américaines sur l'exportation.
2. À la première version : la clé de téléversement
   (`docs/signature-android.md` : la créer au poste, puis
   `bash scripts/construire-release.sh`).
3. Remplir « Contenu de l'app » : `docs/play-store/securite-des-donnees.md` (§5) et
   `docs/play-store/fiche.md` (règles de confidentialité, annonces, public cible 18+,
   classification, accès à l'app par le compte de démonstration).
4. Fiche principale : `docs/play-store/fiche.md` (textes, visuels).

## Avant CHAQUE envoi d'un AAB : le contrôle de cohérence (#4)

Sur le commit exact à soumettre, poussé sur `main`, dans le clone, et non sur une
copie de travail :

```bash
python3 -I scripts/controle_avant_soumission.py --taguer --rapport build/controle-soumission.md
```

Il refuse au moindre rouge : CI complète verte sur ce commit (tests d'interface et
parcours sombre au plus grand texte compris), contrat servi en production, fiche,
« Sécurité des données », notes de version (`docs/play-store/notes/<version>.txt`),
aucun ticket `bloque-soumission` ouvert. Tout vert, il pose et pousse le tag
`v<version>`. Seulement alors : `bash scripts/construire-release.sh` sur ce même commit,
et l'envoi. Le rapport se colle dans la note de publication.

## 3. D'abord pour soi : le test interne (facultatif, sans attente)

Tests › **Test interne** : jusqu'à 100 testeurs, aucun examen de Google, en ligne en
quelques minutes. Utile pour vérifier l'AAB sur son propre téléphone avant d'inviter.

## 4. Le test fermé : 12 copropriétaires, 14 jours

C'est la condition des nouveaux comptes personnels pour obtenir la production.

1. Tests › **Test fermé** › créer une piste ; y téléverser l'AAB.
2. **Les testeurs** : le plus simple est un **groupe Google** (par exemple « CoproFirst –
   testeurs Android ») : chaque copropriétaire le rejoint lui-même, et la liste se tient
   sans retaper d'adresses. Sinon, une liste d'adresses e-mail saisie dans la console.
   Dans tous les cas, l'adresse est celle du **compte Google du téléphone** du testeur.
3. **Le lien d'inscription** (« Rejoindre sur le Web ») est donné par la piste : c'est lui
   qu'on envoie, avec le message prêt à copier de `docs/play-store/invitation-testeurs.md`.
4. Envoyer la version en examen (la première passe par l'examen de Google, quelques
   jours).

**Ce qui compte pour les 14 jours** :

- au moins **12 testeurs inscrits** (« opt-in ») **en continu** pendant **14 jours** ;
  un testeur qui se désinscrit fait retomber le compte : inviter **15 à 18 personnes**
  donne de la marge ;
- les jours ne courent qu'une fois la version du test fermé **publiée** sur la piste ;
- les testeurs doivent **installer** l'app par le lien et s'en **servir** : Google le
  demande dans la candidature à la production ;
- noter les retours (ce qui marche, ce qui manque) : la candidature les demande.

## 5. La production

Tableau de bord › **Demander l'accès à la production** : un questionnaire sur le test
(comment les testeurs ont été recrutés, ce qu'ils ont remonté, ce qui a changé). Google
répond en quelques jours. Puis : production, **France** d'abord, le **même jour** que la
version iOS (#12, #4 : contrôle de cohérence et tag de version des deux côtés).

## Ce que ce dépôt a préparé

| Pièce | Où |
|---|---|
| AAB signé, Play App Signing | `docs/signature-android.md`, `scripts/construire-release.sh` |
| Sécurité des données | `docs/play-store/securite-des-donnees.md` |
| Fiche du magasin | `docs/play-store/fiche.md` |
| Invitation des testeurs | `docs/play-store/invitation-testeurs.md` |
