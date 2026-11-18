// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Les « Écrans du hall » côté SERVEUR, pour les tests (#283) : GET, POST et DELETE de
 * /ecrans, comme le serveur — un code que nul écran n'affiche est refusé (422, sa phrase),
 * un écran retiré quitte la liste. Des écrans inventés. Débogage seulement.
 */
class EcransSimulesCS {
    private val ecrans = Json.parseToJsonElement(FixturesEcransCS.ECRANS).jsonArray.toMutableList()

    /** La réponse d'une route des écrans ; `null` : la route n'en est pas une. */
    fun repondre(methode: String, route: String, corps: String): Pair<Int, String>? {
        ecran.matchEntire(route)?.let { m ->
            if (methode != "DELETE") return null
            val id = m.groupValues[1].toInt()
            if (!ecrans.removeAll { it.jsonObject["id"]?.jsonPrimitive?.int == id }) return 404 to """{"detail": "Écran introuvable"}"""
            return 204 to ""
        }
        if (route != "/ecrans") return null
        return when (methode) {
            "GET" -> 200 to JsonArray(ecrans).toString()
            "POST" -> {
                val code = Json.parseToJsonElement(corps).jsonObject["code_court"]?.jsonPrimitive?.content
                if (code == FixturesEcransCS.CODE_AFFICHE) 201 to FixturesEcransCS.APPAIRE else 422 to FixturesEcransCS.CODE_INCONNU
            }
            else -> null
        }
    }

    private companion object {
        val ecran = Regex("""/ecrans/(\d+)""")
    }
}

/** Les réponses du serveur pour les écrans du hall, telles que les routes les renvoient. Tout est inventé. */
object FixturesEcransCS {
    /** Le code qu'affiche l'écran simulé, tel que l'app l'envoie (huit lettres). */
    const val CODE_AFFICHE = "BCDFGHJK"

    /** GET /ecrans → `[EcranLu]`. Le 7 a lu ce matin ; le 8, jamais, et il est échu. */
    const val ECRANS = """
        [
          {"id": 7, "nom": "Hall du bâtiment A", "perimetre": "bat_a", "perimetre_libelle": "Bât. A",
           "cree_le": "2026-11-02T09:00:00Z", "vu_le": "2026-11-18T07:42:00Z", "expire_le": "2027-11-02T09:00:00Z", "echu": false},
          {"id": 8, "nom": "Loge", "perimetre": "copropriete", "perimetre_libelle": "",
           "cree_le": "2025-10-01T09:00:00Z", "vu_le": null, "expire_le": "2026-10-01T09:00:00Z", "echu": true}
        ]
    """

    /** POST /ecrans → `AppairageConfirme`. */
    const val APPAIRE = """{"nom": "Hall du bâtiment 2", "perimetre": "copropriete", "perimetre_libelle": ""}"""

    /** Le refus du serveur (422) → erreur. */
    const val CODE_INCONNU = """{"detail": "Ce code ne correspond à aucun écran en attente : vérifiez-le, ou relancez l'appairage sur l'écran."}"""
}
