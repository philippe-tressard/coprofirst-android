// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import fr.coprofirst.app.commun.masquages.AuteurAMasquer
import fr.coprofirst.app.commun.masquages.ConfirmationMasquage
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.ReponseCommunauteRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.charte.largeurDeLecture
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.SignalementAuConseil
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.commun.rememberEnvoi
import fr.coprofirst.app.commun.reponses.FilReponses
import fr.coprofirst.app.commun.reponses.GestesReponses
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.commun.texte.TexteRiche
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.launch

/** Les repères de la fiche, ceux de l'iOS (`sondage.*`). */
object ReperesSondage {
    const val QUESTION = "sondage.question"
    const val TOTAL = "sondage.total"
    const val PRECISION = "sondage.precision"
    const val COMMENTAIRE = "sondage.commentaire"
    const val CHARTE = "sondage.charte"
    const val VOTER = "sondage.voter"
    const val MANQUE = "sondage.manque"
    const val RESULTATS_MASQUES = "sondage.resultatsMasques"
    const val RESULTATS_PROVISOIRES = "sondage.resultatsProvisoires"

    fun option(id: Int) = "sondage.option.$id"
    fun resultat(id: Int) = "sondage.resultat.$id"
}

/**
 * La fiche d'un sondage, comme celle du site (`sondages/[id]/+page.svelte`) et de l'iOS
 * (`SondageView`) : l'en-tête, puis le vote s'il est ouvert (réponse, précision, commentaire,
 * charte) ou les résultats quand le serveur les rend visibles (`resultats_visibles`, sa
 * décision), puis le fil des commentaires (`FilReponses`) — publier, supprimer (confirmé),
 * signaler au conseil syndical. Chaque geste dit sa phrase par `avis` (celle du serveur s'il
 * refuse), puis la fiche se relit : elle montre ce que le serveur a retenu.
 *
 * Elle défile seule : l'écran qui l'ouvre la pose dans une page (`PageSondage`, ouverte par
 * la porte) ou une feuille — avec un `AvisEphemere(avis)` autour.
 */
@Composable
fun FicheSondage(
    id: Int,
    session: SessionOuverte,
    avis: AvisDuGeste,
    modifier: Modifier = Modifier,
    service: ServiceSondage = remember(session) { ServiceSondageApi(session) },
) {
    Chargement(charger = { service.sondage(id) }, modifier, cle = id, squelette = { ContenuSondage(squelette(), null, avis) {} }) { sondage, recharger ->
        ContenuSondage(sondage, service, avis, recharger)
    }
}

/** La fiche factice pendant la première lecture, à la forme d'un sondage ouvert. */
@Composable
private fun squelette() = DetailSondage(
    id = 0, question = stringResource(R.string.sondage_squelette_question),
    description = stringResource(R.string.sondage_squelette_description),
    options = (1..3).map { OptionSondage(it, stringResource(R.string.sondage_squelette_option), false, null, null, emptyList()) },
    monVote = null, cloture = false, clotureLe = null, resultatsVisibles = false, commentaires = emptyList(), moi = null,
)

