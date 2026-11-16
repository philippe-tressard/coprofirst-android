// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Les notifications push : drapeau éteint = rien ; offre au bon moment ; enregistrer, retirer, retrouver. */
class NotificationsPushTest {
    private val sessions = SessionsParHote(OkHttpClient())
    private val glycines = session("glycines.exemple.test")
    private val tilleuls = session("tilleuls.exemple.test")

    private fun session(hote: String) = SessionOuverte(Installation("https://$hote", "Résidence"), sessions)

    private class Reglages : ReglagesPush {
        val table = mutableMapOf<String, String>()

        override fun lire(cle: String) = table[cle]

        override fun ecrire(cle: String, valeur: String?) {
            if (valeur == null) table.remove(cle) else table[cle] = valeur
        }

        override fun toutes(): Map<String, String> = table.toMap()
    }

    private class Centre(var autorisation: AutorisationPush) : CentreNotifications {
        var inscriptions = 0
        var demandes = 0

        override fun autorisation() = autorisation

        override fun demandeFaite() {
            demandes++
        }

        override fun inscrire() {
            inscriptions++
        }
    }

    /** Ce qui est parti : « POST hôte abonnement » ou « DELETE hôte abonnement ». */
    private class Releve(var echoue: Boolean = false) : ServiceAppareils {
        val requetes = mutableListOf<String>()
        val appareils = mutableListOf<AppareilPush>()

        override suspend fun enregistrer(appareil: AppareilPush, session: SessionOuverte) {
            requetes += "POST ${session.installation.url} ${appareil.abonnement}"
            appareils += appareil
            if (echoue) throw ErreurService.Injoignable
        }

        override suspend fun retirer(abonnement: String, session: SessionOuverte) {
            requetes += "DELETE ${session.installation.url} $abonnement"
            if (echoue) throw ErreurService.Injoignable
        }
    }

    private var horloge = Instant.parse("2026-11-16T10:00:00Z")
    private val reglages = Reglages()
    private val centre = Centre(AutorisationPush.ACCORDEE)
    private val releve = Releve()

    private fun push(actif: Boolean = true) =
        NotificationsPush(centre, releve, reglages, EnvironnementPush.PRODUCTION, actif) { horloge }

    @Test
    fun drapeauEteintRienNeSeProposeNiNePart() = runTest {
        centre.autorisation = AutorisationPush.NON_DEMANDEE
        val p = push(actif = false)
        p.lireAutorisation()
        assertFalse(p.offreDue())
        centre.autorisation = AutorisationPush.ACCORDEE
        p.residenceOuverte(glycines)
        p.jetonRecu(JetonPush("abc"))
        p.apresDemande(accordee = true)
        assertTrue(releve.requetes.isEmpty())
        assertEquals(0, centre.inscriptions)
        assertTrue(reglages.table.keys.none { it.startsWith("abonnement:") })
    }

    @Test
    fun lOffreSeProposeUneFoisPuisSeTaitQuatorzeJours() {
        centre.autorisation = AutorisationPush.NON_DEMANDEE
        val p = push()
        assertFalse("pas avant de connaître l'autorisation", p.offreDue())
        p.lireAutorisation()
        assertTrue(p.offreDue())
        p.reporter()
        assertFalse(p.offreDue())
        horloge += Duration.ofDays(14)
        assertFalse(p.offreDue())
        horloge += Duration.ofMinutes(1)
        assertTrue(p.offreDue())
        // Le report survit au redémarrage de l'app.
        horloge -= Duration.ofDays(1)
        assertFalse(push().apply { lireAutorisation() }.offreDue())
        for (repondue in listOf(AutorisationPush.ACCORDEE, AutorisationPush.REFUSEE)) {
            centre.autorisation = repondue
            assertFalse(push().apply { lireAutorisation() }.offreDue())
        }
    }

