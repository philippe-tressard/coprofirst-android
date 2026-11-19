// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Actualisable
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.PageHorsLigne
import fr.coprofirst.app.commun.animationsCoupees
import fr.coprofirst.app.commun.fil.CarteDuFil
import fr.coprofirst.app.commun.fil.CibleDuFil
import fr.coprofirst.app.commun.fil.ElementDuFil
import fr.coprofirst.app.commun.fil.JourDuFil
import fr.coprofirst.app.commun.fil.TextesFil
import fr.coprofirst.app.commun.miseenpage.LocalJourDuCarnet
import fr.coprofirst.app.commun.miseenpage.OccuperLaColonneDeFiche
import fr.coprofirst.app.commun.miseenpage.rangeeChoisie
import fr.coprofirst.app.commun.pieces.AvisOuverture
import fr.coprofirst.app.commun.pieces.Ouverture
import fr.coprofirst.app.commun.pieces.Visionneuse
import fr.coprofirst.app.commun.pieces.documentChoisi
import fr.coprofirst.app.commun.pieces.rememberOuverture
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.navigation.RegistreDesMenus
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant
import java.time.LocalDate

/**
 * Le fil de la résidence, jour par jour — la page `tableau-de-bord`, la première après la
 * connexion —, comme l'iOS (`FilView`) : les éléments du plus récent au plus ancien, sous
 * l'en-tête de leur jour qui reste en haut tant que sa journée défile ; « tirer pour
 * actualiser » ; un fil vide et une erreur dits. Un toucher ouvre le résumé, le document ou
 * la fiche d'un sondage ou d'une affaire, poussée par la porte (`DestinationDuFil`).
 *
 * En tête, le logement de l'utilisateur, qui ouvre « Mes lots & accès » (`LogementDuFil`).
 *
 * Dans le carnet d'un pliable déplié (#242), une journée par double page (`FilEnCarnet.kt`).
 *
 * ⚠️ Divergence déclarée : une notification touchée n'ouvre pas encore son élément (#23).
 */
@Composable
fun EcranFil(contexte: ContexteDeMenu) {
    val session = contexte.session
    val parcours: ParcoursFil = viewModel(
        key = "fil@${System.identityHashCode(session)}",
        factory = viewModelFactory { initializer { ParcoursFil(ServiceFilApi(session)) } },
    )
    PageHorsLigne {
        Column {
            LogementDuFil(contexte.logement) { contexte.ouvrir(Destination.Page(PAGE_MES_LOTS)) }
            // Pincer vers la semaine ferme la fiche ouverte à droite du pli : la page se rouvre, seule (#286).
            EcranFil(parcours, session, contexte.ouvrir) { contexte.ouvrir(Destination.Page(contexte.page.id)) }
        }
    }
}

/**
 * @param ouvrir la porte (`ContexteDeMenu.ouvrir`) : l'affaire et le sondage s'ouvrent par elle.
 * @param fermerLaFiche dans le carnet, ferme la fiche ouverte à droite du pli (la semaine prend la double page).
 */
