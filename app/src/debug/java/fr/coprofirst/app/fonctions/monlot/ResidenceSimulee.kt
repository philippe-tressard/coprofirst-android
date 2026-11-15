// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Le côté SERVEUR de « Mes lots & accès » pour les tests (JVM et d'interface) : chaque
 * route sert sa réponse de `FixturesMonLot`, et les gestes la changent comme le serveur
 * (un accès déclaré s'ajoute, un accès perdu le reste). Les tests branchent `repondre` sur
 * leur serveur local ; l'app, elle, passe par son vrai client.
 */
class ResidenceSimulee(var moi: String = FixturesMonLot.moiConseil, var monBail: String = "null") {
    private val vigiks = lire(FixturesMonLot.MES_VIGIKS)
    private val telecommandes = lire(FixturesMonLot.MES_TELECOMMANDES)

    /** Les requêtes reçues : « MÉTHODE /chemin corps ». */
    val recues = mutableListOf<String>()

    @Synchronized
    fun repondre(methode: String, chemin: String, corps: String): Pair<Int, String> {
        recues += "$methode $chemin $corps".trim()
        val route = chemin.substringBefore('?').removePrefix("/api")
        perdu.matchEntire(route)?.let { m ->
            val liste = if (m.groupValues[1] == "vigiks") vigiks else telecommandes
            val i = liste.indexOfFirst { it.jsonObject["id"]?.jsonPrimitive?.int == m.groupValues[2].toInt() }
            if (i < 0) return 404 to """{"detail": "Accès introuvable"}"""
            liste[i] = JsonObject(liste[i].jsonObject + ("statut" to JsonPrimitive("perdu")))
            return 200 to FixturesMonLot.PERDU
        }
        return when ("$methode $route") {
            "GET /auth/me" -> 200 to moi
            "GET /lots/mes-lots" -> 200 to FixturesMonLot.MES_LOTS
            "GET /acces/mes-vigiks" -> 200 to JsonArray(vigiks).toString()
            "GET /acces/mes-telecommandes" -> 200 to JsonArray(telecommandes).toString()
            "GET /bailleur/mes-acces-recus" -> 200 to FixturesMonLot.ACCES_RECUS
            "GET /bailleur/mon-bail" -> 200 to monBail
            "GET /bailleur/tous-les-baux", "GET /bailleur/mes-baux" -> 200 to FixturesMonLot.BAUX
            "POST /acces/commandes" -> commander(corps)
            "POST /acces/declarer-badge" -> declarer(corps)
            else -> if (route.matches(recuperer)) 200 to "[]" else 404 to """{"detail": "Not Found"}"""
        }
    }

    /** La règle du serveur : un lot qui n'est pas le sien est refusé. */
    private fun commander(corps: String): Pair<Int, String> {
        val lot = Json.parseToJsonElement(corps).jsonObject["lot_id"]?.jsonPrimitive?.int
        val siens = Json.parseToJsonElement(FixturesMonLot.MES_LOTS).jsonArray.map { it.jsonObject["id"]?.jsonPrimitive?.int }
        return if (lot in siens) 201 to FixturesMonLot.COMMANDE else 403 to """{"detail": "Vous n'êtes pas associé à ce lot"}"""
    }

    private fun declarer(corps: String): Pair<Int, String> {
        val demande = Json.parseToJsonElement(corps).jsonObject
        val code = demande["code"]?.jsonPrimitive?.content.orEmpty()
        val type = demande["type"]?.jsonPrimitive?.content.orEmpty()
        if ((vigiks + telecommandes).any { it.jsonObject["code"]?.jsonPrimitive?.content == code }) {
            return 409 to """{"detail": "${if (type == "vigik") "Vigik" else "Télécommande"} déjà enregistré sur votre compte"}"""
        }
        val id = 100 + vigiks.size + telecommandes.size
        (if (type == "vigik") vigiks else telecommandes) += buildJsonObject {
            put("id", id)
            put("code", code)
            put("statut", "actif")
            put("chez_locataire", false)
            put("perimetre_cible", JsonArray(emptyList()))
            put("perimetre_libelle", "")
            put("cree_le", "2026-11-15T10:00:00")
        }
        return 201 to FixturesMonLot.declare(code, type, importResolu = code.startsWith("IMPORT"))
    }

    private companion object {
        val perdu = Regex("""/acces/(vigiks|telecommandes)/(\d+)/perdu""")
        val recuperer = Regex("""/bailleur/baux/\d+/recuperer-acces""")

        fun lire(json: String) = Json.parseToJsonElement(json).jsonArray.toMutableList()
    }
}
