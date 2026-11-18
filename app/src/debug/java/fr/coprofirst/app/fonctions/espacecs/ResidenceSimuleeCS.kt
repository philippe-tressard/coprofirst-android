// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.app.commun.perimetres.FixturesPerimetres
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

    /** L'annuaire du conseil et du syndic, que les PUT réécrivent. */
    val annuaire = AnnuaireSimuleCS()

    /** Les écrans du hall (#283), que POST et DELETE changent. */
    val ecrans = EcransSimulesCS()

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
        annuaire.repondre(methode, route, corps)?.let { return it }
        ecrans.repondre(methode, route, corps)?.let { return it }
        annonceGeste.matchEntire(route)?.let { m -> return gesteAnnonce(methode, m.groupValues[1].toInt(), m.groupValues[2]) }
        return when ("$methode $route") {
            "GET /admin/comptes-en-attente" -> 200 to JsonArray(comptes).toString()
            "GET /admin/commandes-acces" -> 200 to JsonArray(commandes).toString()
            "GET /acces/admin/vigiks" -> parcEnEchec?.let { it to """{"detail": "Accès réservé au conseil syndical"}""" }
                ?: (200 to FixturesEspaceCS.VIGIKS)
            "GET /acces/admin/telecommandes" -> 200 to FixturesEspaceCS.TELECOMMANDES
            "GET /courriels-affaires" -> 200 to FixturesAnnoncesCourriels.JOURNAL
            "GET /suivi-affaires" -> 200 to if (chemin.contains("mode=entretien")) FixturesReporting.ENTRETIEN else FixturesReporting.AFFAIRES
            "GET /reporting/renouvellements" -> 200 to FixturesReporting.RENOUVELLEMENTS
            "GET /perimetres" -> 200 to FixturesPerimetres.ARBRE
            "POST /perimetres/libelle" -> {
                val codes = Json.parseToJsonElement(corps).jsonObject["codes"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                200 to FixturesPerimetres.libelle(codes)
            }
            "GET /annonces-hall" -> 200 to if (chemin.contains("archivees=true")) {
                FixturesAnnoncesCourriels.ARCHIVEES
            } else {
                FixturesAnnoncesCourriels.ACTIVES
            }
            "POST /annonces-hall" -> {
                val titre = Json.parseToJsonElement(corps).jsonObject["titre"]?.jsonPrimitive?.content.orEmpty()
                if (titre.isBlank()) 422 to """{"detail": "Le titre est requis"}""" else 201 to FixturesAnnoncesCourriels.creee(titre)
            }
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

    /** PATCH (archiver), POST …/renvoyer-email, DELETE d'une affiche. */
    private fun gesteAnnonce(methode: String, id: Int, suite: String): Pair<Int, String> = when {
        methode == "PATCH" && suite.isEmpty() -> 200 to FixturesAnnoncesCourriels.creee("Affiche $id")
        methode == "POST" && suite == "/renvoyer-email" -> 204 to ""
        methode == "DELETE" && suite.isEmpty() -> 204 to ""
        else -> 404 to """{"detail": "Not Found"}"""
    }

    private companion object {
        val compteTraite = Regex("""/admin/comptes/(\d+)/traiter""")
        val commandeTraitee = Regex("""/admin/commandes-acces/(\d+)/traiter""")
        val accueil = Regex("""/admin/utilisateurs/\d+/accueil-arrivant""")
        val annonceGeste = Regex("""/annonces-hall/(\d+)(/renvoyer-email)?""")

        fun lire(json: String) = Json.parseToJsonElement(json).jsonArray.toMutableList()

        fun kotlinx.serialization.json.JsonElement.id() = jsonObject["id"]?.jsonPrimitive?.int
    }
}
