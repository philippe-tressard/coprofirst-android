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

## Sommaire

Ce document porte les règles et le socle (navigation, session, écran, charte). La suite :

- [`composants/presentation.md`](composants/presentation.md) — avis éphémère, pages à
  onglets, cloche, formats, texte du site, pièces jointes, feuille pleine ;
- [`composants/contenus.md`](composants/contenus.md) — qui la lit et où, périmètres, aperçu
  servi, personnes de l'annuaire, fil des réponses, signalement, fiche d'un sondage, accès,
  file des validations.

Un chapitre neuf va dans le fichier de son thème ; aucun ne dépasse 500 lignes
(`verifier-depot.sh`).

## Règles

1. **Un écran = un paquet, qui s'inscrit lui-même (#109).** Le paquet
   `fr.coprofirst.app.fonctions.<menu>` (et ses tests sous le même paquet) porte son
   `Inscription.kt` : un `object Inscription<Menu> : InscriptionDeMenu` qui déclare ses
   pages (`ecrans`) et ses fiches (`fiches`). `scripts/generer_registre.py` les collecte au
   build ; ses tables, s'il en a, viennent de `scripts/generer_tables_<menu>.py`, découvert
   par convention. Un écran ne touche ni `charte/`, ni `commun/`, ni `session/`, ni
   `entree/`, ni `navigation/`, ni `app/build.gradle.kts` — `verifier-depot.sh` refuse que
   la navigation ou le build nomment un menu : deux sessions parallèles ne se rencontrent
   plus sur aucun fichier commun.
