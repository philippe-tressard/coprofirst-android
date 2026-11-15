// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import android.content.Context
import fr.coprofirst.api.routes.DocumentsApi
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Les pièces ouvertes, en fichiers locaux, comme l'iOS (`PiecesLocales`) : l'app qui les
 * montre (l'équivalent de Coup d'œil) ne lit qu'un fichier. Chacune se lit UNE fois par
 * lancement — par la session de sa résidence — puis se rouvre sans réseau.
 *
 * Tout s'écrit dans le CACHE de l'app (`cacheDir/pieces/`), jamais dans le stockage
 * partagé : aucune autre app n'y lit, sauf le fichier que l'utilisateur ouvre (droit de
 * lecture accordé par `FileProvider`, le temps de l'ouverture). Le cache n'est jamais
 * sauvegardé, et il s'efface au lancement et à la déconnexion (`effacer`), comme l'iOS
 * efface ses copies (`Fichiers.effacerLesCopies`) ; le système peut aussi le vider.
 *
 * Une pièce s'écrit sous `pieces/<empreinte de son adresse>/<nom montré>` : deux
 * « devis.pdf » de deux affaires ne s'écrasent pas, et l'app qui l'ouvre la titre par son
 * nom lisible. ⚠️ Divergence déclarée : l'iOS nomme le dossier par l'adresse rendue sûre ;
 * ici, son empreinte SHA-256 — aussi unique et stable, et toujours sous la limite de
 * longueur d'un nom de fichier.
 */
class PiecesLocales(
    private val racine: File,
    private val fond: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lues = ConcurrentHashMap<String, File>()

    /**
     * Le fichier local d'une pièce (« /uploads/… »), lu par la session si ce lancement ne
     * l'a pas déjà lu.
     * @throws fr.coprofirst.app.session.ErreurService si la résidence ne la sert pas ;
     * @throws IOException si elle ne peut pas s'écrire.
     */
    suspend fun piece(chemin: String, session: SessionOuverte): File {
        val cle = empreinte(session.installation.url + "\n" + chemin)
        lues[cle]?.takeIf { it.isFile }?.let { return it }
        val donnees = session.fichierProtege(chemin)
        val fichier = withContext(fond) {
            ecrire(File(racine, cle), Fichiers.nomAffiche(chemin)) { it.writeBytes(donnees) }
        }
        lues[cle] = fichier
        return fichier
    }

    /** Un document du fil (`/documents/{id}/télécharger`), par le client généré. */
    suspend fun document(id: Int, nom: String?, session: SessionOuverte): File =
        fichierServi(session, "documents", id.toString(), nom, "document-$id") { base, client ->
            DocumentsApi(base, client).getDocumentsDocIdTlcharger(id)
        }

    /**
     * Un fichier servi par une ROUTE de l'API (document, rapport) — que le client généré
     * télécharge dans un fichier temporaire —, rangé sous `<sous>/<cle>/<nom sûr>` : la
     * seule écriture de ce geste, comme l'iOS (`fichierServi`).
     */
    suspend fun fichierServi(
        session: SessionOuverte,
        sous: String,
        cle: String,
        nom: String?,
        defaut: String,
        appel: (base: String, client: Call.Factory) -> File,
    ): File {
        val temporaire = session.appeler(appel)
        return withContext(fond) {
            try {
                val dossier = File(File(racine, Fichiers.nomSur(sous, "f")), Fichiers.nomSur(cle, "f"))
                ecrire(dossier, Fichiers.nomSur(nom, defaut)) { temporaire.copyTo(it, overwrite = true) }
            } finally {
                temporaire.delete()
            }
        }
    }

    /** Efface chaque copie : au lancement et à la déconnexion. */
    fun purger() {
        lues.clear()
        racine.deleteRecursively()
    }

    /** Écrit d'abord à côté, puis renomme : un fichier ouvert n'est jamais à moitié écrit. */
    private fun ecrire(dossier: File, nom: String, remplir: (File) -> Unit): File {
        if (!dossier.isDirectory && !dossier.mkdirs()) throw IOException("dossier non créé")
        val fichier = File(dossier, borner(Fichiers.nomSur(nom, "fichier")))
        val partiel = File(dossier, ".${fichier.name}.partiel")
        remplir(partiel)
        if (!partiel.renameTo(fichier)) {
            partiel.delete()
            throw IOException("fichier non renommé")
        }
        return fichier
    }

    companion object {
        /** Le dossier des copies, sous le cache — celui que déclare `res/xml/chemins_pieces.xml`. */
        const val DOSSIER = "pieces"

        /** Au-delà, le nom se raccourcit (l'extension gardée) : un nom de fichier a ses limites. */
        private const val LONGUEUR_NOM = 120

        @Volatile
        private var partagees: PiecesLocales? = null

        /** Les copies de l'app, une seule mémoire par processus. */
        fun de(contexte: Context): PiecesLocales = partagees ?: synchronized(this) {
            partagees ?: PiecesLocales(File(contexte.applicationContext.cacheDir, DOSSIER)).also { partagees = it }
        }

        /**
         * Efface ce que les pièces laissent sur l'appareil — les copies ouvertes et les
         * photos gardées en mémoire —, jamais l'un sans l'autre : au lancement, à la
         * déconnexion, à « Oublier toutes les résidences ».
         */
        fun effacer(contexte: Context) {
            de(contexte).purger()
            PhotosEnMemoire.vider()
        }

        internal fun empreinte(texte: String): String =
            MessageDigest.getInstance("SHA-256").digest(texte.toByteArray()).joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }

        internal fun borner(nom: String): String {
            if (nom.length <= LONGUEUR_NOM) return nom
            val ext = Fichiers.extensionDe(nom)
            if (ext.isEmpty() || ext.length > 16) return nom.take(LONGUEUR_NOM)
            return nom.dropLast(ext.length + 1).take(LONGUEUR_NOM - ext.length - 1) + ".$ext"
        }
    }
}
