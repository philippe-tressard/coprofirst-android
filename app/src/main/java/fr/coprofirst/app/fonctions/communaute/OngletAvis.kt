// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.AvisListe
import fr.coprofirst.api.modeles.AvisRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.ConstantesDuServeur
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.texte.TexteRiche

/** Ce que la feuille de l'avis ouvre : un nouvel avis, ou la correction du sien. */
private sealed interface FeuilleAvis {
    data object Nouvel : FeuilleAvis
    data class Correction(val avis: AvisRead) : FeuilleAvis
}

/**
 * Les avis sur l'APPLICATION (pas sur la résidence), comme l'iOS (`OngletAvis`) : un filtre
 * par note, chaque avis déplié, la réponse de l'équipe. L'auteur corrige ou archive le sien ;
 * archiver aussi pour qui gère le site (`gestionnaire`, servi) ; restaurer quand le serveur
 * le permet (`restaurable`). « + » seulement à qui n'a pas d'avis (`mon_avis_id`, servi).
 * Répondre et retenir pour coprofirst.fr se fait sur le site.
 *
 * ⚠️ Divergence déclarée : les gestes d'un avis sont dans son menu « ⋮ » — l'iOS les offre
 * au balayage et à l'appui long.
 */
@Composable
fun OngletAvis(contexte: ContexteOnglet, service: ServiceCommunaute) {
    var feuille by remember { mutableStateOf<FeuilleAvis?>(null) }
    var relecture by rememberSaveable { mutableIntStateOf(0) }
    Chargement(
        charger = { service.avis() },
        cle = contexte.actualisation to relecture,
        squelette = { CarteAvis(SQUELETTE, null) },
    ) { liste, recharger ->
        if (liste.monAvisId == null) BoutonCreer(contexte.barre, R.string.avis_donner) { feuille = FeuilleAvis.Nouvel }
        Avis(liste, contexte, service, recharger) { feuille = FeuilleAvis.Correction(it) }
    }
    feuille?.let { ouverte ->
        FormulaireAvis(contexte.avis, service, (ouverte as? FeuilleAvis.Correction)?.avis, fermer = { feuille = null }) { relecture++ }
    }
}