2. **Un service par menu** : une `interface Service<Menu>` (ce dont l'écran a besoin),
   sa réalisation `Service<Menu>Api` par le client généré, à travers
   `SessionOuverte.appeler` — et, pour les tests et les aperçus, une réalisation simulée.
3. **Aucune requête ni route à la main**, **aucune couleur à la main**, **aucun format de
   date ou de nombre à la main**, **aucun `Button` de Material** : `verifier-depot.sh`
   les refuse.
4. **Ce que l'iOS laisse au site reste au site**, et l'écran le dit.
5. **Pas de version dans une PR** : elle se déduit au build (`CLAUDE.md`, « Version »).
6. **Fusion** : la PR s'arme en fusion automatique dès qu'elle est ouverte —
   `gh pr merge <n> --auto --squash` ; elle fusionne seule quand les checks requis sont
   verts (Dépôt, Build, Interface), et sa branche se supprime. Une CI rouge se corrige
   dans la PR ; on ne désarme pas pour fusionner autrement.

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

et son inscription, `fonctions/profil/Inscription.kt` :

```kotlin
object InscriptionProfil : InscriptionDeMenu {
    override val ecrans: Map<String, EcranDeMenu> = mapOf(
        "profil" to { contexte -> EcranProfil(contexte) },
    )
}
```

`ContexteDeMenu` donne à l'écran : `page` (sa page : `titre`, `route`…), `session`
(`SessionOuverte`, pour ses services), `droits` (`DroitsOnglet` du compte : `cs`,
`administrateur`, `locataire`… — un geste fin se juge sur le `peut_*` servi avec l'objet,
jamais sur ces droits seuls), `sortie` (`GestesDeSortie` : `deconnecter()`,
`oublierTout()` — toujours confirmés par `ConfirmationDeSortie` ; `changerDeResidence(installation?)`, `residencesConnues()` pour la feuille « Résidences », #180), `apparence`
(`Apparence` : le thème de l'appareil, `choix`, `options`, `choisir(valeur)`) et `barre`
(la barre du haut : `ActionsDeBarre(contexte) { IconButton(…) }` y pose les actions de
l'écran — le `toolbar` de l'iOS ; elles se retirent quand l'écran se quitte). `relireDroits()` relit les droits du compte (GET /auth/me) après un geste qui les change — un rôle qu'on se retire : la barre et les onglets suivent aussitôt (`rafraichirProfil` de l'iOS, #87). Le titre de la page est posé par la barre : l'écran n'en
pose pas. Une page sans ligne au registre affiche « Bientôt ».

**La porte** (#85) — `contexte.ouvrir(destination)` mène d'un écran ailleurs, sans toucher à
la barre ni dépendre du code d'un autre menu : `Destination.Page(id)` (une page de la barre
ou de « Plus »), `Destination.Element(nature, id)` (la fiche d'un élément, poussée par-dessus
la page ouverte, avec retour), `Destination.Notifications` (la cloche). Elle rend `false`
quand rien ne la porte dans l'app (page que le compte ne voit pas, fiche non déclarée) :
l'écran garde son repli, « Voir sur le site » par exemple. Un menu **déclare** les éléments
qu'il sait ouvrir, par nature (celles du fil : `affaire`, `sondage`), dans SON
inscription :

```kotlin
    override val fiches: Map<String, FicheDeMenu> = mapOf(
        "affaire" to FicheDeMenu(R.string.affaire_titre) { contexte, id -> DetailAffaire(contexte, id) },
    )
```

## Navigation — `fr.coprofirst.app.navigation` (à la couche commune)

| API | Pendant iOS | Rôle |
|---|---|---|
| `RegistreDesMenus` | `RegistreDesMenus` | id de page → écran ; « Bientôt » sinon. COMPOSÉ des inscriptions (`RegistreGenere`, généré) : une page ou une fiche inscrite deux fois arrête l'app — et `RegistreTest` avant elle. |
| `InscriptionDeMenu`, `EcranDeMenu` | — | Ce qu'un menu inscrit depuis son paquet (`fonctions/<menu>/Inscription.kt`) : `ecrans`, `fiches`. |
| `ContexteDeMenu` | `ContexteDeMenu` | Ce qu'un écran reçoit. |
| `DroitsOnglet`, `PagesOuvertes`, `OngletsDuSite.ouverts(page, droits)` | idem | Qui voit quelle page, quel onglet (tables générées). |
| `PagesParDefaut`, `OrdrePages`, `Installation.pages` | idem | La barre : ordre servi (`pages_order`), libellés (`page_config_*`). `PagesParDefaut.horsMenu` : les pages sans entrée de menu (profil, notifications). |
| `OngletsDuSite.descriptifs[page]`, `OngletsDuSite.routes[page]` | `Tables<Menu>.descriptifs`, `.routes` | Le descriptif et l'adresse sur le site de chaque onglet (générés). |
| `EcranResidence` | `OngletsView` | La barre (4 pages + « Plus ») ; en haut, la cloche. |
| `ActionsDeBarre(contexte) { … }` | `.toolbar` | Les actions de la barre du haut, déclarées par l'écran affiché. |
| `TitreDeBarre(contexte.barre, titre)` | `.navigationTitle` | Le titre de la barre du haut, posé par l'écran affiché (le numéro d'une affaire poussée) ; retiré quand il le quitte. |
| `SousTitreDeBarre(contexte.barre, texte?)` | `.navigationSubtitle` | Le sous-titre, sous le titre (« 12 non lues ») ; `null` : aucun. Posé par l'écran affiché, retiré quand il le quitte — dans la barre de la résidence comme dans celle d'une `FeuillePleine` (repère `barre.sousTitre`). |
| `EnTeteDeBarre(titre, sousTitre?)` | — | Le titre (serif, annoncé comme titre) et le sous-titre d'une barre, tels que la résidence et `FeuillePleine` les affichent ; un écran ne l'appelle pas. |
| `contexte.ouvrir(Destination)`, `RegistreDesMenus.fiches`, `FicheDeMenu` | `parcours.suivre`, `Destination`, `Aiguillage` | La porte : ouvrir une page, la fiche d'un élément ou la cloche depuis un écran. |
| `LienDuSite.destination(chemin)`, `EcranResidence(lien, lienSuivi)` | `LienUniversel`, `suivreLeLienEnAttente` | Un lien d'application (#147) : son chemin → la `Destination` (le fil pour un chemin inconnu ; `null` pour la connexion), posée à l'ouverture de la résidence ou dans la résidence ouverte ; une page que le compte ne voit pas → le fil. L'adresse et la connexion sont l'affaire de l'entrée (#7). |

## Session — `fr.coprofirst.app.session`

| API | Rôle |
|---|---|
| `SessionOuverte.appeler { base, client -> … }` | LA porte des requêtes d'une résidence ouverte : le client généré, sur la session HTTP de l'hôte (cookies), hors du fil principal ; toute erreur en sort en `ErreurService`. |
| `ErreurService` (`ErreurService`) | `SessionExpiree`, `Introuvable`, `TropDEssais`, `Injoignable`, `Refus(phraseServie, statut)` (la phrase `detail` du serveur), `Inattendue`… `ErreurService.depuis(e)` ramène toute erreur à un cas. |
| `Installation` | La résidence : `nom`, `adresse`, `versionContrat`, `codePalette`. |
| `Renouvellement` (`Renouvellement`) | LA porte du renouvellement (#13) : un 401 dans `appeler` (client généré, ou fichier protégé) renouvelle la session — un seul POST /auth/refresh à la fois par hôte — puis rejoue la requête une fois. Un écran n'a rien à faire : un renouvellement refusé sort en `SessionExpiree`. |
| `LecturesGardees`, `GarderLesLectures` (`LecturesGardees`, `GarderLesLectures`) | La consultation hors ligne (#28) : la porte, dans le client de chaque hôte, garde les lectures de la liste blanche (`LectureGardee`) et, en consultation, les sert et refuse tout le reste avant qu'il parte — `ErreurService.HorsLigne`. Un écran n'a rien à faire pour être relu hors ligne. |
| `CoffreDuCompte`, `SessionsGardees` (`CoffreDuCompte`, `Trousseau`) | Le courriel retenu (chiffré, Keystore) et la session gardée sous la biométrie : propres à l'entrée (`ParcoursEntree`) ; un écran de menu ne les emploie pas. |

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
| `MessageErreur(texte, reessayer?)` | `MessageErreur(texte:)` | La même, pour une phrase que l'écran compose (« 500 notifications sur 1200 ont été supprimées ; … » puis la raison) : même mise en forme, même « Réessayer ». La version `erreur` passe par elle. |
| `ErreurService.phrase()` | `.phrase` | La phrase d'une erreur, en `@Composable`. |
| `EtatVide(titre, icone?, explication?)` | `ContentUnavailableView` | Une liste vide, dite. |
| `commun.texte.Separateur` : `point`, `tiret`, `fleche` | `Separateur` | Les séparateurs du site (` · `, ` — `, ` → `), GÉNÉRÉS depuis `separateurs` : une espace sécable avant, insécable après — la ligne se coupe devant, jamais après (#117). Jamais recopiés : `joinToString(Separateur.point)`, `"$a${Separateur.tiret}$b"`. Une ressource ne lit pas une constante : elle écrit ` ·\u00A0`, et `verifier_separateurs.py` la confronte à la table (divergence d'Android). |
| `commun.fil` : `CarteDuFil(element, session?)`, `ElementDuFil`, `MetaDuFil`, `TextesFil`, `ApercuDuFil.elements()` | `CarteDuFil`, `Apercu.fil` | La carte d'un élément du fil et son modèle, hors du menu Fil (#153) : le fil la pose, et « En savoir plus » montre trois cartes du fil inventé (`ApercuDuFil`), sans session ni toucher. |
| `LocalHorsLigne` → `EtatHorsLigne(consultation, enLigne)` | `Parcours.consultation`, `Reseau.enLigne` | Le hors ligne de l'espace ouvert (#158), posé une fois par `EspaceHorsLigne` : un écran le lit, il ne le reçoit pas. Hors de l'espace (tests, aperçus) : en ligne. |
| `LocalSaisieAffaire` | `ouverteAJour.profil.saisieAffaire` | La saisie d'une affaire du compte (`saisie_affaire_effective`), posée par la résidence ouverte et relue avec les droits (#178) ; `null` hors d'elle. « Nouvelle affaire » et « Modifier » la lisent (`FormeDeSaisie`). |
| `PageHorsLigne { … }`, `BandeauHorsLigne()` | `BandeauHorsLigne` | Une page qui dit le hors ligne sous sa barre — Fil, Affaires, Annuaire, Profil, Ma résidence, la fiche d'une affaire. Le bandeau décide seul de paraître ; repère `horsligne.bandeau`. |
| `gesteEnLigne(actif)` → `GesteEnLigne(actif, modifier)` | `.gesteEnLigne()` | UN contrôle qui agit sur le serveur : il prend `actif` pour son `enabled` et `modifier` sur lui-même — en consultation, désactivé, en place, et le lecteur d'écran dit pourquoi. Jamais `if (horsLigne)`. `BoutonsFeuille` et le geste de `FeuilleProfil` l'appliquent d'office. |
| `Carte(ouvrir?, action?) { … }` | `Carte` | La surface d'une carte : `surface`, filet `border`, sans ombre ; touchable si `ouvrir`. |
| `LogoResidence(installation?, modifier)`, `LocalLogos` | `LogoResidence` | Le logo de la résidence (GET /config/logo.png, l'adresse tirée de la configuration générée ; la variante sombre au thème sombre), sinon celui de CoproFirst ; une tuile pendant la première lecture ; lu une fois par lancement (`LogosEnMemoire`). Décoratif. Un test pose ses logos par `LocalLogos` (#137). |
| `Section(titre?, modifier?, note?, enRetrait?) { … }`, `Note(texte)`, `LigneValeur(libelle, valeur)` | `Section`, `LabeledContent` | Une section groupée : son libellé (intitulé du lecteur d'écran), sa carte, sa note ; à la marge de l'écran (`enRetrait = false` dans une page déjà en retrait). Une valeur et son libellé sur une ligne. Profil, À propos, la feuille « Résidences » (#138). |
| `PastilleTeinte(libelle, badge, compacte?)`, `LibelleTeinte` | `PastilleTeinte` | Une pastille d'état : la classe `badge-*` du site → couleurs de la charte ; `LibelleTeinte.brut(v)` pour une valeur inconnue (en gris). |
| `PastilleStatut(statut, libelle, compacte?)` | `PastilleStatut` | Le statut d'une affaire : la teinte par son code (`StatutsAffaire.pastilles`, générée de `statutsTicket`), le libellé servi, dit « Statut : … » — Mes affaires, Reporting de l'Espace CS, fil (#92). |
| `BoutonPlein(libelle, actif?, enCours?)` | `.plein` | Le bouton principal (`primaryPlein`), toute la largeur. Le `Button` de Material ne s'écrit que dans `Boutons.kt`. |
| `BoutonPleinDestructif(libelle, actif?, icone?)` | `Button(role: .destructive)` plein | Le bouton plein d'un geste définitif : fond `dangerPlein`, texte `surPlein`, `icone` avant le libellé ; à la largeur de son libellé (l'écran le place). « Supprimer (3) » des notifications. |
| `BoutonContour(libelle)` | `.contour` | Le bouton secondaire. |
| `Roles.statut(v)`, `Roles.role(v)`, `Roles.statutAbrege(v)` | `RolesUtilisateur.libelleStatut` | Le libellé et la pastille (`LibelleTeinte`) d'un statut ou d'un rôle servi, depuis les tables générées ; une clé inconnue s'affiche brute. |
| `Roles.affiches(statut, roles)`, `Roles.demandables` | `ProfilResidence.rolesAffiches` | Les pastilles d'un compte (statut, puis rôles mis en avant) ; les statuts qu'un compte peut demander. |
| `ConfirmationDeSortie(geste, sortie, fermer)`, `GesteDeSortie` | `confirmationDeSortie` | La confirmation de « Se déconnecter » / « Oublier toutes les résidences » (repère `sortie.confirmer`). |
| `Apparence` | Profil › Apparence | Le thème de l'appareil : `choix` (`auto`, `clair`, `sombre`), `options` (`OPTIONS_THEME`), `choisir(v)` — appliqué à la racine. |
| `Pastille(libelle, retenue, compte?, role?) { … }` | `Pastille` | Une pastille de filtre (ou d'onglet, `role = Role.Tab`) : retenue, fond plein (`primaryPlein`) ; sinon surface et filet. Le `compte` ne paraît que sur la retenue. Dite « sélectionnée » au lecteur d'écran. |
| `ChampRecherche(texte, changer, indication, repere)` | `ChampRecherche` | Le champ de recherche d'une liste, DANS la page (pas dans la barre) : loupe, indication, croix « Effacer la recherche ». Le filtre : `Recherche.contientTousLesMots`. |
| `BoutonsFeuille(envoi, repere, annuler, libelle?, actif?) { envoyer }` | `BoutonsFeuille` | Le pied d'une feuille de saisie : « Enregistrer » (ou `libelle`) en `BoutonPlein`, « Enregistrement… » pendant l'envoi, puis « Annuler ». |
| `FlowCapsules(espace?) { … }` | `FlowCapsules`, `FlowEtiquettes` | Des `PastilleTeinte(…, compacte = true)` côte à côte, qui passent à la ligne au bord (6 dp) ; `espace = 8.dp` pour des `Pastille` touchables. |
| `Chiffre(valeur, libelle, modifier?, alerte?, style?, repere?)`, `AlerteChiffre` | `CompteurFile`, `Chiffre` | Un chiffre (`kpi-card` du site) : la valeur (`FormatsDuSite.nombre`) et ce qu'elle compte ; `alerte` le teinte — `Avertissement` (orange, une file à traiter), `Danger` (rouge, un retard). Les compteurs de la file de travail et du Reporting. |
| `RangeeDeChiffres { m -> … }`, `GRANDE_ECHELLE` | `ViewThatFits` | Des chiffres côte à côte, de même largeur (`m` : leur `Modifier`) ; l'un sous l'autre dès l'échelle de texte `GRANDE_ECHELLE` (1,5 : les tailles d'accessibilité de l'iOS), que suit aussi tout ce qui va côte à côte. |
| `Intertitre(titre)` | `Intertitre` | Le titre d'une section d'une fiche, en serif, son compte dans le texte (« 🏢 Lots possédés (3) ») ; annoncé comme titre. |
| `LigneFiche(libelle, valeur)` | `LigneFiche` | Une ligne « libellé — valeur » (le `<dl>` du site), lue d'un bloc ; la valeur, sélectionnable, passe dessous quand la place manque. |
| `NoteEnCarte(texte, titre?, icone?)` | `NoteAcces` | Une note ou un état vide en ligne, dans une `Carte` : icône, titre facultatif, texte sourd. |
| `Libelle(texte, modifier?, couleur?, fort?)` | `.styleLibelle()` | Le libellé en petites capitales, sourd : titre d'une section (`Modifier.semantics { heading() }` en `modifier`) ou libellé d'un champ. Le seul emploi de `Typo.libelle()` (`verifier-depot.sh`). `couleur`, `fort` : l'étoile d'un champ requis. |

⚠️ Divergence déclarée : `BoutonsFeuille` est au pied de la feuille, sous le pouce — l'iOS
les pose dans la barre de la feuille ; une feuille d'Android n'en a pas.

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

## Tests d'interface — `androidTest/…/commun/Gestes.kt`

Chaque écran a ses tests d'interface (`app/src/androidTest`, serveur local, repères = les
identifiants d'accessibilité de l'iOS). Trois règles, tenues par `verifier-depot.sh` :

- **Toucher par le geste commun**, jamais `performClick()` (refusé) : `ecran.toucherTag(repère)`,
  `ecran.toucherTexte(texte)`, `ecran.toucherDescription(description)`, ou `noeud.toucher()`.
  Le geste ferme le clavier, attend le nœud composé et placé (une ligne de liste paresseuse se
  fait venir), fait défiler chaque conteneur de l'extérieur vers l'intérieur, attend le nœud
  immobile, vérifie qu'il est affiché, puis touche. `ecran.amenerTag(repère)` amène sans toucher.
  Un échec dit où était le nœud.
- **Règle v2** : `androidx.compose.ui.test.junit4.v2.createComposeRule` (effets au fil
  principal) ; la v1 est refusée.
- **Ce qui se lit d'un bloc se cherche par sa description** : un composant qui remplace la
  sémantique de son texte (`EtiquetteLieu` : « Lieu : … » ; une carte du fil) ne répond pas à
  `hasText` — `hasTestTag(repère) and hasContentDescription(…, substring = true)` (#129).

Deux gestes de plus, partagés : `ecran.attendreTexte(texte)` (un texte affiché, au délai
commun) et `ecran.entrerDansLaResidence(adresse, nom) { … }` (`entree/EntreeDeTest.kt` : de
l'accueil à la connexion ; le bloc sert les réponses de la connexion mises en file).

Le bilan de la CI (job « Interface ») nomme dans le journal les tests en échec et les cinq
plus lents, en regard du délai commun (`DELAI_GESTE_MS`, 10 s). Au poste :
`bash scripts/tests-interface.sh [classe]` (une seule tâche lourde à la fois).

## Charte — `fr.coprofirst.app.charte`, `ThemeCoproFirst`

| API | Rôle |
|---|---|
| `LocalCouleurs.current` | Les couleurs de la palette en cours (clair ou sombre) : `primary`, `text`, `textMuted`, `surface`, `border`, `danger`, `successFond`… — un jeton de la charte chacune, générées depuis coprofirst. |
| `MaterialTheme.colorScheme` | Les rôles de Material, tirés des mêmes jetons (`ThemeCoproFirst`). |
| `Mise.rayon`, `Mise.marge`, `Mise.cible`, `Mise.ecart`, `Mise.mosaique`, `Mise.mosaiqueCompacte`, `Mise.vignette` | Les mesures communes (`Mise`), PROPRES À L'APP, chacune déclarée dans `MesuresTest` (raison, date). Cible tactile : 48 dp (règle d'Android). |
| `MesuresDuSite.rayon` | **Générée** (`jetonsCharte`, `scripts/generer_mesures.py`) : les mesures du site en dp, que `MesuresTest` confronte à `Mise` — un écart non déclaré, caduc ou dont le jeton a bougé échoue (#2). |
| `Typo.titre()`, `Typo.libelle()` | Titre en serif (`Font.titre`), libellé de section (`.styleLibelle()`) — ce dernier par `Libelle` seul. |
| `Pictogrammes.icone(lucide)`, `Pictogrammes.parNom` | Une icône du site (nom Lucide) en icône de Material ; un nom absent prend le repli. Les tests refusent qu'une page, une rubrique du fil ou une nature d'affaire générées en manquent. ⚠️ L'iOS dessine les tracés Lucide du fil ; leur catalogue n'est pas publié avec la release. |
| `NaturesDuFil.parType`, `NatureDuFil.de(type)`, `NatureDuFil.inconnue` | **Générée** (`naturesDuFil`) : ce que le fil montre d'un type d'élément — `libelle`, `couleur` et `fond` (des JETONS de `Couleurs` : `nature.couleur(LocalCouleurs.current)`), `icone`. Un type inconnu : neutre. |
| `NaturesAffaire.toutes`, `NatureAffaire` | **Générée** (`naturesAffaire`, `teinteNature`) : les natures d'une affaire dans l'ordre du site — `code`, `libelle`, `icone`, `badge` (pour `PastilleTeinte`). |
| `Mouvement.sortie` | La courbe d'entrée (ease-out marquée) : passage d'un écran, apparition d'une rangée, fondu d'un aperçu. |

## À venir dans la couche commune

Tables de libellés — 15b ; la cloche
qui rouvre une notification touchée — #23. Composants de l'iOS employés par UN seul menu
pour l'heure, à monter ici dès qu'un deuxième les emploie : `ChampSaisie` (Espace CS),
`FlowEtiquettes` (Communauté) ; et la pastille « où suis-je » (`PastilleOuSuisJe` : logo et
nom de la résidence dans la barre du haut, changer de résidence). Demandés par le fil
(#11) : un geste de `ContexteDeMenu` qui ouvre une autre page de la barre — et un élément
précis — depuis un écran (fait : la porte, #85) ; le jour et le mois en toutes lettres
dans `FormatsDuSite` (l'en-tête d'un jour du fil). Le suivi : #15, #11.
