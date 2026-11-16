// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree.vitrine

import android.content.Context
import fr.coprofirst.app.session.ClientHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.CacheControl
import okhttp3.Request
import java.io.File

/**
 * La vitrine lit le contenu de coprofirst.fr (#153), comme l'iOS (`Vitrine`) : une seule
 * source pour le site et l'app. Au lancement, la dernière lecture gardée en cache, à défaut
 * la copie embarquée ; puis une lecture du site, une fois par lancement réussie, qui remplace
 * l'une et l'autre si elle donne des pages complètes. Un échec (hors ligne, réponse refusée,
 * contenu incomplet) ne change rien à l'écran.
 *
 * Ce n'est pas l'API de CoproFirst (client généré) : un fichier statique du site, lu sans
 * cookie ni jeton par le client commun (sans redirection), dont l'app n'affiche que le texte.
 */
class VitrineDuSite(
    embarquees: PagesVitrine,
    /** Lit le site : le statut et le corps (au plus [TAILLE_MAX] + 1 octets) ; `null` sans réponse. */
    private val lecteur: (suspend () -> Pair<Int, ByteArray>?)?,
    private val cache: File?,
) {
    private val _pages = MutableStateFlow(cache?.let(::lireCache) ?: embarquees)
    val pages: StateFlow<PagesVitrine> = _pages.asStateFlow()
    private val verrou = Mutex()
    private var lue = false

    suspend fun actualiser() = verrou.withLock {
        val lire = lecteur
        if (lue || lire == null) return@withLock
        val (statut, corps) = lire() ?: return@withLock
        if (statut != 200 || corps.size > TAILLE_MAX) return@withLock
        val lues = PagesVitrine.lire(corps.decodeToString()) ?: return@withLock
        lue = true
        _pages.value = lues
        cache?.let { ecrireCache(it, corps) }
    }

    private fun lireCache(fichier: File): PagesVitrine? =
        runCatching { fichier.takeIf { it.isFile }?.readText()?.let(PagesVitrine::lire) }.getOrNull()

    /** Écrit puis renomme : un cache à moitié écrit ne se relit jamais. */
    private fun ecrireCache(fichier: File, corps: ByteArray) {
        runCatching {
            val provisoire = File(fichier.parentFile, "${fichier.name}.part")
            provisoire.writeBytes(corps)
            if (!provisoire.renameTo(fichier)) provisoire.delete()
        }
    }

    companion object {
        const val ADRESSE = "https://coprofirst.fr/contenu.json"

        /** Au-delà, ce n'est pas le contenu de l'accueil (16 Ko le 08/11/2026). */
        const val TAILLE_MAX = 1_000_000

        @Volatile private var deLApp: VitrineDuSite? = null

        /** La vitrine de l'app : le site, par le client commun, et le cache de l'appareil. */
        fun deLApp(contexte: Context): VitrineDuSite = deLApp ?: synchronized(this) {
            deLApp ?: VitrineDuSite(
                PagesVitrine.embarquees(contexte),
                lecteur = { lire() },
                cache = File(contexte.applicationContext.cacheDir, "contenu-vitrine.json"),
            ).also { deLApp = it }
        }

        /**
         * La lecture du site — la SEULE requête de l'app hors de l'API et de ses fichiers
         * (dispense nommée de `verifier-depot.sh`) : un GET public, sans cookie, sans
         * redirection, qui n'envoie rien de l'utilisateur.
         */
        suspend fun lire(client: Call.Factory = ClientHttp.partage, adresse: String = ADRESSE): Pair<Int, ByteArray>? = withContext(Dispatchers.IO) {
            val requete = Request.Builder().url(adresse).cacheControl(CacheControl.FORCE_NETWORK).build()
            runCatching {
                client.newCall(requete).execute().use { reponse ->
                    val source = reponse.body.source().apply { request(TAILLE_MAX + 1L) }
                    reponse.code to source.buffer.readByteArray(minOf(source.buffer.size, TAILLE_MAX + 1L))
                }
            }.getOrNull()
        }
    }
}
