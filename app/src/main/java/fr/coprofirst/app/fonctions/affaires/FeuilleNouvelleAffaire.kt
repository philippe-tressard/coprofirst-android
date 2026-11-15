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
import fr.coprofirst.app.session.ErreurService

/** Les repères de « Nouvelle affaire », ceux de l'iOS. */
object ReperesNouvelle {
    const val NOUVELLE = "affaires.nouvelle"
    const val TITRE = "nouvelle.titre"
    const val DESCRIPTION = "nouvelle.description"
    const val LIEU = "nouvelle.lieu"
    const val URGENTE = "nouvelle.urgente"
    const val ENVOYER = "nouvelle.envoyer"
    const val DEPUIS_LE_SITE = "nouvelle.depuisLeSite"
    const val ABANDONNER = "nouvelle.abandonner"
    fun categorie(code: String) = "nouvelle.categorie.$code"
}

/**
 * « Nouvelle affaire », comme l'iOS (`NouvelleAffaireView`, `ChampsAffaire`) : catégorie,
 * titre, description, lieu, urgence. Ce que l'app ne pose pas est dit, jamais caché : les
 * pièces jointes et les sections que le conseil remplit restent au site.
 *
 * ⚠️ Divergences déclarées : toutes les sections sont à leur rang — la saisie Essentielle
 * (« Plus de détails ») attend les tables de la saisie que coprofirst ne publie pas encore
 * (#47) ; « Qui la lira » se tait jusqu'au composant commun (#70).
 */
@Composable
internal fun FeuilleNouvelleAffaire(
    referentiels: ReferentielsAffaires?,
    erreurReferentiels: ErreurService?,
    service: ServiceAffaires,
    fermer: () -> Unit,
    creee: (Affaire) -> Unit,
) {
    var saisie by remember { mutableStateOf(SaisieAffaire()) }
    var abandon by remember { mutableStateOf(false) }
    var perimetres by remember { mutableStateOf<List<Perimetre>>(emptyList()) }
    LaunchedEffect(service) {
        // Un échec tait le choix du lieu : le serveur pose alors celui qu'il désigne par défaut.
        val lus = try { Perimetre.aSaisir(service.perimetres()) } catch (_: ErreurService) { emptyList() }
        perimetres = lus
        if (saisie.perimetre == null) saisie = saisie.copy(perimetre = Perimetre.codeParDefaut(lus))
    }
    val categories = referentiels?.permises
    FeuilleDeSaisie(
        R.string.nouvelle_titre, R.string.nouvelle_envoyer, ReperesNouvelle.ENVOYER,
        prete = categories != null && saisie.complete, fermer = fermer,
        envoyer = { creee(service.creer(saisie)) },
        retenue = saisie.entamee, demanderAbandon = { abandon = true },
    ) {
        when {
            categories != null -> Champs(saisie, categories, perimetres) { saisie = it }
            erreurReferentiels != null -> MessageErreur(erreurReferentiels)
            else -> {
                val annonce = stringResource(R.string.nouvelle_chargement_categories)
                Box(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = annonce }, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        DepuisLeSite(referentiels?.poseLesReservees ?: false)
    }
    if (abandon) {
        AlertDialog(
            onDismissRequest = { abandon = false },
            title = { Text(stringResource(R.string.nouvelle_abandonner_titre)) },
            confirmButton = {
                TextButton(onClick = { abandon = false; fermer() }, modifier = Modifier.testTag(ReperesNouvelle.ABANDONNER)) {
                    Text(stringResource(R.string.nouvelle_abandonner), color = LocalCouleurs.current.danger)
                }
            },
            dismissButton = { TextButton(onClick = { abandon = false }) { Text(stringResource(R.string.nouvelle_continuer)) } },
        )
    }
}

@Composable
private fun Champs(saisie: SaisieAffaire, categories: List<ReferentielsAffaires.Categorie>, perimetres: List<Perimetre>, changer: (SaisieAffaire) -> Unit) {
    val c = LocalCouleurs.current
    if (categories.isNotEmpty()) {
        TitreSection(R.string.nouvelle_categorie)
        ChoixCategorie(categories, saisie.categorie) { code ->
            // Un bogue ne s'urge pas.
            changer(saisie.copy(categorie = code).let { if (it.estBogue) it.copy(urgente = false) else it })
        }
        if (categories.any { it.carnet }) {
            Text(stringResource(R.string.nouvelle_carnet), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
    OutlinedTextField(
        saisie.titre, { changer(saisie.copy(titre = it)) }, Modifier.fillMaxWidth().testTag(ReperesNouvelle.TITRE),
        label = { Text(stringResource(R.string.nouvelle_champ_titre)) },
        placeholder = { Text(stringResource(R.string.nouvelle_titre_indication)) }, singleLine = true,
    )
    OutlinedTextField(
        saisie.description, { changer(saisie.copy(description = it)) }, Modifier.fillMaxWidth().testTag(ReperesNouvelle.DESCRIPTION),
        label = { Text(stringResource(R.string.nouvelle_description)) },
        placeholder = { Text(stringResource(R.string.nouvelle_description_indication)) }, minLines = 4, maxLines = 12,
    )
    TitreSection(R.string.nouvelle_de_quoi)
    when {
        saisie.estBogue -> Text(stringResource(R.string.nouvelle_bogue), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        perimetres.isNotEmpty() -> ChoixLieu(saisie.perimetre, perimetres) { changer(saisie.copy(perimetre = it)) }
    }
    if (!saisie.estBogue) {
        Row(
            Modifier.fillMaxWidth().testTag(ReperesNouvelle.URGENTE),
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
private fun ChoixLieu(choisi: String?, perimetres: List<Perimetre>, choisir: (String?) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    val nonPrecise = stringResource(R.string.nouvelle_non_precise)
    ExposedDropdownMenuBox(ouvert, { ouvert = it }) {
        OutlinedTextField(
            perimetres.firstOrNull { it.code == choisi }?.libelle ?: nonPrecise, {}, readOnly = true,
            label = { Text(stringResource(R.string.nouvelle_lieu)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ouvert) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag(ReperesNouvelle.LIEU),
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
private fun ChoixCategorie(categories: List<ReferentielsAffaires.Categorie>, choisie: String, choisir: (String) -> Unit) {
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
                    .selectable(retenue, role = Role.RadioButton) { choisir(categorie.code) }
                    .testTag(ReperesNouvelle.categorie(categorie.code))
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

/** Ce que cette feuille ne pose pas, selon ce que le serveur permet à qui la remplit. */
@Composable
private fun DepuisLeSite(poseLesReservees: Boolean) {
    val c = LocalCouleurs.current
    TitreSection(R.string.nouvelle_depuis_le_site)
    val texte = stringResource(R.string.nouvelle_pieces_au_site) + " " +
        stringResource(if (poseLesReservees) R.string.nouvelle_reserve_au_site else R.string.nouvelle_conseil_au_site)
    Row(
        Modifier.fillMaxWidth().testTag(ReperesNouvelle.DEPUIS_LE_SITE).clearAndSetSemantics { contentDescription = texte },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Language, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        Text(texte, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}
