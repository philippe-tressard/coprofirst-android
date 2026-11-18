// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** #281 : les gestes de SON message — la règle du site et de la route, comme l'iOS (`MessagesAffaireTests`). */
class GestesMessageTest {
    private val ouverte = DetailAffaire(
        id = 4, numero = "A-0004", titre = "Fuite", description = "", categorie = "panne", statut = "ouvert", creeLe = Instant.EPOCH,
    )

    private fun message(contenu: String = "<p>Merci</p>", deMoi: Boolean = true) =
        MessageAffaire(41, contenu, Instant.EPOCH, deMoi, null, null, false)

    @Test
    fun sonMessageSeModifieEtSeSupprime() {
        assertEquals(listOf(GesteSuite.Modifier, GesteSuite.Supprimer), message().gestesOfferts(ouverte))
    }

    @Test
    fun niLeMessageDUnAutreNiUneAffaireClose() {
        assertEquals(emptyList<GesteSuite>(), message(deMoi = false).gestesOfferts(ouverte))
        assertEquals(emptyList<GesteSuite>(), message().gestesOfferts(ouverte.copy(close = true)))
    }

    /** Une mise en forme que l'app ne sait pas réécrire : « Supprimer » seul, elle se modifie depuis le site. */
    @Test
    fun unTexteMisEnFormeSurLeSiteNeSeModifiePasIci() {
        assertEquals(listOf(GesteSuite.Supprimer), message("<p>Merci <strong>beaucoup</strong></p>").gestesOfferts(ouverte))
        assertEquals("Merci", message().contenuSaisi)
    }
}
