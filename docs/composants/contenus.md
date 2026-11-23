<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Composants communs — les contenus partagés

Suite de [`docs/COMPOSANTS.md`](../COMPOSANTS.md) : ce que plusieurs menus montrent ou
saisissent de la même façon — qui la lit et où, les périmètres, l'aperçu servi, les personnes
de l'annuaire, les réponses, le signalement, la fiche d'un sondage, l'accès, la file des
validations. Même contrat : un écran consomme cette API sans la modifier.

## Qui la lit, et où — `fr.coprofirst.app.commun.lecture`

« Qui la lit » et le lieu d'un contenu (affaire, ligne du fil, annonce, idée, sondage), tels
que le serveur les SERT (`lecture`, `perimetre_libelle`) — jamais recomposés : la règle est
celle du serveur seul.

| API | Pendant iOS | Usage |
|---|---|---|
| `Lecture`, `Lecture.depuis(servie)`, `Lecture.lire(json)` | `Lecture` | La pastille servie : par le type généré d'une route, ou relue dans un `meta` libre (le fil) — illisible, `null`, sans emporter le reste. `phraseEntiere` : la bulle, puis ceux qui ne lisent pas. |
| `Lecture.aMontrer(l)` | `Lecture.aMontrer` | La pastille à montrer : `null` quand personne n'est exclu (`par_defaut`) ou qu'elle n'est pas servie. |
| `PastilleLecture(lecture, masculin = false, memeParDefaut = false)` | `PastilleLecture` | Sur une carte : teinte `badge-blue`, pictogrammes des lecteurs, libellé court, cadenas. Ne s'ouvre pas ; le lecteur d'écran lit « Qui la lit : … ». `masculin` : un sondage (« Qui le lit ») ; `memeParDefaut` : une saisie. |
| `LectureDetaillee(lecture, masculin)` | `LectureDetaillee` | Sur un détail : l'intitulé, la pastille, le titre, la phrase entière (repère `lecture.detail`). |
| `EtiquetteLieu(lieu, style?, couleur?)`, `LieuServi.lieu(perimetreLibelle)` | `EtiquetteLieu`, `LieuServi` | Le lieu servi, avec l'épingle ; `""` (le périmètre par défaut) ou absent : rien. |
| `enClair(lecture, masculin)`, `enClair(lieu)` | `.enClair` | Les mêmes en une phrase, pour le libellé d'une carte au lecteur d'écran. |

## Périmètres — `fr.coprofirst.app.commun.perimetres`

Le lieu d'un contenu (annonce du hall, affaire, actualité) se choisit dans l'arbre servi
(GET /perimetres), ici et nulle part ailleurs : un écran garde le `ChoixDePerimetres`,
`ChoixPerimetres` le dessine, la règle est pure et testée sur la JVM. Le périmètre par
défaut est celui que le serveur désigne (`par_defaut`) ; le nom d'une sélection est
celui du serveur (POST /perimetres/libelle), jamais composé par l'app.

