// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Une vignette ne décode jamais la photo pleine taille ; la mémoire des photos reste bornée. */
class PhotosEnMemoireTest {
    @Test
    fun lEchantillonnageGardeLaPhotoAuMoinsAussiGrandeQueLaCible() {
        // 4000 × 3000 pour une vignette de 256 : au 1/8 (500 × 375), pas au 1/16 (250 × 187).
        assertEquals(8, Echantillonnage.facteur(4000, 3000, 256, 256))
        // La plus petite dimension décide : un panorama garde sa hauteur.
        assertEquals(2, Echantillonnage.facteur(8000, 600, 256, 256))
        // Une photo déjà plus petite que la cible ne s'échantillonne pas.
        assertEquals(1, Echantillonnage.facteur(200, 150, 256, 256))
        assertEquals(1, Echantillonnage.facteur(512, 512, 512, 512))
        assertEquals(2, Echantillonnage.facteur(1024, 1024, 512, 512))
        // Des bornes illisibles ne cassent rien.
        assertEquals(1, Echantillonnage.facteur(0, 3000, 256, 256))
        assertEquals(1, Echantillonnage.facteur(4000, 3000, 0, 256))
    }

    @Test
    fun leCoteDecodeSuitLeCadreParPas() {
        assertEquals(256, PhotosEnMemoire.cote(147, 147))
        assertEquals(768, PhotosEnMemoire.cote(700, 300))
        // Un cadre sans borne (liste qui défile) : la largeur d'un écran courant.
        assertEquals(1280, PhotosEnMemoire.cote(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(1280, PhotosEnMemoire.cote(0, 0))
    }

    @Test
    fun leCacheResteSousSonPlafondEtGardeLesPlusRecentes() {
        val cache = CacheBorne<String, ByteArray>(100) { it.size.toLong() }
        cache.garder("a", ByteArray(40))
        cache.garder("b", ByteArray(40))
        cache.lire("a") // « a » redevient la plus récente
        cache.garder("c", ByteArray(40))
        assertNull("la moins récemment vue part", cache.lire("b"))
        assertEquals(80L, cache.poidsTotal)
        // Remplacer une valeur ne la compte pas deux fois.
        cache.garder("a", ByteArray(10))
        assertEquals(50L, cache.poidsTotal)
        // Plus lourde que le plafond : elle n'entre pas, et ne vide pas le reste.
        cache.garder("d", ByteArray(101))
        assertNull(cache.lire("d"))
        assertEquals(2, cache.nombre)
        cache.vider()
        assertEquals(0, cache.nombre)
        assertEquals(0L, cache.poidsTotal)
    }
}
