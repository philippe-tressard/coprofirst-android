// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.onglets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.navigation.OngletDuSite

/**
 * « Sur le site », comme `OngletSurLeSite` de l'iOS : les onglets que ce compte ouvre sur
 * le site et que l'app ne porte pas, dans l'ordre du site, avec leur descriptif et un lien
 * vers chacun — ouvert par le navigateur, à l'adresse de la résidence. L'app ne les cache
 * pas : elle dit où ils sont.
 */
@Composable
fun OngletSurLeSite(
    onglets: List<OngletDuSite>,
    descriptifs: Map<String, String>,
    routes: Map<String, String>,
    adresse: String,
) {
    val couleurs = LocalCouleurs.current
    Carte {
        onglets.forEachIndexed { rang, onglet ->
            if (rang > 0) HorizontalDivider(color = couleurs.border)
            Ligne(onglet, descriptifs[onglet.id], routes[onglet.id]?.let { OngletsSurLeSite.lien(it, adresse) })
        }
    }
}

@Composable
private fun Ligne(onglet: OngletDuSite, descriptif: String?, lien: String?) {
    val couleurs = LocalCouleurs.current
    val site = LocalUriHandler.current
    val action = stringResource(R.string.onglets_ouvrir_le_site)
    val toucher = if (lien != null) {
        Modifier.cliquable(action) { site.openUri(lien) }.testTag(ReperesOnglets.surLeSite(onglet.id))
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Row(
        Modifier.fillMaxWidth().then(toucher).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(onglet.libelle, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
            if (descriptif != null) {
                Text(TexteDuSite.apercu(descriptif), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
            }
        }
        if (lien != null) Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = couleurs.primary)
    }
}

/** Un onglet du site que l'app ne porte pas encore, comme `OngletBientot` de l'iOS : il le dit, sans écran vide. */
@Composable
fun OngletBientot(onglet: OngletDuSite) {
    EtatVide(
        titre = stringResource(R.string.bientot_titre),
        icone = Icons.Outlined.HourglassEmpty,
        explication = stringResource(R.string.bientot_explication, onglet.libelle),
    )
}
