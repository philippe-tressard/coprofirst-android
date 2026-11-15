// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.TexteRiche
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Les trois sections pliées de l'avis, dans l'ordre du site. */
enum class SectionAvis(@param:StringRes val titre: Int) {
    Reglement(R.string.avis_le_reglement),
    Assemblees(R.string.avis_les_assemblees),
    Reserves(R.string.avis_reserves),
}

/**
 * L'avis entier sur une question au règlement (la carte dépliée du site), comme l'iOS
 * (`QuestionReglementView`) : la question, le pied, la mention, puis l'avis. Un avis à
 * renvois se lit en synthèse, fondements et sections pliées ; un avis d'avant, section
 * après section.
 */
@Composable
fun AvisReglement(question: QuestionReglement) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(question.question, style = Typo.titre(), color = couleurs.text, modifier = Modifier.semantics { heading() })
        PiedQuestion(question)
        Text(mention(question), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = couleurs.textMuted)
        if (question.avecRenvois) AvisARenvois(question) else AvisDAvant(question)
    }
}

/** « Posée le … Avis indicatif… Rédigé par …, d'après …. » (`CarteQuestionReglement`). */
@Composable
private fun mention(question: QuestionReglement): String {
    val texte = stringResource(R.string.avis_posee, FormatsDuSite.dateHeure(question.poseeLe))
    val modele = question.modele
    val provenance = question.provenance
    return texte + when {
        modele != null && provenance != null -> stringResource(R.string.avis_redige_par_d_apres, modele, provenance)
        modele != null -> stringResource(R.string.avis_redige_par, modele)
        provenance != null -> stringResource(R.string.avis_redige_d_apres, provenance)
        else -> ""
    }
}

/** Un avis d'avant les renvois se rend comme il l'a toujours été. */
@Composable
private fun AvisDAvant(question: QuestionReglement) {
    TexteRiche(question.reponse)
    Section(R.string.avis_selon_reglement, question.selonReglement)
    Extraits(question.extraitsReglement, R.string.avis_extraits_reglement)
    Section(R.string.avis_selon_ag, question.selonAG)
    Extraits(question.extraitsAG, R.string.avis_extraits_ag)
    Section(R.string.avis_synthese, question.synthese)
    Section(R.string.avis_reserves, question.reserves)
}

@Composable
private fun Section(@StringRes titre: Int, html: String?) {
    if (html.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitreSection(stringResource(titre))
        TexteRiche(html)
    }
}

