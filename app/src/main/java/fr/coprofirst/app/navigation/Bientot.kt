// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Pictogrammes
import fr.coprofirst.app.commun.EtatVide

/** Une page que l'app n'ouvre pas encore, comme l'iOS (`Bientot`) : elle le dit. */
@Composable
fun Bientot(page: PageDeNavigation) {
    EtatVide(
        titre = stringResource(R.string.bientot_titre),
        icone = Pictogrammes.icone(page.icone),
        explication = stringResource(R.string.bientot_explication, page.titre),
    )
}
