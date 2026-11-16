// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le QR de la fiche d'arrivée, lu dans une image comme la caméra la donne (plan Y). */
class LecteurQRTest {
    /** L'image d'un QR code : `pas` octets par ligne, blanc autour, comme un plan Y de caméra. */
    private fun image(texte: String, cote: Int = 240, pas: Int = cote): ByteArray {
        val matrice = QRCodeWriter().encode(texte, BarcodeFormat.QR_CODE, cote, cote)
        return ByteArray(pas * cote) { i ->
            val x = i % pas
            val y = i / pas
            if (x < cote && matrice[x, y]) 0 else 255.toByte()
        }
    }

    @Test
    fun uneAdresseSeLit() {
        assertEquals("https://residence.exemple.test", LecteurQR.adresse(image("https://residence.exemple.test"), 240, 240))
        assertEquals("residence.exemple.test", LecteurQR.adresse(image("residence.exemple.test"), 240, 240))
    }

    @Test
    fun unPlanAuPasPlusLargeQueLImageSeLit() {
        val octets = image("https://residence.exemple.test", pas = 256)
        // Le plan Y d'une caméra : la dernière ligne s'arrête à la largeur.
        val tronque = octets.copyOf(256 * 239 + 240)
        assertEquals("https://residence.exemple.test", LecteurQR.adresse(tronque, 240, 240, pas = 256))
    }

    @Test
    fun unQRQuiNePortePasDAdresseEstIgnore() {
        assertEquals("Bonjour à tous", LecteurQR.lire(image("Bonjour à tous"), 240, 240))
        assertNull(LecteurQR.adresse(image("Bonjour à tous"), 240, 240))
        assertNull(LecteurQR.adresse(image("https://user@residence.exemple.test"), 240, 240))
    }

    @Test
    fun uneImageSansQRNeDitRien() {
        assertNull(LecteurQR.lire(ByteArray(240 * 240) { 127 }, 240, 240))
    }
}
