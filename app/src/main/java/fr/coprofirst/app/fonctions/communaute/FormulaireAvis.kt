// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.AvisRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.ConstantesDuServeur
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.texte.TexteDuSite
import kotlin.math.roundToInt

/**
 * « Donner mon avis » — ou « Modifier l'avis » quand le compte en a déjà un (`existant`).
 * Sous le seuil, ce qui a manqué ; à partir de l'autre, l'accord de diffusion (sans réponse
 * par défaut, comme sur le site). Les seuils sont ceux du serveur (`ConstantesDuServeur`).
 */
@Composable
fun FormulaireAvis(avis: AvisDuGeste, service: ServiceCommunaute, existant: AvisRead?, fermer: () -> Unit, fait: () -> Unit) {
    val ressources = LocalResources.current
    var saisie by remember(existant) { mutableStateOf(existant?.let(::preremplir) ?: SaisieAvis()) }
    val titre = if (existant == null) R.string.avis_donner else R.string.avis_modifier_titre
    FeuilleSaisie(titre, avis, complete = saisie.complete, fermer = fermer, envoyer = {
        val merci = when {
            existant != null -> R.string.avis_mis_a_jour
            saisie.consentementEnvoye == true -> R.string.avis_merci_temoignage
            else -> R.string.avis_merci
        }
        if (existant != null) service.corrigerAvis(existant.id, saisie) else service.deposerAvis(saisie)
        fait()
        ressources.getString(merci)
    }) {
        ChampCommunaute(R.string.communaute_titre, saisie.titre, { saisie = saisie.copy(titre = it) }, R.string.avis_titre_indication)
        Libelle(stringResource(R.string.avis_note), Modifier.semantics { heading() })
        ChoixEtoiles(saisie.note) { saisie = saisie.copy(note = it) }
        Etiquette(stringResource(R.string.avis_note_explication, ConstantesDuServeur.NOTE_PUBLIABLE))
        if (ReglesCommunaute.demandeRemarque(saisie.note)) {
            ChampCommunaute(
                R.string.avis_ce_qui_vous_a_manque, saisie.remarque, { saisie = saisie.copy(remarque = it) },
                R.string.avis_remarque_indication, lignes = 3,
            )
        }
        ChampCommunaute(
            R.string.communaute_description, saisie.description, { saisie = saisie.copy(description = it) },
            R.string.avis_description_indication, lignes = 4,
        )
        if (ReglesCommunaute.demandeConsentement(saisie.note)) {
            Consentement(saisie.consentement) { saisie = saisie.copy(consentement = it) }
        }
    }
}

/** Corriger part du texte servi : le HTML redevient du texte à éditer. */
private fun preremplir(avis: AvisRead) = SaisieAvis(
    note = avis.note,
    titre = avis.titre,
    description = TexteDuSite.saisi(avis.description) ?: TexteDuSite.apercu(avis.description),
    remarque = avis.remarque.orEmpty(),
    consentement = avis.consentementPublication,
)

/** Cinq étoiles à toucher ; le lecteur d'écran les règle comme un curseur. */
@Composable
private fun ChoixEtoiles(note: Int, choisir: (Int) -> Unit) {
    val couleurs = LocalCouleurs.current
    val libelle = stringResource(R.string.avis_note)
    val valeur = if (note == 0) stringResource(R.string.avis_note_aucune) else stringResource(R.string.avis_note_valeur, note)
    val bornes = ConstantesDuServeur.NOTE_MIN..ConstantesDuServeur.NOTE_MAX
    Row(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = libelle
            stateDescription = valeur
            progressBarRangeInfo = ProgressBarRangeInfo(note.toFloat(), 0f..bornes.last.toFloat(), bornes.last - 1)
            setProgress { cible -> choisir(cible.roundToInt().coerceIn(bornes)); true }
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        bornes.forEach { rang ->
            Icon(
                if (rang <= note) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (rang <= note) couleurs.warning else couleurs.textMuted,
                modifier = Modifier.size(Mise.cible).selectable(rang == note, role = Role.RadioButton) { choisir(rang) }.padding(8.dp),
            )
        }
    }
}

/** « Acceptez-vous que cet avis soit lu de tous et publié sur coprofirst.fr ? » — Oui, Non. */
@Composable
private fun Consentement(choix: Boolean?, choisir: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Libelle(stringResource(R.string.avis_diffusion), Modifier.semantics { heading() })
        Text(stringResource(R.string.avis_diffusion_question), style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.text)
        Column(Modifier.selectableGroup()) {
            listOf(true to R.string.avis_oui, false to R.string.avis_non).forEach { (valeur, libelle) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Mise.cible).selectable(choix == valeur, role = Role.RadioButton) { choisir(valeur) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choix == valeur, onClick = null)
                    Text(stringResource(libelle), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