@Composable
private fun Avis(liste: AvisListe, contexte: ContexteOnglet, service: ServiceCommunaute, recharger: () -> Unit, corriger: (AvisRead) -> Unit) {
    val couleurs = LocalCouleurs.current
    val gestes = rememberGestes(contexte.avis)
    var note by rememberSaveable { mutableStateOf<Int?>(null) }
    val retenus = liste.avis.filter { note == null || it.note == note }
    val textes = TextesListe(
        R.string.avis_vide, R.string.avis_vide_detail,
        R.string.avis_en_cours_vide, R.string.avis_en_cours_vide_detail, Icons.Outlined.StarOutline,
    )
    val archiver: (AvisRead, Boolean) -> Unit = { avis, archive ->
        gestes.agir(if (archive) R.string.avis_archive else R.string.avis_restaure, apres = { if (it) recharger() }) {
            service.archiverAvis(avis.id, archive)
            null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        Text(
            stringResource(R.string.avis_pas_un_bogue),
            style = MaterialTheme.typography.bodySmall,
            color = couleurs.textMuted,
            modifier = Modifier.fillMaxWidth().background(couleurs.infoFond, RoundedCornerShape(Mise.rayon)).padding(12.dp),
        )
        RangeeFiltres {
            Pastille(stringResource(R.string.avis_toutes), note == null) { note = null }
            (ConstantesDuServeur.NOTE_MAX downTo ConstantesDuServeur.NOTE_MIN).forEach { valeur ->
                Pastille(stringResource(R.string.avis_filtre_note, valeur), note == valeur) { note = valeur }
            }
        }
        ListeCommunaute(retenus, { it.archivee == true }, textes, videAuServeur = liste.avis.isEmpty()) { avis ->
            CarteAvis(avis, GestesAvis(avis, liste.gestionnaire, corriger = { corriger(avis) }, archiver = { archiver(avis, it) }))
        }
        if (liste.gestionnaire) Etiquette(stringResource(R.string.avis_reponse_sur_le_site))
    }
}

/** Les gestes qu'un avis offre à CE lecteur — archiver, jamais supprimer. */
private class GestesAvis(avis: AvisRead, gestionnaire: Boolean, val corriger: () -> Unit, val archiver: (Boolean) -> Unit) {
    private val archive = avis.archivee == true
    val peutCorriger = avis.estAuteur == true && !archive
    val peutArchiver = !archive && (avis.estAuteur == true || gestionnaire)
    val peutRestaurer = archive && avis.restaurable == true
    val aucun get() = !peutCorriger && !peutArchiver && !peutRestaurer
}

/** Un avis : ses étoiles, le titre, à qui il se lit, l'auteur, le texte, ce qui a manqué, la réponse. */
@Composable
private fun CarteAvis(avis: AvisRead, gestes: GestesAvis?) {
    val couleurs = LocalCouleurs.current
    Carte(Modifier.testTag(ReperesCommunaute.avis(avis.id))) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Box(Modifier.weight(1f)) { Etoiles(avis.note) }
                if (gestes != null && !gestes.aucun) MenuAvis(avis.id, gestes)
            }
            Text(avis.titre, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
            FlowCapsules {
                if (avis.luDeTous != true) PastilleTeinte(stringResource(R.string.avis_auteur_et_conseil), "badge-gray", compacte = true)
                if (avis.publicationValidee == true) PastilleTeinte(stringResource(R.string.avis_retenu), "badge-green", compacte = true)
                listOfNotNull(avis.auteurNom?.takeIf { it.isNotBlank() }, avis.auteurRole?.takeIf { it.isNotBlank() })
                    .joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Etiquette(it) }
                Etiquette(FormatsDuSite.dateCourte(avis.creeLe.toInstant()))
            }
            TexteRiche(avis.description, style = MaterialTheme.typography.bodyMedium)
            avis.remarque?.takeIf { it.isNotBlank() }?.let { remarque ->
                val libelle = stringResource(R.string.avis_ce_qui_a_manque)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(libelle) }
                        append(remarque)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = couleurs.orangeTexte,
                    modifier = Modifier.fillMaxWidth().background(couleurs.orangeFond, RoundedCornerShape(10.dp)).padding(10.dp),
                )
            }
            avis.reponse?.takeIf { it.isNotBlank() }?.let { reponse ->
                Column(
                    Modifier.fillMaxWidth().background(couleurs.infoFond, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val le = avis.reponseLe?.let { FormatsDuSite.dateCourte(it.toInstant()) }
                    Text(
                        if (le != null) stringResource(R.string.avis_reponse_equipe_le, le) else stringResource(R.string.avis_reponse_equipe),
                        style = MaterialTheme.typography.labelLarge,
                        color = couleurs.textMuted,
                    )
                    Text(reponse, style = MaterialTheme.typography.bodyMedium, color = couleurs.text)
                }
            }
        }
    }
}

@Composable
private fun MenuAvis(id: Int, gestes: GestesAvis) {
    var deplie by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { deplie = true }, modifier = Modifier.testTag(ReperesCommunaute.gestesAvis(id))) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.communaute_plus_actions))
        }
        DropdownMenu(expanded = deplie, onDismissRequest = { deplie = false }) {
            if (gestes.peutCorriger) Geste(R.string.avis_modifier, Icons.Outlined.Edit) { deplie = false; gestes.corriger() }
            if (gestes.peutArchiver) Geste(R.string.avis_archiver, Icons.Outlined.Archive) { deplie = false; gestes.archiver(true) }
            if (gestes.peutRestaurer) Geste(R.string.avis_restaurer, Icons.AutoMirrored.Outlined.Undo) { deplie = false; gestes.archiver(false) }
        }
    }
}

@Composable
private fun Geste(libelle: Int, icone: androidx.compose.ui.graphics.vector.ImageVector, agir: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(libelle)) }, leadingIcon = { Icon(icone, contentDescription = null) }, onClick = agir)
}

private val SQUELETTE = AvisRead(
    id = 0,
    note = 4,
    titre = "Un avis sur l'application",
    description = "<p>Il se charge.</p>",
    creeLe = java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"),
)