    @Test
    fun activerNoteLaDemandeEtNInscritQueSurUnOui() {
        val p = push()
        p.apresDemande(accordee = false)
        assertEquals(1, centre.demandes)
        assertEquals(0, centre.inscriptions)
        p.apresDemande(accordee = true)
        assertEquals(1, centre.inscriptions)
    }

    @Test
    fun lAppareilSEnregistreUneFoisParResidenceAvecSonAbonnement() = runTest {
        val p = push()
        p.residenceOuverte(glycines)
        assertTrue("sans jeton, rien ne part", releve.requetes.isEmpty())
        p.jetonRecu(JetonPush("abc"))
        p.jetonRecu(JetonPush("abc"))
        p.residenceOuverte(glycines)
        assertEquals(1, releve.requetes.size)
        val abonnement = p.abonnements.existant(glycines.installation.url)!!
        assertEquals("POST https://glycines.exemple.test $abonnement", releve.requetes.single())
        assertEquals(AppareilPush(JetonPush("abc"), EnvironnementPush.PRODUCTION, abonnement), releve.appareils.single())
        p.residenceOuverte(tilleuls)
        assertNotEquals(abonnement, p.abonnements.existant(tilleuls.installation.url))
        // Un jeton neuf s'enregistre de nouveau.
        p.jetonRecu(JetonPush("def"))
        assertEquals(3, releve.requetes.size)
    }

    @Test
    fun sansAutorisationRienNePartEtUnEchecSeRetente() = runTest {
        centre.autorisation = AutorisationPush.REFUSEE
        val p = push()
        p.jetonRecu(JetonPush("abc"))
        p.residenceOuverte(glycines)
        assertTrue(releve.requetes.isEmpty())
        centre.autorisation = AutorisationPush.ACCORDEE
        releve.echoue = true
        p.residenceOuverte(glycines)
        releve.echoue = false
        p.residenceOuverte(glycines)
        assertEquals(2, releve.requetes.size)
    }

    @Test
    fun laDeconnexionRetireEtOublieMemeSiLeServeurNeRepondPas() = runTest {
        val p = push()
        p.jetonRecu(JetonPush("abc"))
        p.residenceOuverte(glycines)
        val abonnement = p.abonnements.existant(glycines.installation.url)!!
        assertEquals(glycines.installation.url, p.residenceDe(abonnement))
        releve.echoue = true
        p.residenceFermee(glycines)
        assertEquals("DELETE https://glycines.exemple.test $abonnement", releve.requetes.last())
        assertNull(p.abonnements.existant(glycines.installation.url))
        assertNull("une notification qui arriverait encore ne mène plus à rien", p.residenceDe(abonnement))
        assertNull(p.residenceDe(null))
    }

    /** #150 : le parcours donne une session NEUVE à chaque geste — la fermeture se juge par la résidence. */
    @Test
    fun uneResidenceFermeeParUneAutreSessionNeSeReenregistrePas() = runTest {
        val p = push()
        centre.autorisation = AutorisationPush.ACCORDEE
        p.jetonRecu(JetonPush("abc"))
        p.residenceOuverte(glycines)
        p.residenceFermee(session("glycines.exemple.test"))
        val avant = releve.requetes.size
        p.jetonRecu(JetonPush("neuf"))
        assertEquals("aucun enregistrement auprès d'une résidence fermée", avant, releve.requetes.size)
    }

    @Test
    fun lAbonnementEstOpaqueEtDansLeMotifDuContrat() {
        val tires = List(50) { AbonnementsPush.tirer() }
        assertTrue(tires.all { Regex("^[A-Za-z0-9_-]{16,64}$").matches(it) })
        assertEquals(50, tires.toSet().size)
    }

    @Test
    fun leJetonNeSEcritJamais() {
        val jeton = JetonPush("secret-123")
        assertEquals(JetonPush.MASQUE, jeton.toString())
        assertFalse(AppareilPush(jeton, EnvironnementPush.PRODUCTION, "x".repeat(16)).toString().contains("secret"))
    }
}
