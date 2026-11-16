// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.FeuillePleine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * « Scanner le QR code », comme l'iOS (`ScannerQRView`) : la caméra, réglée pour les seuls
 * QR codes ; le premier qui porte une adresse referme la feuille et remplit « Votre
 * résidence » — l'adresse passe ensuite TOUJOURS par la garde de l'hôte (`OrigineAdresse.Scan`).
 * Sans caméra, ou sans le droit de s'en servir : « Caméra indisponible », et la saisie reste.
 *
 * Le droit `CAMERA` se demande à l'ouverture de la feuille, pas avant. L'image est lue sur
 * l'appareil (`LecteurQR`), jamais gardée ni envoyée.
 */
@Composable
fun FeuilleScannerQR(fermer: () -> Unit, reconnu: (String) -> Unit) {
    FeuillePleine(titre = stringResource(R.string.scanner_titre), repereOk = Reperes.SCANNER_FERMER, fermer = fermer) {
        val contexte = LocalContext.current
        val camera = remember { contexte.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
        var autorisee by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        var indisponible by remember { mutableStateOf(!camera) }
        val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
            autorisee = accordee
            indisponible = !accordee
        }
        LaunchedEffect(Unit) { if (camera && !autorisee) demande.launch(Manifest.permission.CAMERA) }
        when {
            indisponible -> EtatVide(
                titre = stringResource(R.string.scanner_indisponible),
                icone = Icons.Outlined.NoPhotography,
                explication = stringResource(R.string.scanner_indisponible_explication),
            )
            autorisee -> CameraQR(reconnu, indisponible = { indisponible = true })
        }
    }
}

/** L'aperçu de la caméra (arrière, sinon avant) et la lecture de chaque image, la plus récente seule. */
@Composable
private fun CameraQR(reconnu: (String) -> Unit, indisponible: () -> Unit) {
    val contexte = LocalContext.current
    val cycle = LocalLifecycleOwner.current
    val aJour by rememberUpdatedState(reconnu)
    val apercu = remember { PreviewView(contexte) }
    DisposableEffect(cycle) {
        val lecture = Executors.newSingleThreadExecutor()
        val principal = ContextCompat.getMainExecutor(contexte)
        val dejaLu = AtomicBoolean(false)
        val preview = Preview.Builder().build().apply { setSurfaceProvider(apercu.surfaceProvider) }
        val analyse = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analyse.setAnalyzer(lecture) { image ->
            image.use {
                if (dejaLu.get()) return@use
                val plan = it.planes[0]
                val octets = ByteArray(plan.buffer.remaining()).also(plan.buffer::get)
                val texte = LecteurQR.adresse(octets, it.width, it.height, plan.rowStride) ?: return@use
                if (dejaLu.compareAndSet(false, true)) principal.execute { aJour(texte) }
            }
        }
        val futur = ProcessCameraProvider.getInstance(contexte)
        var fournisseur: ProcessCameraProvider? = null
        var fermee = false
        futur.addListener({
            if (fermee) return@addListener
            runCatching {
                val f = futur.get().also { fournisseur = it }
                val selecteur = if (f.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                f.bindToLifecycle(cycle, selecteur, preview, analyse)
            }.onFailure { indisponible() }
        }, principal)
        onDispose {
            fermee = true
            fournisseur?.unbind(preview, analyse)
            analyse.clearAnalyzer()
            lecture.shutdown()
        }
    }
    AndroidView(factory = { apercu }, modifier = Modifier.fillMaxSize())
}
