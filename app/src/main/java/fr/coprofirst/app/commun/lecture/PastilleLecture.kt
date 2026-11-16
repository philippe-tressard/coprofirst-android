// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.lecture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Pictogrammes
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.teintes

/** Ce que dit la bulle du site, et ce que lit le lecteur d'écran : « Qui la lit : CS. Lue par… » — « Qui le lit » pour un sondage. */
@Composable
fun enClair(lecture: Lecture, masculin: Boolean = false): String =
    stringResource(if (masculin) R.string.lecture_en_clair_masculin else R.string.lecture_en_clair, lecture.titre, lecture.phrase)

/** « Lieu : Bât. 2 › Toit » — ce que lit le lecteur d'écran. */
@Composable
fun enClair(lieu: String): String = stringResource(R.string.lieu_en_clair, lieu)

/**
 * « Qui la lit » sur une carte, comme l'iOS (`PastilleLecture`, `PastilleLecture.svelte` du
 * site, #129) : la teinte des badges bleus, les pictogrammes des lecteurs, le libellé court,
 * le cadenas quand seuls ceux du périmètre lisent. **Rien** quand personne n'est exclu
 * (`par_defaut`) ni quand l'installation ne la sert pas.
 *
 * Elle ne s'ouvre pas au toucher : sur une carte, le toucher ouvre le contenu, où la phrase
 * entière se lit (`LectureDetaillee`). Le lecteur d'écran lit la phrase de la bulle.
 * `memeParDefaut` : une SAISIE (« Qui la lira ») la montre même quand personne n'est exclu.
 */
@Composable
fun PastilleLecture(lecture: Lecture?, modifier: Modifier = Modifier, masculin: Boolean = false, memeParDefaut: Boolean = false) {
    val montree = (if (memeParDefaut) lecture else Lecture.aMontrer(lecture)) ?: return
    val (texte, fond) = teintes(BADGE, LocalCouleurs.current)
    val vocal = enClair(montree, masculin)
    Row(
        modifier
            .background(fond, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = vocal },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        montree.icones.forEach { Picto(it, texte) }
        Text(
            montree.court,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = texte,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (montree.perimetreReserve) Picto(CADENAS, texte)
    }
}

/**
 * « Qui la lit » sur le détail d'un contenu, comme l'iOS (`LectureDetaillee`) : la pastille,
 * puis la phrase entière — ce que la bulle du site montre au survol, ici visible. Rien quand
 * personne n'est exclu, comme sur la carte (repère `lecture.detail`).
 */
@Composable
fun LectureDetaillee(lecture: Lecture?, modifier: Modifier = Modifier, masculin: Boolean = false) {
    val montree = Lecture.aMontrer(lecture) ?: return
    val couleurs = LocalCouleurs.current
    Column(
        modifier.semantics(mergeDescendants = true) {}.testTag("lecture.detail"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Libelle(stringResource(if (masculin) R.string.lecture_qui_le_lit else R.string.lecture_qui_la_lit), Modifier.semantics { heading() })
        PastilleLecture(montree, Modifier.clearAndSetSemantics {}, masculin)
        Text(montree.titre, style = MaterialTheme.typography.titleSmall, color = couleurs.text)
        Text(montree.phraseEntiere, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
    }
}

/**
 * Le lieu d'un contenu, tel que servi (`LieuServi`), comme l'iOS (`EtiquetteLieu`) :
 * l'épingle et le libellé — rien pour le périmètre par défaut. Taille et teinte : celles de
 * la ligne qui le porte (`style`, `couleur`).
 */
@Composable
fun EtiquetteLieu(
    lieu: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    couleur: Color = LocalCouleurs.current.textMuted,
) {
    lieu ?: return
    val vocal = enClair(lieu)
    Row(
        modifier.clearAndSetSemantics { contentDescription = vocal },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Place, contentDescription = null, tint = couleur, modifier = Modifier.size(14.dp))
        Text(lieu, style = style, color = couleur)
    }
}

@Composable
private fun Picto(nom: String, teinte: Color) =
    Icon(Pictogrammes.icone(nom), contentDescription = null, tint = teinte, modifier = Modifier.size(12.dp))

/** La teinte des badges bleus du site (`badge-blue`), celle de la pastille de lecture. */
private const val BADGE = "badge-blue"

/** Le cadenas d'un périmètre réservé (nom Lucide). */
private const val CADENAS = "lock"
