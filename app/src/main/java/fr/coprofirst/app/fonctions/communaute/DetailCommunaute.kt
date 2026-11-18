// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.ui.platform.LocalResources
import fr.coprofirst.app.commun.masquages.AuteurAMasquer
import fr.coprofirst.app.commun.masquages.ConfirmationMasquage
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.ReponseCommunauteRead
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.commun.FeuillePleine
import fr.coprofirst.app.commun.SignalementAuConseil
import fr.coprofirst.app.commun.reponses.FilReponses
import fr.coprofirst.app.commun.reponses.GestesReponses

/**
 * Un contenu ouvert (une idée, une annonce), dans la feuille pleine commune : son titre, ses
 * `actions`, « Signaler … au conseil syndical » quand `signaler` le permet, « OK » ; son
 * propre avis éphémère — une phrase dite derrière la feuille ne se verrait pas. Le contenu
 * reçoit les gestes de CET avis.
 *
 * ⚠️ Divergence déclarée : l'iOS le pousse dans la pile de navigation ; l'Android n'a pas de
 * pile par onglet — la feuille se ferme par « OK », d'un geste vers le bas ou par le retour.
 */
@Composable
fun DetailCommunaute(
    @StringRes titre: Int,
    fermer: () -> Unit,
    service: ServiceCommunaute,
    /** Le contenu à signaler, et le libellé du geste ; `null` : on ne signale pas le sien. */
    signaler: Pair<FilCommunaute, Int>?,
    actions: @Composable RowScope.() -> Unit = {},
    contenu: @Composable ColumnScope.(GestesCommunaute) -> Unit,
) {
    val avis = remember { AvisDuGeste() }
    val gestes = rememberGestes(avis)
    var signale by remember { mutableStateOf<CibleSignalement?>(null) }
    FeuillePleine(stringResource(titre), ReperesCommunaute.FERMER, fermer, actions = {
        actions()
        signaler?.let { (fil, libelle) ->
            IconButton(onClick = { signale = fil.signalement }, modifier = Modifier.testTag(ReperesCommunaute.SIGNALER)) {
                Icon(Icons.Outlined.Flag, contentDescription = stringResource(libelle))
            }
        }
    }) {
        AvisEphemere(avis, Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { contenu(gestes) }
        }
    }
    SignalementAuConseil(signale, envoyer = { cible, motif -> signalerAuConseil(gestes, service, cible, motif) }, fermer = { signale = null })
}

/**
 * Le fil des réponses d'un contenu (`FilReponses` commun) : publier et retirer passent par
 * la route du contenu, puis la liste se relit ; signaler une réponse ouvre la boîte commune.
 */
@Composable
fun FilDuContenu(
    fil: FilCommunaute,
    reponses: List<ReponseCommunauteRead>,
    moi: Int?,
    service: ServiceCommunaute,
    gestes: GestesCommunaute,
    recharger: () -> Unit,
    invite: String? = null,
) {
    var signalee by remember { mutableStateOf<ReponseCommunauteRead?>(null) }
    var aMasquer by remember { mutableStateOf<AuteurAMasquer?>(null) }
    val ressources = LocalResources.current
    val fils = GestesReponses(
        publier = { texte ->
            gestes.tenter(R.string.communaute_reponse_publiee) { service.repondre(fil, texte); null }.also { if (it) recharger() }
        },
        retirer = { reponse ->
            if (gestes.tenter(R.string.communaute_reponse_supprimee) { service.supprimerReponse(fil, reponse.id); null }) recharger()
        },
        signaler = { signalee = it },
        masquer = if (service.moderation) ({ aMasquer = it }) else null,
    )
    FilReponses(reponses, moi, fils, invite = invite)
    // Le serveur ne sert plus ses réponses : la liste se relit (#282).
    ConfirmationMasquage(
        aMasquer,
        masquer = { auteur ->
            gestes.agir(R.string.masquage_confirmer, apres = { if (it) recharger() }) {
                service.masquer(auteur)
                ressources.getString(R.string.masquage_masque, auteur.nom)
            }
        },
        fermer = { aMasquer = null },
    )
    SignalementAuConseil(
        signalee,
        envoyer = { reponse, motif -> signalerAuConseil(gestes, service, CibleSignalement.commentaire(reponse.id), motif) },
        fermer = { signalee = null },
    )
}

/** « Signaler au conseil syndical » : rien ne change à l'écran, une phrase le confirme. */
private fun signalerAuConseil(gestes: GestesCommunaute, service: ServiceCommunaute, cible: CibleSignalement, motif: String) {
    gestes.agir(R.string.signalement_transmis) {
        service.signalerAuConseil(cible, motif)
        null
    }
}
