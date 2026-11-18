// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.audience

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import fr.coprofirst.app.session.audience.CheminDuSite
import fr.coprofirst.app.session.audience.MesureAudience
import fr.coprofirst.app.session.audience.ServiceAudienceApi
import fr.coprofirst.app.session.audience.TraitsAppareil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * La mesure d'audience de l'app (#29), branchée dans la navigation (#144) : posée à la
 * racine (`NavigationEntree`), lue par chaque écran qui se dit vu. `null` : rien ne se note
 * (aperçus, tests qui n'en veulent pas).
 */
val LocalMesureAudience = staticCompositionLocalOf<MesureAudience?> { null }

/** UNE mesure par processus, dans la portée de l'application : elle survit aux écrans. */
object AudienceDeLApp {
    @Volatile
    private var instance: MesureAudience? = null

    /** La mesure de l'app ; la classe de l'appareil se relit à chaque lot (#77). */
    fun de(contexte: Context): MesureAudience = instance ?: synchronized(this) {
        instance ?: contexte.applicationContext.let { application ->
            MesureAudience(
                ServiceAudienceApi { TraitsAppareil.de(application).classe() },
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            )
        }.also { instance = it }
    }
}

/**
 * Un écran vu, comme `.pageVue` de l'iOS : son chemin sur le site (`CheminDuSite`) se note à
 * l'apparition, et à chaque changement (un autre onglet). `null` : rien à noter.
 */
@Composable
fun PageVue(chemin: String?) {
    val mesure = LocalMesureAudience.current ?: return
    LaunchedEffect(mesure, chemin) { mesure.noter(chemin) }
}

/** Les écrans de l'app qui ont leur page sur le site, hors du menu. */
object CheminsDeLApp {
    /** Une fiche ouverte par la porte (`Destination.Element`) : affaire ou sondage. */
    fun fiche(nature: String?, id: String?): String? = id?.toIntOrNull()?.let {
        when (nature) {
            "affaire" -> CheminDuSite.affaire(it)
            "sondage" -> CheminDuSite.sondage(it)
            else -> null
        }
    }
}
