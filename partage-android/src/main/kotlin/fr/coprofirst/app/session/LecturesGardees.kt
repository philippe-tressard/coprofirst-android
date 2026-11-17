// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Ce que l'appareil garde d'une résidence, au moment d'y entrer sans réseau (#28). */
sealed interface LectureDisponible {
    /** Une lecture gardée, et l'heure de sa dernière mise à jour. */
    data class Gardee(val aJourAu: Instant) : LectureDisponible

    /** Plus vieille que `LecturesGardees.JOURS_MAX` : elle vient d'être effacée. */
    data object Perimee : LectureDisponible

    data object Aucune : LectureDisponible
}

/** Une réponse gardée : son corps, son type, sa date. */
class LectureLue(val corps: ByteArray, val type: String?, val date: Instant)

/**
 * LE magasin des lectures gardées, pour la consultation hors ligne, comme l'iOS
 * (`LecturesGardees`, #101) — la seule porte du disque : aucun écran, aucun service de
 * menu ne lit ni n'écrit ces fichiers.
 *
 * - Un dossier par résidence, nommé par l'empreinte de l'hôte : rien ne se croise d'une
 *   résidence à l'autre, et `effacer` retire le dossier entier. Aucun nom en clair.
 * - Chaque fichier est CHIFFRÉ (`Chiffrement` : AES-GCM, clé du Keystore) — le
 *   `FileProtectionType.complete` de l'iOS — sous `noBackupFilesDir`, exclu de toute
 *   sauvegarde (et `regles_*.xml`).
 * - La date de chaque lecture vit dans l'index du dossier, chiffré de même.
 * - Rien n'est gardé pour une résidence dont la session n'est pas gardée (`sessionGardee`) :
 *   sans la biométrie pour la rouvrir, cette lecture ne servirait à rien.
 * - Aucun jeton, aucun cookie : le corps d'une réponse de LECTURE et son type, rien de ses
 *   en-têtes ni de la requête (`GarderLesLectures`).
 */
