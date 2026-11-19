// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #242 : le marque-page du pliable — sur l'appareil seul, par résidence, effacé à la fermeture. */
class MarquesPagesTest {
    private val disque = mutableMapOf<String, String>()
    private val stockage = object : StockageMarquePage {
        override fun lire(cle: String) = disque[cle]

        override fun ecrire(cle: String, valeur: String?) {
            if (valeur == null) disque.remove(cle) else disque[cle] = valeur
        }
    }
    private val marque = MarquePage("PLUS", pageDePlus = "profil", ficheNature = "ticket", ficheId = "41", jour = "2026-11-17", enColonne = true, page = "rubrique.plans")

    @Test
    fun unPliableRouvreOuIlAvaitReferme() {
        val marques = MarquesPages(stockage, pliable = true)
        marques.ecrire(RESIDENCE, marque)
        assertEquals(marque, marques.lire(RESIDENCE))
        assertNull(marques.lire("https://autre.exemple.test"))
        // Aucune adresse en clair sur le disque : l'empreinte seule.
        assertTrue(disque.keys.none { it.contains("residence") })
    }

    @Test
    fun unTelephoneOrdinaireNEcritNiNeLitRien() {
        disque["deja"] = "là"
        val marques = MarquesPages(stockage, pliable = false)
        marques.ecrire(RESIDENCE, marque)
        assertEquals(mapOf("deja" to "là"), disque)
        assertNull(marques.lire(RESIDENCE))
    }

    @Test
    fun unMarquePageIlliblePasseSonTour() {
        val marques = MarquesPages(stockage, pliable = true)
        marques.ecrire(RESIDENCE, marque)
        disque.replaceAll { _, _ -> "{pas du json" }
        assertNull(marques.lire(RESIDENCE))
    }

    @Test
    fun laResidenceFermeeEffaceSonMarquePage() = runBlocking {
        val marques = MarquesPages(stockage, pliable = true)
        marques.ecrire(RESIDENCE, marque)
        marques.ecrire(AUTRE, marque)
        val session = SessionOuverte(Installation(RESIDENCE, "Les Glycines"), SessionsParHote(OkHttpClient()), Dispatchers.Unconfined)
        marques.residenceFermee(session)
        assertNull(marques.lire(RESIDENCE))
        assertEquals(marque, marques.lire(AUTRE))
    }

    private companion object {
        const val RESIDENCE = "https://residence.exemple.test"
        const val AUTRE = "https://autre.exemple.test"
    }
}
