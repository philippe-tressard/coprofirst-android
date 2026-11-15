// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.ReponseCommunauteRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesCommunaute {
    const val CREER = "communaute.creer"
    const val ENREGISTRER = "formulaire.enregistrer"
    const val ARCHIVES = "communaute.archives"
    const val SONDAGES_CREATION = "sondages.creation"
    const val FERMER = "communaute.fermer"
    const val ANNONCE_CONTACT = "annonce.contact"
    const val ANNONCE_SUPPRIMER = "annonce.supprimer"
    const val ANNONCE_CONFIRMER = "annonce.confirmerSuppression"
    fun sondage(id: Int) = "sondage.$id"
    fun idee(id: Int) = "idee.$id"
    fun voter(id: Int) = "idee.$id.voter"
    fun annonce(id: Int) = "annonce.$id"
    fun avis(id: Int) = "avis.$id"
    fun gestesAvis(id: Int) = "avis.$id.gestes"
}

/** Les phrases d'une liste, celles du site (`TextesListe` de l'iOS). */
data class TextesListe(
    @param:StringRes val vide: Int,
    @param:StringRes val videDetail: Int,
    @param:StringRes val enCours: Int,
    @param:StringRes val enCoursDetail: Int,
    val icone: ImageVector,
)

/**
 * Une liste de la Communauté : les éléments en cours, puis les Archives repliées
 * (« 📁 Archives (n) »). Rien à montrer : la liste vide, dite — ou « plus rien en cours »
 * quand tout est archivé. Le tri et le filtre sont faits par l'appelant.
 */
@Composable
fun <T> ListeCommunaute(
    elements: List<T>,
    archivee: (T) -> Boolean,
    textes: TextesListe,
    /** Rien du tout chez le serveur (avant filtre) : la phrase du vide, pas celle des archives. */
    videAuServeur: Boolean,
    ligne: @Composable (T) -> Unit,
) {
    val enCours = elements.filterNot(archivee)
    val archives = elements.filter(archivee)
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        when {
            videAuServeur -> EtatVide(stringResource(textes.vide), icone = textes.icone, explication = stringResource(textes.videDetail))
            enCours.isEmpty() -> EtatVide(
                stringResource(textes.enCours),
                icone = Icons.Outlined.Archive,
                explication = stringResource(textes.enCoursDetail).takeIf { archives.isNotEmpty() },
            )
            else -> enCours.forEach { ligne(it) }
        }
        if (archives.isNotEmpty()) SectionArchives(archives, ligne)
    }
}

@Composable
private fun <T> SectionArchives(elements: List<T>, ligne: @Composable (T) -> Unit) {
    var ouverte by rememberSaveable { mutableStateOf(false) }
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(ReperesCommunaute.ARCHIVES)
                .cliquable(stringResource(R.string.communaute_archives, elements.size)) { ouverte = !ouverte },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.communaute_archives, elements.size),
                style = MaterialTheme.typography.titleSmall,
                color = couleurs.textMuted,
                modifier = Modifier.weight(1f),
            )
            Icon(if (ouverte) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = couleurs.textMuted)
        }
        AnimatedVisibility(ouverte) {
            Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) { elements.forEach { ligne(it) } }
        }
    }
}

/** Des pastilles de filtre qui défilent de côté, sous l'onglet. */
@Composable
fun RangeeFiltres(contenu: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        contenu()
    }
}

/** Les étoiles d'une note : « 4 étoiles sur 5 » pour le lecteur d'écran. */
@Composable
fun Etoiles(note: Int) {
    val couleurs = LocalCouleurs.current
    val dit = pluralStringResource(R.plurals.avis_etoiles, note, note)
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = dit }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..NOTE_AFFICHEE).forEach { rang ->
            Icon(
                if (rang <= note) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (rang <= note) couleurs.warning else couleurs.border,
            )
        }
    }
}

/** Le nombre d'étoiles montrées : celui du site. */
const val NOTE_AFFICHEE = 5

/**
 * L'intertitre d'une section, dit comme tel au lecteur d'écran. ⚠️ Provisoire : il passe au
 * libellé commun `Libelle` (#84, `.styleLibelle()` de l'iOS) dès sa fusion.
 */
@Composable
fun LibelleSection(@StringRes texte: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(texte),
        style = MaterialTheme.typography.titleSmall,
        color = LocalCouleurs.current.textMuted,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Le fil des réponses d'une idée ou d'une annonce, EN LECTURE : le conseil syndical en
 * tête et en bleu (le serveur trie), texte brut. ⚠️ Divergence déclarée : répondre,
 * supprimer sa réponse et signaler attendent le fil commun (`FilReponses`, #70) — l'iOS
 * les offre ; l'écran dit qu'ils se font sur le site d'ici là.
 */
@Composable
fun ReponsesEnLecture(reponses: List<ReponseCommunauteRead>) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reponses.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.communaute_reponses, reponses.size, reponses.size),
                style = MaterialTheme.typography.titleSmall,
                color = couleurs.text,
            )
        }
        reponses.forEach { reponse ->
            Carte(Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    listOfNotNull(reponse.auteurNom, reponse.auteurBatiment).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (reponse.estCs == true) couleurs.info else couleurs.text,
                )
                Text(FormatsDuSite.dateHeureCourte(reponse.creeLe.toInstant()), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
                Text(reponse.contenu, style = MaterialTheme.typography.bodyMedium, color = couleurs.text, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Text(stringResource(R.string.communaute_reponses_sur_le_site), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
    }
}
