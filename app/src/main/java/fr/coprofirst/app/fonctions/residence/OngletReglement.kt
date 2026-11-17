// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.app.commun.miseenpage.rangeeChoisie
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/** La limite du champ, celle du site (`maxlength`). */
const val LONGUEUR_QUESTION = 2000

/**
 * L'onglet « Règlement », comme l'iOS (`ReglementView`) : poser une question sur le
 * règlement de copropriété ; l'assistant répond en juriste, avec les extraits qui fondent
 * sa réponse. `cs` (conseil syndical ou administration) choisit les textes.
 */
@Composable
fun OngletReglement(
    etat: EtatReglement,
    questions: List<QuestionReglement>,
    cs: Boolean,
    service: ServiceResidence,
    ouvrirFiche: () -> Unit,
    posee: (QuestionReglement) -> Unit,
    ouvrir: (QuestionReglement) -> Unit,
) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Avertissement(cs)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EnTeteRubrique(stringResource(R.string.reglement_texte))
            Text(stringResource(R.string.reglement_texte_dans_la_fiche), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
            TextButton(onClick = ouvrirFiche, modifier = Modifier.heightIn(min = Mise.cible)) {
                Text(stringResource(R.string.reglement_voir_dans_la_fiche), fontWeight = FontWeight.SemiBold, color = couleurs.primary)
            }
        }
        FormulaireQuestion(etat, cs, service, posee)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EnTeteRubrique(stringResource(if (cs) R.string.reglement_questions_posees else R.string.reglement_vos_questions))
            if (questions.isEmpty()) {
                EtatVide(
                    stringResource(R.string.reglement_aucune),
                    icone = Icons.AutoMirrored.Outlined.Chat,
                    explication = stringResource(if (cs) R.string.reglement_aucune_detail else R.string.reglement_aucune_detail_prive),
                )
            }
            questions.forEach { CarteQuestion(it) { ouvrir(it) } }
        }
    }
}

/** « Les réponses sont un **avis indicatif** … », sur le fond d'information du site. */
@Composable
private fun Avertissement(cs: Boolean) {
    val couleurs = LocalCouleurs.current
    val forme = RoundedCornerShape(Mise.rayon)
    val texte = buildAnnotatedString {
        append(stringResource(R.string.reglement_avis_debut))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.reglement_avis_gras)) }
        append(stringResource(if (cs) R.string.reglement_avis_fin_cs else R.string.reglement_avis_fin))
    }
    Text(
        texte,
        style = MaterialTheme.typography.bodyMedium,
        color = couleurs.text,
        modifier = Modifier.fillMaxWidth().background(couleurs.infoFond, forme).border(1.dp, couleurs.infoBordure, forme).padding(14.dp),
    )
}

/** « Votre question » : le champ, et le bouton qui la pose — désactivé tant que l'assistant ne peut pas répondre. */
@Composable
private fun FormulaireQuestion(etat: EtatReglement, cs: Boolean, service: ServiceResidence, posee: (QuestionReglement) -> Unit) {
    val couleurs = LocalCouleurs.current
    val focus = LocalFocusManager.current
    val haptique = LocalHapticFeedback.current
    val portee = rememberCoroutineScope()
    var question by rememberSaveable { mutableStateOf("") }
    // Pas gardé à la rotation : la requête en cours ne survit pas à l'écran qui l'a lancée.
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    val empechement = when {
        !etat.disponible -> if (cs) R.string.reglement_indisponible_cs else R.string.reglement_indisponible
        !etat.texteDisponible -> R.string.reglement_sans_texte
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EnTeteRubrique(stringResource(R.string.reglement_votre_question))
        OutlinedTextField(
            value = question,
            onValueChange = { question = it.take(LONGUEUR_QUESTION) },
            placeholder = { Text(stringResource(R.string.reglement_exemple)) },
            label = { Text(stringResource(R.string.reglement_votre_question)) },
            enabled = !enCours && empechement == null,
            minLines = 3,
            maxLines = 8,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = couleurs.primary,
                unfocusedBorderColor = couleurs.borderChamp,
                focusedContainerColor = couleurs.surface,
                unfocusedContainerColor = couleurs.surface,
            ),
            modifier = Modifier.fillMaxWidth().testTag(ReperesResidence.QUESTION),
        )
        empechement?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted) }
        erreur?.let { MessageErreur(it) }
        BoutonPlein(
            if (enCours) R.string.reglement_lecture else R.string.reglement_poser,
            Modifier.testTag(ReperesResidence.POSER),
            actif = question.isNotBlank() && empechement == null,
            enCours = enCours,
        ) {
            focus.clearFocus()
            enCours = true
            erreur = null
            portee.launch {
                try {
                    val reponse = service.poser(question)
                    question = ""
                    haptique.performHapticFeedback(HapticFeedbackType.Confirm)
                    posee(reponse)
                } catch (e: ErreurService) {
                    erreur = e
                } finally {
                    enCours = false
                }
            }
        }
        if (enCours) Text(stringResource(R.string.reglement_patience), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
    }
}

/** Une question : la date, la question, l'aperçu de la réponse, le pied. Un toucher ouvre l'avis entier. */
@Composable
private fun CarteQuestion(question: QuestionReglement, ouvrir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    CarteAGouttiere(
        Nature.Reglement,
        Modifier.testTag(ReperesResidence.question(question.id)).rangeeChoisie(NATURE_AVIS, question.id.toString()),
        date = question.poseeLe,
        ouvrir = ouvrir, action = stringResource(R.string.reglement_ouvrir_avis),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(question.question, style = MaterialTheme.typography.titleMedium, color = couleurs.text, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(20.dp))
        }
        Text(
            TexteDuSite.apercu(question.reponseSansRenvois), style = MaterialTheme.typography.bodyMedium,
            maxLines = 3, overflow = TextOverflow.Ellipsis, color = couleurs.textMuted,
        )
        PiedQuestion(question)
    }
}

/** Le pied d'un avis : verdict, extraits vérifiés, texte remplacé, FAQ, auteur ; les capsules passent à la ligne. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PiedQuestion(question: QuestionReglement) {
    val couleurs = LocalCouleurs.current
    val style = MaterialTheme.typography.bodySmall
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        // Le ton du verdict est servi (`verdict_ton`) ; sa teinte, celle de `BadgeVerdict`.
        PastilleTeinte(question.verdictLibelle, question.badgeDuVerdict, compacte = true)
        if (question.extraits.isNotEmpty()) {
            val n = question.extraits.size
            Text(
                pluralStringResource(R.plurals.reglement_extraits, n, question.verifies, n) +
                    pluralStringResource(R.plurals.reglement_verifies, question.verifies),
                style = style, color = if (question.verifies < n) couleurs.warningTexte else couleurs.textMuted,
            )
        }
        if (!question.texteEnVigueur) Text(stringResource(R.string.reglement_texte_remplace), style = style, color = couleurs.warningTexte)
        if (question.dansLaFAQ) Text(stringResource(R.string.reglement_dans_la_faq), style = style, color = couleurs.textMuted)
        question.auteur?.let { Text(it, style = style, color = couleurs.textMuted) }
    }
}
