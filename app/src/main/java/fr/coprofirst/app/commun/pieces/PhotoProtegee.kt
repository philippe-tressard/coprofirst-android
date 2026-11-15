// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte

/** Où en est une photo. */
private sealed interface EtatPhoto {
    data object EnCours : EtatPhoto
    data class Prete(val image: Bitmap) : EtatPhoto
    data object Echec : EtatPhoto
}

/**
 * Une photo de `/uploads/`, lue par la session de la résidence — comme l'iOS
 * (`PhotoProtegee`) : un fond neutre, l'attente, puis la photo en fondu ; une icône
 * « Photo indisponible » si elle ne se lit pas. `remplir` la recadre pour remplir son
 * cadre ; sinon elle s'y loge entière. Décodée à la taille du cadre, gardée en mémoire
 * (`PhotosEnMemoire`) : la rouvrir ne relit pas le réseau.
 */
@Composable
fun PhotoProtegee(chemin: String, session: SessionOuverte, modifier: Modifier = Modifier, remplir: Boolean = true) {
    val couleurs = LocalCouleurs.current
    BoxWithConstraints(modifier.background(couleurs.neutreFond), contentAlignment = Alignment.Center) {
        val cote = PhotosEnMemoire.cote(constraints.maxWidth, constraints.maxHeight)
        val cle = PhotosEnMemoire.cle(session, chemin, cote)
        var etat by remember(cle) {
            mutableStateOf(PhotosEnMemoire.lire(cle)?.let { EtatPhoto.Prete(it) } ?: EtatPhoto.EnCours)
        }
        LaunchedEffect(cle) {
            if (etat is EtatPhoto.Prete) return@LaunchedEffect
            etat = try {
                PhotosEnMemoire.charger(session, chemin, cote)?.let { EtatPhoto.Prete(it) } ?: EtatPhoto.Echec
            } catch (_: ErreurService) {
                EtatPhoto.Echec
            }
        }
        Crossfade(targetState = etat, animationSpec = tween(200), label = "photo") { courant ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (courant) {
                    EtatPhoto.EnCours -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = couleurs.textMuted)
                    is EtatPhoto.Prete -> Image(
                        bitmap = remember(courant.image) { courant.image.asImageBitmap() },
                        contentDescription = null,
                        contentScale = if (remplir) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    EtatPhoto.Echec -> Icon(
                        Icons.Filled.BrokenImage,
                        contentDescription = stringResource(R.string.pieces_photo_indisponible),
                        tint = couleurs.textMuted,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
