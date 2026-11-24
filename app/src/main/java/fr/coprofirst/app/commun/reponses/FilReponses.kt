// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.reponses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.ReponseCommunauteRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.rememberEnvoi
import kotlinx.coroutines.launch

/** « 💬 Répondre », « 💬 1 réponse », « 💬 3 réponses » — le bouton du site, l'en-tête par défaut. */
@Composable
fun enteteReponses(nombre: Int): String =
    if (nombre == 0) stringResource(R.string.reponses_repondre_entete) else pluralStringResource(R.plurals.reponses_nombre, nombre, nombre)

/**
 * Le fil des réponses — d'une idée, d'une annonce, ou les commentaires d'un sondage —, comme
 * l'iOS (`FilReponses`, `Reponses.svelte` du site) : l'en-tête, chaque réponse (le conseil
 * syndical en tête et en bleu : le serveur trie), puis le champ pour répondre. Texte brut :
 * une réponse n'est jamais du HTML.
 *
 * Les droits sont SERVIS : « Supprimer » suit `peut_supprimer` et se CONFIRME ici (« Supprimer
 * définitivement », repère `reponses.confirmerSuppression`) avant `gestes.retirer` ;
 * « Signaler » s'offre sur les réponses des autres (`moi` : l'identifiant du compte, GET
 * /auth/me) et appelle `gestes.signaler`. Une `Column` : elle se pose dans la page qui défile.
 *
 * ⚠️ Divergence déclarée : les gestes d'une réponse sont sous son bouton « ⋮ » (repère
 * `reponse.<id>.actions`) ; l'iOS les offre au balayage et au menu contextuel — Compose n'a
 * pas d'actions de balayage dans une liste.
 */
@Composable
fun FilReponses(
    reponses: List<ReponseCommunauteRead>,
    moi: Int?,
    gestes: GestesReponses,
    modifier: Modifier = Modifier,
    invite: String? = null,
    entete: (@Composable (Int) -> String)? = null,
) {
    val couleurs = LocalCouleurs.current
    var aRetirer by remember { mutableStateOf<ReponseCommunauteRead?>(null) }
    val portee = rememberCoroutineScope()
    val titre = if (entete != null) entete(reponses.size) else enteteReponses(reponses.size)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Libelle(titre, Modifier.padding(top = 8.dp).semantics { heading() }.testTag(ReperesReponses.ENTETE))
        reponses.forEach { reponse ->
            LigneReponse(
                reponse,
                supprimer = if (Reponses.peutSupprimer(reponse)) ({ aRetirer = reponse }) else null,
                signaler = if (Reponses.peutSignaler(reponse, moi)) ({ gestes.signaler(reponse) }) else null,
                masquer = gestes.masquer?.let { masquer -> Reponses.aMasquer(reponse, moi)?.let { auteur -> { masquer(auteur) } } },
            )
        }
        SaisieReponse(invite ?: stringResource(R.string.reponses_invite), gestes.publier)
    }
    aRetirer?.let { reponse ->
        AlertDialog(
            onDismissRequest = { aRetirer = null },
            title = { Text(stringResource(R.string.reponses_confirmer_titre)) },
            text = { Text(stringResource(R.string.reponses_confirmer_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        aRetirer = null
                        portee.launch { gestes.retirer(reponse) }
                    },
                    modifier = Modifier.testTag(ReperesReponses.CONFIRMER_SUPPRESSION),
                ) { Text(stringResource(R.string.reponses_supprimer), color = couleurs.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { aRetirer = null }) { Text(stringResource(R.string.annuler)) } },
        )
    }
}

/**
 * Le champ pour répondre, et l'envoi : la flèche attend un texte, tourne pendant l'envoi ;
 * réussi, le champ se vide et le clavier se ferme ; échoué, le texte reste.
 */
@Composable
private fun SaisieReponse(invite: String, publier: suspend (String) -> Boolean) {
    val couleurs = LocalCouleurs.current
    val focus = LocalFocusManager.current
    var texte by rememberSaveable { mutableStateOf("") }
    val envoi = rememberEnvoi()
    val forme = RoundedCornerShape(Mise.rayon)
    Row(
        Modifier.fillMaxWidth().background(couleurs.surface, forme).border(1.dp, couleurs.border, forme).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = texte,
            onValueChange = { texte = it },
            placeholder = { Text(invite) },
            maxLines = 6,
            shape = forme,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = couleurs.surface,
                unfocusedContainerColor = couleurs.surface,
                unfocusedBorderColor = couleurs.borderChamp,
            ),
            modifier = Modifier.weight(1f).testTag(ReperesReponses.SAISIE),
        )
        IconButton(
            onClick = {
                val contenu = Reponses.aPublier(texte) ?: return@IconButton
                // `publier` dit lui-même l'échec : l'envoi n'en garde rien.
                envoi.lancer {
                    if (publier(contenu)) {
                        texte = ""
                        focus.clearFocus()
                    }
                }
            },
            enabled = Reponses.aPublier(texte) != null && !envoi.enCours,
            modifier = Modifier.size(Mise.cible).testTag(ReperesReponses.ENVOYER),
        ) {
            if (envoi.enCours) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.ArrowCircleUp, contentDescription = stringResource(R.string.reponses_repondre), Modifier.size(30.dp))
            }
        }
    }
}
