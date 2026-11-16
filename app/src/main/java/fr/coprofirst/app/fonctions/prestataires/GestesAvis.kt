// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.GRANDE_ECHELLE
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.commun.texte.Separateur
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « ⭐ Noter » (`GesteNoter.svelte`), sur la fiche d'un prestataire et sur la carte d'un
 * contrat, et la feuille qu'il ouvre. Réservé au conseil syndical (`est_moderateur` servi) :
 * l'écran ne le pose que pour lui.
 */
@Composable
internal fun BoutonNoter(prestataire: Prestataire, contrat: Int?, service: ServicePrestataires, gestes: GestesAvis) {
    var ouvert by remember { mutableStateOf(false) }
    val repere = contrat?.let(ReperesSaisieAvis::noterContrat) ?: ReperesSaisieAvis.noter(prestataire.id)
    BoutonGeste(stringResource(R.string.notation_noter), repere) { ouvert = true }
    if (ouvert) {
        FeuilleAvis(ModeAvis.Noter(contrat), prestataire, service, gestes) {
            ouvert = false
            gestes.avis.feuilleFermee()
        }
    }
}

/**
 * Les gestes d'un avis déplié, chacun selon son droit SERVI, comme l'iOS : « Modifier cet
 * avis » si l'avis porte `peut_modifier` (l'auteur seul), « Retirer cet avis » pour le
 * conseil syndical. Sans droit, aucun bouton. Côte à côte, l'un sous l'autre en grand texte.
 */
@Composable
internal fun GestesDeLAvis(avis: AvisPrestataire, retirable: Boolean, modifier: () -> Unit, retirer: () -> Unit) {
    if (!avis.peutModifier && !retirable) return
    val boutons: @Composable () -> Unit = {
        if (avis.peutModifier) {
            BoutonGeste(stringResource(R.string.notation_modifier), ReperesSaisieAvis.modifier(avis.id), Icons.Outlined.Edit, onClick = modifier)
        }
        if (retirable) {
            val auteur = avis.auteurNom ?: stringResource(R.string.notation_ce_membre)
            BoutonGeste(
                stringResource(R.string.notation_retirer),
                ReperesSaisieAvis.retirer(avis.id),
                Icons.Outlined.Delete,
                destructif = true,
                vocal = stringResource(R.string.notation_retirer_de, auteur),
                onClick = retirer,
            )
        }
    }
    if (LocalDensity.current.fontScale >= GRANDE_ECHELLE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { boutons() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { boutons() }
    }
}

/**
 * Retirer un avis (`NotationsPrestataire.supprimer`) : définitif, donc confirmé avec les
 * mots du site ; la moyenne se recalcule au serveur. `retire` suit un retrait réussi.
 */
@Composable
internal fun ConfirmationRetrait(
    avis: AvisPrestataire,
    service: ServicePrestataires,
    gestes: GestesAvis,
    fermer: () -> Unit,
    retire: () -> Unit,
) {
    val portee = rememberCoroutineScope()
    val ressources = LocalResources.current
    val dit = stringResource(R.string.notation_retire)
    val auteur = avis.auteurNom ?: stringResource(R.string.notation_un_membre)
    val message = stringResource(R.string.notation_retrait_etat, EtatAvis.teinte(avis.etat).libelle, auteur) +
        (avis.commentaire?.let { "${Separateur.tiret}« $it »" } ?: "") +
        "\n\n" + stringResource(R.string.notation_retrait_definitif)
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(stringResource(R.string.notation_retirer)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    fermer()
                    portee.launch {
                        try {
                            service.retirer(avis.id)
                            gestes.avis.dire(dit)
                            retire()
                            gestes.apres()
                        } catch (e: ErreurService) {
                            gestes.avis.dire(e.phrase(ressources), TonAvis.Erreur)
                        }
                    }
                },
                modifier = Modifier.testTag(ReperesSaisieAvis.CONFIRMER_RETRAIT),
            ) { Text(stringResource(R.string.notation_confirmer_retrait), color = LocalCouleurs.current.danger) }
        },
        dismissButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.annuler)) } },
    )
}

/** Un geste nommé, bordé (`.bordered` de l'iOS), à la largeur de son libellé ; grisé hors ligne. */
@Composable
private fun BoutonGeste(
    libelle: String,
    repere: String,
    icone: ImageVector? = null,
    destructif: Boolean = false,
    vocal: String? = null,
    onClick: () -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val geste = gesteEnLigne()
    OutlinedButton(
        onClick = onClick,
        enabled = geste.actif,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (destructif) couleurs.danger else couleurs.primary),
        modifier = geste.modifier.heightIn(min = Mise.cible).testTag(repere)
            .then(if (vocal != null) Modifier.semantics { contentDescription = vocal } else Modifier),
    ) {
        icone?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text(libelle, modifier = Modifier.padding(start = if (icone != null) 6.dp else 0.dp))
    }
}
