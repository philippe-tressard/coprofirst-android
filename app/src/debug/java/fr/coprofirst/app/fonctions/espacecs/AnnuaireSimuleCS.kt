// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * L'« Annuaire CS & Syndic » côté SERVEUR, pour les tests : GET et PUT de /admin/annuaire/cs
 * et /syndic, comme le serveur — le PUT rend la composition ENREGISTRÉE (les fiches neuves
 * reçoivent leur numéro, les noms leur forme servie « Prénom NOM ») —, le rapprochement d'un
 * nom et le nom affiché d'une saisie. Des personnes inventées. Débogage seulement.
 */
class AnnuaireSimuleCS {
    private var prochainId = 100
    private var conseil: JsonObject = Json.parseToJsonElement(CONSEIL).jsonObject
    private var syndic: JsonObject = Json.parseToJsonElement(SYNDIC).jsonObject

    /** La réponse d'une route de l'annuaire, ou `null` si la route n'est pas la sienne. */
    fun repondre(methode: String, route: String, corps: String): Pair<Int, String>? = when ("$methode $route") {
        "GET /admin/annuaire/cs" -> 200 to conseil.toString()
        "GET /admin/annuaire/syndic" -> 200 to syndic.toString()
        "PUT /admin/annuaire/cs" -> 200 to enregistrerConseil(Json.parseToJsonElement(corps).jsonObject).toString()
        "PUT /admin/annuaire/syndic" -> 200 to enregistrerSyndic(Json.parseToJsonElement(corps).jsonObject).toString()
        "GET /admin/annuaire/rapprochement" -> 200 to RAPPROCHEMENT
        "POST /noms/affiche" -> Json.parseToJsonElement(corps).jsonObject.let {
            200 to """{"nom_affiche": "${nomAffiche(it.texte("prenom"), it.texte("nom"))}"}"""
        }
        else -> null
    }

    private fun enregistrerConseil(entree: JsonObject): JsonObject {
        val membres = entree["membres"]?.jsonArray.orEmpty().mapIndexed { ordre, m ->
            val o = m.jsonObject
            buildJsonObject {
                put("id", o["id"]?.jsonPrimitive?.intOrNull ?: prochainId++)
                put("genre", o.texte("genre")); put("prenom", o.texte("prenom")); put("nom", o.texte("nom"))
                put("nom_affiche", nomAffiche(o.texte("prenom"), o.texte("nom")))
                put("batiment_id", o["batiment_id"] ?: JsonNull)
                put("batiment_nom", if (o["batiment_id"]?.jsonPrimitive?.intOrNull == 1) JsonPrimitive("A") else JsonNull)
                put("etage", o["etage"] ?: JsonNull); put("etage_libelle", "")
                put("est_gestionnaire_site", false)
                put("est_president", o["est_president"]?.jsonPrimitive?.booleanOrNull ?: false)
                put("ordre", ordre); put("user_id", o["user_id"] ?: JsonNull)
            }
        }
        conseil = buildJsonObject {
            put("ag_annee", entree["ag_annee"] ?: JsonNull); put("ag_date", entree["ag_date"] ?: JsonNull)
            put("whatsapp_url", entree.texte("whatsapp_url")); put("membres", JsonArray(membres))
        }
        return JsonObject(conseil + ("ok" to JsonPrimitive(true)))
    }

    private fun enregistrerSyndic(entree: JsonObject): JsonObject {
        val membres = entree["membres"]?.jsonArray.orEmpty().mapIndexed { ordre, m ->
            val o = m.jsonObject
            val telephone = o.texte("telephone")
            buildJsonObject {
                put("id", o["id"]?.jsonPrimitive?.intOrNull ?: prochainId++)
                put("genre", o.texte("genre")); put("prenom", o.texte("prenom")); put("nom", o.texte("nom"))
                put("nom_affiche", nomAffiche(o.texte("prenom"), o.texte("nom")))
                put("fonction", o["fonction"] ?: JsonNull); put("email", o["email"] ?: JsonNull)
                put("telephone", telephone)
                put("telephones", JsonArray(telephone.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive)))
                put("est_principal", o["est_principal"]?.jsonPrimitive?.booleanOrNull ?: false)
                put("ordre", ordre); put("user_id", o["user_id"] ?: JsonNull)
            }
        }
        syndic = buildJsonObject {
            put("nom_syndic", entree.texte("nom_syndic")); put("nom_syndic_source", "saisie")
            put("adresse", entree.texte("adresse")); put("site_web", entree["site_web"] ?: JsonNull)
            put("membres", JsonArray(membres))
        }
        return JsonObject(syndic + ("ok" to JsonPrimitive(true)))
    }

    private companion object {
        fun JsonObject.texte(cle: String): String = (this[cle] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

        fun List<JsonElement>?.orEmpty(): List<JsonElement> = this ?: emptyList()

        /** La règle du serveur (`nom_affiche`) : le prénom tel que saisi, le nom en capitales. */
        fun nomAffiche(prenom: String, nom: String) = listOf(prenom.trim(), nom.trim().uppercase()).filter { it.isNotEmpty() }.joinToString(" ")

        /** GET /admin/annuaire/cs → `CompositionConseil`. */
        const val CONSEIL = """
            {"ag_annee": 2026, "ag_date": "2026-06-12", "whatsapp_url": "https://chat.whatsapp.com/exemple",
             "membres": [
               {"id": 1, "genre": "Mme", "prenom": "Camille", "nom": "Exemple", "nom_affiche": "Camille EXEMPLE",
                "batiment_id": 1, "batiment_nom": "A", "etage": 2, "etage_libelle": "2ème étage",
                "est_gestionnaire_site": true, "est_president": true, "ordre": 0, "user_id": 21},
               {"id": 2, "genre": "Mr", "prenom": "Basile", "nom": "Fictif", "nom_affiche": "Basile FICTIF",
                "batiment_id": null, "batiment_nom": null, "etage": null, "etage_libelle": "",
                "est_gestionnaire_site": false, "est_president": false, "ordre": 1, "user_id": null}
             ]}
        """

        /** GET /admin/annuaire/syndic → `InfoSyndicAdmin`. */
        const val SYNDIC = """
            {"nom_syndic": "Cabinet Exemple", "nom_syndic_source": "saisie", "adresse": "1 rue de l'Exemple, 75000 Paris",
             "site_web": "https://exemple.fr",
             "membres": [
               {"id": 11, "genre": "Mme", "prenom": "Dominique", "nom": "Modèle", "nom_affiche": "Dominique MODÈLE",
                "fonction": "Gestionnaire", "email": "gestion@exemple.fr", "telephone": "01 99 00 00 01",
                "telephones": ["01 99 00 00 01"], "est_principal": true, "ordre": 0, "user_id": null},
               {"id": 12, "genre": "Mr", "prenom": "Sacha", "nom": "Essai", "nom_affiche": "Sacha ESSAI",
                "fonction": "Comptable", "email": null, "telephone": null, "telephones": [], "est_principal": false,
                "ordre": 1, "user_id": null}
             ]}
        """

        /** GET /admin/annuaire/rapprochement → `Rapprochement` : un inscrit, logé au bâtiment A. */
        const val RAPPROCHEMENT = """{"user_id": 31, "batiment_id": 1, "batiment_nom": "A", "etage": 3, "etage_libelle": "3ème étage"}"""
    }
}
