// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.LigneFiche
import fr.coprofirst.app.commun.NoteEnCarte
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite

/** La note qui tient la place du tableau des charges (calculé dans le front du site). */
@Composable
fun ChargesSurLeSite() = NoteEnCarte(stringResource(R.string.monlot_charges_sur_le_site), stringResource(R.string.monlot_charges))

/**
 * Les caractéristiques d'un lot (`CaracteristiquesLot.svelte`), dans l'ordre du site : ce
 * que la carte met avant, puis Type, Étage, Superficie, Porte ou place, Situé dans,
 * Desservi par — puis ce que le propriétaire doit savoir.
 */
@Composable
fun CaracteristiquesDuLot(lot: LotDuCompte, avant: List<Pair<String, String>> = emptyList()) {
    val lignes = avant.toMutableList()
    if (lot.type != null) lignes += stringResource(R.string.monlot_type) to lot.typeLu
    lot.etageLu?.let { lignes += stringResource(R.string.monlot_etage) to it }
    // Comme l'iOS : `fmtNombre` partout (« 64,5 m² »), le site écrivant ici la valeur brute (coprofirst#2507).
    lot.superficie?.let { lignes += stringResource(R.string.monlot_superficie) to stringResource(R.string.monlot_superficie_valeur, FormatsDuSite.nombre(it)) }
    lot.numeroUsage?.takeIf { it.isNotEmpty() }?.let { lignes += stringResource(R.string.monlot_porte_ou_place) to it }
    lot.perimetres("situe_dans")?.let { lignes += stringResource(R.string.monlot_situe_dans) to it }
    lot.perimetres("desservi_par")?.let { lignes += stringResource(R.string.monlot_desservi_par) to it }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lignes.forEach { (libelle, valeur) -> LigneFiche(libelle, valeur) }
        if (lot.alertes.isNotEmpty()) ConstatsDuLot(lot.alertes)
    }
}

/** Les constats d'un lot (`ConstatsLots.svelte`) : ce qui bloque d'abord. */
@Composable
private fun ConstatsDuLot(constats: List<ConstatDuLot>) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LibellesMonLot.ordonner(constats).forEach { constat ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PastilleTeinte(LibellesMonLot.gravite(constat.gravite), compacte = true)
                Text(constat.message, style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.text)
            }
        }
    }
}

/** Une carte de lot : son intitulé (« Bât. A / 15 »), un complément en tête, ses caractéristiques, un pied. */
@Composable
fun CarteLot(
    lot: LotDuCompte,
    avant: List<Pair<String, String>> = emptyList(),
    complement: @Composable () -> Unit = {},
    pied: @Composable () -> Unit = {},
) {
    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lot.intitule,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = LocalCouleurs.current.text,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                complement()
            }
            CaracteristiquesDuLot(lot, avant)
            pied()
        }
    }
}
