// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.modeles.LoginRequest
import fr.coprofirst.api.routes.AdminApi
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.api.routes.DocumentsApi
import fr.coprofirst.api.routes.FluxApi
import fr.coprofirst.api.routes.LotsApi
import fr.coprofirst.api.routes.TicketsApi
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/** Une requête refusée avant de partir : la résidence est consultée hors ligne. */
class HorsLigneException : IOException("hors ligne")

/**
 * CE QUI EST GARDÉ, et rien d'autre : la liste blanche des lectures de l'iOS
 * (`LectureGardee.operations`, #101), par leur chemin sous `/api` — le fil, les affaires
 * (liste, ce qu'il faut pour la lire, détail, évolutions, messages), le profil, l'annuaire
 * que tout résident lit, un document déjà ouvert —, et les fichiers protégés (`/uploads/`)
 * déjà ouverts. Aucune route de l'administration ni de l'Espace CS ; une route absente
 * d'ici n'est jamais écrite sur l'appareil.
 */
object LectureGardee {
    /** Un client qui n'envoie rien : seuls les chemins des routes générées sont lus. */
    private val muet = Call.Factory { throw UnsupportedOperationException() }
    private const val ID = Int.MIN_VALUE

    /** Les chemins des routes, tirés du client GÉNÉRÉ : une route renommée ne compile plus. */
    private val routes: List<Regex> = listOf(
        FluxApi("", muet).getFluxRequestConfig(null).path,
        TicketsApi("", muet).getTicketsRequestConfig(null).path,
        TicketsApi("", muet).getTicketsReferentielsRequestConfig(null).path,
        LotsApi("", muet).getLotsMesPerimetresRequestConfig(null).path,
        TicketsApi("", muet).getTicketsTicketIdRequestConfig(ID, null).path,
        TicketsApi("", muet).getTicketsTicketIdEvolutionsRequestConfig(ID, null).path,
        TicketsApi("", muet).getTicketsTicketIdMessagesRequestConfig(ID, null).path,
        AuthApi("", muet).getAuthMeRequestConfig(null).path,
        // La seule sous l'administration : l'annuaire que TOUT résident lit (aucun rôle demandé).
        AdminApi("", muet).getAdminAnnuaireRequestConfig(null).path,
        DocumentsApi("", muet).getDocumentsDocIdTlchargerRequestConfig(ID, null).path,
    ).map { chemin -> Regex("^.*" + Regex.escape(chemin).replace(ID.toString(), "\\E[^/]+\\Q") + "$") }
    private val fichier = Regex("""^/uploads/.+""")
    private val sessionGeneree = listOf(
        AuthApi("", muet).postAuthRefreshRequestConfig(null).path,
        AuthApi("", muet).postAuthLoginRequestConfig(LoginRequest("", "")).path,
        AuthApi("", muet).postAuthLogoutRequestConfig(null).path,
    )
    private val deconnexion = AuthApi("", muet).postAuthLogoutRequestConfig(null).path

    /** La clé d'une lecture gardée — son chemin servi, requête comprise — ou `null` : jamais gardée. */
    fun cle(requete: Request): String? {
        if (requete.method != "GET") return null
        val chemin = chemin(requete)
        if (routes.none { it.matches(chemin) } && !fichier.matches(chemin)) return null
        return requete.url.encodedPath + (requete.url.encodedQuery?.let { "?$it" } ?: "")
    }

    /** Ce qui part encore pendant la consultation : la session elle-même (la rouvrir, la fermer). */
    fun estLaSession(requete: Request): Boolean = sessionGeneree.any { chemin(requete).endsWith(it) }

    fun estLaDeconnexion(requete: Request): Boolean = chemin(requete).endsWith(deconnexion)

    /** Le chemin lisible (non encodé) : celui que le client généré écrit. */
    private fun chemin(requete: Request) = requete.url.pathSegments.joinToString("/", prefix = "/")
}

/**
 * LA porte des lectures gardées, comme l'iOS (`GarderLesLectures`) : glissée dans le client
 * HTTP de chaque hôte (`SessionsParHote`), toute requête y passe — client généré et fichiers
 * protégés —, sans qu'aucun service de menu n'ait à le savoir.
 *
 * - **En ligne** : une lecture de la liste blanche qui réussit est gardée (si la session de
 *   l'hôte l'est) ; un 403 ou un 404 retire la lecture que ce compte ne peut plus ouvrir.
 *   Une session refusée efface tout de l'hôte (`oublierLaSession`).
 * - **En consultation** (entré sans réseau) : une lecture gardée est servie telle que le
 *   serveur l'avait servie ; TOUT le reste — une écriture, une lecture non gardée — est
 *   refusé ICI, avant de partir (`HorsLigneException` → `ErreurService.HorsLigne`) : sans
 *   clé d'idempotence côté serveur, un geste pourrait partir deux fois.
 * - Une connexion ou un renouvellement accepté referme la consultation.
 */
class GarderLesLectures(private val hote: String, private val lectures: LecturesGardees) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requete = chain.request()
        val cle = LectureGardee.cle(requete)
        val session = LectureGardee.estLaSession(requete)
        if (lectures.enConsultation(hote) && !session) {
            val lue = cle?.let { lectures.lire(it, hote) } ?: throw HorsLigneException()
            return Response.Builder()
                .request(requete).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .apply { lue.type?.let { header("Content-Type", it) } }
                .body(lue.corps.toResponseBody(lue.type?.toMediaTypeOrNull()))
                .build()
        }
        val reponse = chain.proceed(requete)
        if (session && reponse.code == 200 && !LectureGardee.estLaDeconnexion(requete)) {
            lectures.quitterLaConsultation(hote)
        }
        if (cle == null) return reponse
        return when (reponse.code) {
            200 -> garder(reponse, cle)
            403, 404 -> reponse.also { lectures.retirer(cle, hote) }
            else -> reponse
        }
    }

    private fun garder(reponse: Response, cle: String): Response {
        val longueur = reponse.body.contentLength()
        if (!lectures.garde(hote) || longueur > LecturesGardees.PLAFOND) return reponse
        val source = reponse.body.source()
        source.request(LecturesGardees.PLAFOND + 1L)
        // Un corps qui dépasse le plafond passe sans être gardé.
        if (source.buffer.size > LecturesGardees.PLAFOND) return reponse
        val type = reponse.body.contentType()
        val corps = source.readByteArray()
        lectures.garder(corps, type?.toString(), cle, hote)
        return reponse.newBuilder().body(corps.toResponseBody(type)).build()
    }
}
