// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import fr.coprofirst.app.session.AbonneSession
import fr.coprofirst.app.session.LecturesGardees
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Où l'on a refermé la résidence (#242) : la page, la page de « Plus », l'élément ouvert — à côté
 * de sa liste (`enColonne`) ou poussé —, le jour du Fil, la page de « Ma résidence » en face de
 * son sommaire (`page`, par son nom, #287).
 */
@Serializable
data class MarquePage(
    val onglet: String,
    val pageDePlus: String? = null,
    val ficheNature: String? = null,
    val ficheId: String? = null,
    val jour: String? = null,
    val enColonne: Boolean = false,
    val page: String? = null,
)

/** Le disque du marque-page, une valeur par clé ; `null` l'efface. */
interface StockageMarquePage {
    fun lire(cle: String): String?

    fun ecrire(cle: String, valeur: String?)
}

/**
 * Le MARQUE-PAGE du pliable (#242), comme l'iOS (coprofirst-ios#119, décision du 17/11/2026) : on
 * referme sur une page, on rouvre au même endroit — même après l'arrêt de l'app par le système.
 * Gardé sur l'appareil SEULEMENT, par résidence (l'empreinte de son adresse, aucun nom en clair),
 * et seulement si l'appareil a une CHARNIÈRE : un téléphone ordinaire n'écrit rien et s'ouvre sur
 * le fil, comme avant. Effacé quand la résidence se ferme (déconnexion, « Oublier toutes les
 * résidences », session refusée) : un abonné au cycle de la session (`AbonneSession`, #150).
 * Exclu de toute sauvegarde (`regles_*.xml` : aucune préférence ne quitte l'appareil).
 */
class MarquesPages(private val stockage: StockageMarquePage, private val pliable: Boolean) : AbonneSession {
    fun lire(residence: String): MarquePage? {
        if (!pliable) return null
        val texte = stockage.lire(cle(residence)) ?: return null
        return runCatching { json.decodeFromString<MarquePage>(texte) }.getOrNull()
    }

    fun ecrire(residence: String, marque: MarquePage) {
        if (pliable) stockage.ecrire(cle(residence), json.encodeToString(marque))
    }

    fun effacer(residence: String) = stockage.ecrire(cle(residence), null)

    override suspend fun residenceOuverte(session: SessionOuverte) = Unit

    override suspend fun residenceFermee(session: SessionOuverte) = effacer(session.installation.url)

    private fun cle(residence: String) = LecturesGardees.empreinte(residence)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var instance: MarquesPages? = null

        /** Les marque-pages de l'app : un par processus, sur le disque de l'appareil. */
        fun de(contexte: Context): MarquesPages = instance ?: synchronized(this) {
            instance ?: MarquesPages(StockageMarquePagePreferences(contexte.applicationContext), appareilPliable(contexte)).also { instance = it }
        }

        /** L'appareil a-t-il une charnière ? (un capteur d'angle, Android 11 et plus). */
        fun appareilPliable(contexte: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                contexte.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    }
}

private class StockageMarquePagePreferences(contexte: Context) : StockageMarquePage {
    private val preferences = contexte.getSharedPreferences("marque-page", Context.MODE_PRIVATE)

    override fun lire(cle: String): String? = preferences.getString(cle, null)

    override fun ecrire(cle: String, valeur: String?) = preferences.edit { if (valeur == null) remove(cle) else putString(cle, valeur) }
}

/** Les marque-pages, fournis par l'activité ; hors d'elle (tests, aperçus) : aucun. */
val LocalMarquesPages = staticCompositionLocalOf<MarquesPages?> { null }
