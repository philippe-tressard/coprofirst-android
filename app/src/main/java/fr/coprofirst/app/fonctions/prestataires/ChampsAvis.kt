// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.pleine

/**
 * Le corps de la saisie d'un avis (`FormulaireNotation.svelte`), comme l'iOS (`ChampsAvis`,
 * coprofirst-ios#260) : l'état, les deux listes de motifs à cocher (`commentaireAvis`), la
 * phrase qu'ils composent, le texte libre — et sous le dernier champ, la phrase du site qui
 * dit ce qui retient l'avis.
 */
@Composable
internal fun ChampsAvis(saisie: SaisieAvis, refus: RefusAvis?, prestataire: String, changer: (SaisieAvis) -> Unit) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Libelle(stringResource(R.string.notation_etat_groupe), Modifier.semantics { heading() })
        FlowCapsules(espace = 8.dp) {
            SaisieAvis.etats.forEach { e ->
                val teinte = EtatAvis.teinte(e)
                Pastille(
                    teinte.libelle,
                    retenue = saisie.etat == e,
                    modifier = Modifier.testTag(ReperesSaisieAvis.etat(e)),
                    teinte = pleine(teinte.badge, couleurs),
                ) { changer(saisie.copy(etat = e)) }
            }
        }
    }
    Motifs(stringResource(R.string.notation_points_forts), TablesNotations.pointsForts, saisie.forts, ReperesSaisieAvis::fort) {
        changer(saisie.copy(forts = it))
    }
    Motifs(stringResource(R.string.notation_points_faibles), TablesNotations.pointsFaibles, saisie.faibles, ReperesSaisieAvis::faible) {
        changer(saisie.copy(faibles = it))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Libelle(stringResource(R.string.notation_commentaire_genere), Modifier.semantics { heading() })
        val genere = saisie.genere
        Text(
            genere.ifEmpty { stringResource(R.string.notation_rien_de_coche) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (genere.isEmpty()) couleurs.textMuted else couleurs.text,
            modifier = Modifier.testTag(ReperesSaisieAvis.GENERE),
        )
    }
    OutlinedTextField(
        value = saisie.libre,
        onValueChange = { changer(saisie.copy(libre = it)) },
        label = { Text("${stringResource(R.string.notation_commentaire_libre)} ${stringResource(R.string.notation_aide_libre)}") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().testTag(ReperesSaisieAvis.LIBRE),
    )
    refus?.let {
        val phrase = when (it) {
            RefusAvis.EtatManquant -> stringResource(R.string.notation_choisir_etat)
            RefusAvis.MauvaisSansCommentaire -> stringResource(R.string.notation_mauvais_sans_commentaire)
            RefusAvis.AjoutNonConfirme -> stringResource(R.string.notation_confirmer_ajout_refus, prestataire)
        }
        Text(phrase, style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted, modifier = Modifier.testTag(ReperesSaisieAvis.REFUS))
    }
}

/** Une liste de motifs, une case par motif — dans l'ordre du site, qui est celui de la phrase. */
@Composable
private fun Motifs(titre: String, liste: List<String>, coches: Set<String>, repere: (Int) -> String, changer: (Set<String>) -> Unit) {
    Column {
        Libelle(titre, Modifier.semantics { heading() })
        liste.forEachIndexed { rang, motif ->
            CaseACocher(motif, motif in coches, repere(rang)) { coche -> changer(if (coche) coches + motif else coches - motif) }
        }
    }
}

/** La case du site (`<input type="checkbox">`) : toute la rangée la bascule, à hauteur de pouce. */
@Composable
internal fun CaseACocher(libelle: String, coche: Boolean, repere: String, basculer: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(repere)
            .toggleable(value = coche, role = Role.Checkbox, onValueChange = basculer),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = coche, onCheckedChange = null)
        Text(libelle, style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.text)
    }
}
