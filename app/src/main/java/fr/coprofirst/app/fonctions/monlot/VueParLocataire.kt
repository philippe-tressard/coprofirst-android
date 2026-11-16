// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Intertitre
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.NoteEnCarte
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.acces.LibellesAcces
import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.commun.annuaire.Coordonnees
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « 👥 Vue par locataire » (`OngletAcces.svelte`, bailleur seulement), comme l'iOS : ce
 * que chaque locataire détient de ses accès, et « ↩ Tout récupérer ».
 */
@Composable
fun VueParLocataire(
    bauxActifs: List<Bail>,
    objets: List<AccesDetenu>,
    service: ServiceMonLot,
    annoncer: (Int) -> Unit,
    recharger: () -> Unit,
) {
    var aRecuperer by remember { mutableStateOf<GroupeAcces?>(null) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    val portee = rememberCoroutineScope()
    val groupes = GroupeAcces.grouper(bauxActifs, objets)
    Intertitre(stringResource(R.string.monlot_vue_par_locataire))
    erreur?.let { MessageErreur(it) }
    if (groupes.isEmpty()) {
        NoteEnCarte(stringResource(R.string.monlot_aucun_bail_actif_point))
    } else {
        Text(stringResource(R.string.monlot_vue_par_locataire_intro), style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
        groupes.forEach { groupe -> CarteGroupe(groupe) { aRecuperer = groupe } }
    }
    aRecuperer?.let { groupe ->
        AlertDialog(
            onDismissRequest = { aRecuperer = null },
            title = { Text(stringResource(R.string.monlot_tout_recuperer_question)) },
            text = { Text(groupe.nom) },
            confirmButton = {
                TextButton(onClick = {
                    aRecuperer = null
                    portee.launch {
                        try {
                            groupe.bauxIds.forEach { service.recupererAcces(it) }
                            erreur = null
                            annoncer(R.string.monlot_acces_recuperes)
                            recharger()
                        } catch (e: ErreurService) {
                            erreur = e
                        }
                    }
                }) { Text(stringResource(R.string.monlot_tout_recuperer)) }
            },
            dismissButton = { TextButton(onClick = { aRecuperer = null }) { Text(stringResource(R.string.monlot_annuler)) } },
        )
    }
}

@Composable
private fun CarteGroupe(groupe: GroupeAcces, recuperer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(groupe.nom, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = couleurs.text)
            Coordonnees(groupe.email, emptyList())
            if (groupe.objets.isEmpty()) {
                PastilleTeinte(stringResource(R.string.monlot_aucun_acces_confie_badge), "badge-gray", compacte = true)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val compte = groupe.objets.size
                PastilleTeinte(
                    pluralStringResource(R.plurals.monlot_acces_confies_compte, compte, compte),
                    "badge-yellow",
                    compacte = true,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = recuperer, modifier = Modifier.heightIn(min = Mise.cible)) {
                    Text(stringResource(R.string.monlot_tout_recuperer), color = couleurs.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            groupe.objets.forEach { objet ->
                FlowRow(
                    Modifier.semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(objet.code, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    // Le site abrège ici (« 📡 TC ») : l'app prend le libellé de la table générée, comme l'iOS.
                    PastilleTeinte(
                        LibellesAcces.typeAcces(objet.type),
                        if (objet.type == TypeAcces.Vigik.code) "badge-blue" else "badge-purple",
                        compacte = true,
                    )
                    PastilleTeinte(LibellesAcces.statutAcces(objet.statut), compacte = true)
                }
            }
        }
    }
}
