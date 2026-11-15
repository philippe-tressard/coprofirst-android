// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Les photos montrées en vignette, gardées en MÉMOIRE seulement — jamais sur le disque —,
 * dans une limite (`PLAFOND`) : la moins récemment vue part la première. Aucune
 * bibliothèque d'images : le décodeur du système, échantillonné à la taille montrée, pour
 * ne jamais décoder une photo pleine taille dans une vignette de 56 dp.
 *
 * `ImageDecoder` (Android 9 et plus) redresse la photo selon son orientation EXIF et lit
 * HEIF (AVIF dès Android 12). ⚠️ Divergence déclarée : sous Android 8, `BitmapFactory`
 * n'applique pas l'orientation — une photo prise de côté s'y montre de côté (l'iOS la
 * redresse toujours) ; lire l'EXIF demanderait une bibliothèque de plus.
 */
object PhotosEnMemoire {
    /** Un huitième de la mémoire de l'app, au plus 48 Mo. */
    private val PLAFOND: Long = minOf(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)

    /** Le pas des tailles décodées : une même photo ne se décode pas pour chaque pixel de cadre. */
    private const val PAS = 256

    /** La taille décodée d'un cadre sans borne (une photo dans une liste qui défile). */
    private const val SANS_BORNE = 1080

    private val cache = CacheBorne<String, Bitmap>(PLAFOND) { it.allocationByteCount.toLong() }

    /** Le côté à décoder pour un cadre de cette taille (en pixels), arrondi au pas. */
    fun cote(largeur: Int, hauteur: Int): Int {
        fun borne(v: Int) = if (v in 1 until Int.MAX_VALUE) v else SANS_BORNE
        val plus = maxOf(borne(largeur), borne(hauteur))
        return (plus + PAS - 1) / PAS * PAS
    }

    fun cle(session: SessionOuverte, chemin: String, cote: Int): String = "${session.installation.url}\n$chemin\n$cote"

    fun lire(cle: String): Bitmap? = cache.lire(cle)

    /** Lit la photo par la session, la décode hors du fil principal, la garde ; `null` si illisible. */
    suspend fun charger(session: SessionOuverte, chemin: String, cote: Int): Bitmap? {
        val octets = session.fichierProtege(chemin)
        val image = withContext(Dispatchers.Default) { decoder(octets, cote) } ?: return null
        cache.garder(cle(session, chemin, cote), image)
        return image
    }

    /** À la déconnexion : la photo d'un compte ne se montre pas au suivant. */
    fun vider() = cache.vider()

    private fun decoder(octets: ByteArray, cote: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decoderRedresse(octets, cote) else decoderBrut(octets, cote)
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decoderRedresse(octets: ByteArray, cote: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(octets))) { decodeur, info, _ ->
            decodeur.setTargetSampleSize(Echantillonnage.facteur(info.size.width, info.size.height, cote, cote))
        }

    private fun decoderBrut(octets: ByteArray, cote: Int): Bitmap? {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = Echantillonnage.facteur(bornes.outWidth, bornes.outHeight, cote, cote)
        }
        return BitmapFactory.decodeByteArray(octets, 0, octets.size, options)
    }
}

/** Les calculs du décodage, sans Android : testés en JVM. */
object Echantillonnage {
    /**
     * Le facteur d'échantillonnage (`inSampleSize`, une puissance de 2) le plus grand qui
     * garde l'image décodée au moins aussi grande que la cible, dans chaque dimension :
     * une photo de 4000 × 3000 pour une vignette de 256 se décode au 1/8 (500 × 375).
     */
    fun facteur(largeur: Int, hauteur: Int, cibleLargeur: Int, cibleHauteur: Int): Int {
        if (largeur <= 0 || hauteur <= 0 || cibleLargeur <= 0 || cibleHauteur <= 0) return 1
        var facteur = 1
        while (largeur / (facteur * 2) >= cibleLargeur && hauteur / (facteur * 2) >= cibleHauteur) facteur *= 2
        return facteur
    }
}

/**
 * Un cache en mémoire borné par le POIDS de ses valeurs : au-delà du plafond, les moins
 * récemment lues partent ; une valeur plus lourde que le plafond n'entre pas.
 */
class CacheBorne<K : Any, V : Any>(private val plafond: Long, private val poids: (V) -> Long) {
    private val entrees = LinkedHashMap<K, V>(16, 0.75f, true)
    private var total = 0L

    @Synchronized
    fun lire(cle: K): V? = entrees[cle]

    @Synchronized
    fun garder(cle: K, valeur: V) {
        val lourd = poids(valeur)
        if (lourd > plafond) return
        entrees.put(cle, valeur)?.let { total -= poids(it) }
        total += lourd
        val iterateur = entrees.entries.iterator()
        while (total > plafond && iterateur.hasNext()) {
            total -= poids(iterateur.next().value)
            iterateur.remove()
        }
    }

    @Synchronized
    fun vider() {
        entrees.clear()
        total = 0
    }

    /** Le poids gardé. */
    val poidsTotal: Long @Synchronized get() = total

    val nombre: Int @Synchronized get() = entrees.size
}
