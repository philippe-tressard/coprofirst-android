// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.liaison

import android.content.Context
import androidx.core.content.edit
import fr.coprofirst.app.fonctions.notifications.montre.MessageLiaison
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Chiffrement
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

/** La liaison que le téléphone a donnée : la résidence et son jeton de montre (coprofirst#2771). */
class LiaisonGardee(val adresse: String, val residence: String, val jeton: String, val expireLe: Instant) {
    override fun toString() = "LiaisonGardee(adresse=$adresse, residence=$residence, jeton=•••, expireLe=$expireLe)"
}

/** Où la montre garde sa liaison : une seule, celle de la dernière résidence ouverte au téléphone. */
interface CoffreLiaison {
    fun lire(): LiaisonGardee?

    fun garder(liaison: LiaisonGardee)

    fun effacer()
}

/**
 * Ce que la montre fait d'un message du téléphone (`MessageLiaison`) : elle garde la liaison,
 * ou l'efface si elle vise la même résidence. Une adresse qui n'est pas celle d'une
 * installation (https, normalisée) ou une échéance illisible ne se garde pas.
 */
object Liaisons {
    fun recevoir(message: MessageLiaison, coffre: CoffreLiaison) {
        when (message) {
            is MessageLiaison.Liaison -> {
                val adresse = AdresseInstallation.relire(message.adresse) ?: return
                val expireLe = try { Instant.parse(message.expireLe) } catch (_: DateTimeParseException) { return }
                coffre.garder(LiaisonGardee(adresse.url, message.residence, message.jeton, expireLe))
            }
            is MessageLiaison.Effacement -> if (coffre.lire()?.adresse == message.adresse) coffre.effacer()
        }
    }

    /** La liaison qui vaut encore : un jeton échu ne lit plus rien, la montre attend le téléphone. */
    fun valide(coffre: CoffreLiaison, maintenant: Instant = Instant.now()): LiaisonGardee? =
        coffre.lire()?.takeIf { it.expireLe.isAfter(maintenant) }
}

/**
 * Dans les préférences privées de la montre, exclues de toute sauvegarde ; le jeton y est
 * CHIFFRÉ par une clé du Keystore (`Chiffrement`, partagé avec le téléphone, #206). Une
 * entrée illisible (clé perdue) se lit comme une montre pas reliée.
 */
class CoffrePreferences(contexte: Context, private val chiffrement: Chiffrement) : CoffreLiaison {
    private val preferences = contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    override fun lire(): LiaisonGardee? = runCatching {
        val chiffre = preferences.getString(JETON, null) ?: return null
        LiaisonGardee(
            preferences.getString(ADRESSE, null) ?: return null,
            preferences.getString(RESIDENCE, null) ?: return null,
            String(chiffrement.dechiffrer(Base64.getDecoder().decode(chiffre)), Charsets.UTF_8),
            Instant.ofEpochSecond(preferences.getLong(EXPIRE_LE, 0)),
        )
    }.getOrNull()

    override fun garder(liaison: LiaisonGardee) = preferences.edit {
        putString(ADRESSE, liaison.adresse)
        putString(RESIDENCE, liaison.residence)
        putString(JETON, Base64.getEncoder().encodeToString(chiffrement.chiffrer(liaison.jeton.toByteArray(Charsets.UTF_8))))
        putLong(EXPIRE_LE, liaison.expireLe.epochSecond)
    }

    override fun effacer() = preferences.edit { clear() }

    companion object {
        /** L'alias de la clé du Keystore qui chiffre le jeton de montre. */
        const val ALIAS = "coprofirst.montre.jeton"
        private const val FICHIER = "liaison"
        private const val ADRESSE = "adresse"
        private const val RESIDENCE = "residence"
        private const val JETON = "jeton"
        private const val EXPIRE_LE = "expire_le"
    }
}
