// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.magasin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.entree.entrerDansLaResidence
import fr.coprofirst.app.partage.R as RCommun
import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Les visuels de la fiche du Play Store (#31), produits par l'app elle-même, comme
 * `LancementCaptures` de l'iOS : l'icône (512 × 512), l'image de présentation (1024 × 500)
 * et les écrans (1080 × 1920, 9:16, thème clair) d'une résidence fictive servie par le
 * serveur local. Le logo est dessiné tel quel, jamais recoloré (`LicenseRef-CoproFirst-Logo`).
 *
 * Ne tourne que sur demande (`-e captures oui`, `scripts/captures-play.sh`), qui rapatrie
 * les fichiers de `Android/data/fr.coprofirst.app/files/captures/` : ignoré partout ailleurs,
 * CI comprise.
 */
@RunWith(AndroidJUnit4::class)
class CapturesPlay {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val contexte = instrumentation.targetContext
    private val dossier: File by lazy { requireNotNull(contexte.getExternalFilesDir("captures")).apply { mkdirs() } }

    @Before
    fun seulementSurDemande() = assumeTrue(InstrumentationRegistry.getArguments().getString("captures") == "oui")

    @After
    fun fermer() = serveur.close()

    private fun enregistrer(nom: String, image: Bitmap) =
        File(dossier, "$nom.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** L'icône du magasin : les deux couches de l'icône adaptative, à fond perdu. */
    @Test
    fun icone() {
        val icone = ContextCompat.getDrawable(contexte, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val toile = Canvas(image)
        listOf(icone.background, icone.foreground).forEach { couche ->
            couche.setBounds(0, 0, 512, 512)
            couche.draw(toile)
        }
        enregistrer("icone-512", image)
    }

    /** L'image de présentation : le fond et les teintes de la charte, le logo, le nom, la promesse. */
    @Test
    fun presentation() {
        val charte = Palettes.pour(null).clair
        val image = Bitmap.createBitmap(1024, 500, Bitmap.Config.ARGB_8888)
        val toile = Canvas(image)
        toile.drawColor(charte.bg.toArgb())
        requireNotNull(ContextCompat.getDrawable(contexte, RCommun.drawable.logo)).apply {
            setBounds(96, 130, 336, 370)
            draw(toile)
        }
        val nom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = charte.text.toArgb()
            textSize = 92f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val promesse = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = charte.textMuted.toArgb()
            textSize = 38f
        }
        toile.drawText("CoproFirst", 392f, 248f, nom)
        toile.drawText("L'application de votre résidence", 396f, 312f, promesse)
        enregistrer("presentation-1024x500", image)
    }

    /** Les écrans : le fil, les accès, le profil — en 1080 × 1920, le temps des captures. */
    @Test
    fun ecrans() {
        val automate = instrumentation.uiAutomation
        automate.executeShellCommand("wm size 1080x1920").close()
        try {
            serveur.serveur.dispatcher = ResidenceFictive()
            ecran.setContent { NavigationEntree(serveur.apparence(), serveur.parcours()) }
            ecran.entrerDansLaResidence("residence.exemple.test", "Les Glycines")
            // Une carte du fil se lit d'un bloc : son texte est dans sa description.
            ecran.waitUntil(DELAI_MS) {
                ecran.onAllNodes(hasContentDescription("Fuite d'eau", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            capturer("1-fil")
            ecran.toucherTexte("Accès")
            ecran.attendreTexte("T3")
            capturer("2-acces")
            ecran.toucherTexte("Plus")
            ecran.toucherTag("plus.profil")
            ecran.attendreTexte("Camille EXEMPLE")
            capturer("3-profil")
        } finally {
            automate.executeShellCommand("wm size reset").close()
        }
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }

    private fun capturer(nom: String) {
        ecran.waitForIdle()
        enregistrer(nom, requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
    }
}