@Composable
private fun TitreSection(titre: String, modifier: Modifier = Modifier) {
    Text(
        titre, style = MaterialTheme.typography.titleMedium, color = LocalCouleurs.current.text,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * L'avis à RENVOIS (`AvisReglement.svelte`) : la synthèse, puis « Ce qui fonde l'avis » —
 * une ligne par renvoi, qui ouvre sa section et amène à l'extrait, signalé un instant —,
 * puis trois sections pliées, une seule ouverte à la fois.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AvisARenvois(question: QuestionReglement) {
    val couleurs = LocalCouleurs.current
    val haptique = LocalHapticFeedback.current
    val portee = rememberCoroutineScope()
    var ouverte by remember(question.id) { mutableStateOf<SectionAvis?>(null) }
    var cible by remember(question.id) { mutableStateOf<String?>(null) }
    val ancres = remember(question.id) { question.fondements.mapNotNull { it.renvoi }.associateWith { BringIntoViewRequester() } }
    fun basculer(section: SectionAvis) {
        haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
        ouverte = if (ouverte == section) null else section
    }
    fun aller(extrait: ExtraitReglement) {
        val renvoi = extrait.renvoi ?: return
        haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
        ouverte = if (extrait.source == QuestionReglement.SOURCE_AG) SectionAvis.Assemblees else SectionAvis.Reglement
        cible = renvoi
        portee.launch {
            // La section dépliée doit être posée avant qu'on y défile (`revelerCible` du site : 2,5 s).
            delay(DELAI_POSE_MS)
            ancres[renvoi]?.bringIntoView()
            delay(DUREE_SIGNAL_MS)
            if (cible == renvoi) cible = null
        }
    }
    TexteRiche(question.reponse)
    if (question.fondements.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TitreSection(stringResource(R.string.avis_fondements), Modifier.padding(bottom = 4.dp).testTag(ReperesResidence.FONDEMENTS))
            question.fondements.forEach { LigneFondement(it) { aller(it) } }
            if (question.renvoisSansExtrait.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.avis_sans_extrait, question.renvoisSansExtrait.size, question.renvoisSansExtrait.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall, color = couleurs.warningTexte, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    Column {
        val sections = buildList {
            if (question.selonReglement != null || question.extraitsReglement.isNotEmpty()) {
                add(Triple(SectionAvis.Reglement, question.selonReglement, question.extraitsReglement))
            }
            if (question.selonAG != null || question.extraitsAG.isNotEmpty()) {
                add(Triple(SectionAvis.Assemblees, question.selonAG, question.extraitsAG))
            }
            if (question.reserves != null) add(Triple(SectionAvis.Reserves, question.reserves, emptyList()))
        }
        sections.forEach { (section, html, extraits) ->
            SectionPliee(section, if (section == SectionAvis.Reserves) null else extraits.size, ouverte == section, { basculer(section) }) {
                if (!html.isNullOrEmpty()) TexteRiche(html)
                Extraits(extraits, cible = cible, ancres = ancres)
            }
        }
    }
}

/** Le délai avant de défiler vers l'extrait, puis la durée de son signal. */
private const val DELAI_POSE_MS = 60L
private const val DUREE_SIGNAL_MS = 2_500L

/** Une ligne de « Ce qui fonde l'avis » : le renvoi, où se trouve l'extrait, ce que le code en a vérifié. */
@Composable
private fun LigneFondement(extrait: ExtraitReglement, aller: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible)
            .cliquable(stringResource(R.string.avis_aller_a_l_extrait), aller)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(ReperesResidence.fondement(extrait.renvoi.orEmpty())),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(extrait.renvoi.orEmpty(), Modifier.widthIn(min = 36.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = couleurs.primary)
        Text(extrait.origine.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = couleurs.text)
        if (extrait.verifie) {
            val dit = stringResource(R.string.avis_retrouve)
            Text(
                "✓", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = couleurs.successTexte,
                modifier = Modifier.semantics { contentDescription = dit },
            )
        } else {
            Text(stringResource(R.string.avis_non_retrouve_court), style = MaterialTheme.typography.bodySmall, color = couleurs.warningTexte)
        }
    }
}

/** Une section pliée : un bandeau — titre, compte, chevron — qui ouvre ce qu'il cache. */
@Composable
private fun SectionPliee(section: SectionAvis, compte: Int?, ouverte: Boolean, basculer: () -> Unit, contenu: @Composable () -> Unit) {
    val couleurs = LocalCouleurs.current
    val rotation by animateFloatAsState(if (ouverte) 180f else 0f, label = "chevron")
    val etat = stringResource(if (ouverte) R.string.avis_deplie else R.string.avis_replie)
    Column {
        HorizontalDivider(color = couleurs.border)
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible)
                .cliquable(stringResource(if (ouverte) R.string.avis_replier else R.string.avis_deplier), basculer)
                .semantics(mergeDescendants = true) { stateDescription = etat }
                .testTag(ReperesResidence.section(section)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(section.titre), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = couleurs.text)
            if (compte != null && compte > 0) {
                Text(
                    FormatsDuSite.nombre(compte), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = couleurs.textMuted,
                    modifier = Modifier.background(couleurs.neutreFond, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(20.dp).rotate(rotation))
        }
        AnimatedVisibility(ouverte) {
            Column(Modifier.padding(top = 6.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { contenu() }
        }
    }
}

/**
 * Les extraits d'un avis, avec ce que le CODE en a vérifié (`ExtraitsReglement`) : un
 * extrait retrouvé montre son repère, un extrait non retrouvé le dit en clair. Un extrait
 * à renvoi porte l'ancre où mène son fondement, et se signale quand il est la `cible`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Extraits(
    extraits: List<ExtraitReglement>,
    @StringRes titre: Int? = null,
    cible: String? = null,
    ancres: Map<String, BringIntoViewRequester> = emptyMap(),
) {
    if (extraits.isEmpty()) return
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        titre?.let { TitreSection(stringResource(it)) }
        extraits.forEach { extrait ->
            val vise = extrait.renvoi != null && extrait.renvoi == cible
            val signal by animateFloatAsState(if (vise) 1f else 0f, label = "signal")
            val forme = RoundedCornerShape(Mise.rayon)
            val ancre = extrait.renvoi?.let { ancres[it] }
            Column(
                Modifier.fillMaxWidth()
                    .then(if (ancre != null) Modifier.bringIntoViewRequester(ancre) else Modifier)
                    .border(3.dp, couleurs.primary.copy(alpha = signal), forme)
                    .padding(8.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag(ReperesResidence.extrait(extrait.renvoi)),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                extrait.renvoi?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = couleurs.primary) }
                Text(
                    "« ${extrait.citation} »", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = couleurs.text,
                    modifier = Modifier.filetGauche(couleurs.border).padding(start = 12.dp),
                )
                if (extrait.verifie) {
                    Text("✓ ${extrait.origine.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = couleurs.successTexte)
                } else {
                    Text(
                        stringResource(R.string.avis_non_retrouve) +
                            (extrait.reference?.let { stringResource(R.string.avis_reference_donnee, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = couleurs.warningTexte,
                    )
                }
                extrait.apport?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted) }
            }
        }
    }
}
