// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GardeHoteTest {
    private val connue = Installation("https://connue.exemple.test", "Connue")
    private val garde = GardeHote { listOf(connue) }
    private fun adresse(hote: String) = AdresseInstallation.normaliser(hote)!!

    @Test
    fun saisieDUnHoteConnuOuDeLaDemoPasseSansConfirmation() {
        assertFalse(garde.exigeConfirmation(adresse("connue.exemple.test"), OrigineAdresse.Saisie))
        assertFalse(garde.exigeConfirmation(AdresseInstallation.DEMO, OrigineAdresse.Saisie))
        assertTrue(garde.exigeConfirmation(adresse("jamais-vu.exemple.test"), OrigineAdresse.Saisie))
    }

    @Test
    fun unQROuUnLienSeConfirmentToujours() {
        assertTrue(garde.exigeConfirmation(adresse("connue.exemple.test"), OrigineAdresse.Scan))
        assertTrue(garde.exigeConfirmation(AdresseInstallation.DEMO, OrigineAdresse.Lien))
    }

    @Test
    fun confirmerExigeLaPreuveOuLeSecondAccord() {
        val hote = adresse("jamais-vu.exemple.test")
        assertFalse(garde.confirmer(hote, preuve = null, secondAccord = true))
        assertFalse(garde.confirmer(hote, PreuveHote.Absente, secondAccord = false))
        assertFalse(garde.confirmer(hote, PreuveHote.SansLApp, secondAccord = false))
        assertTrue(garde.exigeConfirmation(hote, OrigineAdresse.Saisie))
        assertTrue(garde.confirmer(hote, PreuveHote.Absente, secondAccord = true))
        assertFalse(garde.exigeConfirmation(hote, OrigineAdresse.Saisie))
        assertTrue(GardeHote { emptyList() }.confirmer(hote, PreuveHote.Declaree, secondAccord = false))
    }
}
