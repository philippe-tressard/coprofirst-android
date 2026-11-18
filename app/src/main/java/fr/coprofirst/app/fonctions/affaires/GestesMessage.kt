// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.commun.texte.TexteDuSite

/** Le texte du message à RÉÉCRIRE, s'il se réenregistre sans rien perdre (`TexteDuSite.saisi`) ; sinon `null`. */
val MessageAffaire.contenuSaisi: String? get() = TexteDuSite.saisi(contenu)

/**
 * Les gestes de SON message (#281), la règle du site et de la route
 * (`_message_de_l_auteur` de coprofirst) : l'auteur (`est_auteur` servi), sur une affaire
 * qui n'est pas close (`close` servi) — le serveur refuse le reste (403, 409). « Modifier »
 * seulement si l'app réécrit le texte sans rien perdre ; sinon « Supprimer » seul, et la mise
 * en forme se modifie depuis le site.
 */
fun MessageAffaire.gestesOfferts(affaire: DetailAffaire): List<GesteSuite> = when {
    !deMoi || affaire.close -> emptyList()
    contenuSaisi == null -> listOf(GesteSuite.Supprimer)
    else -> GesteSuite.entries
}

/** Le message qu'un geste vise, tenu par la fiche : seule elle présente la feuille et la question. */
@Stable
internal class ChoixDeMessage {
    var aCorriger by mutableStateOf<MessageAffaire?>(null)
    var aSupprimer by mutableStateOf<MessageAffaire?>(null)

    fun choisir(geste: GesteSuite, message: MessageAffaire) {
        if (geste == GesteSuite.Modifier) aCorriger = message else aSupprimer = message
    }
}

/** Le « ⋯ » de SON message, sous le bouton commun (`BoutonDesGestes`) ; rien sans geste. */
@Composable
internal fun GestesDuMessage(message: MessageAffaire, affaire: DetailAffaire, choisir: (GesteSuite, MessageAffaire) -> Unit) {
    val ressources = LocalResources.current
    BoutonDesGestes(
        message.gestesOfferts(affaire), ReperesAffaires.gestesMessage(message.id), ReperesAffaires::gesteMessage,
        stringResource(R.string.message_gestes),
        { ressources.getString(if (it == GesteSuite.Modifier) R.string.message_modifier else R.string.message_supprimer) },
    ) { choisir(it, message) }
}

/**
 * La présentation des gestes d'un message, comme l'iOS (`GestesDesMessages`) : « Modifier »
 * ouvre la feuille de « Répondre » partie du texte servi ; « Supprimer » pose la question du
 * site. Chacun s'annonce (« Message modifié », « Message supprimé »), puis l'affaire se relit.
 */
@Composable
internal fun GestesDesMessages(affaire: DetailAffaire, choix: ChoixDeMessage, service: ServiceAffaires, avis: AvisDuGeste, recharger: () -> Unit) {
    val ressources = LocalResources.current
    val annonce = rememberGesteAnnonce(avis, recharger)

    choix.aCorriger?.let { message ->
        FeuilleRepondre(
            affaire, service,
            fermer = {
                choix.aCorriger = null
                avis.feuilleFermee()
            },
            corrige = message,
        ) {
            avis.direApresFermeture(ressources.getString(R.string.message_corrige))
            recharger()
        }
    }

    choix.aSupprimer?.let { message ->
        AlertDialog(
            onDismissRequest = { choix.aSupprimer = null },
            title = { Text(stringResource(R.string.message_question)) },
            confirmButton = {
                val geste = gesteEnLigne()
                TextButton(
                    onClick = {
                        choix.aSupprimer = null
                        annonce.lancer(ressources.getString(R.string.message_supprime)) { service.supprimerMessage(affaire.id, message.id) }
                    },
                    enabled = geste.actif,
                    modifier = geste.modifier.testTag(ReperesAffaires.CONFIRMER_SUPPRESSION_MESSAGE),
                ) { Text(stringResource(R.string.message_confirmer), color = LocalCouleurs.current.danger) }
            },
            dismissButton = { TextButton(onClick = { choix.aSupprimer = null }) { Text(stringResource(R.string.annuler)) } },
        )
    }
}
