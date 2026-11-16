// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

/**
 * Le corps d'une requête générée qui doit dire « effacer » ce champ, comme le
 * `CorpsEnvoye` de l'iOS. Il complète le sérialiseur, qui omet tout champ null (#99).
 *
 * Le serveur distingue un champ ENVOYÉ à null d'un champ ABSENT (`model_fields_set`). Depuis
 * #99, ce client n'envoie plus aucun null, ce qui protège les PATCH partiels mais rend
 * l'effacement impossible : ramener un avis de prestataire à « sans affaire » exige
 * `"ticket_id": null` (coprofirst-android#209).
 *
 * Ce décorateur ajoute `"champ": null` au corps JSON (un objet) de chaque requête, pour les
 * `champs` nommés qui n'y figurent pas. Un champ qui porte une valeur garde sa valeur. La
 * route, la méthode et les en-têtes restent ceux du client généré : seul le corps change.
 *
 * Usage : `PrestatairesApi(base, NulsExplicites(client, setOf("ticket_id"))).patch…(…)`.
 */
class NulsExplicites(private val client: Call.Factory, private val champs: Set<String>) : Call.Factory {
    override fun newCall(request: Request): Call = client.newCall(if (champs.isEmpty()) request else completer(request))

    private fun completer(request: Request): Request {
        val corps = request.body ?: return request
        val type = corps.contentType()
        if (type?.subtype != "json") return request
        val texte = Buffer().also { corps.writeTo(it) }.readUtf8()
        val objet = Json.parseToJsonElement(texte) as? JsonObject ?: return request
        val complet = JsonObject(objet + champs.filterNot(objet::containsKey).associateWith { JsonNull })
        return request.newBuilder().method(request.method, complet.toString().toRequestBody(type)).build()
    }
}
