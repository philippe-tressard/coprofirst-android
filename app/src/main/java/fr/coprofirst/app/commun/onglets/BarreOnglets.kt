// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.onglets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.navigation.OngletDuSite

/**
 * La barre d'onglets d'une page du site, en pastilles qui défilent de côté, comme
 * `BarreOnglets` de l'iOS : une pastille par onglet, repère `onglet.<id>`. Les onglets
 * viennent d'`OngletsDuSite.ouverts` (jamais une liste écrite dans l'écran) — par le cadre
 * (`CadreAOnglets`), ou directement sur une page qui garde sa mise en page (Mes lots &
 * accès, Prestataires, Résidence). `marges` : l'espace avant la première et après la
 * dernière, pour que la barre défile jusqu'au bord de l'écran.
 */
@Composable
fun BarreOnglets(
    onglets: List<OngletDuSite>,
    actif: String,
    modifier: Modifier = Modifier,
    marges: PaddingValues = PaddingValues(0.dp),
    choisir: (String) -> Unit,
) {
    val haptique = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(marges)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        onglets.forEach { onglet ->
            Pastille(
                libelle = onglet.libelle,
                retenue = onglet.id == actif,
                modifier = Modifier.testTag(ReperesOnglets.onglet(onglet.id)),
                role = Role.Tab,
            ) {
                if (onglet.id != actif) {
                    haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    choisir(onglet.id)
                }
            }
        }
    }
}
