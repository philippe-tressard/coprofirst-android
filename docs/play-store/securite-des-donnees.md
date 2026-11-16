<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Google Play : la section « Sécurité des données »

Le pendant de l'étiquette « App Privacy » de l'App Store (coprofirst-ios,
`.claude/skills/publication-app-store/confidentialite.md`) : mêmes choix, mêmes données,
dans la grille de Google (#32). Trois textes disent la même chose (`standards/14` §3 bis,
déclaré = réel) : ce fichier, le formulaire saisi par **Philippe** dans la Play Console
(§5), la politique de confidentialité de <https://coprofirst.fr/confidentialite.html>.

**Ce fichier est lu par un contrôle** : `scripts/verifier_securite_donnees.py`, rejoué par
`scripts/verifier-depot.sh` (pre-commit, CI). Les tableaux précédés d'une marque
`<!-- table:… -->` sont comparés au code et au manifeste, dans les deux sens : un envoi
neuf du client généré, une permission neuve, et le contrôle échoue tant que ce fichier
ne les classe pas. Ne pas reformater ces tableaux.

Les règles de Google changent : relire sa page d'aide sur la section « Sécurité des
données » avant chaque saisie.

## 1. Le choix : déclarer ce qui part vers la résidence

L'app n'envoie rien à l'éditeur : elle parle au site de **la résidence que l'utilisateur a
choisie**, qui garde ce qu'il y écrit. Comme pour l'App Store, ce dépôt **déclare quand
même** ces données comme collectées (l'éditeur tient lui-même des installations : la
démonstration, sa propre résidence), liées au compte, et **non partagées** : leur envoi à
la résidence est le geste de l'utilisateur, qui en a saisi l'adresse.

