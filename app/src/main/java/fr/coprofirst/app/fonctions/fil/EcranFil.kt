// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import fr.coprofirst.app.commun.pieces.Ouverture
import fr.coprofirst.app.commun.pieces.Visionneuse
import fr.coprofirst.app.commun.pieces.documentChoisi
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.PageHorsLigne
import fr.coprofirst.app.commun.fil.CarteDuFil
import fr.coprofirst.app.commun.fil.CibleDuFil
import fr.coprofirst.app.commun.fil.ElementDuFil
import fr.coprofirst.app.commun.fil.TextesFil
import fr.coprofirst.app.commun.miseenpage.LocalJourDuCarnet
import fr.coprofirst.app.commun.miseenpage.OccuperLaColonneDeFiche
import fr.coprofirst.app.commun.miseenpage.rangeeChoisie
import fr.coprofirst.app.commun.pieces.AvisOuverture
import fr.coprofirst.app.commun.pieces.rememberOuverture
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.navigation.RegistreDesMenus
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant

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
            EcranFil(parcours, session, contexte.ouvrir)
        }
    }
}

/** @param ouvrir la porte (`ContexteDeMenu.ouvrir`) : l'affaire et le sondage s'ouvrent par elle. */
@Composable
fun EcranFil(parcours: ParcoursFil, session: SessionOuverte, ouvrir: (Destination) -> Boolean) {
    Chargement(charger = { parcours.charger() }, squelette = { SqueletteFil() }) { _, _ -> ListeFil(parcours, session, ouvrir) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListeFil(parcours: ParcoursFil, session: SessionOuverte, ouvrir: (Destination) -> Boolean) {
    val etat by parcours.etat.collectAsStateWithLifecycle()
    val jours = remember(etat.elements) { etat.jours }
    val maintenant = remember(etat.elements) { Instant.now() }
    var resume by rememberSaveable { mutableStateOf<String?>(null) }
    val ouverture = rememberOuverture(session)
    // Un document du fil s'ouvre dans l'app (#274), à côté du fil en fenêtre large.
    Visionneuse(ouverture)
    // En fenêtre large, la fiche d'un élément du fil s'ouvre à côté de lui (#69).
    OccuperLaColonneDeFiche()
    // Dans le carnet d'un pliable (#242), une journée par double page.
    val carnet = LocalJourDuCarnet.current
    val feuillet = carnet?.let { FeuilletsDuFil.jour(jours, it.value) }
    val veille = feuillet?.let { f -> FeuilletsDuFil.veille(jours, f.jour)?.let { j -> { carnet.value = j } } }
    val lendemain = feuillet?.let { f -> FeuilletsDuFil.lendemain(jours, f.jour)?.let { j -> { carnet.value = j } } }
    val affiches = if (carnet == null) jours else listOfNotNull(feuillet)
    // Dans le carnet, le pied de page (« Veille », « Lendemain ») tient sous la page qui défile.
    Column(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = etat.rafraichissement, onRefresh = parcours::rafraichir, modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().let { m -> feuillet?.let { m.tournerLaPage(it.jour, veille, lendemain) } ?: m },
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
                    if (carnet != null) {
                        item(key = "ephemeride") { Ephemeride(jour, maintenant) }
                    } else {
                        stickyHeader(key = "jour-${jour.jour}") { EnTeteJour(TextesFil.entete(jour, maintenant)) }
                    }
                    items(jour.elements, key = { it.id }) { element ->
                        val destination = DestinationDuFil.pour(element.cible, RegistreDesMenus.fiches.keys)
                        CarteDuFil(
                            element,
                            session,
                            Modifier.padding(horizontal = Mise.marge).animateItem().let { m ->
                                when (destination) {
                                is DestinationDuFil.Element -> m.rangeeChoisie(destination.nature, destination.id.toString())
                                is DestinationDuFil.Document -> m.documentChoisi(Ouverture.cleDocument(destination.id))
                                else -> m
                            }
                            },
                            maintenant = maintenant,
                            action = destination?.let { actionDuFil(it) },
                            ouvrir = when (destination) {
                                null -> null
                                DestinationDuFil.Resume -> ({ resume = element.id })
                                is DestinationDuFil.Document -> ({ ouverture.document(destination.id, destination.nom, element.titre) })
                                is DestinationDuFil.Element -> ({ ouvrir(Destination.Element(destination.nature, destination.id.toString())) })
                            },
                        )
                    }
                }
            }
        }
        if (feuillet != null) PiedDuJour(veille, lendemain)
    }
    etat.elements.firstOrNull { it.id == resume }?.let { element -> FeuilleResume(element, session) { resume = null } }
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
