// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.attendre
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.miseenpage.EnFenetreLarge
import fr.coprofirst.app.commun.miseenpage.OccuperLaColonneDeFiche
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * #274, comme `DocumentView` de l'iOS : un document s'ouvre DANS l'app — « Le document se
 * télécharge. », puis ses pages ou son image, avec « Partager » et « Ouvrir dans une autre app ».
 * En fenêtre étroite dans une feuille (« OK » la ferme) ; en fenêtre large, à côté de la liste,
 * sans « OK ».
 */
@RunWith(AndroidJUnit4::class)
class VisionneuseInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private val contexte = InstrumentationRegistry.getInstrumentation().targetContext
    private val ouverture = Ouverture(
        contexte,
        SessionOuverte(Installation("https://residence.test", "Les Glycines"), SessionsParHote()),
        MainScope(),
    ) {}

    private fun montrer(document: DocumentMontre?, large: Boolean = false) {
        ecran.setContent {
            ThemeCoproFirst {
                if (large) {
                    EnFenetreLarge {
                        Column {
                            OccuperLaColonneDeFiche()
                            Visionneuse(ouverture)
                        }
                    }
                } else {
                    Visionneuse(ouverture)
                }
            }
        }
        ecran.runOnIdle { ouverture.montre = document }
    }

    @Test
    fun unPdfMontreSesPagesDansLApp() {
        montrer(DocumentMontre(Ouverture.cleDocument(7), "Règlement de copropriété", pdf(pages = 2)))
        ecran.attendreTexte("Règlement de copropriété")
        ecran.attendre(hasTestTag("$REPERE_PAGE.0") and hasContentDescription("Page 1 sur 2"))
        ecran.attendreTag(REPERE_PARTAGER)
        ecran.attendreTag(REPERE_AUTRE_APP)
        ecran.toucherTag(REPERE_FERMER)
        ecran.runOnIdle { assertNull("« OK » ferme le document", ouverture.montre) }
    }

    @Test
    fun pendantLeTelechargementLaPhraseDeLIOS() {
        montrer(DocumentMontre(Ouverture.cleDocument(7), "Règlement de copropriété", null))
        ecran.attendreTag(REPERE_TELECHARGEMENT)
        ecran.attendreTexte("Le document se télécharge.")
        assertEquals("rien à partager avant le fichier", 0, ecran.onAllNodes(hasTestTag(REPERE_PARTAGER)).fetchSemanticsNodes().size)
    }

    @Test
    fun uneImageSeMontreEntiere() {
        montrer(DocumentMontre(Ouverture.cleDocument(8), "Plan du sous-sol", image()))
        ecran.attendre(hasContentDescription("Plan du sous-sol"))
    }

    @Test
    fun enFenetreLargeLeDocumentSeTientACoteDeLaListe() {
        montrer(DocumentMontre(Ouverture.cleDocument(7), "Règlement de copropriété", pdf(pages = 1)), large = true)
        ecran.attendre(hasTestTag("$REPERE_PAGE.0") and hasContentDescription("Page 1 sur 1"))
        assertEquals("à plat, sans « OK »", 0, ecran.onAllNodes(hasTestTag(REPERE_FERMER)).fetchSemanticsNodes().size)
    }

    /** Un PDF de `pages` pages A4, écrit dans le cache des tests. */
    private fun pdf(pages: Int): File {
        val document = PdfDocument()
        repeat(pages) { rang ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, rang + 1).create())
            document.finishPage(page)
        }
        return File(contexte.cacheDir, "visionneuse-test.pdf").also { fichier ->
            fichier.outputStream().use { document.writeTo(it) }
            document.close()
        }
    }

    /** Une image PNG de 400 × 300, écrite dans le cache des tests. */
    private fun image(): File = File(contexte.cacheDir, "visionneuse-test.png").also { fichier ->
        fichier.outputStream().use { Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
