// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.FeuillePleine
import fr.coprofirst.app.session.SessionOuverte

/** Le repère de « OK », qui ferme la feuille. */
const val REPERE_FEUILLE_SONDAGE_OK = "sondage.ok"

/**
 * La fiche d'un sondage EN FEUILLE pleine hauteur (`FeuillePleine`) — titre « Sondage », « OK »
 * —, avec son avis éphémère : ce qu'ouvre une carte sondage du fil.
 *
 * ⚠️ Divergence déclarée : l'iOS POUSSE la fiche (`NavigationLink`, retour par la barre) ;
 * la navigation commune d'Android n'a pas de pile par page (#11) — la fiche s'ouvre donc en
 * feuille, comme le résumé du fil.
 */
@Composable
fun FeuilleSondage(
    id: Int,
    session: SessionOuverte,
    fermer: () -> Unit,
    service: ServiceSondage = remember(session) { ServiceSondageApi(session) },
) {
    val avis = remember { AvisDuGeste() }
    FeuillePleine(stringResource(R.string.sondage_titre), REPERE_FEUILLE_SONDAGE_OK, fermer) {
        AvisEphemere(avis, Modifier.fillMaxSize()) { FicheSondage(id, session, avis, service = service) }
    }
}
