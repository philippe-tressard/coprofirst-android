// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.delegations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesDelegations {
    fun carte(id: Int) = "delegation.$id"
    fun statut(id: Int) = "delegation.$id.statut"
    fun accepter(id: Int) = "delegation.$id.accepter"
    fun revoquer(id: Int) = "delegation.$id.revoquer"
    const val VIDE = "delegations.vide"
    const val NOUVELLE = "delegations.nouvelle"
    const val REVOQUER_CONFIRMER = "delegations.revoquer.confirmer"
    const val MANDANT = "nouvelle-delegation.mandant"
    const val AIDANT = "nouvelle-delegation.aidant"
    const val MOTIF = "nouvelle-delegation.motif"
    const val AVEC_FIN = "nouvelle-delegation.avec-fin"
    const val DATE_FIN = "nouvelle-delegation.date-fin"
    const val ENREGISTRER = "nouvelle-delegation.enregistrer"
    const val RECHERCHE = "nouvelle-delegation.recherche"
    fun resident(id: Int) = "nouvelle-delegation.resident.$id"
}

/** À partir de cette échelle de texte, les deux personnes se posent l'une sous l'autre. */
private const val ECHELLE_EMPILEE = 1.5f

/**
 * Une délégation, comme `CarteDelegation` de l'iOS : qui est aidé, par qui, son état et sa
 * période, son motif ; et les gestes que le serveur ouvre au compte qui lit
 * (`peutAccepter`, `peutRevoquer`). `enCours` : un geste part, les boutons attendent.
 */
@Composable
internal fun CarteDelegation(delegation: Delegation, enCours: Boolean, accepter: () -> Unit, revoquer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Carte(Modifier.testTag(ReperesDelegations.carte(delegation.id))) {
        Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            Personnes(delegation)
            FlowCapsules {
                PastilleTeinte(delegation.libelleStatut, Modifier.testTag(ReperesDelegations.statut(delegation.id)))
                Text(
                    periode(delegation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = couleurs.textMuted,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            if (delegation.motif.isNotEmpty()) {
                Text(delegation.motif, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = couleurs.textMuted)
            }
            if (delegation.peutAccepter || delegation.peutRevoquer) Gestes(delegation, enCours, accepter, revoquer)
        }
    }
}

/** Personne aidée → aidant ; l'une sous l'autre aux grandes tailles de texte. */
@Composable
private fun Personnes(delegation: Delegation) {
    val empilees = LocalDensity.current.fontScale >= ECHELLE_EMPILEE
    val fleche: @Composable () -> Unit = {
        Icon(
            if (empilees) Icons.Filled.ArrowDownward else Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = LocalCouleurs.current.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
    val aidee: @Composable () -> Unit = { Personne(stringResource(R.string.delegations_personne_aidee), delegation.mandantNom) }
    val aidant: @Composable () -> Unit = { Personne(stringResource(R.string.delegations_aidant), delegation.aidantNom) }
    if (empilees) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { aidee(); fleche(); aidant() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Mise.ecart), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f, fill = false)) { aidee() }
            fleche()
            Column(Modifier.weight(1f, fill = false)) { aidant() }
        }
    }
}

/** Un rôle et un nom, lus ensemble par le lecteur d'écran. */
@Composable
private fun Personne(role: String, nom: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Libelle(role)
        Text(nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = LocalCouleurs.current.text)
    }
}

/**
 * « Accepter » (le bouton plein) et « Révoquer » (le contour, en `danger` : il retire un
 * accès), côte à côte, de même hauteur.
 */
@Composable
private fun Gestes(delegation: Delegation, enCours: Boolean, accepter: () -> Unit, revoquer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (delegation.peutAccepter) {
            BoutonPlein(
                R.string.delegations_accepter,
                Modifier.weight(1f).testTag(ReperesDelegations.accepter(delegation.id)),
                enCours = enCours,
                onClick = accepter,
            )
        }
        if (delegation.peutRevoquer) {
            OutlinedButton(
                onClick = revoquer,
                enabled = !enCours,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = couleurs.danger),
                border = ButtonDefaults.outlinedButtonBorder(!enCours).copy(brush = SolidColor(couleurs.danger)),
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = Mise.cible)
                    .testTag(ReperesDelegations.revoquer(delegation.id)),
            ) {
                if (enCours && !delegation.peutAccepter) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = couleurs.danger)
                } else {
                    Text(stringResource(R.string.delegations_revoquer))
                }
            }
        }
    }
}

/** « Du 02/04/2026 au 30/06/2026 », ou « Du 02/04/2026 — illimité », comme le site. */
@Composable
internal fun periode(delegation: Delegation): String {
    val debut = FormatsDuSite.dateCourte(delegation.dateDebut)
    return delegation.dateFin?.let { stringResource(R.string.delegations_periode, debut, FormatsDuSite.dateCourte(it)) }
        ?: stringResource(R.string.delegations_periode_illimitee, debut)
}
