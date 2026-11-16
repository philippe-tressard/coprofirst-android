// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import javax.crypto.Cipher

/** Ce qui garde une session sur l'appareil — l'écran d'activation le dit, comme l'iOS (#84). */
enum class ProtectionSession {
    /** La biométrie de l'appareil (forte) : elle seule rouvre. */
    Biometrie,

    /** Sans biométrie inscrite : le verrouillage de l'appareil — qui connaît le code rouvre. */
    CodeAppareil,

    /** Ni verrouillage ni biométrie : l'appareil en main suffit, rien d'autre ne protège. */
    Aucune,
}

/**
 * Ce qui demande à l'utilisateur de déverrouiller une session gardée : la biométrie, ou le
 * code de l'appareil. Rend le chiffre déverrouillé, ou `null` s'il renonce.
 */
fun interface Deverrouillage {
    suspend fun deverrouiller(chiffre: Cipher, protection: ProtectionSession): Cipher?
}

/** Ce que rend la lecture d'une session gardée. */
sealed interface LectureSession {
    data class Jeton(val valeur: String) : LectureSession

    /** L'utilisateur a renoncé : la session reste gardée, rien ne s'ouvre. */
    data object Annulee : LectureSession

    /** Illisible (biométrie changée, clé perdue) : elle vient d'être effacée. */
    data object Perdue : LectureSession
}

/**
 * Le jeton de rafraîchissement de chaque installation, gardé sur l'appareil après l'accord
 * de l'utilisateur — le Trousseau de l'iOS (`Trousseau`). Jamais en clair, jamais sauvegardé,
 * jamais dans un journal ; la clé hôte par hôte. `remplacer` suit la rotation du jeton à
 * chaque renouvellement SANS rien demander à l'utilisateur.
 */
interface SessionsGardees {
    /** L'appareil sait garder une session (Android 11 et plus, `SessionsGardeesKeystore`). */
    val disponible: Boolean

    /** Garde le jeton et rend ce qui le protège — `null` s'il n'a pas pu être gardé. */
    fun garder(hote: String, jeton: String): ProtectionSession?

    /** Présent, sans le lire (aucune invite). */
    fun contient(hote: String): Boolean

    /** Le jeton a tourné : le neuf remplace l'ancien, sous la même protection. */
    fun remplacer(hote: String, jeton: String)

    /** Lit le jeton ; le système demande la biométrie (ou le code) s'il le protège. */
    suspend fun lire(hote: String, deverrouillage: Deverrouillage): LectureSession

    fun supprimer(hote: String)

    /** « Oublier toutes les résidences » : plus aucune session gardée. */
    fun vider()

    /** Un appareil qui ne garde rien (Android 8 à 10, les aperçus) : la connexion seule. */
    object Indisponibles : SessionsGardees {
        override val disponible = false
        override fun garder(hote: String, jeton: String): ProtectionSession? = null
        override fun contient(hote: String) = false
        override fun remplacer(hote: String, jeton: String) = Unit
        override suspend fun lire(hote: String, deverrouillage: Deverrouillage) = LectureSession.Perdue
        override fun supprimer(hote: String) = Unit
        override fun vider() = Unit
    }
}

/**
 * Des sessions « gardées » en mémoire, le temps du processus : les tests. `protection` dit ce
 * que l'appareil simulé offre ; `refuser` simule l'utilisateur qui renonce à la biométrie.
 */
class SessionsGardeesEnMemoire(
    private val protection: ProtectionSession = ProtectionSession.Biometrie,
) : SessionsGardees {
    private val jetons = mutableMapOf<String, String>()
    @Volatile
    var refuser = false

    override val disponible = true

    @Synchronized
    override fun garder(hote: String, jeton: String): ProtectionSession {
        jetons[hote] = jeton
        return protection
    }

    @Synchronized
    override fun contient(hote: String) = hote in jetons

    @Synchronized
    override fun remplacer(hote: String, jeton: String) {
        if (hote in jetons) jetons[hote] = jeton
    }

    override suspend fun lire(hote: String, deverrouillage: Deverrouillage): LectureSession {
        if (refuser) return LectureSession.Annulee
        return synchronized(this) { jetons[hote] }?.let { LectureSession.Jeton(it) } ?: LectureSession.Perdue
    }

    @Synchronized
    override fun supprimer(hote: String) {
        jetons.remove(hote)
    }

    @Synchronized
    override fun vider() = jetons.clear()
}
