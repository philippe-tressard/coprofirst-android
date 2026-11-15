// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.ErreurService

/** Les repères des avis : ceux de l'iOS. */
object ReperesAvis {
    fun exercice(annee: Int) = "avis.exercice.$annee"
    fun avis(id: Int) = "avis.$id"
    fun detail(id: Int) = "avis.$id.detail"
}

/**
 * Les avis du conseil syndical sur un prestataire, rangés par exercice et par intervention
 * (`NotationsPrestataire.svelte`, #207 de l'iOS) — tels que l'API les SERT. Un exercice par
 * bloc, le plus récent ouvert ; dedans, une ligne par affaire et le badge de chaque avis,
 * « non notée » sinon. Un badge porte un chevron (un avis replié se VOIT dépliable) et
 * déplie, sous la ligne, l'auteur, la date et le commentaire — un seul à la fois.
 *
 * Lecture seule, comme l'iOS : corriger (`peut_modifier`) et retirer un avis restent sur le
 * site — aucun droit de retrait n'est servi. Illisible, le récapitulatif se tait : la fiche
 * reste lisible sans lui.
 */
@Composable
internal fun RecapitulatifAvis(prestataire: Prestataire, service: ServicePrestataires) {
    var exercices by remember(prestataire.id) { mutableStateOf<List<ExerciceAvis>>(emptyList()) }
    var exerciceOuvert by rememberSaveable(prestataire.id) { mutableStateOf<Int?>(null) }
    var avisOuvert by rememberSaveable(prestataire.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(prestataire.id) {
        exercices = try {
            service.interventions(prestataire.id)
        } catch (_: ErreurService) {
            emptyList()
        }
        if (exerciceOuvert == null) exerciceOuvert = exercices.firstOrNull()?.annee
    }
    if (exercices.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Libelle(stringResource(R.string.prestataires_avis_du_conseil, FormatsDuSite.nombre(prestataire.nbNotations)))
        exercices.forEach { exercice ->
            val ouvert = exerciceOuvert == exercice.annee
            EnteteExercice(exercice, ouvert) { exerciceOuvert = if (ouvert) null else exercice.annee }
            Depliage(ouvert) {
                exercice.lignes.forEach { ligne ->
                    LigneAvis(ligne, avisOuvert) { id -> avisOuvert = if (avisOuvert == id) null else id }
                }
            }
        }
    }
}

@Composable
private fun EnteteExercice(exercice: ExerciceAvis, ouvert: Boolean, basculer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val angle by animateFloatAsState(if (ouvert) QUART_DE_TOUR else 0f, tween(DUREE_MS), label = "exercice")
    val action = stringResource(if (ouvert) R.string.prestataires_replier_exercice else R.string.prestataires_deplier_exercice)
    Column(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(ReperesAvis.exercice(exercice.annee))
            .semantics(mergeDescendants = true) { heading() }
            .cliquable(action, basculer),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.rotate(angle))
            // Le titre seul sur sa ligne, les nombres dessous (très grand texte), comme l'iOS.
            Column {
                Text(
                    stringResource(R.string.prestataires_exercice, exercice.libelle),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = couleurs.text,
                )
                Text(nombres(exercice), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
            }
        }
        FlowCapsules {
            exercice.comptes.forEach { (etat, nombre) ->
                val teinte = EtatAvis.teinte(etat)
                PastilleTeinte(stringResource(R.string.prestataires_compte_etat, FormatsDuSite.nombre(nombre), teinte.libelle), teinte.badge, compacte = true)
            }
        }
    }
}

/** « 5 interventions · 4 notées » : le pluriel du site (au-delà de 1). */
@Composable
private fun nombres(exercice: ExerciceAvis): String {
    val interventions = stringResource(
        if (exercice.nbInterventions > 1) R.string.prestataires_interventions else R.string.prestataires_interventions_une,
        FormatsDuSite.nombre(exercice.nbInterventions),
    )
    val notees = stringResource(
        if (exercice.nbNotees > 1) R.string.prestataires_notees else R.string.prestataires_notees_une,
        FormatsDuSite.nombre(exercice.nbNotees),
    )
    return "$interventions · $notees"
}

@Composable
private fun LigneAvis(ligne: LigneIntervention, avisOuvert: Int?, basculer: (Int) -> Unit) {
    val couleurs = LocalCouleurs.current
    val titre = ligne.titre ?: stringResource(R.string.prestataires_avis_sans_affaire)
    Column(Modifier.padding(start = RETRAIT).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            ligne.date?.let { "${FormatsDuSite.date(it)} · $titre" } ?: titre,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = if (ligne.generaux) FontStyle.Italic else FontStyle.Normal),
            color = couleurs.text,
        )
        FlowCapsules {
            if (ligne.avis.isEmpty()) {
                Text(
                    stringResource(R.string.prestataires_non_notee),
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = couleurs.textMuted,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            ligne.avis.forEach { avis -> BadgeAvis(avis, avisOuvert == avis.id) { basculer(avis.id) } }
        }
        ligne.avis.firstOrNull { it.id == avisOuvert }?.let { DetailAvis(it) }
    }
}

/** Le badge d'un avis et son chevron : il se lit dépliable avant qu'on le touche. */
@Composable
private fun BadgeAvis(avis: AvisPrestataire, ouvert: Boolean, basculer: () -> Unit) {
    val teinte = EtatAvis.teinte(avis.etat)
    val angle by animateFloatAsState(if (ouvert) DEMI_TOUR else 0f, tween(DUREE_MS), label = "avis")
    val lu = stringResource(R.string.prestataires_avis_badge, teinte.libelle)
    val action = stringResource(if (ouvert) R.string.prestataires_replier_avis else R.string.prestataires_deplier_avis)
    Row(
        Modifier.heightIn(min = Mise.cible).testTag(ReperesAvis.avis(avis.id))
            .semantics(mergeDescendants = true) { contentDescription = lu }
            .cliquable(action, basculer),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PastilleTeinte(teinte, compacte = true)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = LocalCouleurs.current.textMuted, modifier = Modifier.rotate(angle))
    }
}

@Composable
private fun DetailAvis(avis: AvisPrestataire) {
    val couleurs = LocalCouleurs.current
    Column(
        Modifier.fillMaxWidth().testTag(ReperesAvis.detail(avis.id))
            .background(couleurs.neutreFond, RoundedCornerShape(DETAIL_RAYON))
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${avis.auteurNom ?: FormatsDuSite.TIRET} · ${FormatsDuSite.date(avis.creeLe)}",
            style = MaterialTheme.typography.bodySmall,
            color = couleurs.textMuted,
        )
        avis.commentaire?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = couleurs.text) }
    }
}

private const val DUREE_MS = 200
private const val DEMI_TOUR = 180f
private const val QUART_DE_TOUR = 90f
private val RETRAIT = 18.dp
private val DETAIL_RAYON = 10.dp
