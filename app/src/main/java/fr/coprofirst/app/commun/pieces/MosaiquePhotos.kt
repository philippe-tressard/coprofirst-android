// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.session.SessionOuverte

/** L'écart entre deux tuiles : un filet, comme Messages. */
private val JOINT = 2.dp

/** Le voile posé sur une tuile (« +N », ouverture en cours) : la surface, translucide. */
private const val VOILE = 0.72f

/**
 * Les photos d'un élément en mosaïque, comme l'iOS (`MosaiquePhotos`) : une photo occupe
 * tout le cadre ; deux, côte à côte ; trois et plus, une grande à gauche et deux petites à
 * droite, la dernière disant « +N » quand il en reste. Le cadre garde la même hauteur quel
 * que soit le nombre : le texte qui suit ne saute pas. Les tuiles remplissent leur case ;
 * la photo entière se voit au toucher (`Ouverture`).
 */
@Composable
fun MosaiquePhotos(
    chemins: List<String>,
    session: SessionOuverte,
    hauteur: Dp,
    enCours: String?,
    modifier: Modifier = Modifier,
    ouvrir: (Int) -> Unit,
) {
    if (chemins.isEmpty()) return
    @Composable
    fun tuile(rang: Int, mod: Modifier, reste: Int = 0) =
        Tuile(chemins, rang, reste, session, enCours == chemins[rang], mod) { ouvrir(rang) }

    Box(modifier.fillMaxWidth().height(hauteur).clip(RoundedCornerShape(Mise.rayon))) {
        when (chemins.size) {
            1 -> tuile(0, Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(JOINT)) {
                tuile(0, Modifier.weight(1f).fillMaxHeight())
                tuile(1, Modifier.weight(1f).fillMaxHeight())
            }
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(JOINT)) {
                tuile(0, Modifier.weight(2f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(JOINT)) {
                    tuile(1, Modifier.weight(1f).fillMaxWidth())
                    tuile(2, Modifier.weight(1f).fillMaxWidth(), reste = chemins.size - 3)
                }
            }
        }
    }
}

@Composable
private fun Tuile(
    chemins: List<String>,
    rang: Int,
    reste: Int,
    session: SessionOuverte,
    enCours: Boolean,
    modifier: Modifier,
    ouvrir: () -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val libelle = if (reste > 0) {
        stringResource(R.string.pieces_photo_reste, rang + 1, chemins.size, reste)
    } else {
        stringResource(R.string.pieces_photo, rang + 1, chemins.size)
    }
    Box(
        modifier
            .cliquable(stringResource(R.string.pieces_ouvrir_photo), ouvrir)
            .clearAndSetSemantics { contentDescription = libelle }
            .testTag("pieces.photo.$rang"),
        contentAlignment = Alignment.Center,
    ) {
        PhotoProtegee(chemins[rang], session, Modifier.fillMaxSize())
        if (reste > 0) {
            Box(Modifier.fillMaxSize().background(couleurs.surface.copy(alpha = VOILE)), contentAlignment = Alignment.Center) {
                Text("+$reste", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = couleurs.text)
            }
        }
        if (enCours) {
            Box(Modifier.fillMaxSize().background(couleurs.surface.copy(alpha = VOILE)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = couleurs.primary)
            }
        }
    }
}

/**
 * La vignette d'une carte FERMÉE, comme l'iOS (`VignettePieces`, `FluxVignette` du site) :
 * la première photo, « +N » s'il y en a d'autres ; sans photo, un trombone et le nombre de
 * pièces. Même place, même taille dans tous les cas : le rythme du fil ne bouge pas.
 * Décorative — la carte dit ses pièces au lecteur d'écran (`PiecesJointes.enClair`).
 */
@Composable
fun VignettePieces(pieces: PiecesJointes, session: SessionOuverte, modifier: Modifier = Modifier) {
    if (pieces.vide) return
    val couleurs = LocalCouleurs.current
    Box(
        modifier.size(Mise.vignette).clip(RoundedCornerShape(Mise.rayon / 2)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val premiere = pieces.photos.firstOrNull()
        if (premiere != null) {
            PhotoProtegee(premiere, session, Modifier.fillMaxSize())
            if (pieces.photos.size > 1) {
                Text(
                    "+${pieces.photos.size - 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = couleurs.text,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(couleurs.surface.copy(alpha = VOILE), CircleShape)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().background(couleurs.infoFond),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(20.dp))
                Text(
                    "${pieces.documents.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = couleurs.primary,
                )
            }
        }
    }
}
