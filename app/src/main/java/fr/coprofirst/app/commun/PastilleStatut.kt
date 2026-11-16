// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import fr.coprofirst.app.R

/**
 * Le statut d'une affaire en pastille (`BadgeEtatAffaire` du site), comme l'iOS
 * (`PastilleStatut`) : la teinte par son CODE (`StatutsAffaire.pastilles`, générée depuis
 * `statutsTicket`), le libellé EN CLAIR que l'appelant tient du serveur, dit « Statut : … »
 * au lecteur d'écran. Commune à Mes affaires, au Reporting de l'Espace CS et au fil (#92).
 */
@Composable
fun PastilleStatut(statut: String, libelle: String, modifier: Modifier = Modifier, compacte: Boolean = false) {
    val vocal = stringResource(R.string.affaires_statut_vocal, libelle)
    PastilleTeinte(
        libelle, StatutsAffaire.pastilles[statut] ?: "badge-gray",
        modifier.semantics { contentDescription = vocal }, compacte = compacte,
    )
}
