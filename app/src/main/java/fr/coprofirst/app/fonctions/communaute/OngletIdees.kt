// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.IdeeRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.lecture.EtiquetteLieu
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.lecture.LieuServi
import fr.coprofirst.app.commun.lecture.PastilleLecture
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.commun.texte.TexteRiche

/**
 * La boîte à idées, comme l'iOS (`OngletIdees`) : les plus soutenues d'abord, un filtre par
 * état, le cœur pour voter (une bascule, vue tout de suite ; le serveur confirme ou défait).
 * Un toucher ouvre l'idée et ses réponses ; « + » propose une idée.
 */
@Composable
fun OngletIdees(contexte: ContexteOnglet, service: ServiceCommunaute, moi: Int) {
    var nouvelle by rememberSaveable { mutableStateOf(false) }
    var relecture by rememberSaveable { mutableIntStateOf(0) }
    BoutonCreer(contexte.barre, R.string.idees_nouvelle) { nouvelle = true }
    Chargement(
        charger = { service.idees() },
        cle = contexte.actualisation to relecture,
        squelette = { LigneIdee(SQUELETTE, {}, {}) },
    ) { lues, recharger ->
        Idees(lues, contexte, service, moi, recharger)
    }
    if (nouvelle) {
        FormulaireIdee(contexte.avis, service, fermer = { nouvelle = false }) { relecture++ }
    }
}

@Composable
private fun Idees(lues: List<IdeeRead>, contexte: ContexteOnglet, service: ServiceCommunaute, moi: Int, recharger: () -> Unit) {
    val gestes = rememberGestes(contexte.avis)
    var affichees by remember(lues) { mutableStateOf(lues) }
    var filtre by rememberSaveable { mutableStateOf<String?>(null) }
    var ouverte by rememberSaveable { mutableStateOf<Int?>(null) }
    // Le vote dit sa phrase par l'avis de l'écran qui l'a touché (la liste, ou l'idée ouverte).
    val voter: (IdeeRead, GestesCommunaute) -> Unit = { idee, gestes ->
        val avant = affichees
        affichees = ReglesCommunaute.basculerVote(affichees, idee.id)
        gestes.agir(R.string.communaute_vote_enregistre, apres = { reussi -> if (reussi) recharger() else affichees = avant }) {
            service.voterIdee(idee.id)
        }
    }
    val retenues = ReglesCommunaute.idees(affichees, filtre)
    val textes = TextesListe(
        R.string.idees_vide, R.string.idees_vide_detail,
        R.string.idees_en_cours_vide, R.string.idees_en_cours_vide_detail, Icons.Outlined.Lightbulb,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        RangeeFiltres {
            val compte = retenues.count { it.archivee != true }
            Pastille(stringResource(R.string.idees_toutes), filtre == null, compte = compte) { filtre = null }
            TablesCommunaute.statutsIdee.forEach { (code, teinte) ->
                Pastille(teinte.libelle, filtre == code, compte = compte) { filtre = code }
            }
        }
        ListeCommunaute(retenues, { it.archivee == true }, textes, videAuServeur = affichees.isEmpty()) { idee ->
            LigneIdee(idee, voter = { voter(idee, gestes) }) { ouverte = idee.id }
        }
    }
    ouverte?.let { id ->
        DetailIdee(affichees.firstOrNull { it.id == id }, moi, service, voter, recharger) { ouverte = null }
    }
}

/** Une idée : le cœur et ses voix à gauche, le titre, l'état, l'aperçu, les réponses. */
@Composable
private fun LigneIdee(idee: IdeeRead, voter: () -> Unit, ouvrir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Carte(Modifier.testTag(ReperesCommunaute.idee(idee.id)), ouvrir = ouvrir, action = idee.titre) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BoutonCoeur(idee, voter)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(idee.titre, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
                FlowCapsules {
                    PastilleTeinte(ReglesCommunaute.teinte(idee.statut, TablesCommunaute.statutsIdee), compacte = true)
                    PastilleLecture(idee.lecture?.let(Lecture::depuis))
                    EtiquetteLieu(LieuServi.lieu(idee.perimetreLibelle))
                }
                Text(
                    TexteDuSite.apercu(idee.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = couleurs.textMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val reponses = idee.nbReponses ?: idee.reponses?.size ?: 0
                if (reponses > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = couleurs.textMuted)
                        Etiquette(pluralStringResource(R.plurals.communaute_reponses, reponses, reponses))
                    }
                }
            }
        }
    }
}

/** Le cœur : plein quand on soutient l'idée ; un toucher bascule, sans ouvrir l'idée. */
@Composable
private fun BoutonCoeur(idee: IdeeRead, voter: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val vote = idee.monVote == true
    val voix = idee.nbVotes ?: 0
    val action = stringResource(if (vote) R.string.idees_retirer_vote else R.string.idees_voter)
    val etat = pluralStringResource(R.plurals.idees_votes, voix, voix)
    Column(
        Modifier.defaultMinSize(Mise.cible, Mise.cible).testTag(ReperesCommunaute.voter(idee.id))
            .cliquable(action, voter)
            .semantics(mergeDescendants = true) { stateDescription = etat },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (vote) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = action,
            tint = if (vote) couleurs.danger else couleurs.textMuted,
        )
        Text(FormatsDuSite.nombre(voix), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = couleurs.text)
    }
}

/**
 * Une idée ouverte : sa description entière, le cœur, et le fil des réponses (répondre,
 * supprimer la sienne, signaler celles des autres). « Signaler cette idée au conseil
 * syndical » à qui ne l'a pas écrite.
 */
@Composable
private fun DetailIdee(
    idee: IdeeRead?,
    moi: Int,
    service: ServiceCommunaute,
    voter: (IdeeRead, GestesCommunaute) -> Unit,
    recharger: () -> Unit,
    fermer: () -> Unit,
) {
    val signaler = idee?.takeIf { it.auteurId != moi }?.let { FilCommunaute.Idee(it.id) to R.string.idees_signaler }
    DetailCommunaute(R.string.idees_titre_detail, fermer, service, signaler) { gestes ->
        if (idee == null) {
            Etiquette(stringResource(R.string.idees_introuvable))
            return@DetailCommunaute
        }
        val couleurs = LocalCouleurs.current
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BoutonCoeur(idee) { voter(idee, gestes) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PastilleTeinte(ReglesCommunaute.teinte(idee.statut, TablesCommunaute.statutsIdee))
                Text(idee.titre, style = Typo.titre(), color = couleurs.text, modifier = Modifier.semantics { heading() })
                Etiquette(FormatsDuSite.dateCourte(idee.creeLe.toInstant()))
            }
        }
        TexteRiche(idee.description)
        FilDuContenu(FilCommunaute.Idee(idee.id), idee.reponses.orEmpty(), moi, service, gestes, recharger)
    }
}

private val SQUELETTE = IdeeRead(
    id = 0,
    titre = "Une idée pour la résidence",
    description = "<p>Elle se charge.</p>",
    statut = "ouverte",
    creeLe = java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    perimetreLibelle = "",
)