/** `service` absent : le squelette, sans geste. */
@Composable
private fun ContenuSondage(sondage: DetailSondage, service: ServiceSondage?, avis: AvisDuGeste, recharger: () -> Unit) {
    val ressources = LocalResources.current
    val portee = rememberCoroutineScope()
    var vote by remember(sondage.id) { mutableStateOf(VoteSondage()) }
    val envoi = rememberEnvoi()
    var signale by remember { mutableStateOf<ReponseCommunauteRead?>(null) }
    var aMasquer by remember { mutableStateOf<AuteurAMasquer?>(null) }

    /**
     * Un geste : la phrase du site s'il réussit (puis la fiche se relit), celle du serveur sinon.
     * `dire` : une phrase déjà composée (le nom de qui on masque) plutôt que `reussite`.
     */
    suspend fun geste(reussite: Int, relire: Boolean = true, dire: String? = null, action: suspend (ServiceSondage) -> Unit): Boolean {
        service ?: return false
        return try {
            action(service)
            avis.dire(dire ?: ressources.getString(reussite))
            if (relire) recharger()
            true
        } catch (e: ErreurService) {
            avis.dire(e.phrase(ressources), TonAvis.Erreur)
            false
        }
    }

    val gestes = GestesReponses(
        publier = { texte -> geste(R.string.sondage_commentaire_publie) { it.commenter(texte, sondage.id) } },
        retirer = { reponse -> geste(R.string.sondage_commentaire_supprime) { it.supprimerCommentaire(reponse.id, sondage.id) } },
        signaler = { signale = it },
        // « Masquer ce que publie… » l'auteur d'un commentaire (#297), comme l'iOS.
        masquer = if (service?.moderation == true) ({ aMasquer = it }) else null,
    )
    // La largeur de lecture sur une tablette (#69) ; la page entière défile, ses marges comprises.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.largeurDeLecture().fillMaxWidth().padding(horizontal = Mise.marge).padding(bottom = Mise.marge),
            verticalArrangement = Arrangement.spacedBy(Mise.ecart),
        ) {
            Carte { EnteteSondage(sondage) }
            // Visibles et le vote encore à faire : les résultats (provisoires) passent au-dessus
            // du formulaire (5Hostachy #2803, coprofirst-ios#257).
            if (sondage.resultatsAvantVote) ResultatsSondage(sondage)
            AnimatedContent(targetState = sondage.peutVoter, label = "vote") { ouvert ->
                if (ouvert) {
                    FormulaireVote(sondage, vote, { vote = it }, envoi.enCours) {
                        val envoye = vote
                        // `geste` dit lui-même l'échec par l'avis : l'envoi n'en garde rien.
                        envoi.lancer {
                            if (geste(R.string.sondage_vote_enregistre) { it.voter(envoye, sondage.id) }) vote = VoteSondage()
                        }
                    }
                } else {
                    ResultatsSondage(sondage)
                }
            }
            FilReponses(
                sondage.commentaires,
                sondage.moi,
                gestes,
                invite = stringResource(R.string.sondage_commentaire_invite_fil),
                entete = { stringResource(R.string.sondage_commentaires, it) },
            )
        }
    }
    SignalementAuConseil(signale, envoyer = { reponse, motif ->
        portee.launch {
            geste(R.string.signalement_transmis, relire = false) { it.signalerAuConseil(CibleSignalement.commentaire(reponse.id), motif) }
        }
    }, fermer = { signale = null })
    // Le serveur ne sert plus ses commentaires : la fiche se relit.
    ConfirmationMasquage(aMasquer, masquer = { auteur ->
        portee.launch {
            geste(R.string.masquage_confirmer, dire = ressources.getString(R.string.masquage_masque, auteur.nom)) { it.masquer(auteur) }
        }
    }, fermer = { aMasquer = null })
}

/** L'état, la clôture, la question, la description et le nombre de votes. */
@Composable
private fun EnteteSondage(sondage: DetailSondage) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowCapsules {
            PastilleTeinte(
                stringResource(if (sondage.cloture) R.string.sondage_cloture else R.string.sondage_ouvert),
                if (sondage.cloture) "badge-gray" else "badge-green",
            )
            sondage.clotureLe?.let { fin ->
                Text(
                    stringResource(
                        if (sondage.cloture) R.string.sondage_cloture_le else R.string.sondage_cloture_prevue,
                        FormatsDuSite.dateCourte(fin),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = couleurs.textMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Text(
            sondage.question,
            style = Typo.titre(),
            color = couleurs.text,
            modifier = Modifier.semantics { heading() }.testTag(ReperesSondage.QUESTION),
        )
        sondage.description?.takeIf { TexteDuSite.blocs(it).isNotEmpty() }?.let {
            TexteRiche(it, style = MaterialTheme.typography.bodyLarge, couleur = couleurs.textMuted)
        }
        if (sondage.totalVotes > 0) {
            Text(
                pluralStringResource(R.plurals.sondage_votes, sondage.totalVotes, FormatsDuSite.nombre(sondage.totalVotes)),
                style = MaterialTheme.typography.bodyMedium,
                color = couleurs.textMuted,
                modifier = Modifier.testTag(ReperesSondage.TOTAL),
            )
        }
    }
}
