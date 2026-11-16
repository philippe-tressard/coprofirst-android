// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.Libelle

/**
 * Le vote, comme le formulaire de la fiche du site et de l'iOS (`FormulaireVote`) : choisir
 * une réponse, la préciser si l'option le demande (`champ_libre`), commenter si l'on veut,
 * s'engager à la charte, puis « Voter ». Le bouton attend que rien ne manque, et dit ce qui
 * manque (repère `sondage.manque`).
 */
@Composable
internal fun FormulaireVote(sondage: DetailSondage, vote: VoteSondage, changer: (VoteSondage) -> Unit, envoi: Boolean, voter: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val champLibre = sondage.options.firstOrNull { it.id == vote.option }?.champLibre ?: false
    val manque = vote.manque(champLibre)
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sondage.options.forEach { option ->
                LigneChoix(option, vote.option == option.id) { changer(vote.choisir(option.id)) }
            }
        }
        AnimatedVisibility(visible = champLibre, enter = fadeIn(), exit = fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EnteteRequis(stringResource(R.string.sondage_preciser), vide = VoteSondage.nettoye(vote.reponseLibre) == null)
                ChampVote(vote.reponseLibre, { changer(vote.copy(reponseLibre = it)) }, R.string.sondage_preciser_invite,
                    R.string.sondage_preciser, ReperesSondage.PRECISION, fond = couleurs.primaryLight)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Libelle(stringResource(R.string.sondage_commentaire))
            ChampVote(vote.commentaire, { changer(vote.copy(commentaire = it)) }, R.string.sondage_commentaire_invite,
                R.string.sondage_commentaire, ReperesSondage.COMMENTAIRE, fond = couleurs.surface)
        }
        CaseCharte(vote.charteAcceptee) { changer(vote.copy(charteAcceptee = it)) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoutonPlein(
                if (envoi) R.string.sondage_envoi else R.string.sondage_voter,
                Modifier.testTag(ReperesSondage.VOTER),
                actif = manque == null,
                enCours = envoi,
                onClick = voter,
            )
            if (manque != null && !envoi) {
                Text(
                    stringResource(manque),
                    style = MaterialTheme.typography.bodySmall,
                    color = couleurs.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag(ReperesSondage.MANQUE),
                )
            }
        }
    }
}

/**
 * Une réponse possible : le rond qui se coche, le libellé, ✏️ si elle demande une précision.
 * Ses votes ne se disent pas ici : visibles, les barres sont au-dessus du formulaire
 * (#218). Dite « bouton radio » au lecteur d'écran.
 */
@Composable
private fun LigneChoix(option: OptionSondage, choisie: Boolean, choisir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val haptique = LocalHapticFeedback.current
    val forme = RoundedCornerShape(Mise.rayon)
    val aPreciser = stringResource(R.string.sondage_option_a_preciser)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Mise.cible)
            .background(if (choisie) couleurs.primaryLight else couleurs.surface, forme)
            .border(1.dp, if (choisie) couleurs.primary else couleurs.border, forme)
            .selectable(selected = choisie, role = Role.RadioButton) {
                if (!choisie) haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
                choisir()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(ReperesSondage.option(option.id)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (choisie) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (choisie) couleurs.primary else couleurs.textMuted,
        )
        Text(
            option.libelle,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = couleurs.text,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (option.champLibre) Text("✏️", Modifier.semantics { contentDescription = aPreciser })
    }
}

/** Un champ du vote (précision, commentaire) : plusieurs lignes, l'invite du site. */
@Composable
private fun ChampVote(texte: String, changer: (String) -> Unit, invite: Int, libelle: Int, repere: String, fond: Color) {
    val couleurs = LocalCouleurs.current
    val nom = stringResource(libelle)
    OutlinedTextField(
        value = texte,
        onValueChange = changer,
        placeholder = { Text(stringResource(invite)) },
        minLines = 3,
        maxLines = 8,
        shape = RoundedCornerShape(Mise.rayon),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fond,
            unfocusedContainerColor = fond,
            unfocusedBorderColor = couleurs.borderChamp,
        ),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = nom }.testTag(repere),
    )
}

/** « Je m'engage à rester respectueux… » : une case, sur le fond d'avertissement du site. */
@Composable
private fun CaseCharte(acceptee: Boolean, changer: (Boolean) -> Unit) {
    val couleurs = LocalCouleurs.current
    val haptique = LocalHapticFeedback.current
    val forme = RoundedCornerShape(Mise.rayon)
    Row(
        Modifier
            .fillMaxWidth()
            .background(couleurs.warningFond, forme)
            .border(1.dp, couleurs.warningBordure, forme)
            .toggleable(value = acceptee, role = Role.Checkbox) {
                haptique.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                changer(it)
            }
            .padding(14.dp)
            .testTag(ReperesSondage.CHARTE),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (acceptee) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (acceptee) couleurs.primary else couleurs.warningTexte,
        )
        Text(stringResource(R.string.sondage_charte), style = MaterialTheme.typography.bodyMedium, color = couleurs.warningTexte)
    }
}

/** L'en-tête d'un champ requis : l'étoile reste rouge tant que le champ est vide (`EtoileRequis` du site). */
@Composable
private fun EnteteRequis(texte: String, vide: Boolean) {
    val couleurs = LocalCouleurs.current
    val requis = stringResource(R.string.sondage_requis)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Libelle(texte)
        Libelle(
            "*",
            Modifier.semantics { contentDescription = requis },
            couleur = if (vide) couleurs.danger else couleurs.textMuted,
            fort = true,
        )
    }
}
