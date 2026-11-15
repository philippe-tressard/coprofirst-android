// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les pièces jointes, rangées et nommées comme le site (`#lib/fichiers.ts`) — les cas de
 * l'iOS (`PiecesJointesTests`, `FichierServiTests`). Avant ce rangement, l'iOS jetait les
 * documents : une affaire avec un devis PDF ne le montrait nulle part.
 */
class FichiersTest {
    @Test
    fun lesDocumentsNeSontPasJetes() {
        val pieces = PiecesJointes(
            listOf("/uploads/a.jpg", "/uploads/devis.pdf", "/uploads/b.HEIC", "/uploads/c.avif", "/uploads/liste.xlsx"),
        )
        assertEquals(listOf("/uploads/a.jpg", "/uploads/b.HEIC", "/uploads/c.avif"), pieces.photos)
        assertEquals(listOf("/uploads/devis.pdf", "/uploads/liste.xlsx"), pieces.documents)
    }

    @Test
    fun uneMemeAdresseServieDeuxFoisNeCompteQuUneFois() {
        // Le serveur peut servir une photo dans `photos_urls` ET dans `fichiers_urls`.
        val pieces = PiecesJointes(listOf("/uploads/a.jpg", "", "/uploads/a.jpg", "/uploads/n.pdf", "/uploads/n.pdf"))
        assertEquals(listOf("/uploads/a.jpg"), pieces.photos)
        assertEquals(listOf("/uploads/n.pdf"), pieces.documents)
        assertEquals(2, pieces.nombre)
        assertTrue(PiecesJointes(emptyList()).vide)
        assertEquals(PiecesJointes.AUCUNE, PiecesJointes(listOf("")))
    }

    @Test
    fun lesPiecesSeDisentAuLecteurDEcran() {
        assertEquals("1 photo", PiecesJointes(listOf("/a.jpg")).enClair)
        assertEquals("2 photos, 1 pièce jointe", PiecesJointes(listOf("/a.jpg", "/b.png", "/c.pdf")).enClair)
        assertEquals("2 pièces jointes", PiecesJointes(listOf("/c.pdf", "/d.docx")).enClair)
        assertNull(PiecesJointes.AUCUNE.enClair)
    }

    @Test
    fun leNomMontreEstCeluiDuSite() {
        val televerse = "/uploads/tickets/0123456789abcdef0123456789abcdef_Devis%20plombier.pdf"
        assertEquals("Devis plombier.pdf", Fichiers.nomAffiche(televerse))
        assertEquals("Devis plombier", Fichiers.nomSansExtension(televerse))
        assertEquals("PDF", Fichiers.typeAffiche(televerse))
        // Un préfixe qui n'est pas hexadécimal, ou trop court, reste dans le nom.
        assertTrue(Fichiers.nomAffiche("/uploads/x/0123456789abcdef0123456789abcdeZ_a.pdf").startsWith("0123"))
        assertEquals("abc_a.pdf", Fichiers.nomAffiche("/uploads/x/abc_a.pdf"))
        // Un nom qui n'est QUE le préfixe ne devient pas vide.
        assertEquals("0123456789abcdef0123456789abcdef_", Fichiers.nomAffiche("/uploads/0123456789abcdef0123456789abcdef_"))
        // La requête et une barre finale ne font pas le nom ; un « + » reste un « + ».
        assertEquals("plan.pdf", Fichiers.nomAffiche("https://r.exemple.test/uploads/plan.pdf?v=2"))
        assertEquals("a+b.pdf", Fichiers.nomAffiche("/uploads/a+b.pdf"))
        assertEquals("dossier", Fichiers.nomAffiche("/uploads/dossier/"))
    }

    @Test
    fun unEncodageInvalideGardeLeNomBrut() {
        // Comme `removingPercentEncoding` : une séquence incomplète ou hors UTF-8 ne se décode pas.
        assertEquals("a%2.pdf", Fichiers.nomAffiche("/u/a%2.pdf"))
        assertEquals("a%zz.pdf", Fichiers.nomAffiche("/u/a%zz.pdf"))
        assertEquals("a%FF.pdf", Fichiers.nomAffiche("/u/a%FF.pdf"))
        assertEquals("a%+1.pdf", Fichiers.nomAffiche("/u/a%+1.pdf"))
        assertEquals("Été.pdf", Fichiers.nomAffiche("/u/%C3%89t%C3%A9.pdf"))
    }

    @Test
    fun leTypeMontreSuitLesFamillesDuSite() {
        mapOf(
            "/u/a.docx" to "WORD", "/u/a.XLS" to "EXCEL", "/u/a.pptx" to "POWERPOINT", "/u/a.txt" to "TEXTE",
            "/u/a.jpeg" to "JPG", "/u/a.md" to "MD", "/u/sans-extension" to "FICHIER", "/u/.cache" to "FICHIER",
        ).forEach { (chemin, type) -> assertEquals(chemin, type, Fichiers.typeAffiche(chemin)) }
    }

    @Test
    fun lesExtensionsDImageSontCellesDuSite() {
        // Générées : `jpe?g` et `tiff?` y sont dépliés.
        assertTrue(FichiersDuSite.extensionsImage.containsAll(setOf("jpg", "jpeg", "tif", "tiff", "heif", "avif")))
        assertFalse(Fichiers.estImage("/uploads/devis.pdf"))
        assertFalse(Fichiers.estImage("/uploads/dossier.jpg/fichier"))
        assertTrue(Fichiers.estImage("/uploads/PHOTO.JPG"))
    }

    @Test
    fun unNomSurNeSortPasDeSonDossier() {
        assertEquals("-PV AG 2026.pdf", Fichiers.nomSur("../PV AG 2026.pdf", "document-7"))
        assertEquals("rapport-3", Fichiers.nomSur(null, "rapport-3"))
        assertEquals("rapport-3", Fichiers.nomSur(" .. ", "rapport-3"))
        assertEquals("a-b-c-d.pdf", Fichiers.nomSur("a/b\\c:d.pdf", "x"))
        assertEquals("a-b.pdf", Fichiers.nomSur("a\u0000b.pdf", "x"))
        assertEquals("a-b.pdf", Fichiers.nomSur("a‎b.pdf", "x"))
    }

    @Test
    fun unNomTropLongSeRaccourcitEnGardantSonExtension() {
        val long = "x".repeat(300) + ".pdf"
        val borne = PiecesLocales.borner(long)
        assertEquals(120, borne.length)
        assertTrue(borne.endsWith(".pdf"))
        assertEquals("court.pdf", PiecesLocales.borner("court.pdf"))
        assertEquals(120, PiecesLocales.borner("y".repeat(300)).length)
    }
}