| API | Pendant iOS | Usage |
|---|---|---|
| `Perimetre(code, libelle, profondeur, selectionnable, parDefaut, parent, libelleCourt, description, niveauxLibres)` | `Perimetre` | LE modèle d'un périmètre. `Perimetre.lu(PerimetreRead)`, `Perimetre.lus(liste)` (actifs seulement), `Perimetre.aSaisir(perimetres, garder?)` (ce qu'un sélecteur propose : `niveaux_libres` servi ; `garder` : le lieu déjà enregistré d'un contenu qu'on modifie), `Perimetre.codeParDefaut(perimetres)`. |
| `ServicePerimetres` : `perimetres()`, `lieu(codes)` ; `ServicePerimetresApi(session)` | `ServicePerimetres`, `ServiceApercus.lieu` | L'arbre (GET /perimetres) et le nom d'une sélection (POST /perimetres/libelle), par le client généré. Un service de menu peut le réaliser par délégation. |
| `ArbreDesPerimetres(noeuds)` | `ArbreDesPerimetres` | `defaut`, `tetes` (racines ciblables et enfants d'un regroupement, hors défaut), `espaces(tete)`, `tete(code)`, `noeud(code)`. |
| `ChoixDePerimetres(codes, touchee)` | `ChoixDePerimetres` | Immuable : chaque geste rend le nouvel état. `basculer(code, arbre)` (la table en cinq lignes du site), `choisirDefaut(arbre)`, `estDefaut`, `ouverte` (la tête dont la rangée se montre), `espacesRetenus`, `teteRetenue`, `libelleDeTete` (« Bâtiment A › Hall · Toit »), `aide` (la `description` du seul code retenu). |
| `ChoixPerimetres(arbre, choix, changer, repere, service?)` | `ChoixPerimetres` | Les pastilles des têtes (défaut d'abord), « Préciser dans <tête> — facultatif » et ses espaces dans une surface bordée (fondu et léger glissement, 220 ms ; fondu seul sous « Supprimer les animations »), l'aide, puis le lieu servi si `service`. Retour haptique de sélection. Repères : `"$repere.<code>"` par pastille, `"$repere.lieu"`. |
| `LieuDeLaSaisie(codes, service, repere)` | `LieuDeLaSaisie` | Le lieu d'une sélection, nommé par le serveur, dit par `EtiquetteLieu` (`commun/lecture`) ; sa ligne réservée, invisible, avant la première réponse. |

```kotlin
val service = remember(contexte.session) { ServicePerimetresApi(contexte.session) }
Chargement(charger = { ArbreDesPerimetres(service.perimetres()) }, squelette = { … }) { arbre, _ ->
    var choix by remember(arbre) { mutableStateOf(ChoixDePerimetres().choisirDefaut(arbre)) }
    ChoixPerimetres(arbre, choix, { choix = it }, repere = "nouvelle-annonce-hall.perimetre", service = service)
    // à l'envoi : perimetreCible = choix.codes.toList()
}
```

⚠️ Divergence déclarée : l'iOS range `lieu` dans `ServiceApercus` (avec « qui la lira » et
« Nom affiché », pas encore portés) ; ici, il vit dans `ServicePerimetres`.

## Aperçu servi — `fr.coprofirst.app.commun.apercu`

Un formulaire ne compose pas une règle d'affichage sur ce qu'on saisit : il la DEMANDE au
serveur (le lieu choisi, demain « qui la lira », « Nom affiché »). Le mécanisme est écrit
une fois.

| API | Pendant iOS | Usage |
|---|---|---|
| `ApercuServi(entree, demander, connue?) { valeur -> … }` | `ApercuServi` | `valeur` : celle que le serveur a rendue pour `entree`, ou la dernière tant qu'il n'a pas répondu ; `null` avant la première. Fondu court (aucun sous « Supprimer les animations »). Le contenu réserve sa place. |
| `EtatApercu(connue?, pause?)` : `saisie(entree, portee, demander)`, `valeur`, `abandonner()` | `EtatApercu` | Délai de 150 ms (`DELAI_MS`, une rafale = une demande), réponse périmée ignorée, valeur déjà servie jamais redemandée, échec tu. |

⚠️ Divergence déclarée : l'iOS ne fait pas partir la demande hors ligne ni en consultation ;
l'Android n'a pas encore ces états — la demande part, échoue, et se tait.

## Personnes de l'annuaire — `fr.coprofirst.app.commun.annuaire`

Une même personne a un même rendu (standards/11 §14) : l'Annuaire des résidents et
l'« Annuaire CS & Syndic » de l'Espace CS.

| API | Pendant iOS | Usage |
|---|---|---|
| `Annuaire` (`ConseilSyndical`, `Syndic`, `Membre` : `nomComplet`, `initiales`, `telephones`…), `AnnuaireLu.lu()` | `Annuaire` | L'annuaire servi (GET /admin/annuaire), lu du type généré ; `groupesCS` : les membres par bâtiment. |
| `CarteContact(personne, medaillons, session, principal = false) { détail }` | `CarteContact` | La carte d'une personne : avatar (photo protégée ou initiales), nom servi, médaillons, liseré d'accent pour le principal, puis le détail de la section. |
| `medaillonsDuConseil(membre, site)`, `medaillonsDuSyndic(membre)`, `Medaille` | idem | Les rôles mis en avant : gestionnaire du site, président ; interlocuteur principal. |
| `Coordonnees(personne)`, `Coordonnees(email, telephones, titre?)` | `Coordonnees` | Courriel et numéros touchables (`mailto:`, `tel:`), repères `ReperesContact.COURRIEL`, `.APPELER` — aussi les baux de « Mes lots & accès ». |

L'aperçu servi a aussi `NomDeLaSaisie(prenom, nom, service, repere, connu?)` : la ligne
« Nom affiché » d'une feuille d'édition, rendue par le serveur (POST /noms/affiche,
`ServiceNoms` / `ServiceNomsApi(session)`), jamais composée ici — `NomDeLaSaisie` de l'iOS.

## Fil des réponses — `fr.coprofirst.app.commun.reponses`

Les réponses d'une idée, d'une annonce, les commentaires d'un sondage (`ReponseCommunauteRead`,
le type du contrat — texte brut, jamais du HTML). Les droits sont SERVIS : l'écran ne les
recalcule pas.

