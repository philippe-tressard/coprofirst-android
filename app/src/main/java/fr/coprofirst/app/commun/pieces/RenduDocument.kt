// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Les pages d'un PDF, l'une sous l'autre à la largeur de la colonne (#274), rendues à mesure
 * qu'elles paraissent : une liste paresseuse ne garde en mémoire que les pages à l'écran. Le
 * zoom et l'impression sont ceux de l'app de l'appareil (« Ouvrir dans une autre app »).
 */
@Composable
internal fun PagesPdf(fichier: File) {
    val pdf = remember(fichier) { RenduPdf.ouvrir(fichier) }
    DisposableEffect(pdf) { onDispose { pdf?.close() } }
    if (pdf == null) {
        Illisible()
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largeur = constraints.maxWidth.coerceIn(1, LARGEUR_MAX_PX)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(pdf.pages) { rang -> PagePdf(pdf, rang, largeur) }
        }
    }
}

@Composable
private fun PagePdf(pdf: RenduPdf, rang: Int, largeur: Int) {
    val image by produceState<ImageBitmap?>(null, pdf, rang, largeur) {
        value = withContext(Dispatchers.IO) { pdf.page(rang, largeur) }?.asImageBitmap()
    }
    val description = stringResource(R.string.visionneuse_page, rang + 1, pdf.pages)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(pdf.formats[rang])
            .testTag("$REPERE_PAGE.$rang")
            .semantics { contentDescription = description },
    ) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

/** Une image, entière dans la colonne, réduite à sa taille d'affichage (jamais l'original en mémoire). */
@Composable
internal fun ImageDuDocument(fichier: File, titre: String) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        val cote = maxOf(constraints.maxWidth, constraints.maxHeight).coerceIn(1, LARGEUR_MAX_PX)
        val image by produceState<ImageBitmap?>(null, fichier, cote) {
            value = withContext(Dispatchers.IO) { reduite(fichier, cote) }?.asImageBitmap()
        }
        image?.let { Image(it, contentDescription = titre, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

@Composable
private fun Illisible() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.pieces_echec), style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.danger)
    }
}

/** L'image décodée au plus près de `cote` pixels (une puissance de deux), `null` si illisible. */
private fun reduite(fichier: File, cote: Int): Bitmap? {
    val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(fichier.path, bornes)
    if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return null
    var pas = 1
    while (maxOf(bornes.outWidth, bornes.outHeight) / (pas * 2) >= cote) pas *= 2
    return BitmapFactory.decodeFile(fichier.path, BitmapFactory.Options().apply { inSampleSize = pas })
}

/**
 * Un PDF ouvert pour être lu page à page. `PdfRenderer` ne rend qu'une page à la fois : chaque
 * rendu passe sous un verrou, et un PDF fermé ne rend plus rien.
 */
internal class RenduPdf private constructor(
    private val descripteur: ParcelFileDescriptor,
    private val rendu: PdfRenderer,
    /** Le rapport largeur / hauteur de chaque page. */
    val formats: List<Float>,
) : Closeable {
    val pages: Int get() = formats.size
    private var ferme = false

    /** La page `rang` à `largeur` pixels, sur le blanc du papier ; `null` une fois fermé. */
    fun page(rang: Int, largeur: Int): Bitmap? = synchronized(this) {
        if (ferme) return null
        rendu.openPage(rang).use { page ->
            val hauteur = (largeur.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
            createBitmap(largeur, hauteur).also {
                // Le PAPIER d'un PDF est blanc quelle que soit la charte : PdfRenderer rend sur un fond
                // transparent, qui serait noir au thème sombre. Ce n'est pas une couleur de l'interface.
                it.eraseColor(android.graphics.Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    override fun close() = synchronized(this) {
        if (ferme) return
        ferme = true
        rendu.close()
        descripteur.close()
    }

    companion object {
        /** `null` : un fichier que `PdfRenderer` ne sait pas lire (abîmé, protégé par un mot de passe). */
        fun ouvrir(fichier: File): RenduPdf? {
            val descripteur = try {
                ParcelFileDescriptor.open(fichier, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (_: IOException) {
                return null
            }
            return try {
                val rendu = PdfRenderer(descripteur)
                val formats = (0 until rendu.pageCount).map { rang -> rendu.openPage(rang).use { it.width.toFloat() / it.height.coerceAtLeast(1) } }
                RenduPdf(descripteur, rendu, formats)
            } catch (_: IOException) {
                descripteur.close()
                null
            } catch (_: SecurityException) {
                descripteur.close()
                null
            }
        }
    }
}

/** Au-delà, une page ou une image se rend réduite : la mémoire d'une colonne, pas d'un poster. */
private const val LARGEUR_MAX_PX = 2048