Une seule lecture part ailleurs, et elle n'envoie rien : le texte public de la vitrine,
`https://coprofirst.fr/contenu.json`, relu une fois par lancement (#153) — une requête GET
sans cookie, sans jeton, sans paramètre, qui ne porte aucune donnée de l'utilisateur. Rien
à déclarer dans la grille ; elle est dite ici pour que la fiche reste vraie.

**Une donnée part chez Google, sans contenu : le jeton d'appareil des notifications.**
Les notifications passent par Firebase Cloud Messaging (bibliothèque permise par le
point 4 de la licence, décision du 16/11/2026). Le téléphone obtient de Google un
identifiant d'appareil (le jeton FCM) et l'envoie à la résidence (`postAppareils`). La
résidence le rend à Google pour acheminer chaque notification, qui ne porte qu'un
identifiant opaque, jamais de donnée personnelle : le détail se lit ensuite par l'API
(garde-fou du projet). Ce dépôt **déclare ce jeton partagé avec Google**, pour
l'acheminement seulement. Google range les transferts à un prestataire qui agit pour le
compte de l'app hors du « partage » : c'est la lecture la plus prudente qui l'emporte ici.
Le lien téléphone ↔ montre (Wearable, #207) se déclare dans le lot qui l'apporte.
Son premier geste, « Continuer sur le téléphone » (lot 2a), ne fait passer qu'**une adresse
de page de la résidence** (« https://<résidence>/tickets/12 ») de la montre au téléphone
relié, par Google Play services. Elle ne porte aucun contenu de l'alerte. La liaison elle-même
(lot 2b) fera passer le jeton de montre et le nom de la résidence. Les deux se classent
ensemble dans la grille à ce lot-là, en lecture prudente : Google Play services peut acheminer
par ses serveurs, et pas seulement de proche en proche.

⚠️ Lecture prudente, pas un avis juridique ; c'est à Philippe de la retenir. Si une autre
lecture l'emporte, ce fichier et la saisie changent ensemble.

## 2. Ce que l'app demande au système

<!-- table:permissions -->
| Permission (`AndroidManifest.xml`) | À quoi elle sert |
|---|---|
| `android.permission.INTERNET` | parler au site de la résidence, en HTTPS seulement (le clair est refusé par le manifeste) |
| `android.permission.CAMERA` | lire, sur l'appareil, le QR code de la fiche d'arrivée ; l'image ne quitte pas l'appareil |
| `android.permission.USE_BIOMETRIC` | rouvrir la session gardée au Keystore ; l'app ne reçoit que la réponse du système |
| `android.permission.ACCESS_NETWORK_STATE` | savoir si le réseau est là, pour la consultation hors ligne ; rien ne quitte l'appareil |

Aucune autre : ni localisation, ni contacts, ni stockage partagé, ni notifications tant
que #23 ne les demande. « à venir (#N) » : la permission que le ticket ajoute, refusée
dès qu'elle est demandée — la PR qui l'apporte retire la marque. Le contrôle lit le manifeste du dépôt ; ce
que les bibliothèques ajouteraient au manifeste fusionné se relit au build de la release
(§6).

## 3. Les données, dans la grille de Google

Pour chacune : **collectée** oui, **chiffrée en transit** oui (HTTPS), **partagée** non —
sauf `ID appareil`, partagé avec Google pour l'acheminement des notifications (§1).

| Code (§4) | Case de la Play Console | Ce que c'est | Obligatoire ? | Finalités |
|---|---|---|---|---|
| `Nom` | Informations personnelles › Nom | nom et prénom du profil ; noms d'un annuaire tenu par le conseil | obligatoire | Fonctionnement de l'app, Gestion du compte |
| `E-mail` | Informations personnelles › Adresse e-mail | le courriel de connexion ; celui d'un annuaire | obligatoire | Fonctionnement de l'app, Gestion du compte |
| `Téléphone` | Informations personnelles › Numéro de téléphone | le téléphone du profil ; celui d'un annuaire | facultatif | Fonctionnement de l'app |
| `ID utilisateur` | Informations personnelles › ID utilisateur | le compte et sa session (cookies posés par la résidence) | obligatoire | Fonctionnement de l'app, Gestion du compte |
| `Autres infos` | Informations personnelles › Autres informations | mot de passe, société, fonction, statut et bâtiment demandés, réglages du compte, rôles donnés par un administrateur | obligatoire | Fonctionnement de l'app, Gestion du compte |
| `Messages` | Messages › Autres messages dans l'application | les messages d'une affaire | facultatif | Fonctionnement de l'app |
| `Contenu utilisateur` | Activité dans les applications › Autre contenu généré par l'utilisateur | affaires, annonces, idées, avis, votes, commentaires, questions, demandes de badge | facultatif | Fonctionnement de l'app |
| `Interactions` | Activité dans les applications › Interactions avec l'application | les écrans vus, sous le chemin de la page équivalente du site, et le canal « android » : la mesure d'audience de la résidence, **jamais** si le compte l'a refusée au Profil | facultatif | Analyses |
| `ID appareil` | Identifiants de l'appareil ou autres | le jeton d'appareil des notifications (FCM) et l'abonnement (#23) ; **partagé avec Google** (Firebase Cloud Messaging), pour l'acheminement seulement, sans contenu | facultatif | Fonctionnement de l'app |

**Non cochées** : localisation, informations financières, santé, photos et vidéos,
fichiers, agenda, contacts, historique de navigation, applications installées, journaux de
plantage, diagnostics. L'app n'en lit ni n'en envoie. Le terme d'une recherche part à la
résidence pour y répondre, qui ne le garde pas : pas d'« Historique des recherches dans
l'application » (même choix que l'iOS).

**Suppression** : l'utilisateur peut demander la suppression de son compte dans l'app
(Profil › Supprimer mon compte, #21) ; la résidence l'exécute.

## 4. Chaque envoi, classé

Toutes les écritures du client généré que l'app appelle (`post`, `patch`, `put`,
`delete`), et les données que chacune porte (« — » : un geste sur un objet existant, sans
donnée saisie). « à venir (#N) » : un envoi que le ticket ajoute, prêt d'avance ; le
contrôle le refuse dès que l'envoi existe — la PR qui l'apporte retire la marque.

<!-- table:envois -->
| Envoi | Données |
|---|---|
| `postAuthLogin` | `E-mail` `ID utilisateur` `Autres infos` |
| `postAuthLogout` | — |
| `postAuthRefresh` | `ID utilisateur` |
| `postAuthChangePassword` | `Autres infos` |
| `patchAuthMe` | `Nom` `E-mail` `Téléphone` `Autres infos` |
| `postAuthMeDemandeModification` | `Autres infos` |
| `postAuthDemandeSuppression` | `Autres infos` |
| `deleteAuthDemandeSuppression` | — |
| `patchAuthMeOptOutTelemetrie` | `Autres infos` |
| `deleteAuthMeTelemetrie` | — |
| `postTelemetryCollect` | `Interactions` |
| `postAppareils` | `ID appareil` |
| `deleteAppareilsAbonnement` | `ID appareil` |
| `postTickets` | `Contenu utilisateur` |
| `patchTicketsTicketId` | `Contenu utilisateur` |
| `postTicketsTicketIdEvolutions` | `Contenu utilisateur` |
| `patchTicketsTicketIdEvolutionsEvolId` | `Contenu utilisateur` |
| `deleteTicketsTicketIdEvolutionsEvolId` | — |
| `postTicketsTicketIdMessages` | `Messages` |
| `postSignalements` | `Contenu utilisateur` |
| `postPerimetresLibelle` | `Contenu utilisateur` |
| `postLecturesApercu` | `Contenu utilisateur` |
| `postNomsAffiche` | `Nom` |
| `postReglementQuestions` | `Contenu utilisateur` |
| `postAnnonces` | `Contenu utilisateur` |
| `patchAnnoncesAnnonceIdStatut` | — |
| `deleteAnnoncesAnnonceId` | — |
| `postAnnoncesCibleIdReponses` | `Contenu utilisateur` |
| `deleteAnnoncesCibleIdReponsesRepId` | — |
| `postAnnoncesHall` | `Contenu utilisateur` |
| `patchAnnoncesHallAnnonceId` | — |
| `deleteAnnoncesHallAnnonceId` | — |
| `postAnnoncesHallAnnonceIdRenvoyerEmail` | — |
| `postIdees` | `Contenu utilisateur` |
| `postIdeesIdeeIdVoter` | `Contenu utilisateur` |
| `postIdeesCibleIdReponses` | `Contenu utilisateur` |
| `deleteIdeesCibleIdReponsesRepId` | — |
| `postAvis` | `Contenu utilisateur` |
| `patchAvisAvisId` | `Contenu utilisateur` |
| `patchAvisAvisIdArchivage` | — |
| `postSondagesSondageIdVoter` | `Contenu utilisateur` |
| `postSondagesSondageIdCommenter` | `Contenu utilisateur` |
| `deleteSondagesSondageIdCommentairesCommentaireId` | — |
| `postAccesCommandes` | `Contenu utilisateur` |
| `postAccesDeclarerBadge` | `Contenu utilisateur` |
| `patchAccesTelecommandesTcIdPerdu` | — |
| `patchAccesVigiksVigikIdPerdu` | — |
| `postBailleurBauxBailIdRecupererAcces` | — |
| `postDelegations` | `Contenu utilisateur` |
| `postDelegationsDelegationIdAccepter` | — |
| `postDelegationsDelegationIdRevoquer` | — |
| `patchNotificationsNotifIdLue` | — |
| `deleteNotificationsNotifId` | — |
| `postNotificationsToutMarquerLu` | — |
| `postNotificationsSupprimerLues` | — |
| `postNotificationsSupprimerSelection` | — |
| `postAdminComptesUserIdTraiter` | `Autres infos` |
| `postAdminDemandesProfilDemandeIdTraiter` | `Autres infos` |
| `postAdminCommandesAccesCmdIdTraiter` | — |
| `postAdminUtilisateursUserIdAjouterRole` | `Autres infos` |
| `postAdminUtilisateursUserIdRetirerRole` | `Autres infos` |
| `postAdminUtilisateursUserIdAutoMatch` | — |
| `postAdminUtilisateursUserIdAccueilArrivant` | `Contenu utilisateur` |
| `patchAdminUtilisateursUserIdBanCommunaute` | `Autres infos` |
| `deleteAdminUtilisateursUserId` | — |
| `postPrestatairesNotations` | `Contenu utilisateur` |
| `patchPrestatairesNotationsNId` | `Contenu utilisateur` |
| `deletePrestatairesNotationsNId` | — |
| `putAdminAnnuaireCs` | `Nom` `E-mail` `Téléphone` |
| `putAdminAnnuaireSyndic` | `Nom` `E-mail` `Téléphone` |

## 5. À saisir dans la Play Console (Philippe)

Play Console › l'app › Règles et programmes › Contenu de l'app › **Sécurité des données**.
Les libellés peuvent avoir changé.

| Question | Réponse |
|---|---|
| Votre application collecte-t-elle ou partage-t-elle des types de données requis ? | **Oui** |
| Toutes les données sont-elles chiffrées en transit ? | **Oui** |
| Proposez-vous aux utilisateurs un moyen de demander la suppression de leurs données ? | **Oui** (dans l'app, Profil ; et le lien de la politique) |
| Les types de données | les neuf du §3, aucun autre (`ID appareil` quand #23 sera publié) |
| Pour chacun : collectée / partagée | **collectée** ; **non partagée** (§1) |
| Traitée de façon éphémère ? | **Non** |
| Obligatoire ou facultative | la colonne du §3 |
| Finalités | la colonne du §3 |

Puis Contenu de l'app › **Règles de confidentialité** :
`https://coprofirst.fr/confidentialite.html`.

## 6. Ce qui n'est pas mesuré

Le contrôle ne le voit pas (INCONNU, jamais conforme) :

- ce que chaque résidence garde et journalise : son code et sa politique ;
- ce que le manifeste **fusionné** ajoute par les bibliothèques : à relire au build de la
  release (Android Studio › Merged Manifest, ou `aapt2 dump permissions` sur l'AAB) ;
- un champ neuf dans un envoi **déjà classé** : la table classe l'envoi, pas ses champs ;
- ce qui est saisi dans la Play Console.