| API | Pendant iOS | Usage |
|---|---|---|
| `FilReponses(reponses, moi, gestes, modifier?, invite?, entete?)` | `FilReponses` | Une `Column` (dans la page qui défile) : l'en-tête (repère `reponses.entete` ; « 💬 Répondre », « 💬 3 réponses » par défaut — `entete: @Composable (Int) -> String` le remplace), chaque réponse (repère `reponse.<id>` ; le conseil syndical sur le fond bleu, ⭐), puis le champ (`reponses.saisie`, `invite` : « Votre réponse… » par défaut) et la flèche (`reponses.envoyer`). « Supprimer » suit `peut_supprimer` servi et se **confirme** dans le fil (« Supprimer définitivement », `reponses.confirmerSuppression`) ; « Signaler » ne s'offre que sur les réponses des autres (`moi` : l'id de GET /auth/me ; `null` : partout). |
| `GestesReponses(publier, retirer, signaler)` | `GestesReponses` | Ce que le fil fait au serveur, dit par l'écran : `publier: suspend (String) -> Boolean` (`false` : le texte reste dans le champ), `retirer: suspend (ReponseCommunauteRead) -> Unit` (après la confirmation), `signaler: (ReponseCommunauteRead) -> Unit` (ouvre la boîte de l'écran, `SignalementAuConseil`). |
| `Reponses.peutSupprimer(r)`, `.peutSignaler(r, moi)`, `.estCs(r)`, `.auteur(r)`, `.aPublier(texte)` | `ReponseCommunaute` | Les règles, testées sur la JVM. |
| `ReperesReponses` | — | Les repères de l'iOS ; plus `reponse.<id>.actions` (le « ⋮ »), `reponse.<id>.supprimer`, `reponse.<id>.signaler`. |
| `enteteReponses(n)` | `FilReponses.enteteReponses` | L'en-tête par défaut. |

⚠️ Divergence déclarée : les gestes d'une réponse sont sous son « ⋮ » ; l'iOS les offre au
balayage et au menu contextuel (Compose n'a pas d'actions de balayage dans une liste).

## Signaler au conseil syndical — `fr.coprofirst.app.commun`

| API | Pendant iOS | Usage |
|---|---|---|
| `SignalementAuConseil(cible, envoyer, fermer)` | `.signalementAuConseil` | La boîte des mots du site (`signaler()`) : paraît tant que `cible: C?` n'est pas `null` ; motif **obligatoire** (`signalement.motif`) ; « Signaler » (`signalement.envoyer`) rend `envoyer(cible, motif sans blancs)` et ferme ; « Annuler » ferme. L'écran envoie et dit « Signalement transmis au conseil syndical » (`R.string.signalement_transmis`). |
| `CibleSignalement(type, id)`, `CibleSignalement.commentaire(id)` | `CibleSignalement` | `cible_type`, `cible_id` de POST /signalements — le type que le site envoie. |
| `ServiceSignalements`, `ServiceSignalementsApi(session)` | `ServiceSignalements` | `suspend signalerAuConseil(cible, motif)` — POST /signalements par le client généré. Le service d'un écran qui signale en hérite (`ServiceSignalements by ServiceSignalementsApi(session)`). |