@Composable
fun EcranFil(parcours: ParcoursFil, session: SessionOuverte, ouvrir: (Destination) -> Boolean, fermerLaFiche: () -> Unit = {}) {
    Chargement(charger = { parcours.charger() }, squelette = { SqueletteFil() }) { _, _ -> ListeFil(parcours, session, ouvrir, fermerLaFiche) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListeFil(parcours: ParcoursFil, session: SessionOuverte, ouvrir: (Destination) -> Boolean, fermerLaFiche: () -> Unit) {
    val etat by parcours.etat.collectAsStateWithLifecycle()
    val jours = remember(etat.elements) { etat.jours }
    val maintenant = remember(etat.elements) { Instant.now() }
    var resume by rememberSaveable { mutableStateOf<String?>(null) }
    val ouverture = rememberOuverture(session)
    // Un document du fil s'ouvre dans l'app (#274), à côté du fil en fenêtre large.
    Visionneuse(ouverture)
    // Dans le carnet d'un pliable (#242), une journée par double page ; pincée, sa semaine (#286).
    val carnet = LocalJourDuCarnet.current
    var semaine by rememberSaveable { mutableStateOf<String?>(null) }
    val lundi = semaine?.takeIf { carnet != null }?.let(LocalDate::parse)
    // En fenêtre large, la fiche d'un élément du fil s'ouvre à côté de lui (#69) ; la semaine prend
    // toute la double page, sans colonne.
    if (lundi == null) OccuperLaColonneDeFiche()
    // Ce qu'une carte touchée dans la semaine ouvre, une fois la page du jour revenue (et la colonne occupée).
    var aOuvrir by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(aOuvrir, lundi) {
        if (lundi == null) aOuvrir?.let { geste -> aOuvrir = null; geste() }
    }
    /** Ce qu'ouvre un toucher sur la carte, et ce qu'il dit au lecteur d'écran. */
    @Composable
    fun gesteDe(element: ElementDuFil): Pair<String?, (() -> Unit)?> {
        val destination = DestinationDuFil.pour(element.cible, RegistreDesMenus.fiches.keys)
        return (destination?.let { actionDuFil(it) }) to when (destination) {
            null -> null
            DestinationDuFil.Resume -> ({ resume = element.id })
            is DestinationDuFil.Document -> ({ ouverture.document(destination.id, destination.nom, element.titre) })
            is DestinationDuFil.Element -> ({ ouvrir(Destination.Element(destination.nature, destination.id.toString())) })
        }
    }
    val fige = animationsCoupees()
    AnimatedContent(
        targetState = lundi,
        transitionSpec = {
            if (fige) fadeIn() togetherWith fadeOut() else (fadeIn() + scaleIn(initialScale = ECHELLE_ENTREE)) togetherWith fadeOut()
        },
        label = "carnet",
    ) { semaineAffichee ->
        if (semaineAffichee != null && carnet != null) {
            PageDeLaSemaine(
                semaineAffichee,
                jours,
                lire = parcours::semaine,
                carte = { element, jour, modifier ->
                    val (action, geste) = gesteDe(element)
                    CarteDuFil(element, session, modifier, maintenant = maintenant, action = action, ouvrir = geste?.let {
                        {
                            SemaineDuFil.jourDeLaCarte(jours, element, jour)?.let { carnet.value = it }
                            aOuvrir = it
                            semaine = null
                        }
                    })
                },
                changer = { semaine = it.toString() },
                ouvrirLeJour = { jour ->
                    SemaineDuFil.jourDuFil(jours, jour)?.let { carnet.value = it }
                    semaine = null
                },
            )
        } else {
            PageDuFil(etat, parcours, jours, maintenant, carnet?.let { j ->
                FeuilletsDuFil.jour(jours, j.value)?.let { f ->
                    Feuillet(f, j, jours, voirLaSemaine = {
                        fermerLaFiche()
                        semaine = SemaineDuFil.lundi(LocalDate.parse(f.jour)).toString()
                    })
                }
            }, ouverture, fige) { element, modifier ->
                val (action, geste) = gesteDe(element)
                CarteDuFil(element, session, modifier, maintenant = maintenant, action = action, ouvrir = geste)
            }
        }
    }
    etat.elements.firstOrNull { it.id == resume }?.let { element -> FeuilleResume(element, session) { resume = null } }
}

/** Le jour ouvert dans le carnet, et ses voisins : la veille, le lendemain, la semaine. */
private class Feuillet(val jour: JourDuFil, carnet: MutableState<String?>, jours: List<JourDuFil>, val voirLaSemaine: () -> Unit) {
    val veille: (() -> Unit)? = FeuilletsDuFil.veille(jours, jour.jour)?.let { j -> { carnet.value = j } }
    val lendemain: (() -> Unit)? = FeuilletsDuFil.lendemain(jours, jour.jour)?.let { j -> { carnet.value = j } }
}

/** Le fil jour par jour ; dans le carnet (`feuillet`), le seul jour ouvert, son éphéméride et son pied de page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageDuFil(
    etat: EtatFil,
    parcours: ParcoursFil,
    jours: List<JourDuFil>,
    maintenant: Instant,
    feuillet: Feuillet?,
    ouverture: Ouverture,
    fige: Boolean,
    carte: @Composable (ElementDuFil, Modifier) -> Unit,
) {
    val affiches = if (feuillet == null) jours else listOf(feuillet.jour)
    // Dans le carnet, le pied de page (« Veille », « Lendemain ») tient sous la page qui défile.
    Column(Modifier.fillMaxSize()) {
        Actualisable(enCours = etat.rafraichissement, actualiser = parcours::rafraichir, modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().let { m ->
                    feuillet?.let {
                        m.testTag(REPERE_JOUR)
                            .zoomDuCarnet(ZoomDuCarnet.Sens.Pincer, fige, it.voirLaSemaine)
                            .tournerLaPage(it.jour.jour, it.veille, it.lendemain)
                    } ?: m
                },
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                etat.echec?.let { echec ->
                    item(key = "echec") { MessageErreur(echec, Modifier.padding(horizontal = Mise.marge)) { parcours.rafraichir() } }
                }
                if (ouverture.echec != null) item(key = "ouverture") { AvisOuverture(ouverture, Modifier.padding(horizontal = Mise.marge)) }
                if (etat.elements.isEmpty() && etat.echec == null) {
                    item(key = "vide") {
                        EtatVide(
                            titre = stringResource(R.string.fil_vide),
                            icone = Icons.Outlined.AutoAwesome,
                            explication = stringResource(R.string.fil_vide_detail),
                        )
                    }
                }
                affiches.forEach { jour ->
                    if (feuillet != null) {
                        item(key = "ephemeride") { Ephemeride(jour, maintenant, feuillet.voirLaSemaine) }
                    } else {
                        stickyHeader(key = "jour-${jour.jour}") { EnTeteJour(TextesFil.entete(jour, maintenant)) }
                    }
                    items(jour.elements, key = { it.id }) { element ->
                        val destination = DestinationDuFil.pour(element.cible, RegistreDesMenus.fiches.keys)
                        carte(
                            element,
                            Modifier.padding(horizontal = Mise.marge).animateItem().let { m ->
                                when (destination) {
                                    is DestinationDuFil.Element -> m.rangeeChoisie(destination.nature, destination.id.toString())
                                    is DestinationDuFil.Document -> m.documentChoisi(Ouverture.cleDocument(destination.id))
                                    else -> m
                                }
                            },
                        )
                    }
                }
            }
        }
        if (feuillet != null) PiedDuJour(feuillet.veille, feuillet.lendemain)
    }
}

/** L'en-tête d'un jour, collé en haut tant que sa journée défile (déjà en capitales). */
@Composable
private fun EnTeteJour(texte: String) {
    Libelle(
        texte,
        Modifier
            .fillMaxWidth()
            .background(LocalCouleurs.current.bg)
            .padding(horizontal = Mise.marge, vertical = 8.dp)
            .semantics { heading() },
    )
}

/** Trois cartes factices, à la forme d'un élément, pendant la première lecture. */
@Composable
private fun SqueletteFil() {
    val squelette = ElementDuFil(
        id = "squelette", type = "ticket_ouvert", date = Instant.now(),
        titre = stringResource(R.string.fil_squelette_titre), detail = stringResource(R.string.fil_squelette_detail),
        cible = CibleDuFil.Resume(null),
    )
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EnTeteJour(stringResource(R.string.fil_aujourdhui))
        repeat(3) { CarteDuFil(squelette, session = null, Modifier.padding(horizontal = Mise.marge)) }
    }
}

/** La semaine qui paraît d'un pincement s'approche à peine : elle part de 108 %. */
private const val ECHELLE_ENTREE = 1.08f
