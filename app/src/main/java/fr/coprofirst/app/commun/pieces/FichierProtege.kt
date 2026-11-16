// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * LA porte des fichiers protégés — photos et pièces jointes servies sous `/uploads/`.
 *
 * Pourquoi elle existe : ces fichiers ne sont PAS des routes de l'API (ni dans le contrat,
 * ni sous `/api`) — le serveur sert des URL (`photos_urls`, `fichiers_urls`, `photo_url`)
 * que Caddy délivre derrière la session (`forward_auth` : il demande à l'API si CE fichier
 * se lit par CE compte). Le client généré ne peut donc pas les lire ; l'iOS les lit de même
 * hors de lui, par la session HTTP de l'installation (`fichierProtege`). C'est la seule
 * requête écrite à la main de l'app avec `PreuveHote.kt`, dispensée nommément par
 * `scripts/verifier-depot.sh` : aucun autre fichier ne peut en écrire.
 *
 * Ce qu'elle garantit :
 * - elle ne lit que l'hôte de la session (même schéma, même hôte, même port) : une URL
 *   servie qui mènerait ailleurs est refusée AVANT tout envoi — ni les cookies de la
 *   résidence ne fuient, ni l'app ne lit un hôte que l'utilisateur n'a pas donné ;
 * - elle passe par `SessionOuverte.appeler` : le client de l'hôte (ses cookies, aucune
 *   redirection suivie — `ClientHttp`), hors du fil principal, erreurs en `ErreurService` ;
 * - elle ne garde en mémoire qu'un fichier borné (`PLAFOND`).
 *
 * Les documents servis par une route (`/documents/{id}/télécharger`) passent, eux, par le
 * client généré (`PiecesLocales.document`).
 *
 * Elle lit aussi le logo d'une résidence (`LogosEnMemoire`, #137) : sa route est au contrat
 * (GET /config/logo.png), mais le client généré type une image en JSON — l'adresse se tire de
 * sa configuration générée, et le fichier se lit ici, à l'hôte de l'installation seul.
 */
object FichierProtege {
    /**
     * Au-delà, le fichier n'est pas lu : le serveur borne une image à 5 Mo et un document
     * à 15 Mo (POST /uploads/fichier) ; le double laisse de la marge sans risquer la mémoire.
     */
    const val PLAFOND: Long = 32L * 1024 * 1024

    /**
     * L'adresse absolue d'un chemin servi (« /uploads/… » ou une URL entière), résolue à la
     * racine de l'hôte de `base` (la racine de l'API de la session) — `null` si elle mène à
     * un autre schéma, un autre hôte, un autre port, ou porte des identifiants.
     */
    fun adresse(base: String, chemin: String): HttpUrl? {
        val api = base.toHttpUrlOrNull() ?: return null
        val racine = api.newBuilder().encodedPath("/").query(null).fragment(null).build()
        val url = racine.resolve(chemin.trim()) ?: return null
        val memeHote = url.scheme == api.scheme && url.host == api.host && url.port == api.port
        return url.takeIf { memeHote && it.username.isEmpty() && it.password.isEmpty() }
    }

    /** Lit le fichier ; tout refus en `ErreurService` (hôte étranger : `Inattendue`, sans requête). */
    internal fun lire(base: String, client: Call.Factory, chemin: String): ByteArray {
        val url = adresse(base, chemin) ?: throw ErreurService.Inattendue
        val requete = Request.Builder().url(url).get().build()
        return client.newCall(requete).execute().use { reponse ->
            // Une redirection n'est pas suivie (`ClientHttp`) : elle n'est pas le fichier.
            if (reponse.code != 200) throw ErreurService.depuisStatut(reponse.code)
            val source = reponse.body.source()
            source.request(PLAFOND + 1)
            if (source.buffer.size > PLAFOND) throw ErreurService.Inattendue
            source.readByteArray()
        }
    }
}

/**
 * Un fichier de `/uploads/` (photo, pièce jointe), lu par la session de la résidence —
 * comme l'iOS (`fichierProtege`). Seul chemin d'un écran vers un fichier protégé.
 * @throws ErreurService toujours ce type.
 */
suspend fun SessionOuverte.fichierProtege(chemin: String): ByteArray =
    appeler { base, client -> FichierProtege.lire(base, client, chemin) }
