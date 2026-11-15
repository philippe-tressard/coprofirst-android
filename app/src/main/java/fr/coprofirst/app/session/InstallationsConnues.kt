// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Où se gardent les résidences connues — une chaîne, sur l'appareil seul. */
interface StockageResidences {
    fun lire(): String?
    fun ecrire(valeur: String)
}

/** Les préférences privées de l'app — exclues de toute sauvegarde (`regles_*.xml`). */
class StockagePreferences(contexte: Context) : StockageResidences {
    private val preferences = contexte.getSharedPreferences("residences", Context.MODE_PRIVATE)
    override fun lire(): String? = preferences.getString(CLE, null)
    override fun ecrire(valeur: String) = preferences.edit { putString(CLE, valeur) }

    private companion object {
        const val CLE = "installations-connues"
    }
}

/**
 * Les résidences déjà ouvertes sur cet appareil, la plus récente en tête : leur adresse,
 * leur nom, leur contrat. La dernière pré-remplit l'écran de connexion, comme sur iOS. Pas d'annuaire — une résidence n'apparaît ici que si
 * l'utilisateur l'a donnée. Rien n'est envoyé ailleurs.
 */
class InstallationsConnues(private val stockage: StockageResidences) {
    var liste: List<Installation> = lireListe()
        private set

    /** Retient une résidence ouverte : en tête, une seule fois par adresse. */
    fun retenir(installation: Installation) {
        liste = listOf(installation) + liste.filterNot { it.url == installation.url }
        enregistrer()
    }

    /** « Oublier toutes les résidences » : comme une app neuve. */
    fun vider() {
        liste = emptyList()
        enregistrer()
    }

    private fun lireListe(): List<Installation> {
        val texte = stockage.lire() ?: return emptyList()
        // Un stockage illisible ne fait pas tomber l'app : on repart d'une liste vide.
        val lue = runCatching { json.decodeFromString(serialiseur, texte) }.getOrDefault(emptyList())
        // Une adresse qui ne se relit plus normalisée n'est pas gardée.
        return lue.filter { it.adresse != null }.distinctBy { it.url }
    }

    private fun enregistrer() = stockage.ecrire(json.encodeToString(serialiseur, liste))

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val serialiseur = ListSerializer(Installation.serializer())
    }
}
