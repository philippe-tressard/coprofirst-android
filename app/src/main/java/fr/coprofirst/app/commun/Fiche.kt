// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Typo

/*
 * Les pièces d'une fiche, communes à plusieurs menus (#84) — pendants iOS : `Intertitre`,
 * `LigneFiche`, `NoteAcces`, `.styleLibelle()`.
 */

/** Un intertitre de section, avec son compte déjà dans le texte (« 🏢 Lots possédés (3) »). */
@Composable
fun Intertitre(titre: String) {
    Text(
        titre,
        style = Typo.titre(MaterialTheme.typography.titleLarge),
        color = LocalCouleurs.current.text,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading() },
    )
}

/** Une ligne « libellé — valeur » d'une fiche (le `<dl>` du site) ; la valeur passe dessous quand la place manque. */
@Composable
fun LigneFiche(libelle: String, valeur: String) {
    val couleurs = LocalCouleurs.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(libelle, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted, modifier = Modifier.widthIn(min = 110.dp))
        SelectionContainer {
            Text(valeur, style = MaterialTheme.typography.bodyLarge, color = couleurs.text)
        }
    }
}

/** Une note ou un état vide en ligne, dans une carte : un titre facultatif et son texte (`NoteAcces` de l'iOS). */
@Composable
fun NoteEnCarte(texte: String, titre: String? = null, icone: ImageVector = Icons.Outlined.Info) {
    val couleurs = LocalCouleurs.current
    Carte(Modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icone, contentDescription = null, tint = couleurs.textMuted)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (titre != null) Text(titre, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
                Text(texte, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
            }
        }
    }
}

/**
 * Un libellé en petites capitales, sourd (`.styleLibelle()` de l'iOS) : le titre d'une
 * section, ou le libellé d'un champ — le seul emploi de `Typo.libelle()` (`verifier-depot.sh`).
 * Il met le texte en capitales (sans effet sur un texte qui l'est déjà). Une section le
 * marque titre par son `modifier` (`Modifier.semantics { heading() }`) ; un libellé de
 * champ, non. `couleur` et `fort` servent la marque d'un champ requis (l'étoile rouge,
 * en gras, de `EnteteRequis`).
 */
@Composable
fun Libelle(
    texte: String,
    modifier: Modifier = Modifier,
    couleur: Color = LocalCouleurs.current.textMuted,
    fort: Boolean = false,
) {
    val style = Typo.libelle()
    Text(
        texte.uppercase(),
        style = if (fort) style.copy(fontWeight = FontWeight.Bold) else style,
        color = couleur,
        modifier = modifier,
    )
}
