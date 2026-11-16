<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Google Play : la fiche du magasin

Le texte à coller dans la Play Console (Développer la présence sur le Play Store ›
Fiche principale), en parité avec la fiche de l'App Store (#31). **Philippe** le saisit ;
aucune session ne publie. Les blocs marqués `<!-- champ:… -->` sont lus par
`scripts/test_fiche_play.py`, qui tient les longueurs de Google (30, 80, 4000
caractères) : ne pas changer leur forme.

Ce qui diffère de l'App Store, et pourquoi : la biométrie d'Android au lieu de Face ID ;
la mesure d'audience de la résidence, dite et refusable (la fiche iOS 1.0 dit encore
« aucune mesure d'audience ») ; aucune mention d'un appareil Apple.

## Nom de l'app

<!-- champ:nom -->
```
CoproFirst
```

## Description courte

<!-- champ:courte -->
```
L'application de votre résidence : affaires, accès, communauté et conseil.
```

## Description complète

<!-- champ:complete -->
```
CoproFirst est l'application de votre résidence. Elle se connecte au site de votre copropriété, installé par elle ou hébergé pour elle, et montre à chacun ce qui le concerne, et rien de plus.

• Fil d'activité : ce qui se passe dans votre résidence, en un coup d'œil.
• Affaires : signalez une panne, suivez-la jusqu'à sa résolution, avec photos et documents.
• Résidence : le carnet d'entretien, le règlement, les informations utiles.
• Accès : vos lots, badges et télécommandes.
• Communauté : annonces entre voisins, idées, sondages.
• Annuaire, prestataires, FAQ et notifications, si votre résidence les active.
• Conseil syndical : les outils de suivi et de validation réservés à ses membres.

Pour entrer : saisissez l'adresse de votre résidence, scannez le QR code de votre fiche d'arrivée, ou essayez la démonstration. Votre empreinte ou votre visage peut protéger votre session.

Confidentialité. Ce que vous lisez et écrivez est gardé par votre résidence, pas par l'éditeur de l'application. Aucune publicité, aucun traceur. Comme sur son site, votre résidence compte les pages consultées pour ses statistiques de fréquentation ; vous pouvez le refuser dans votre profil. Les notifications ne contiennent aucune information personnelle : le détail se lit dans l'application.

CoproFirst est un logiciel libre (AGPL-3.0) : son code est public, celui de cette application aussi.
```

## Autres champs

| Champ (Play Console) | Valeur |
|---|---|
| Catégorie de l'app | Productivité |
| Tags | Productivité, Gestion de propriété (les plus proches que propose la console) |
| Adresse e-mail | celle de Philippe, saisie par lui (jamais écrite dans ce dépôt public) |
| Site Web | `https://coprofirst.fr` |
| Règles de confidentialité | `https://coprofirst.fr/confidentialite.html` |
| Prix | gratuit, sans achat intégré |
| Annonces | l'app ne contient **pas** d'annonces |
| Public cible | **18 ans et plus** (des copropriétaires et leurs locataires) |
| Classification du contenu | questionnaire IARC : les utilisateurs **communiquent entre eux** (annonces, messages d'une affaire, commentaires) ; ni violence, ni contenu sexuel, ni jeux d'argent, ni substances ; partage de localisation : **non** |
| Accès à l'app | « Toutes les fonctionnalités nécessitent un compte » : le compte de démonstration de demo.coprofirst.fr, identifiant et mot de passe saisis par Philippe (comme pour la revue d'Apple) |
| Sécurité des données | `docs/play-store/securite-des-donnees.md` |
| Pays | France d'abord ; la liste de l'App Store ensuite, sur décision de Philippe |

## Visuels

| Visuel | Exigence de Google | Source |
|---|---|---|
| Icône | 512 × 512, PNG 32 bits | le logo de CoproFirst, recopié à l'identique (`LicenseRef-CoproFirst-Logo`) |
| Image de présentation | 1024 × 500, PNG ou JPEG | la charte du site, sans recolorer le logo |
| Captures du téléphone | 2 à 8, PNG ou JPEG, côté court ≥ 1080 px recommandé, 9:16 | les écrans de l'app, thème clair, résidence de démonstration |
| Captures de tablette | facultatives ; 7 et 10 pouces | après les lots grand écran (#69) |

Ils se produisent par l'app elle-même, sur l'émulateur, dans `build/captures-play/` (jamais
versionnés : ils se refont à chaque version) :

```bash
bash scripts/captures-play.sh
```

`icone-512.png`, `presentation-1024x500.png` (fond, texte et teintes de la charte, logo
tel quel), puis les écrans `1-fil.png`, `2-acces.png`, `3-profil.png` en 1080 × 1920,
thème clair, résidence fictive (`CapturesPlay`, comme `LancementCaptures` de l'iOS).

## Notes de version

Le texte de chaque version (500 caractères au plus), au moment de la publier ; la
première : « Première version de CoproFirst sur Android. »
