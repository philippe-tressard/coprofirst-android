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

et, dans `RegistreDesMenus.kt`, la ligne réservée `// "profil" — #21` devient :

```kotlin
        "profil" to { contexte -> EcranProfil(contexte) },
```

`ContexteDeMenu` donne à l'écran : `page` (sa page : `titre`, `route`…), `session`
(`SessionOuverte`, pour ses services), `droits` (`DroitsOnglet` du compte : `cs`,
`administrateur`, `locataire`… — un geste fin se juge sur le `peut_*` servi avec l'objet,
jamais sur ces droits seuls), `sortie` (`GestesDeSortie` : `deconnecter()`,
`oublierTout()` — toujours confirmés par `ConfirmationDeSortie`), `apparence`
(`Apparence` : le thème de l'appareil, `choix`, `options`, `choisir(valeur)`) et `barre`
(la barre du haut : `ActionsDeBarre(contexte) { IconButton(…) }` y pose les actions de
l'écran — le `toolbar` de l'iOS ; elles se retirent quand l'écran se quitte). Le titre de la page est posé par la barre : l'écran n'en
pose pas. Une page sans ligne au registre affiche « Bientôt ».

**La porte** (#85) — `contexte.ouvrir(destination)` mène d'un écran ailleurs, sans toucher à
la barre ni dépendre du code d'un autre menu : `Destination.Page(id)` (une page de la barre
ou de « Plus »), `Destination.Element(nature, id)` (la fiche d'un élément, poussée par-dessus
la page ouverte, avec retour), `Destination.Notifications` (la cloche). Elle rend `false`
quand rien ne la porte dans l'app (page que le compte ne voit pas, fiche non déclarée) :
l'écran garde son repli, « Voir sur le site » par exemple. Un menu **déclare** les éléments
qu'il sait ouvrir dans `RegistreDesMenus.fiches`, par nature (celles du fil : `affaire`,
`sondage`), sur SA ligne réservée :

```kotlin
        "affaire" to FicheDeMenu("Affaire") { contexte, id -> DetailAffaire(contexte, id) },
```

## Navigation — `fr.coprofirst.app.navigation` (à la couche commune)

| API | Pendant iOS | Rôle |
|---|---|---|
| `RegistreDesMenus` | `RegistreDesMenus` | id de page → écran ; « Bientôt » sinon. |
| `ContexteDeMenu` | `ContexteDeMenu` | Ce qu'un écran reçoit. |
| `DroitsOnglet`, `PagesOuvertes`, `OngletsDuSite.ouverts(page, droits)` | idem | Qui voit quelle page, quel onglet (tables générées). |
| `PagesParDefaut`, `OrdrePages`, `Installation.pages` | idem | La barre : ordre servi (`pages_order`), libellés (`page_config_*`). `PagesParDefaut.horsMenu` : les pages sans entrée de menu (profil, notifications). |
| `OngletsDuSite.descriptifs[page]`, `OngletsDuSite.routes[page]` | `Tables<Menu>.descriptifs`, `.routes` | Le descriptif et l'adresse sur le site de chaque onglet (générés). |
| `EcranResidence` | `OngletsView` | La barre (4 pages + « Plus ») ; en haut, la cloche. |
| `ActionsDeBarre(contexte) { … }` | `.toolbar` | Les actions de la barre du haut, déclarées par l'écran affiché. |
| `contexte.ouvrir(Destination)`, `RegistreDesMenus.fiches`, `FicheDeMenu` | `parcours.suivre`, `Destination`, `Aiguillage` | La porte : ouvrir une page, la fiche d'un élément ou la cloche depuis un écran. |

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
| `Roles.affiches(statut, roles)`, `Roles.demandables` | `ProfilResidence.rolesAffiches` | Les pastilles d'un compte (statut, puis rôles mis en avant) ; les statuts qu'un compte peut demander. |
| `ConfirmationDeSortie(geste, sortie, fermer)`, `GesteDeSortie` | `confirmationDeSortie` | La confirmation de « Se déconnecter » / « Oublier toutes les résidences » (repère `sortie.confirmer`). |
| `Apparence` | Profil › Apparence | Le thème de l'appareil : `choix` (`auto`, `clair`, `sombre`), `options` (`OPTIONS_THEME`), `choisir(v)` — appliqué à la racine. |
| `Pastille(libelle, retenue, compte?, role?) { … }` | `Pastille` | Une pastille de filtre (ou d'onglet, `role = Role.Tab`) : retenue, fond plein (`primaryPlein`) ; sinon surface et filet. Le `compte` ne paraît que sur la retenue. Dite « sélectionnée » au lecteur d'écran. |
| `ChampRecherche(texte, changer, indication, repere)` | `ChampRecherche` | Le champ de recherche d'une liste, DANS la page (pas dans la barre) : loupe, indication, croix « Effacer la recherche ». Le filtre : `Recherche.contientTousLesMots`. |
| `BoutonsFeuille(envoi, repere, annuler, libelle?, actif?) { envoyer }` | `BoutonsFeuille` | Le pied d'une feuille de saisie : « Enregistrer » (ou `libelle`) en `BoutonPlein`, « Enregistrement… » pendant l'envoi, puis « Annuler ». |
| `FlowCapsules { … }` | `FlowCapsules` | Des `PastilleTeinte(…, compacte = true)` côte à côte, qui passent à la ligne au bord. |

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

## Avis éphémère — `fr.coprofirst.app.commun`

La phrase brève qui suit un geste (le « toast » du site) : l'écran la DIT, seul
`AvisEphemere` la montre. Elle monte du bas (fondu seul quand les animations de l'appareil
sont coupées), reste `AvisDuSite.DUREE_MS` — **générée** depuis `dureeToastMs` de
`tables-clients.json` (`DUREE_TOAST_MS` du site, 4 s), comme l'iOS (`AvisDuSite`) —, puis
se retire. Elle est annoncée au lecteur d'écran (région vivante) et suivie d'un retour
haptique selon son ton. Une phrase dite remplace celle qui est à l'écran ; le retrait d'une
phrase n'efface jamais celle qui l'a remplacée.

| API | Pendant iOS | Usage |
|---|---|---|
| `AvisDuGeste()` | `AvisDuGeste` | L'avis d'un écran : `dire(texte, ton = TonAvis.Succes)`, `dire(PhraseAvis)` ; `texte`, `phrase` (lus par l'avis seul). |
| `avis.direApresFermeture(texte, ton)`, `avis.feuilleFermee()` | idem | Dite par une FEUILLE : la phrase attend que la feuille soit fermée (`feuilleFermee()` dans son `fermer`) — sinon elle passerait derrière. |
| `TonAvis` (`Succes`, `Avertissement`, `Erreur`, `Info`), `TonAvis.depuis(v)` | `TonAvis` | Le ton du site (`success`, `warning`, `error`, `info`) : le pictogramme et le retour haptique ; un ton servi inconnu se dit en `Info`. |
| `AvisEphemere(avis) { contenu }` | `.avisEphemere(avis)` | Le contenu, et l'avis en bas, par-dessus (repère `avis`). Le cadre à onglets le pose déjà : un onglet dit `contexte.avis.dire(…)`. |

⚠️ Divergence déclarée : la bulle garde les couleurs de l'avis du site quel que soit le ton
(`text` à 92 %, `textInverse`), comme l'iOS ; `teinteDuTon` de `tables-clients.json` est
celle des verdicts du journal (`journal-verdicts.ts`), pas celle de l'avis.

```kotlin
val avis = remember { AvisDuGeste() }
val ressources = LocalResources.current
AvisEphemere(avis, Modifier.fillMaxSize()) {
    // … un geste réussi :
    avis.dire(ressources.getString(R.string.monlot_acces_enregistre))
}
```

## Pages à onglets — `fr.coprofirst.app.commun.onglets`

Une page du site à onglets (Espace CS, Paramétrage…) déclare sa TABLE d'onglets ; le cadre
fait le reste, comme `CadreAOnglets` de l'iOS : barre d'onglets dans l'ordre du site,
filtrée par les droits du compte (`OngletsDuSite.ouverts(page, droits)`), descriptif de
l'onglet (généré), défilement, « tirer pour actualiser », avis éphémère, « page réservée »
si le compte n'en ouvre aucun. Le titre est celui de la barre du haut : le cadre n'en pose
pas. L'onglet rend son CONTENU — une `Column`, jamais une `LazyColumn` (le cadre défile).

| API | Pendant iOS | Usage |
|---|---|---|
| `CadreAOnglets(contexte, page)` | `CadreAOnglets` | Le cadre ; repères `onglet.<id>`, `onglets.reservee`. |
| `PageAOnglets(id, ecrans, reserve, renvoieAuSite = false)` | `PageAOnglets` | `ecrans` : id d'onglet du site → `@Composable (ContexteOnglet) -> Unit`, une ligne par onglet ; `reserve` : la phrase (`@StringRes`) de la page réservée. `renvoieAuSite` : la barre n'offre que les onglets inscrits, puis « Sur le site ». `descriptifs`, `routes` : ceux du site par défaut. |
| `ContexteOnglet` | `ContexteOnglet` | Ce qu'un onglet reçoit : `page`, `onglet`, `session`, `droits`, `avis` (`avis.dire(…)`), `actualisation` (augmente à chaque « tirer pour actualiser » : `Chargement(cle = contexte.actualisation)`), `barre` (`ActionsDeBarre(contexte.barre) { … }`). |
| `BarreOnglets(onglets, actif, marges?) { choisir }` | `BarreOnglets` | La barre seule, pour une page qui garde sa mise en page (Mes lots & accès, Prestataires, Résidence) : `Pastille` qui défilent de côté, repères `onglet.<id>`. |
| `OngletBientot(onglet)` | `OngletBientot` | Un onglet du site que l'app ne porte pas encore : « Bientôt » (posé par le cadre). |
| `OngletSurLeSite(onglets, descriptifs, routes, adresse)` | `OngletSurLeSite` | Les onglets laissés au site, chacun avec son lien (repère `site.onglet.<id>`), ouvert par le navigateur à l'adresse de la résidence (posé par le cadre). |
| `OngletsSurLeSite.lien(route, adresse)` | `OngletSurLeSite.lien` | L'adresse d'un onglet sur le site, sous celle de la résidence (qui peut porter un chemin) ; `null` pour ce qui n'est pas un chemin. |
| `OngletsDeLaPage(page, portes, renvoieAuSite, droits, surLeSite)` | — | La logique de la barre (`barre`, `ailleurs`, `actif(choisi)`), testée sur la JVM. |

```kotlin
// fonctions/espacecs/EcranEspaceCS.kt — et SA ligne au registre : "espace-cs" to { EcranEspaceCS(it) }
@Composable
fun EcranEspaceCS(contexte: ContexteDeMenu) = CadreAOnglets(
    contexte,
    PageAOnglets(
        "espace-cs",
        mapOf(
            "validations" to { onglet -> OngletValidations(onglet) },

            "badges" to { onglet -> OngletBadges(onglet) },
        ),
        reserve = R.string.espacecs_reserve,
    ),
)

@Composable
fun OngletValidations(contexte: ContexteOnglet) {
    val service = remember(contexte.session) { ServiceEspaceCSApi(contexte.session) }
    Chargement(charger = { service.aValider() }, cle = contexte.actualisation, squelette = { … }) { comptes, recharger ->
        Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) { … contexte.avis.dire(…) … }
    }
}
```

## Cloche — `fr.coprofirst.app.navigation`

| API | Pendant iOS | Usage |
|---|---|---|
| `BoutonCloche(page) { ouvrir }` | `ClocheNotifications` | Dans la barre du haut de chaque page de la résidence (posée par `EcranResidence`, après les actions de l'écran ; repère `cloche`). |
| `FeuilleNotifications(page, contexte, fermer)` | `.feuilleNotifications` | La page `notifications` DU REGISTRE (`RegistreDesMenus`), en feuille : son titre, les actions que son écran déclare (`ActionsDeBarre` : la barre de la feuille), « OK » (repère `notifications.ok`). |
| `Cloche.page` | — | La page des notifications (`PagesParDefaut.horsMenu`, générée) ; absente : pas de cloche. |

⚠️ Divergence déclarée : pas de point « non lues » sur la cloche — l'iOS le compte en
relisant toute la liste (GET /notifications) ; aucune route du contrat ne sert ce nombre.
Il reste dit en tête de la page des notifications.

## Formats — `fr.coprofirst.app.commun.formats.FormatsDuSite`

Les formats du site (`FormatsDuSite`), fr-FR, à l'heure de Paris ; « — » pour une
valeur absente. Dates : `date`, `dateCourte`, `dateLongue`, `dateHeure`,
`dateHeureCourte`, `heure`, `jourCourt`, `jourIso` (envoyé), `moisAnnee`, `moisCourt` —
chacune prend un `Instant` ou la chaîne ISO servie (`lire` la lit comme le navigateur).
Nombres : `nombre`, `montant`, `octets`. Personnes : `nom(affiche, prenom, nom)` — le
`nom_affiche` servi d'abord, jamais composé dans un écran.

## Texte du site — `fr.coprofirst.app.commun.texte`

Le HTML riche du site (descriptions, textes du fil, suites, sondages — l'éditeur Tiptap)
ne s'affiche **jamais** tel quel, et ne se convertit nulle part ailleurs qu'ici : un
lecteur maison, sur la liste blanche du site, qui ne charge ni n'exécute rien. Une vue web
ou le lecteur HTML d'Android sont refusés par `verifier-depot.sh`.

| API | Pendant iOS | Usage |
|---|---|---|
| `TexteRiche(html, style?, couleur?)` | `TexteRiche` | Le texte riche d'un détail : gras, italique, souligné, barré, code, titres, citations, `br`, `hr`, listes à puces et numérotées avec **retrait suspendu**, liens en `primary` (ouverts par le système). `style` : `bodyLarge` par défaut ; `couleur` : `text`. |
| `TexteDuSite.apercu(html)` | `.apercu` | Le texte simple d'une carte : balises retirées, entités décodées, paragraphes en espaces (la vue tronque ensuite). |
| `TexteDuSite.riche(html)`, `.blocs(html)` | `.riche`, `.blocs` | Les mêmes en `AnnotatedString` (pour un `Text` seul) ou bloc par bloc (`BlocDuSite` : `niveau`, `marqueur`, `texte`, `ecart`). `blocs(html).isEmpty()` : rien à montrer. |
| `TexteDuSite.html(saisi = …)` | `.html(saisi:)` | Un texte SAISI dans l'app → le HTML de l'éditeur du site (`<p>`, `<br>`, `<` échappé). À envoyer tel quel. |
| `TexteDuSite.saisi(html)` | `.saisi(html:)` | Le texte à réécrire d'un HTML servi — `null` si le réenregistrer perdrait sa mise en forme : l'écran le montre sans champ, avec `TexteDuSite.MISE_EN_FORME_AU_SITE`. |
| `TexteDuSite.lienSur(href)` | `.lienSur` | L'adresse si elle est absolue, d'un schéma de `BalisesDuSite.schemasDeLien`, sans caractère de contrôle ; sinon `null` (texte inerte). |
| `BalisesDuSite` | `BalisesDuSite` | La liste blanche, **générée** (`scripts/generer_tables.py`) : `balises`, `attributs` (`balisesAdmises`, `attributsAdmis` de `tables-clients.json`), `schemasDeLien` (`http`, `https`, `mailto` — pas encore publiés par coprofirst : tenus par le générateur). |
| `Recherche.contientTousLesMots(champs, recherche)` | `Recherche` | Le filtre d'une liste : tous les mots, dans n'importe quel ordre, sans accents ni casse, sur ce que la ligne affiche. |

Exemple :

```kotlin
Carte { Text(TexteDuSite.apercu(affaire.description), maxLines = 3, overflow = TextOverflow.Ellipsis) }
TexteRiche(affaire.description, style = MaterialTheme.typography.bodyMedium)
val texte = TexteDuSite.saisi(affaire.description) // null : montrer, ne pas proposer de champ
```

## Pièces jointes — `fr.coprofirst.app.commun.pieces`

Les photos et documents joints à un élément (affaire, suite, message, carte du fil,
annonce) se rangent, se nomment et s'ouvrent ici, et nulle part ailleurs. **Pas d'ajout
de pièce** dans l'app, comme l'iOS : l'écran dit qu'elles s'ajoutent depuis le site.

| API | Pendant iOS | Usage |
|---|---|---|
| `PiecesJointes(urls)` | `PiecesJointes(urls:)` | Toutes les URL servies ensemble (`photosUrls + fichiersUrls`) → `photos`, `documents` (l'extension décide), dans l'ordre servi, sans doublon ; `vide`, `nombre`, `enClair` (« 2 photos, 1 pièce jointe », pour le lecteur d'écran d'une carte). |
| `Fichiers.estImage`, `.nomAffiche`, `.typeAffiche`, `.nomSansExtension`, `.nomSur` | `Fichiers` | Les règles du site (`fichiers.ts`) : nom montré sans le préfixe de téléversement, type « PDF », « WORD »…, nom sûr pour le disque. |
| `FichiersDuSite` | `FichiersDuSite` | **Générée** (`scripts/generer_tables.py`) : `extensionsImage`, `LONGUEUR_PREFIXE`, `familles` — pas encore publiées par coprofirst : tenues par le générateur, aux valeurs de l'iOS. |
| `PiecesJointesVue(pieces, session, format = Grand\|Compact)` | `PiecesJointesView` | Un élément OUVERT : intitulé « Pièces jointes · N » (`Grand`), mosaïque des photos, puis lignes des documents ; un toucher ouvre la pièce. `Compact` : sous une suite ou un message, sans intitulé. Rien sans pièce. |
| `VignettePieces(pieces, session)` | `VignettePieces` | Une carte FERMÉE : la première photo et « +N », ou un trombone et le nombre de documents. Décorative : la carte dit `pieces.enClair`. |
| `PhotoProtegee(chemin, session, remplir?)` | `PhotoProtegee` | Une photo de `/uploads/` (avatar, photo de fiche…) : lue par la session, décodée à la taille du cadre, gardée en mémoire seulement. |
| `MosaiquePhotos(chemins, session, hauteur, enCours, ouvrir)` | `MosaiquePhotos` | La mosaïque seule (1, 2, ou 1 grande + 2 petites et « +N »). |
| `LigneDocument(nom, enCours, taille?, ouvrir)` | `LigneDocument` | Un document : type, nom sans extension, taille (`FormatsDuSite.octets`) si connue. `nom` : le chemin servi ou `fichier_nom`. |
| `rememberOuverture(session)` → `Ouverture` ; `AvisOuverture(ouverture)` | `quickLookPreview`, `DocumentView` | Ouvrir en grand : `piece(chemin)` (une pièce jointe), `document(id, nom)` (un document, par le client généré) ou `fichier(sous, cle, nom, defaut) { base, client -> …Api(base, client).get…Tlcharger(id) }` (tout fichier servi par une route : un rapport, un export) — copie dans le cache de l'app, puis l'app de l'appareil qui sait la montrer ; `enCours`, `echec` (session expirée, aucune app pour ce type…), dit par `AvisOuverture`. |
| `SessionOuverte.fichierProtege(chemin)` | `fichierProtege` | LA porte des fichiers de `/uploads/` (hors contrat) : l'hôte de la session et lui seul, sans redirection, erreurs en `ErreurService`. Dispensée nommément par `verifier-depot.sh`. |
| `PiecesLocales.fichierServi(session, sous, cle, nom, defaut) { base, client -> … }` | `fichierServi` | Un fichier servi par une ROUTE (rapport…) que le client généré télécharge, rangé dans le cache sous un nom sûr. |
| `PiecesLocales.effacer(contexte)` | `Fichiers.effacerLesCopies` | Copies et photos en mémoire effacées : au lancement, à la déconnexion, à « Oublier toutes les résidences » (déjà branché). |

⚠️ Divergences déclarées : Android n'a pas d'aperçu intégré comme Coup d'œil — la pièce
s'ouvre dans l'app de l'appareil (`ACTION_VIEW`, droit de lecture sur CE fichier) ; une
photo touchée s'ouvre seule (l'iOS feuillette toutes celles de l'élément) ; sous Android 8,
une photo n'est pas redressée selon son orientation EXIF.

Exemple :

```kotlin
val pieces = remember(affaire) { PiecesJointes(affaire.photosUrls.orEmpty() + affaire.fichiersUrls) }
PiecesJointesVue(pieces, contexte.session)                                   // l'affaire ouverte
PiecesJointesVue(PiecesJointes(message.fichiersUrls), contexte.session, format = FormatPieces.Compact)
Carte(Modifier.semantics { pieces.enClair?.let { stateDescription = it } }, ouvrir = ouvrir) {
    Row { Text(titre, Modifier.weight(1f)); VignettePieces(pieces, contexte.session) }   // une carte fermée
}

val ouverture = rememberOuverture(contexte.session)                          // un document du fil
LigneDocument(document.fichierNom, ouverture.enCours == "document-${document.id}", taille = document.tailleOctets) {
    ouverture.document(document.id, document.fichierNom)
}
AvisOuverture(ouverture)
```

## Charte — `fr.coprofirst.app.charte`, `ThemeCoproFirst`

| API | Rôle |
|---|---|
| `LocalCouleurs.current` | Les couleurs de la palette en cours (clair ou sombre) : `primary`, `text`, `textMuted`, `surface`, `border`, `danger`, `successFond`… — un jeton de la charte chacune, générées depuis coprofirst. |
| `MaterialTheme.colorScheme` | Les rôles de Material, tirés des mêmes jetons (`ThemeCoproFirst`). |
| `Mise.rayon`, `Mise.marge`, `Mise.cible`, `Mise.ecart`, `Mise.mosaique`, `Mise.mosaiqueCompacte`, `Mise.vignette` | Les mesures communes (`Mise`). Cible tactile : 48 dp (règle d'Android). |
| `Typo.titre()`, `Typo.libelle()` | Titre en serif (`Font.titre`), libellé de section (`.styleLibelle()`). |

## À venir dans la couche commune

Tables de libellés — 15b ; le fil d'activité (page `tableau-de-bord`) — #11 ; la cloche
qui rouvre une notification touchée — #23. Composants de l'iOS employés par UN seul menu
pour l'heure, à monter ici dès qu'un deuxième les emploie : `ChampSaisie` (Espace CS),
`FlowEtiquettes` (Communauté) ; et la pastille « où suis-je » (`PastilleOuSuisJe` : logo et
nom de la résidence dans la barre du haut, changer de résidence). Le suivi : #15, #11.
