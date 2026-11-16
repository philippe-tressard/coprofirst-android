// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise

/**
 * Une section groupée, comme une `Section` de l'iOS (#138) : son libellé (un intitulé pour le
 * lecteur d'écran), sa carte, sa note dessous, à la marge de l'écran — `enRetrait = false` dans
 * une page déjà en retrait. Profil, À propos, la feuille « Résidences ».
 */
@Composable
fun Section(
    titre: String?,
    modifier: Modifier = Modifier,
    note: String? = null,
    enRetrait: Boolean = true,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val marge = if (enRetrait) Modifier.padding(horizontal = Mise.marge) else Modifier
    Column(modifier.fillMaxWidth().then(marge), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (titre != null) {
            Libelle(titre, Modifier.padding(start = 4.dp).semantics { heading() })
        }
        Carte(contenu = contenu)
        if (note != null) Note(note, Modifier.padding(horizontal = 4.dp))
    }
}

/** Une phrase discrète, sous une section ou dans une carte. */
@Composable
fun Note(texte: String, modifier: Modifier = Modifier, couleur: Color = LocalCouleurs.current.textMuted) =
    Text(texte, style = MaterialTheme.typography.bodySmall, color = couleur, modifier = modifier)

/** Une valeur et son libellé, sur une ligne (`LabeledContent` de l'iOS). */
@Composable
fun LigneValeur(libelle: String, valeur: String) {
    val couleurs = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(libelle, color = couleurs.text, modifier = Modifier.weight(1f))
        Text(valeur, color = couleurs.textMuted)
    }
}
