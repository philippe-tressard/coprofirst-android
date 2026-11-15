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
 * Le côté SERVEUR de l'Espace CS pour les tests (JVM et d'interface) : chaque route sert sa
 * réponse de `FixturesEspaceCS`, et les gestes la changent comme le serveur (un compte ou
 * une commande traités quittent la file ; traités deux fois, refusés). Les tests branchent
 * `repondre` sur leur serveur local ; l'app passe par son vrai client.
 */
class ResidenceSimuleeCS {
    private val comptes = lire(FixturesEspaceCS.COMPTES)
    private val commandes = lire(FixturesEspaceCS.COMMANDES)

    /** Les requêtes reçues : « MÉTHODE /chemin corps ». */
    val recues = mutableListOf<String>()

    /** Le statut à rendre à la prochaine lecture du parc (`null` : la réponse servie). */
    var parcEnEchec: Int? = null

    @Synchronized
    fun repondre(methode: String, chemin: String, corps: String): Pair<Int, String> {
        recues += "$methode $chemin $corps".trim()
        val route = chemin.substringBefore('?').removePrefix("/api")
        compteTraite.matchEntire(route)?.let { m -> return traiterCompte(m.groupValues[1].toInt(), corps) }
        commandeTraitee.matchEntire(route)?.let { m -> return traiterCommande(m.groupValues[1].toInt(), corps) }
        if (methode == "POST" && accueil.matches(route)) return 200 to FixturesEspaceCS.ACCUEIL
        return when ("$methode $route") {
            "GET /admin/comptes-en-attente" -> 200 to JsonArray(comptes).toString()
            "GET /admin/commandes-acces" -> 200 to JsonArray(commandes).toString()
            "GET /acces/admin/vigiks" -> parcEnEchec?.let { it to """{"detail": "Accès réservé au conseil syndical"}""" }
                ?: (200 to FixturesEspaceCS.VIGIKS)
            "GET /acces/admin/telecommandes" -> 200 to FixturesEspaceCS.TELECOMMANDES
            else -> 404 to """{"detail": "Not Found"}"""
        }
    }

    private fun traiterCompte(id: Int, corps: String): Pair<Int, String> {
        if (comptes.none { it.id() == id }) return 409 to """{"detail": "Compte déjà traité"}"""
        comptes.removeAll { it.id() == id }
        val valider = Json.parseToJsonElement(corps).jsonObject["action"]?.jsonPrimitive?.content == "valider"
        return 200 to FixturesEspaceCS.traite(id, valider)
    }

    private fun traiterCommande(id: Int, corps: String): Pair<Int, String> {
        if (commandes.none { it.id() == id }) return 409 to """{"detail": "Commande déjà traitée"}"""
        commandes.removeAll { it.id() == id }
        val accepter = Json.parseToJsonElement(corps).jsonObject["action"]?.jsonPrimitive?.content == "accepter"
        return 200 to FixturesEspaceCS.commandeTraitee(accepter)
    }

    private companion object {
        val compteTraite = Regex("""/admin/comptes/(\d+)/traiter""")
        val commandeTraitee = Regex("""/admin/commandes-acces/(\d+)/traiter""")
        val accueil = Regex("""/admin/utilisateurs/\d+/accueil-arrivant""")

        fun lire(json: String) = Json.parseToJsonElement(json).jsonArray.toMutableList()

        fun kotlinx.serialization.json.JsonElement.id() = jsonObject["id"]?.jsonPrimitive?.int
    }
}
