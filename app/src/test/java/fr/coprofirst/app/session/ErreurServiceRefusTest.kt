// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.infrastructure.ClientError
import fr.coprofirst.api.infrastructure.ClientException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #233 (Registre R6) : les refus du serveur disent quoi faire — le 409 du sixième compte d'une
 * installation en mode limité, le 403 d'une écriture sur une installation suspendue. Leur phrase
 * (`detail`) s'affiche TELLE QUELLE, jamais remplacée par une erreur générique.
 */
class ErreurServiceRefusTest {
    private fun refus(statut: Int, corps: String) =
        ErreurService.depuis(ClientException("refus", statut, ClientError<Any>(body = corps, statusCode = statut)))

    @Test
    fun le409DuModeLimiteDitQuoiFaire() {
        val phrase = "Cette installation compte déjà 5 comptes actifs : désactivez-en un, ou faites-la inscrire."
        assertEquals(ErreurService.Refus(phrase, 409), refus(409, """{"detail": "$phrase"}"""))
    }

    @Test
    fun le403DeLaSuspensionDitPourquoi() {
        val phrase = "L'installation est suspendue : elle est en lecture seule."
        assertEquals(ErreurService.Refus(phrase, 403), refus(403, """{"detail": "$phrase"}"""))
    }

    @Test
    fun sansPhraseServieLeStatutDecide() {
        assertEquals(ErreurService.Introuvable, refus(403, "{}"))
        assertEquals(ErreurService.Inattendue, refus(409, "pas du JSON"))
    }
}
