// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import fr.coprofirst.app.session.AdresseInstallation

/**
 * La lecture d'une image de la caméra : le QR code de la fiche d'arrivée, et rien d'autre
 * (#6, `ScannerQRView` de l'iOS). Sur l'appareil seul, par ZXing ; l'image n'est ni gardée
 * ni envoyée. Seul un QR qui porte une ADRESSE compte — un autre QR est ignoré, la caméra
 * continue de chercher.
 */
object LecteurQR {
    private val indices = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))

    /**
     * Le texte d'un QR qui porte une adresse d'installation, lu dans la luminance d'une image
     * (le plan Y d'une image YUV : `pas` octets par ligne), ou `null`.
     */
    fun adresse(luminance: ByteArray, largeur: Int, hauteur: Int, pas: Int = largeur): String? =
        lire(luminance, largeur, hauteur, pas)?.takeIf { AdresseInstallation.normaliser(it) != null }

    /** Le texte du QR code de l'image, ou `null` s'il n'y en a pas (ou qu'il est illisible). */
    fun lire(luminance: ByteArray, largeur: Int, hauteur: Int, pas: Int = largeur): String? {
        // La dernière ligne d'un plan peut s'arrêter à la largeur, avant le pas : on complète.
        val donnees = if (luminance.size >= pas * hauteur) luminance else luminance.copyOf(pas * hauteur)
        val source = PlanarYUVLuminanceSource(donnees, pas, hauteur, 0, 0, largeur, hauteur, false)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), indices).text
        } catch (_: ReaderException) {
            null
        }
    }
}
