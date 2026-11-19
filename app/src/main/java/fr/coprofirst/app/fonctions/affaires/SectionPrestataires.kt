// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Mouvement
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.animationsCoupees
import fr.coprofirst.app.session.ErreurService

/**
 * « Prestataires » de « Modifier l'affaire », comme l'iOS (`SectionPrestataires`, #182,
 * coprofirst#2704) : une ligne par prestataire avec son ✕, puis « Ajouter un prestataire »,
 * qui propose ceux de la résidence (GET /prestataires) qui n'y sont pas encore. Offerte selon
 * le droit SERVI seul (`peut_changer_prestataires`). L'ordre compte — le premier est celui du
 * Kanban : « Monter » et « Descendre » au menu d'une ligne (appui long) et en actions du
 * lecteur d'écran. Un ajout, un retrait, un déplacement : la section suit en glissant
 * (ease-out, 200 ms), sans animation si l'appareil les coupe.
 */
@Composable
internal fun SectionPrestataires(choisis: List<PrestataireAffaire>, service: ServiceAffaires, changer: (List<PrestataireAffaire>) -> Unit) {
    val c = LocalCouleurs.current
    var catalogue by remember { mutableStateOf<List<PrestataireAffaire>?>(null) }
    var illisible by remember { mutableStateOf(false) }
    LaunchedEffect(service) {
        try {
            catalogue = service.prestatairesDeLaResidence()
        } catch (_: ErreurService) {
            illisible = true
        }
    }
    val deja = choisis.mapNotNull { it.id }.toSet()
    val disponibles = catalogue.orEmpty().filter { it.id !in deja }
    val glisse = if (animationsCoupees()) snap<IntSize>() else tween(DUREE_MS, easing = Mouvement.sortie)
    Column(Modifier.animateContentSize(glisse), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Libelle(stringResource(R.string.prestataires_affaire_section), Modifier.semantics { heading() })
        if (choisis.isEmpty()) {
            Text(stringResource(R.string.prestataires_affaire_aucun), Modifier.testTag(ReperesPrestatairesAffaire.AUCUN), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        choisis.forEachIndexed { rang, prestataire ->
            LignePrestataire(prestataire, rang, choisis, changer)
        }
        Ajouter(disponibles, catalogue != null) { changer(choisis + it) }
        if (illisible) Text(stringResource(R.string.prestataires_affaire_illisible), style = MaterialTheme.typography.bodySmall, color = c.danger)
        if (choisis.size > 1) Text(stringResource(R.string.prestataires_affaire_pied), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

@Composable
private fun LignePrestataire(prestataire: PrestataireAffaire, rang: Int, choisis: List<PrestataireAffaire>, changer: (List<PrestataireAffaire>) -> Unit) {
    val c = LocalCouleurs.current
    var menu by remember { mutableStateOf(false) }
    val monter = stringResource(R.string.prestataires_affaire_monter)
    val descendre = stringResource(R.string.prestataires_affaire_descendre)
    fun echanger(avec: Int) = changer(choisis.toMutableList().also { it[rang] = choisis[avec]; it[avec] = choisis[rang] })
    val gestes = buildList {
        if (rang > 0) add(monter to { echanger(rang - 1) })
        if (rang < choisis.size - 1) add(descendre to { echanger(rang + 1) })
    }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible)
                .semantics { customActions = gestes.map { (libelle, geste) -> CustomAccessibilityAction(libelle) { geste(); true } } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { if (gestes.isNotEmpty()) menu = true },
                modifier = Modifier.weight(1f).testTag(ReperesPrestatairesAffaire.ligne(prestataire)),
            ) {
                Text(prestataire.nom, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = c.text)
            }
            IconButton(
                onClick = { changer(choisis.filterIndexed { i, _ -> i != rang }) },
                modifier = Modifier.testTag(ReperesPrestatairesAffaire.retirer(prestataire)),
            ) {
                Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.prestataires_affaire_retirer, prestataire.nom), tint = c.textMuted)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            gestes.forEach { (libelle, geste) ->
                DropdownMenuItem(text = { Text(libelle) }, onClick = { menu = false; geste() })
            }
        }
    }
}

@Composable
private fun Ajouter(disponibles: List<PrestataireAffaire>, lu: Boolean, ajouter: (PrestataireAffaire) -> Unit) {
    val c = LocalCouleurs.current
    var menu by remember { mutableStateOf(false) }
    // Toute la rangée se touche, aussi large que celles des prestataires (pendant de
    // coprofirst-ios#309) : réduite à son libellé, sa droite restait vide sous le doigt.
    Box(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { menu = true },
            enabled = disponibles.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(ReperesPrestatairesAffaire.AJOUTER),
        ) {
            Icon(Icons.Filled.AddCircle, contentDescription = null, tint = if (disponibles.isNotEmpty()) c.primary else c.textMuted)
            // Le libellé prend le reste de la rangée : il reste à gauche, le bouton s'étend à droite.
            Text(
                stringResource(R.string.prestataires_affaire_ajouter),
                Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                color = if (disponibles.isNotEmpty()) c.primary else c.textMuted,
            )
        }
        DropdownMenu(menu, { menu = false }) {
            disponibles.forEach { prestataire ->
                DropdownMenuItem(
                    text = { Text(prestataire.nom) },
                    onClick = { menu = false; ajouter(prestataire) },
                    modifier = Modifier.testTag(ReperesPrestatairesAffaire.choix(prestataire)),
                )
            }
        }
    }
    if (lu && disponibles.isEmpty()) {
        Text(stringResource(R.string.prestataires_affaire_tous_choisis), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

private const val DUREE_MS = 200

/** Les repères des tests d'interface : ceux de l'iOS (`modifier.prestataires.*`). */
object ReperesPrestatairesAffaire {
    const val AUCUN = "modifier.prestataires.aucun"
    const val AJOUTER = "modifier.prestataires.ajouter"
    fun choix(p: PrestataireAffaire) = "modifier.prestataires.choix.${p.id}"
    fun ligne(p: PrestataireAffaire) = "modifier.prestataire.${p.id ?: 0}"
    fun retirer(p: PrestataireAffaire) = "modifier.prestataire.${p.id ?: 0}.retirer"
}
