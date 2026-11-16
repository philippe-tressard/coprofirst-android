<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Composants communs — la présentation

Suite de [`docs/COMPOSANTS.md`](../COMPOSANTS.md) (règles, navigation, session, écran,
charte) : l'avis, les pages à onglets, la cloche, les formats, le texte du site, les pièces
jointes, la feuille pleine. Même contrat : un écran consomme cette API sans la modifier.

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
| `ContexteOnglet` | `ContexteOnglet` | Ce qu'un onglet reçoit : `page`, `onglet`, `session`, `droits`, `avis` (`avis.dire(…)`), `actualisation` (augmente à chaque « tirer pour actualiser » : `Chargement(cle = contexte.actualisation)`), `barre` (`ActionsDeBarre(contexte.barre) { … }`), `ouvrir` (la porte du menu : `contexte.ouvrir(Destination.Element("affaire", id))`). |
| `BarreOnglets(onglets, actif, marges?) { choisir }` | `BarreOnglets` | La barre seule, pour une page qui garde sa mise en page (Mes lots & accès, Prestataires, Résidence) : `Pastille` qui défilent de côté, repères `onglet.<id>`. |
| `OngletBientot(onglet)` | `OngletBientot` | Un onglet du site que l'app ne porte pas encore : « Bientôt » (posé par le cadre). |
| `OngletSurLeSite(onglets, descriptifs, routes, adresse)` | `OngletSurLeSite` | Les onglets laissés au site, chacun avec son lien (repère `site.onglet.<id>`), ouvert par le navigateur à l'adresse de la résidence (posé par le cadre). |
| `OngletsSurLeSite.lien(route, adresse)` | `OngletSurLeSite.lien` | L'adresse d'un onglet sur le site, sous celle de la résidence (qui peut porter un chemin) ; `null` pour ce qui n'est pas un chemin. |
| `OngletsDeLaPage(page, portes, renvoieAuSite, droits, surLeSite)` | — | La logique de la barre (`barre`, `ailleurs`, `actif(choisi)`), testée sur la JVM. |

```kotlin
// fonctions/espacecs/EcranEspaceCS.kt — inscrit par InscriptionEspaceCS : "espace-cs" to { EcranEspaceCS(it) }
@Composable
fun EcranEspaceCS(contexte: ContexteDeMenu) = CadreAOnglets(
    contexte,
    PageAOnglets(
        "espace-cs",
        mapOf(
            "validations" to { onglet -> OngletComptesCS(onglet) }, // commun.validations

            "badges" to { onglet -> OngletBadges(onglet) },
        ),
        reserve = R.string.espacecs_reserve,
    ),
)

@Composable
fun OngletBadges(contexte: ContexteOnglet) {
    val service = remember(contexte.session) { ServiceBadgesApi(contexte.session) }
    Chargement(charger = { service.parc() }, cle = contexte.actualisation, squelette = { … }) { parc, recharger ->
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
`dateHeureCourte`, `heure`, `jourCourt`, `jourIso` (envoyé), `moisAnnee`, `moisCourt`,
`relatif` (« hier », « il y a 3 jours », comme `.relative(presentation: .named)` de l'iOS) —
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

## Feuille pleine — `fr.coprofirst.app.commun`

| API | Pendant iOS | Usage |
|---|---|---|
| `FeuillePleine(titre, repereOk, fermer, actions?) { contenu }` | feuille `.large` | Une feuille pleine hauteur qui porte un écran : titre, `actions` (sa barre), « OK » (`repereOk`) ; le contenu défile seul. Les notifications de la cloche et la fiche d'un sondage. |
| `ErreurService.phrase(ressources)` | `.phrase` | La phrase d'une erreur hors composition (l'avis d'un geste échoué). |