```kotlin
var signale by remember { mutableStateOf<ReponseCommunauteRead?>(null) }
FilReponses(idee.reponses, moi, GestesReponses(publier = { … }, retirer = { … }, signaler = { signale = it }))
SignalementAuConseil(signale, envoyer = { r, motif ->
    portee.launch { service.signalerAuConseil(CibleSignalement.commentaire(r.id), motif); avis.dire(…) }
}, fermer = { signale = null })
```

## Masquer un auteur — `fr.coprofirst.app.commun.masquages`

Masquer, pour soi seul, ce que publie un résident (#282) : rien n'est supprimé, il n'en est
pas averti, et le filtre est au serveur — l'app ne le refait pas, ni ne redit qui ne se
masque pas (le refus du serveur porte sa phrase).

| API | Pendant iOS | Usage |
|---|---|---|
| `AuteurAMasquer.de(id, nom, moi)` | `AuteurAMasquer` | L'auteur qu'un geste propose de masquer ; `null` sans identifiant ni nom servis, ou pour soi : le geste ne s'offre pas. |
| `ConfirmationMasquage(auteur, masquer, fermer)` | `ConfirmationMasquage` | La question du site (« Masquer ce que publie… ») : paraît tant que `auteur` n'est pas `null` ; « Masquer » ferme et rend l'auteur à l'écran, qui envoie, dit l'avis et relit sa liste. Repères `ReperesMasquage`. |
| `ServiceMasquages`, `ServiceMasquagesApi(session)` | `ServiceMasquages` | `masques()`, `masquer(auteur)`, `demasquer(auteurId)` — GET, POST, DELETE /masquages par le client généré. |

## Fiche d'un sondage — `fr.coprofirst.app.commun.detail.sondage`

| API | Pendant iOS | Usage |
|---|---|---|
| `FicheSondage(id, session, avis, modifier?, service?)` | `SondageView` | La fiche, qui défile seule : l'en-tête (état, clôture, question `sondage.question`, description, total `sondage.total`), puis le vote s'il est ouvert (`sondage.option.<id>`, `sondage.precision`, `sondage.commentaire`, `sondage.charte`, `sondage.voter`, ce qui manque `sondage.manque`) ou les résultats (`sondage.resultat.<id>` ; masqués : `sondage.resultatsMasques`), puis les commentaires (`FilReponses`, signalement compris). Chaque geste dit sa phrase par `avis` (celle du serveur s'il refuse) et la fiche se relit. L'écran pose `AvisEphemere(avis)` autour. `service` : `ServiceSondageApi(session)` par défaut. |
| `PageSondage(id, session, service?)` | `NavigationLink → SondageView` | La fiche en page poussée, avec son avis : la fiche « sondage » du registre (`RegistreDesMenus.fiches`) — s'ouvre de partout par `contexte.ouvrir(Destination.Element("sondage", id))`. |
| `ServiceSondage` (`: ServiceSignalements`), `ServiceSondageApi(session)` | `ServiceSondage` | `sondage(id)` (GET /sondages/{id} et /auth/me ensemble ; un compte illisible n'empêche pas la fiche), `voter(vote, sondage)`, `commenter(texte, sondage)`, `supprimerCommentaire(id, sondage)`. |
| `DetailSondage`, `OptionSondage`, `VoteSondage`, `CalculsSondage.pourcentage` | `DetailSondage`, `VoteSondage`… | Le modèle : `peutVoter`, `totalVotes` ; `VoteSondage.choisir(id)` (vide la précision), `manque(champLibre)` (`@StringRes`, l'ordre du site), `corps()` (les textes vides ne partent pas). |
| `SondageSimule`, `FixturesSondage` (source `debug`) | `ServiceSimule`, `FixturesSondage` | Le serveur simulé et les fiches de l'iOS, pour les tests et les aperçus. |


## Accès — `fr.coprofirst.app.commun.acces`

Un badge ou une télécommande, nommés de même par Mes lots & accès et l'Espace CS (#79) :
le contrat ne sert que des CODES, les libellés et les teintes viennent du site.

| API | Pendant iOS | Usage |
|---|---|---|
| `TypeAcces` (`Vigik`, `Telecommande`), `.code`, `.libelle` | `TypeAcces` | Les deux familles d'accès et leur code servi (`vigik`, `telecommande`) ; `libelle` : celui du site. |
| `LibellesAcces.typeAcces(code)` | `LibellesAcces.typeAcces` | Le libellé d'une famille (« 🏷️ Badge Vigik ») ; un code inconnu s'affiche brut. |
| `LibellesAcces.statutAcces(code)` | `LibellesAcces.statutAcces` | L'état d'un accès, en `LibelleTeinte` (pour `PastilleTeinte`) ; inconnu, brut et gris. |
| `TablesAcces.statutsAcces`, `TablesAcces.typesAcces` | `TablesAcces` | **Générées** (`scripts/generer_tables.py`, `kotlin_acces`) depuis `statutsAcces`, `typesAcces` de `tables-clients.json` (`types-acces.ts`). On les lit par `LibellesAcces`. |

## File des validations — `fr.coprofirst.app.commun.validations`

LA file de travail du conseil (`Commun/Validations` de l'iOS) — comptes à valider, demandes
d'accès, leurs gestes —, que « Comptes & accès » de l'Espace CS et « À traiter » du
Paramétrage (#26) ouvrent toutes deux (#79), par les routes `require_cs_or_admin`. Ses
textes : `strings_validations.xml` (identifiants `espacecs_*` gardés).

| API | Pendant iOS | Usage |
|---|---|---|
| `OngletComptesCS(contexte: ContexteOnglet)` | `OngletComptesCS` | La file, prête à poser dans une page à onglets : deux compteurs, les comptes, puis les demandes ; chaque ligne s'approuve ou se rejette. Signature STABLE : elle ne dépend que du contexte de l'onglet. |
| `OngletComptesCS(service, avis, actualisation)` | — | La même file sur un service donné (un test, un aperçu). |
| `ServiceValidations`, `ServiceValidationsApi(session)` | `ServiceComptesCS` | `comptesEnAttente()`, `commandesEnAttente()`, `traiterCompte(id, valider)` (rend l'annonce du serveur, avec son ton), `accueillir(id, accueil)`, `traiterCommande(id, codes?)` (`null` : refuser) ; `file()` : les deux lectures ensemble (`FileDeTravail`). |
| `CompteEnAttente`, `CommandeEnAttente`, `AccueilArrivant`, `codesSaisis(saisie)` | `CompteEnAttente`… | Le modèle (noms servis, jamais composés ici) ; la saisie des codes découpée comme le site (virgules, points-virgules, espaces). |
| `EtatValidations(service, file)` | `EtatComptesCS` | La file et ses quatre gestes (`valider`, `rejeter`, `traiter`) ; `enCours` : l'élément dont le geste attend. Un refus garde la file. |
| `CarteDeFile(repere, enCours, approuver, rejeter) { contenu }` | `CarteDeFile` | La carte d'une ligne d'une file (comptes, commandes, demandes de profil du Paramétrage) : son contenu, puis « Approuver » et « Rejeter » ; repères `<repere>`, `<repere>.approuver`, `<repere>.rejeter`. ⚠️ Divergence déclarée : l'iOS teinte « Approuver » d'un aplat vert ; l'aplat s'écrit une fois (`BoutonPlein`) — ici, deux contours teintés. |
| `ReperesValidations` | identifiants d'accessibilité | Les repères de la file (`cs.compteur.comptes`, `cs.compte.<id>`, `cs.commande.<id>`, `cs.rejet.confirmer`…), ceux de l'iOS. |