class LecturesGardees(
    private val racine: File,
    private val chiffrement: Chiffrement,
    private val sessionGardee: (String) -> Boolean,
    private val horloge: () -> Instant = Instant::now,
) {
    /** Les résidences ouvertes sans réseau (en mémoire : un lancement repart en ligne). */
    private val consultations = mutableSetOf<String>()

    /** Cet hôte garde-t-il ses lectures ? Seulement si sa session est gardée. */
    fun garde(hote: String): Boolean = sessionGardee(hote)

    /** Garde le corps d'une lecture réussie ; sans session gardée, rien n'est écrit. */
    @Synchronized
    fun garder(corps: ByteArray, type: String?, cle: String, hote: String) {
        if (!garde(hote) || corps.size > PLAFOND) return
        runCatching {
            val nom = empreinte(cle)
            dossier(hote).mkdirs()
            ecrire(File(dossier(hote), nom), json.encodeToString(Enveloppe.serializer(), Enveloppe(type, corps)).toByteArray())
            ecrireIndex(hote, lireIndex(hote) + (nom to horloge().toEpochMilli()))
        }.onFailure {
            // Écriture refusée (disque plein, clé indisponible) : rien de cet hôte ne reste mal daté.
            effacerDossier(hote)
        }
    }

    /** La lecture gardée sous cette clé — `null` si elle manque ou a passé la durée maximale (effacée). */
    @Synchronized
    fun lire(cle: String, hote: String): LectureLue? {
        val nom = empreinte(cle)
        val date = lireIndex(hote)[nom]?.let(Instant::ofEpochMilli) ?: return null
        if (perime(date)) {
            oublier(listOf(nom), hote)
            return null
        }
        val enveloppe = runCatching {
            json.decodeFromString(Enveloppe.serializer(), String(lireFichier(File(dossier(hote), nom))))
        }.getOrNull() ?: return null
        return LectureLue(enveloppe.corps, enveloppe.type, date)
    }

    /** Retire UNE lecture : le serveur ne la sert plus à ce compte (403, 404). */
    @Synchronized
    fun retirer(cle: String, hote: String) = oublier(listOf(empreinte(cle)), hote)

    /**
     * Ce qui est gardé pour cet hôte. Une lecture gardée ouvre la CONSULTATION : tant
     * qu'elle dure, rien ne part vers cet hôte hors de sa session (`GarderLesLectures`).
     * Une lecture périmée est effacée, et dite.
     */
    @Synchronized
    fun consulter(hote: String): LectureDisponible {
        val derniere = lireIndex(hote).values.maxOrNull()?.let(Instant::ofEpochMilli) ?: return LectureDisponible.Aucune
        if (perime(derniere)) {
            effacerDossier(hote)
            return LectureDisponible.Perimee
        }
        consultations += hote
        return LectureDisponible.Gardee(derniere)
    }

    @Synchronized
    fun enConsultation(hote: String): Boolean = hote in consultations

    /** La session de cet hôte est rouverte : ses lectures repartent du serveur. */
    @Synchronized
    fun quitterLaConsultation(hote: String) {
        consultations -= hote
    }

    /** Efface tout ce qui est gardé de cet hôte — la déconnexion, une session refusée. */
    @Synchronized
    fun effacer(hote: String) {
        consultations -= hote
        effacerDossier(hote)
    }

    /**
     * Au lancement : efface les lectures périmées, et celles des hôtes dont la session n'est
     * plus gardée (`gardes`) — rien ne reste d'une résidence où l'on n'est pas connecté.
     * Sans `gardes` : tout (« Oublier toutes les résidences »).
     */
    @Synchronized
    fun purger(gardes: Set<String> = emptySet()) {
        val attendus = gardes.associateBy(::empreinte)
        for (dossier in racine.listFiles().orEmpty()) {
            val hote = attendus[dossier.name]
            if (hote == null) {
                dossier.deleteRecursively()
                continue
            }
            val dates = lireIndex(hote)
            val presents = dossier.list().orEmpty().filter { it != INDEX }
            val aOublier = presents.filter { nom -> dates[nom]?.let { perime(Instant.ofEpochMilli(it)) } ?: true } +
                dates.keys.filter { it !in presents }
            oublier(aOublier, hote)
        }
    }

    @Serializable
    private class Enveloppe(val type: String?, val corps: ByteArray)

    private fun dossier(hote: String) = File(racine, empreinte(hote))

    private fun ecrire(fichier: File, octets: ByteArray) {
        val provisoire = File(fichier.parentFile, "${fichier.name}.part")
        provisoire.writeBytes(chiffrement.chiffrer(octets))
        if (!provisoire.renameTo(fichier)) error("écriture refusée")
    }

    private fun lireFichier(fichier: File): ByteArray = chiffrement.dechiffrer(fichier.readBytes())

    private fun lireIndex(hote: String): Map<String, Long> = runCatching {
        json.decodeFromString(serialiseurIndex, String(lireFichier(File(dossier(hote), INDEX))))
    }.getOrDefault(emptyMap())

    private fun ecrireIndex(hote: String, dates: Map<String, Long>) =
        ecrire(File(dossier(hote), INDEX), json.encodeToString(serialiseurIndex, dates).toByteArray())

    /** Efface ces lectures et leurs dates ; un index vide (ou qui ne s'écrit plus) emporte le dossier. */
    private fun oublier(noms: List<String>, hote: String) {
        if (noms.isEmpty()) return
        noms.forEach { File(dossier(hote), it).delete() }
        val dates = lireIndex(hote) - noms.toSet()
        if (dates.isEmpty() || runCatching { ecrireIndex(hote, dates) }.isFailure) effacerDossier(hote)
    }

    private fun effacerDossier(hote: String) {
        dossier(hote).deleteRecursively()
    }

    private fun perime(date: Instant) = horloge().toEpochMilli() - date.toEpochMilli() > TimeUnit.DAYS.toMillis(JOURS_MAX)

    companion object {
        /**
         * La durée maximale d'une lecture gardée, en jours — celle de l'iOS. Au-delà, sans
         * lecture fraîche, elle est effacée : un compte révoqué ne garde pas ses données
         * indéfiniment hors ligne.
         */
        const val JOURS_MAX = 7L

        /** La taille maximale d'une réponse gardée ; au-delà, elle passe sans être gardée. */
        const val PLAFOND = 50 * 1024 * 1024

        private const val INDEX = "dates"
        private val json = Json { ignoreUnknownKeys = true }
        private val serialiseurIndex = MapSerializer(String.serializer(), Long.serializer())

        fun empreinte(texte: String): String =
            MessageDigest.getInstance("SHA-256").digest(texte.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
