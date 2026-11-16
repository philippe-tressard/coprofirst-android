// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.routes.ConfigApi
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.LocalSombre
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mouvement
import fr.coprofirst.app.commun.pieces.FichierProtege
import fr.coprofirst.app.session.ClientHttp
import fr.coprofirst.app.session.Installation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Les logos lus, gardés en mémoire par adresse, comme l'iOS (`LogosEnMemoire`) : un logo se lit
 * une fois par lancement ; un échec se dit `null` et se retente à la demande suivante.
 */
class LogosEnMemoire<I : Any>(private val lecteur: suspend (String) -> I?) {
    private val lus = LinkedHashMap<String, I>()
    private val verrou = Mutex()

    /** Celui déjà lu, sans lecture. */
    fun garde(adresse: String): I? = synchronized(lus) { lus[adresse] }

    suspend fun charger(adresse: String): I? = garde(adresse) ?: verrou.withLock {
        garde(adresse) ?: lecteur(adresse)?.also { image ->
            synchronized(lus) {
                lus[adresse] = image
                if (lus.size > MAX) lus.remove(lus.keys.first())
            }
        }
    }

    companion object {
        private const val MAX = 16

        /**
         * L'adresse du logo d'une installation (GET /config/logo.png, la route du contrat lue de
         * la configuration GÉNÉRÉE — jamais écrite ici), au thème sombre la variante sombre
         * quand elle est servie ; `null` sans logo téléversé : celui de CoproFirst.
         */
        fun adresse(installation: Installation, sombre: Boolean): String? {
            val base = installation.adresse?.baseApi ?: return null
            if (!installation.aUnLogo) return null
            val variante = ConfigApi.VarianteGetConfigLogoPng.SOMBRE.takeIf { sombre && installation.aUnLogoSombre }
            val config = ConfigApi(base).getConfigLogoPngRequestConfig(taille = null, variante = variante)
            val url = "$base${config.path}".toHttpUrl().newBuilder()
            config.query.forEach { (cle, valeurs) -> valeurs.forEach { url.addQueryParameter(cle, it) } }
            return url.build().toString()
        }

        /**
         * Les logos de l'app : lus par la porte des fichiers (`FichierProtege` : l'hôte de
         * l'installation seul, sans redirection, borné), par le client commun — le logo est
         * public, il se lit avant toute session (connexion, retour). Une image illisible : `null`.
         */
        val commun: LogosEnMemoire<ImageBitmap> = LogosEnMemoire { adresse ->
            withContext(Dispatchers.IO) {
                // Hors ligne, hôte injoignable, refus, image illisible : le logo de CoproFirst, jamais une chute.
                runCatching {
                    val octets = FichierProtege.lire(adresse, ClientHttp.partage, adresse)
                    BitmapFactory.decodeByteArray(octets, 0, octets.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
}

/** Les logos lus : ceux de l'app (`LogosEnMemoire.commun`) ; un test pose les siens. */
val LocalLogos = staticCompositionLocalOf { LogosEnMemoire.commun }

/**
 * Le logo de la RÉSIDENCE, comme l'iOS (`LogoResidence`, #137) : celui qu'elle a téléversé,
 * sinon celui de CoproFirst. Au thème sombre, sa variante sombre quand elle est servie. Le
 * temps de la première lecture, une tuile discrète — pas la marque de CoproFirst, qui
 * laisserait croire à un logo absent ; au changement de thème, l'ancien reste jusqu'à ce que
 * l'autre soit lu, puis un fondu les échange (250 ms, sans animation si l'appareil les coupe).
 * Décoratif : le nom de la résidence est dit à côté.
 */
@Composable
fun LogoResidence(installation: Installation?, modifier: Modifier = Modifier) {
    val sombre = LocalSombre.current
    val voulue = installation?.let { LogosEnMemoire.adresse(it, sombre) }
    val logos = LocalLogos.current
    var affichee by remember(installation?.url) { mutableStateOf<String?>(null) }
    var echec by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(voulue) {
        val adresse = voulue ?: return@LaunchedEffect
        val image = logos.charger(adresse)
        echec = if (image == null) adresse else null
        if (image != null) affichee = adresse
    }
    val montree = listOfNotNull(voulue, affichee).firstOrNull { logos.garde(it) != null }
    val etat = when {
        montree != null -> montree
        voulue == null || echec == voulue -> MARQUE
        else -> TUILE
    }
    val fondu = if (animationsCoupees()) snap<Float>() else tween(DUREE_MS, easing = Mouvement.sortie)
    // L'état se lit dans les tests : servi, la marque de CoproFirst, ou l'attente de la première lecture.
    val repere = when (etat) {
        MARQUE -> REPERE_LOGO_MARQUE
        TUILE -> REPERE_LOGO_ATTENTE
        else -> REPERE_LOGO_SERVI
    }
    Crossfade(etat, modifier.clip(RoundedCornerShape(5.dp)).testTag(repere), animationSpec = fondu, label = "logo") { quoi ->
        when (quoi) {
            MARQUE -> Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.fillMaxSize())
            TUILE -> Box(Modifier.fillMaxSize().background(LocalCouleurs.current.primaryLight))
            else -> logos.garde(quoi)?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Les repères du logo, selon ce qu'il montre. */
const val REPERE_LOGO_SERVI = "logo.residence.servi"
const val REPERE_LOGO_MARQUE = "logo.residence.marque"
const val REPERE_LOGO_ATTENTE = "logo.residence.attente"

private const val MARQUE = "marque"
private const val TUILE = "tuile"
private const val DUREE_MS = 250
