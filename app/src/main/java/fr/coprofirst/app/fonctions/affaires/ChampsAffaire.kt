// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.session.ErreurService

/** Les repères des champs, préfixés par leur feuille, comme l'iOS (`nouvelle.*`, `modifier.*`). */
class ReperesChamps(prefixe: String) {
    val titre = "$prefixe.titre"
    val description = "$prefixe.description"
    val lieu = "$prefixe.lieu"
    val urgente = "$prefixe.urgente"
    val contenuFige = "$prefixe.contenuFige"
    private val categorie = "$prefixe.categorie"
    fun categorie(code: String) = "$categorie.$code"

    companion object {
        val NOUVELLE = ReperesChamps("nouvelle")
        val MODIFIER = ReperesChamps("modifier")
    }
}

/**
 * Les champs d'une affaire, écrits UNE fois pour la Création et l'Édition, comme l'iOS
 * (`ChampsAffaire`) : catégorie, titre, description, lieu, urgence.
 *
 * Ce que l'Édition ne peut pas réenregistrer sans perte se montre sans se toucher : une
 * description mise en forme sur le site (`descriptionFigee`), plusieurs lieux
 * (`lieuModifiable`). `contenuFige` : le serveur ne laisse plus corriger le contenu
 * (`peut_modifier`) — l'urgence reste. `poserLeDefaut` (Création) : le lieu que le serveur
 * désigne par défaut est choisi d'avance.
 */
@Composable
internal fun ChampsAffaire(
    saisie: SaisieAffaire,
    categories: List<ReferentielsAffaires.Categorie>,
    service: ServiceAffaires,
    reperes: ReperesChamps,
    poserLeDefaut: Boolean = false,
    descriptionFigee: String? = null,
    lieuModifiable: Boolean = true,
    contenuFige: Boolean = false,
    changer: (SaisieAffaire) -> Unit,
) {
    val c = LocalCouleurs.current
    val actuelle by rememberUpdatedState(saisie)
    var perimetres by remember { mutableStateOf<List<Perimetre>>(emptyList()) }
    LaunchedEffect(service) {
        // Un échec tait le choix du lieu : le serveur pose alors celui qu'il désigne par défaut.
        val lus = try { Perimetre.aSaisir(service.perimetres(), garder = actuelle.perimetre) } catch (_: ErreurService) { emptyList() }
        perimetres = lus
        if (poserLeDefaut && actuelle.perimetre == null) changer(actuelle.copy(perimetre = Perimetre.codeParDefaut(lus)))
    }
    if (contenuFige) {
        Text(
            stringResource(R.string.modifier_contenu_fige), Modifier.testTag(reperes.contenuFige),
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
        )
    }
    if (categories.isNotEmpty()) {
        TitreSection(R.string.nouvelle_categorie)
        ChoixCategorie(categories, saisie.categorie, reperes, actif = !contenuFige) { code ->
            // Un bogue ne s'urge pas.
            changer(saisie.copy(categorie = code).let { if (it.estBogue) it.copy(urgente = false) else it })
        }
        if (categories.any { it.carnet }) {
            Text(stringResource(R.string.nouvelle_carnet), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
    OutlinedTextField(
        saisie.titre, { changer(saisie.copy(titre = it)) }, Modifier.fillMaxWidth().testTag(reperes.titre),
        enabled = !contenuFige,
        label = { Text(stringResource(R.string.nouvelle_champ_titre)) },
        placeholder = { Text(stringResource(R.string.nouvelle_titre_indication)) }, singleLine = true,
    )
    if (descriptionFigee != null) {
        TitreSection(R.string.nouvelle_description)
        Text(TexteDuSite.riche(descriptionFigee), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Text(TexteDuSite.MISE_EN_FORME_AU_SITE, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    } else {
        OutlinedTextField(
            saisie.description, { changer(saisie.copy(description = it)) }, Modifier.fillMaxWidth().testTag(reperes.description),
            enabled = !contenuFige,
            label = { Text(stringResource(R.string.nouvelle_description)) },
            placeholder = { Text(stringResource(R.string.nouvelle_description_indication)) }, minLines = 4, maxLines = 12,
        )
    }
    TitreSection(R.string.nouvelle_de_quoi)
    when {
        saisie.estBogue -> Text(stringResource(R.string.nouvelle_bogue), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        !lieuModifiable -> Text(stringResource(R.string.modifier_plusieurs_lieux), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        perimetres.isNotEmpty() -> ChoixLieu(saisie.perimetre, perimetres, reperes, actif = !contenuFige) { changer(saisie.copy(perimetre = it)) }
    }
    if (!saisie.estBogue) {
        Row(
            Modifier.fillMaxWidth().testTag(reperes.urgente),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.affaires_urgente), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text)
            Switch(saisie.urgente, { changer(saisie.copy(urgente = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = c.dangerPlein))
        }
        // Ce que dit l'avertissement du site : l'app trace, elle n'alerte pas.
        if (saisie.urgente) Text(stringResource(R.string.nouvelle_urgente_avertissement), style = MaterialTheme.typography.bodySmall, color = c.danger)
    }
}

/** Le lieu : « Non précisé » (le serveur pose son défaut), puis l'arbre servi, en retrait. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoixLieu(choisi: String?, perimetres: List<Perimetre>, reperes: ReperesChamps, actif: Boolean, choisir: (String?) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    val nonPrecise = stringResource(R.string.nouvelle_non_precise)
    ExposedDropdownMenuBox(ouvert, { ouvert = it && actif }) {
        OutlinedTextField(
            perimetres.firstOrNull { it.code == choisi }?.libelle ?: nonPrecise, {}, readOnly = true, enabled = actif,
            label = { Text(stringResource(R.string.nouvelle_lieu)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ouvert) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag(reperes.lieu),
        )
        ExposedDropdownMenu(ouvert, { ouvert = false }) {
            DropdownMenuItem(text = { Text(nonPrecise) }, onClick = { ouvert = false; choisir(null) })
            perimetres.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.libelle, Modifier.padding(start = (p.profondeur * 16).dp)) },
                    onClick = { ouvert = false; choisir(p.code) },
                )
            }
        }
    }
}

/**
 * Le choix de la catégorie, en vignettes, comme l'iOS (`ChoixCategorie`) : dans l'ordre servi ;
 * une catégorie du bâti porte le liseré doré du carnet d'entretien.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoixCategorie(categories: List<ReferentielsAffaires.Categorie>, choisie: String, reperes: ReperesChamps, actif: Boolean, choisir: (String) -> Unit) {
    val c = LocalCouleurs.current
    val forme = RoundedCornerShape(Mise.rayon - 2.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
        categories.forEach { categorie ->
            val retenue = choisie == categorie.code
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(min = 140.dp)
                    .clip(forme)
                    .background(if (retenue) c.primaryLight else c.surface)
                    .border(if (retenue || categorie.carnet) 1.5.dp else 1.dp, if (retenue) c.primary else if (categorie.carnet) c.accent else c.border, forme)
                    .selectable(retenue, enabled = actif, role = Role.RadioButton) { choisir(categorie.code) }
                    .testTag(reperes.categorie(categorie.code))
                    .clearAndSetSemantics {
                        contentDescription = "${categorie.libelle} — ${categorie.description}"
                        selected = retenue
                    }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(categorie.emoji)
                    Text(categorie.libelle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text)
                    if (retenue) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
                }
                Text(categorie.description, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, minLines = 2)
            }
        }
    }
}

