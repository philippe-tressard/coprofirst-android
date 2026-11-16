// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.tiles.renderer.TileRenderer
import fr.coprofirst.app.commun.attendreQue
import fr.coprofirst.montre.tuile.EtatTuile
import fr.coprofirst.montre.tuile.MiseEnPageTuile
import fr.coprofirst.montre.tuile.TuileCloche
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L'APERÇU de la tuile, que le manifeste exige : la tuile rendue par le moteur des tuiles,
 * sur l'écran de l'émulateur Wear OS, dans l'état que la fiche montre (« 2 nouvelles
 * alertes · dont 1 urgente »). L'image est écrite dans les sorties du test, que la CI joint
 * (« apercu-tuile ») ; elle se versionne telle quelle, jamais redessinée (#207).
 */
@RunWith(AndroidJUnit4::class)
class ApercuTuileTest {
    @get:Rule
    val ecran = createComposeRule()

    @Test
    fun laTuileSeRendEtSonApercuSEcrit() {
        val contexte = InstrumentationRegistry.getInstrumentation().targetContext
        val mesures = contexte.resources.displayMetrics
        val appareil = DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp((mesures.widthPixels / mesures.density).toInt())
            .setScreenHeightDp((mesures.heightPixels / mesures.density).toInt())
            .setScreenDensity(mesures.density)
            .setScreenShape(
                if (contexte.resources.configuration.isScreenRound) DeviceParametersBuilders.SCREEN_SHAPE_ROUND
                else DeviceParametersBuilders.SCREEN_SHAPE_RECT,
            )
            .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
            .build()
        val mise = LayoutElementBuilders.Layout.fromLayoutElement(
            MiseEnPageTuile.de(contexte, appareil, EtatTuile.Compte(nonLues = 2, urgentes = 1)),
        )
        var cadre: FrameLayout? = null
        ecran.setContent {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).also { vue ->
                        cadre = vue
                        // Le moteur lit ses polices dans le thème de son contexte : celui des tuiles, que la
                        // montre lui donne, et que l'activité du test n'a pas.
                        val theme = ContextThemeWrapper(ctx, androidx.wear.protolayout.renderer.R.style.ProtoLayoutBaseTheme)
                        TileRenderer(theme, ContextCompat.getMainExecutor(ctx)) {}.inflateAsync(mise, TuileCloche.ressources(), vue)
                    }
                },
                modifier = Modifier.fillMaxSize().testTag("apercu.tuile"),
            )
        }
        ecran.attendreQue("la tuile rendue") { (cadre?.childCount ?: 0) > 0 }
        ecran.waitForIdle()

        val image = ecran.onNodeWithTag("apercu.tuile").captureToImage().asAndroidBitmap()
        assertTrue("aperçu vide", image.width > 0 && image.height > 0)
        TestStorage().openOutputFile("apercu_tuile.png").use { sortie -> image.compress(Bitmap.CompressFormat.PNG, 100, sortie) }
    }
}
