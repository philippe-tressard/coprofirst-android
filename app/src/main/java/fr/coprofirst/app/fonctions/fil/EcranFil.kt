// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

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
 * ⚠️ Divergences déclarées : l'affaire ne s'ouvre pas tant que Mes affaires n'a pas déclaré
 * sa fiche (`DestinationDuFil`) ;
 * ni le logement de l'utilisateur dans la barre (`PastilleLogement` de l'iOS, qui ouvre
 * « Mes lots & accès ») ni la pastille de la résidence (`PastilleOuSuisJe`, #15) ; pas de
 * bandeau hors ligne ; une notification touchée n'ouvre pas encore son élément (#23).
 */
@Composable
fun EcranFil(contexte: ContexteDeMenu) {
    val session = contexte.session
    val parcours: ParcoursFil = viewModel(
        key = "fil@${System.identityHashCode(session)}",
        factory = viewModelFactory { initializer { ParcoursFil(ServiceFilApi(session)) } },
    )
    PageHorsLigne { EcranFil(parcours, session, contexte.ouvrir) }
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
    PullToRefreshBox(isRefreshing = etat.rafraichissement, onRefresh = parcours::rafraichir, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
            jours.forEach { jour ->
                stickyHeader(key = "jour-${jour.jour}") { EnTeteJour(TextesFil.entete(jour, maintenant)) }
                items(jour.elements, key = { it.id }) { element ->
                    val destination = DestinationDuFil.pour(element.cible, RegistreDesMenus.fiches.keys)
                    CarteDuFil(
                        element,
                        session,
                        Modifier.padding(horizontal = Mise.marge).animateItem(),
                        maintenant = maintenant,
                        action = destination?.let { TextesFil.action(it) },
                        ouvrir = when (destination) {
                            null -> null
                            DestinationDuFil.Resume -> ({ resume = element.id })
                            is DestinationDuFil.Document -> ({ ouverture.document(destination.id, destination.nom) })
                            is DestinationDuFil.Element -> ({ ouvrir(Destination.Element(destination.nature, destination.id.toString())) })
                        },
                    )
                }
            }
        }
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
