// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Où se garde le choix du thème — une chaîne, sur l'appareil seul. */
interface StockageApparence {
    fun lire(): String?
    fun ecrire(valeur: String)
}

/** Les préférences privées de l'app — exclues de toute sauvegarde (`regles_*.xml`). */
class StockageApparencePreferences(contexte: Context) : StockageApparence {
    private val preferences = contexte.getSharedPreferences("appareil", Context.MODE_PRIVATE)
    override fun lire(): String? = preferences.getString(TablesCommunes.CLE_THEME, null)
    override fun ecrire(valeur: String) = preferences.edit { putString(TablesCommunes.CLE_THEME, valeur) }
}

/**
 * Le thème de l'APPAREIL, comme l'iOS (Profil › Apparence) : « Automatique », « Clair »
 * ou « Sombre » (`OPTIONS_THEME`, générées). Le Profil le choisit ; la racine l'applique.
 */
class Apparence(private val stockage: StockageApparence) {
    private val _choix = MutableStateFlow(valide(stockage.lire()))
    val choix: StateFlow<String> = _choix.asStateFlow()

    val options: List<OptionTheme> get() = TablesCommunes.optionsTheme

    fun choisir(valeur: String) {
        val v = valide(valeur)
        stockage.ecrire(v)
        _choix.value = v
    }

    /** Sombre ou non, pour ce choix — `auto` suit l'appareil (`systemeSombre`). */
    fun sombre(choix: String, systemeSombre: Boolean): Boolean = when (choix) {
        CLAIR -> false
        SOMBRE -> true
        else -> systemeSombre
    }

    private fun valide(valeur: String?): String =
        valeur?.takeIf { v -> options.any { it.valeur == v } } ?: TablesCommunes.THEME_PAR_DEFAUT

    companion object {
        const val CLAIR = "clair"
        const val SOMBRE = "sombre"
    }
}
