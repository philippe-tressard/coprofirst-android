// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.delegations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.ChampRecherche
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.texte.Recherche

/**
 * Le choix d'une personne de la résidence (le `Picker` « navigationLink » de l'iOS) : le
 * champ montre la personne choisie, ou « Choisir… » ; un toucher ouvre la liste en plein
 * écran, « Choisir… » en tête pour défaire le choix.
 *
 * ⚠️ Divergence déclarée : la liste a une recherche (`ChampRecherche`), que l'iOS n'a pas —
 * une résidence compte des centaines de comptes, et un menu déroulant d'Android ne se
 * parcourt pas comme la liste poussée de l'iOS.
 */
@Composable
internal fun ChoixResident(
    libelle: String,
    residents: List<ResidentDelegable>,
    choisi: Int?,
    repere: String,
    choisir: (Int?) -> Unit,
) {
    var ouvert by remember { mutableStateOf(false) }
    val couleurs = LocalCouleurs.current
    val nom = residents.firstOrNull { it.id == choisi }?.choix
    Carte(Modifier.testTag(repere), ouvrir = { ouvert = true }, action = stringResource(R.string.delegations_choisir)) {
        Libelle(libelle)
        Text(
            nom ?: stringResource(R.string.delegations_choisir),
            style = MaterialTheme.typography.bodyLarge,
            color = if (nom == null) couleurs.textMuted else couleurs.text,
        )
    }
    if (ouvert) {
        ListeDesResidents(libelle, residents, choisi, fermer = { ouvert = false }) {
            choisir(it)
            ouvert = false
        }
    }
}

@Composable
private fun ListeDesResidents(
    titre: String,
    residents: List<ResidentDelegable>,
    choisi: Int?,
    fermer: () -> Unit,
    choisir: (Int?) -> Unit,
) {
    var recherche by remember { mutableStateOf("") }
    val couleurs = LocalCouleurs.current
    val retenus = residents.filter { Recherche.contientTousLesMots(listOf(it.nomAffiche, it.email), recherche) }
    Dialog(onDismissRequest = fermer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(couleurs.bg).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = Mise.marge, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(titre, style = Typo.titre(), color = couleurs.text, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = fermer) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.annuler)) }
            }
            ChampRecherche(
                recherche,
                { recherche = it },
                stringResource(R.string.delegations_rechercher),
                ReperesDelegations.RECHERCHE,
                Modifier.padding(horizontal = Mise.marge, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Mise.marge, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "aucun") { Ligne(stringResource(R.string.delegations_choisir), choisi == null, null) { choisir(null) } }
                if (retenus.isEmpty()) item(key = "vide") { EtatVide(stringResource(R.string.delegations_aucun_resident)) }
                items(retenus, key = { it.id }) { r ->
                    Ligne(r.choix, r.id == choisi, ReperesDelegations.resident(r.id)) { choisir(r.id) }
                }
            }
        }
    }
}

/** Une ligne de la liste : le nom et l'adresse, et une coche sur celle qui est choisie. */
@Composable
private fun Ligne(texte: String, retenue: Boolean, repere: String?, choisir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Carte(if (repere == null) Modifier else Modifier.testTag(repere), ouvrir = choisir) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(texte, style = MaterialTheme.typography.bodyLarge, color = couleurs.text, modifier = Modifier.weight(1f))
            if (retenue) Icon(Icons.Filled.Check, contentDescription = null, tint = couleurs.primary)
        }
    }
}
