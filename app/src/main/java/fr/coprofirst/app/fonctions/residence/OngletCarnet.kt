// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import fr.coprofirst.app.commun.perimetres.Perimetre
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.navigation.Destination

/**
 * L'onglet « Carnet d'entretien », comme l'iOS (`CarnetView`) : ce qui a été entretenu,
 * réparé et contrôlé, rangé par équipement ; les visites en retard en tête. Réservé aux
 * propriétaires et au conseil syndical (l'onglet ne s'ouvre qu'à eux).
 *
 * Une affaire s'ouvre dans l'app par la porte (sa fiche, déclarée par Mes affaires) ; tant
 * qu'aucune fiche ne la porte, sur le site, comme un contrat ou une intervention.
 */
@Composable
fun OngletCarnet(
    carnet: ContenuCarnet,
    perimetres: List<Perimetre>,
    perimetre: String?,
    cs: Boolean,
    adresse: String,
    porte: (Destination) -> Boolean,
    choisir: (String?) -> Unit,
) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.carnet_aide), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
        if (perimetres.isNotEmpty()) FiltrePerimetre(perimetres, perimetre, carnet.entrees.size, choisir)
        if (cs) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.BarChart, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.carnet_bilan_sur_le_site), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
            }
        }
        if (carnet.enAlerte.isNotEmpty()) EncartAlerte(carnet.enAlerte)
        if (carnet.entrees.isEmpty()) RubriqueVide(stringResource(R.string.carnet_vide))
        carnet.groupes.forEach { groupe ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EnTeteRubrique(groupe.titre ?: stringResource(R.string.carnet_sans_equipement), compte = groupe.entrees.size)
                groupe.entrees.forEach { CarteEntree(it, adresse, porte) }
            }
        }
    }
}

/** Le filtre servi par le serveur (`?perimetre=`) : changer de périmètre recharge. */
@Composable
private fun FiltrePerimetre(perimetres: List<Perimetre>, perimetre: String?, compte: Int, choisir: (String?) -> Unit) {
    val couleurs = LocalCouleurs.current
    val haptique = LocalHapticFeedback.current
    var deplie by remember { mutableStateOf(false) }
    val tous = stringResource(R.string.carnet_tous)
    val libelle = perimetres.firstOrNull { it.code == perimetre }?.libelle ?: tous
    val forme = RoundedCornerShape(50)
    Box {
        Row(
            Modifier.heightIn(min = Mise.cible).background(couleurs.surface, forme).border(1.dp, couleurs.border, forme)
                .cliquable(stringResource(R.string.carnet_perimetre)) { deplie = true }
                .semantics { stateDescription = libelle }
                .padding(horizontal = 14.dp)
                .testTag(ReperesResidence.PERIMETRE),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.FilterList, contentDescription = null, tint = couleurs.text, modifier = Modifier.size(18.dp))
            Text(libelle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = couleurs.text)
            Text(FormatsDuSite.nombre(compte), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = couleurs.text)
            Icon(Icons.Outlined.UnfoldMore, contentDescription = null, tint = couleurs.text, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = deplie, onDismissRequest = { deplie = false }) {
            (listOf(null to tous) + perimetres.map { it.code to it.libelle }).forEach { (code, nom) ->
                DropdownMenuItem(
                    text = { Text(nom) },
                    onClick = {
                        deplie = false
                        if (code != perimetre) {
                            haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            choisir(code)
                        }
                    },
                )
            }
        }
    }
}

/** L'alerte en TÊTE : ce que le carnet apprend et que rien d'autre ne dit. */
@Composable
private fun EncartAlerte(entrees: List<EntreeCarnet>) {
    val couleurs = LocalCouleurs.current
    val forme = RoundedCornerShape(Mise.rayon)
    Column(
        Modifier.fillMaxWidth().background(couleurs.warningFond, forme).border(1.dp, couleurs.warningBordure, forme)
            .padding(14.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "⚠️ " + pluralStringResource(R.plurals.carnet_visites_attendues, entrees.size, entrees.size) + " " +
                stringResource(R.string.carnet_echeance_depassee),
            fontWeight = FontWeight.SemiBold, color = couleurs.warningTexte,
        )
        entrees.forEach { entree ->
            Text("• ${entree.libelle}" + (entree.alerte?.let { " — $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = couleurs.warningTexte)
        }
    }
}

/** Une entrée : la date et l'origine en gouttière, le libellé, l'extrait, le détail, l'alerte, le numéro, le lien. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarteEntree(entree: EntreeCarnet, adresse: String, porte: (Destination) -> Boolean) {
    val couleurs = LocalCouleurs.current
    CarteAGouttiere(Nature.duCarnet(entree.origine), date = entree.jour) {
        Text(entree.libelle, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
        entree.extrait?.let {
            Text(TexteDuSite.apercu(it), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = couleurs.textMuted)
        }
        entree.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted) }
        if (entree.alerte != null || entree.numero != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Le badge orange du site (`badge-orange`).
                entree.alerte?.let { PastilleTeinte("⚠️ $it", BADGE_ALERTE, compacte = true) }
                entree.numero?.let { PastilleNeutre("#$it") }
            }
        }
        val lien = entree.lien?.let { OngletsSurLeSite.lien(it, adresse) }
        val affaire = entree.affaire
        when {
            affaire != null -> LienEntree(R.string.carnet_voir_affaire, Icons.AutoMirrored.Outlined.ArrowForward, lien) {
                porte(Destination.Element(NATURE_AFFAIRE, affaire))
            }
            lien != null -> LienEntree(R.string.carnet_ouvrir_sur_le_site, Icons.AutoMirrored.Outlined.OpenInNew, lien) { false }
        }
    }
}

/** Un lien d'entrée : `dansLApp` d'abord (la porte) ; s'il ne mène nulle part, le site. */
@Composable
private fun LienEntree(libelle: Int, icone: ImageVector, lien: String?, dansLApp: () -> Boolean) {
    val couleurs = LocalCouleurs.current
    val site = LocalUriHandler.current
    val texte = stringResource(libelle)
    Row(
        Modifier.heightIn(min = Mise.cible).cliquable(texte) { if (!dansLApp() && lien != null) site.openUri(lien) },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texte, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = couleurs.primary)
        Icon(icone, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(16.dp))
    }
}

/** La nature d'une affaire pour la porte (`Destination.Element`), celle du fil. */
private const val NATURE_AFFAIRE = "affaire"

/** La classe d'alerte du site. */
private const val BADGE_ALERTE = "badge-orange"
