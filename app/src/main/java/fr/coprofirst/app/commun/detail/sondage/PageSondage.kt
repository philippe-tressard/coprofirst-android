// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.session.SessionOuverte

/**
 * La fiche d'un sondage en page POUSSÉE, avec son avis éphémère — comme l'iOS
 * (`NavigationLink → SondageView`). La porte l'ouvre (`Destination.Element("sondage", id)`,
 * déclarée dans `RegistreDesMenus.fiches`) : la barre du haut porte « Sondage » et le retour.
 */
@Composable
fun PageSondage(
    id: Int,
    session: SessionOuverte,
    service: ServiceSondage = remember(session) { ServiceSondageApi(session) },
) {
    val avis = remember { AvisDuGeste() }
    AvisEphemere(avis, Modifier.fillMaxSize()) { FicheSondage(id, session, avis, service = service) }
}
