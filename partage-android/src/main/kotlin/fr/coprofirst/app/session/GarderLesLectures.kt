// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

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
 * CE QU'UNE APP GARDE pour la consultation hors ligne, et rien d'autre (#28) : la liste
 * blanche de SES lectures, par le chemin des routes du client GÉNÉRÉ (une route renommée ne
 * compile plus) — un identifiant s'y écrit [ID] —, les fichiers protégés (`/uploads/`) si
 * elle les garde, et les routes de la session, qui partent encore pendant la consultation.
 * Le téléphone a la sienne (`LectureGardee`), la montre la sienne (#206) : une route absente
 * d'une liste n'est jamais écrite sur l'appareil.
 */
class ListeDesLectures(
    routes: List<String>,
    private val fichiers: Boolean = false,
    private val session: List<String> = emptyList(),
    private val deconnexion: String? = null,
) {
    private val motifs = routes.map { chemin ->
        Regex("^.*" + Regex.escape(chemin).replace(ID.toString(), "\\E[^/]+\\Q") + "$")
    }

    /** La clé d'une lecture gardée — son chemin servi, requête comprise — ou `null` : jamais gardée. */
    fun cle(requete: Request): String? {
        if (requete.method != "GET") return null
        val chemin = chemin(requete)
        if (motifs.none { it.matches(chemin) } && !(fichiers && FICHIER.matches(chemin))) return null
        return requete.url.encodedPath + (requete.url.encodedQuery?.let { "?$it" } ?: "")
    }

    /** Ce qui part encore pendant la consultation : la session elle-même (la rouvrir, la fermer). */
    fun estLaSession(requete: Request): Boolean = session.any { chemin(requete).endsWith(it) }

    fun estLaDeconnexion(requete: Request): Boolean = deconnexion != null && chemin(requete).endsWith(deconnexion)

    /** Le chemin lisible (non encodé) : celui que le client généré écrit. */
    private fun chemin(requete: Request) = requete.url.pathSegments.joinToString("/", prefix = "/")

    companion object {
        /** L'identifiant à donner au client généré pour une route qui en prend un. */
        const val ID = Int.MIN_VALUE

        /** Un client qui n'envoie rien : seuls les chemins des routes générées en sont lus. */
        val muet = Call.Factory { throw UnsupportedOperationException() }

        private val FICHIER = Regex("""^/uploads/.+""")
    }
}

/**
 * LA porte des lectures gardées, comme l'iOS (`GarderLesLectures`) : glissée dans le client
 * HTTP de chaque hôte, toute requête y passe — client généré et fichiers protégés —, sans
 * qu'aucun service de menu n'ait à le savoir. Ce qui se garde : la [liste] de l'app.
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
class GarderLesLectures(
    private val hote: String,
    private val lectures: LecturesGardees,
    private val liste: ListeDesLectures,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requete = chain.request()
        val cle = liste.cle(requete)
        val session = liste.estLaSession(requete)
        if (lectures.enConsultation(hote) && !session) {
            val lue = cle?.let { lectures.lire(it, hote) } ?: throw HorsLigneException()
            return Response.Builder()
                .request(requete).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .apply { lue.type?.let { header("Content-Type", it) } }
                .body(lue.corps.toResponseBody(lue.type?.toMediaTypeOrNull()))
                .build()
        }
        val reponse = chain.proceed(requete)
        if (session && reponse.code == 200 && !liste.estLaDeconnexion(requete)) {
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
