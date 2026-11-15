<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Les composants communs — l'API des écrans

Ce document est le **contrat** entre la couche commune (charte, navigation, `commun/`,
`session/`) et les écrans des menus. Un écran **consomme** cette API ; il ne la modifie
pas. Un besoin qu'elle ne couvre pas devient un **ticket** (étiquette `commun`), traité
par la session qui tient la couche commune — jamais un contournement local.

Chaque composant reprend le comportement de son pendant iOS (nommé entre parenthèses) :
l'app iOS est la référence fonctionnelle (`CLAUDE.md`).

## Règles

1. **Un écran = un paquet + une ligne au registre.** Le paquet
   `fr.coprofirst.app.fonctions.<menu>` (et ses tests sous le même paquet) ; la ligne,
   dans `app/src/main/java/fr/coprofirst/app/navigation/RegistreDesMenus.kt`, où chaque
   menu a **sa ligne réservée d'avance** (un commentaire `// "<id>" — #<ticket>`, entre
   deux lignes vides). La session du menu remplace SA ligne, et aucune autre — deux
   sessions parallèles ne touchent jamais la même ligne. Elle ne touche ni `charte/`, ni
   `commun/`, ni `session/`, ni `entree/`, ni `navigation/` (hors sa ligne).
2. **Un service par menu** : une `interface Service<Menu>` (ce dont l'écran a besoin),
   sa réalisation `Service<Menu>Api` par le client généré, à travers
   `SessionOuverte.appeler` — et, pour les tests et les aperçus, une réalisation simulée.
3. **Aucune requête ni route à la main**, **aucune couleur à la main**, **aucun format de
   date ou de nombre à la main**, **aucun `Button` de Material** : `verifier-depot.sh`
   les refuse.
4. **Ce que l'iOS laisse au site reste au site**, et l'écran le dit.

## Un écran de menu, de bout en bout

```kotlin
// app/src/main/java/fr/coprofirst/app/fonctions/profil/EcranProfil.kt
package fr.coprofirst.app.fonctions.profil

@Composable
fun EcranProfil(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceProfilApi(contexte.session) }
    Chargement(charger = { service.moi() }, squelette = { Carte { Text("Prénom Nom") } }) { moi, _ ->
        Carte { Text(FormatsDuSite.nom(moi.nomAffiche, moi.prenom, moi.nom)) }
    }
}
```

et, dans `RegistreDesMenus.kt`, la ligne réservée `// "profil" — #21` devient :

```kotlin
        "profil" to { contexte -> EcranProfil(contexte) },
```

`ContexteDeMenu` donne à l'écran : `page` (sa page : `titre`, `route`…), `session`
(`SessionOuverte`, pour ses services), `droits` (`DroitsOnglet` du compte : `cs`,
`administrateur`, `locataire`… — un geste fin se juge sur le `peut_*` servi avec l'objet,
jamais sur ces droits seuls), `sortie` (`GestesDeSortie` : `deconnecter()`,
`oublierTout()` — toujours confirmés par `ConfirmationDeSortie`) et `apparence`
(`Apparence` : le thème de l'appareil, `choix`, `options`, `choisir(valeur)`). Le titre de la page est posé par la barre : l'écran n'en
pose pas. Une page sans ligne au registre affiche « Bientôt ».

## Navigation — `fr.coprofirst.app.navigation` (à la couche commune)

| API | Pendant iOS | Rôle |
|---|---|---|
| `RegistreDesMenus` | `RegistreDesMenus` | id de page → écran ; « Bientôt » sinon. |
| `ContexteDeMenu` | `ContexteDeMenu` | Ce qu'un écran reçoit. |
| `DroitsOnglet`, `PagesOuvertes`, `OngletsDuSite.ouverts(page, droits)` | idem | Qui voit quelle page, quel onglet (tables générées). |
| `PagesParDefaut`, `OrdrePages`, `Installation.pages` | idem | La barre : ordre servi (`pages_order`), libellés (`page_config_*`). |
| `EcranResidence` | `OngletsView` | La barre (4 pages + « Plus »). |

## Session — `fr.coprofirst.app.session`

| API | Rôle |
|---|---|
| `SessionOuverte.appeler { base, client -> … }` | LA porte des requêtes d'une résidence ouverte : le client généré, sur la session HTTP de l'hôte (cookies), hors du fil principal ; toute erreur en sort en `ErreurService`. |
| `ErreurService` (`ErreurService`) | `SessionExpiree`, `Introuvable`, `TropDEssais`, `Injoignable`, `Refus(phraseServie, statut)` (la phrase `detail` du serveur), `Inattendue`… `ErreurService.depuis(e)` ramène toute erreur à un cas. |
| `Installation` | La résidence : `nom`, `adresse`, `versionContrat`, `codePalette`. |

Un service :

```kotlin
interface ServiceProfil {
    suspend fun moi(): UserRead
}

class ServiceProfilApi(private val session: SessionOuverte) : ServiceProfil {
    override suspend fun moi() = session.appeler { base, client -> AuthApi(base, client).getAuthMe() }
}
```

## Écran — `fr.coprofirst.app.commun`

| Composant | Pendant iOS | Usage |
|---|---|---|
| `Chargement(charger, squelette, contenu)` | `Chargement` | Charger puis montrer : squelette estompé, puis contenu ; sur un échec, `MessageErreur` et « Réessayer ». `contenu(valeur, recharger)`. `cle` relance quand elle change. |
| `MessageErreur(erreur, reessayer?)` | `MessageErreur` | Une erreur en une phrase (celle du serveur s'il en donne une), sur `dangerFond`, annoncée au lecteur d'écran. |
| `ErreurService.phrase()` | `.phrase` | La phrase d'une erreur, en `@Composable`. |
| `EtatVide(titre, icone?, explication?)` | `ContentUnavailableView` | Une liste vide, dite. |
| `Carte(ouvrir?, action?) { … }` | `Carte` | La surface d'une carte : `surface`, filet `border`, sans ombre ; touchable si `ouvrir`. |
| `PastilleTeinte(libelle, badge, compacte?)`, `LibelleTeinte` | `PastilleTeinte` | Une pastille d'état : la classe `badge-*` du site → couleurs de la charte ; `LibelleTeinte.brut(v)` pour une valeur inconnue (en gris). |
| `BoutonPlein(libelle, actif?, enCours?)` | `.plein` | Le bouton principal ; le seul `Button` de l'app. |
| `BoutonContour(libelle)` | `.contour` | Le bouton secondaire. |
| `Roles.statut(v)`, `Roles.role(v)`, `Roles.statutAbrege(v)` | `RolesUtilisateur.libelleStatut` | Le libellé et la pastille (`LibelleTeinte`) d'un statut ou d'un rôle servi, depuis les tables générées ; une clé inconnue s'affiche brute. |
| `ConfirmationDeSortie(geste, sortie, fermer)`, `GesteDeSortie` | `confirmationDeSortie` | La confirmation de « Se déconnecter » / « Oublier toutes les résidences » (repère `sortie.confirmer`). |
| `Apparence` | Profil › Apparence | Le thème de l'appareil : `choix` (`auto`, `clair`, `sombre`), `options` (`OPTIONS_THEME`), `choisir(v)` — appliqué à la racine. |

Exemple :

```kotlin
@Composable
fun EcranProfil(service: ServiceProfil) {
    Chargement(
        charger = { service.moi() },
        squelette = { Carte { Text("Prénom Nom") } },
    ) { moi, recharger ->
        Carte { Text(FormatsDuSite.nom(moi.nomAffiche, moi.prenom, moi.nom)) }
    }
}
```

## Formats — `fr.coprofirst.app.commun.formats.FormatsDuSite`

Les formats du site (`FormatsDuSite`), fr-FR, à l'heure de Paris ; « — » pour une
valeur absente. Dates : `date`, `dateCourte`, `dateLongue`, `dateHeure`,
`dateHeureCourte`, `heure`, `jourCourt`, `jourIso` (envoyé), `moisAnnee`, `moisCourt` —
chacune prend un `Instant` ou la chaîne ISO servie (`lire` la lit comme le navigateur).
Nombres : `nombre`, `montant`, `octets`. Personnes : `nom(affiche, prenom, nom)` — le
`nom_affiche` servi d'abord, jamais composé dans un écran.

## Charte — `fr.coprofirst.app.charte`, `ThemeCoproFirst`

| API | Rôle |
|---|---|
| `LocalCouleurs.current` | Les couleurs de la palette en cours (clair ou sombre) : `primary`, `text`, `textMuted`, `surface`, `border`, `danger`, `successFond`… — un jeton de la charte chacune, générées depuis coprofirst. |
| `MaterialTheme.colorScheme` | Les rôles de Material, tirés des mêmes jetons (`ThemeCoproFirst`). |
| `Mise.rayon`, `Mise.marge`, `Mise.cible`, `Mise.ecart` | Les mesures communes (`Mise`). Cible tactile : 48 dp (règle d'Android). |
| `Typo.titre()`, `Typo.libelle()` | Titre en serif (`Font.titre`), libellé de section (`.styleLibelle()`). |

## À venir dans la couche commune

Texte du site (HTML sur liste blanche) et tables de libellés — 15b ; cadre à onglets,
droits par onglet, avis éphémère, recherche — 15c ; pièces jointes, photo protégée,
document — 15d ; le fil d'activité (page `tableau-de-bord`) et la cloche — #11, #22.
Le suivi : #15, #11.
