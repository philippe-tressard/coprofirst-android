// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.PreuveHote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import mockwebserver3.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le parcours d'entrée sur les choix de l'iOS : reconnaissance pendant la frappe. */
@OptIn(ExperimentalCoroutinesApi::class)
class ParcoursEntreeTest : BancEntree() {
    @Test
    fun ceQuiNEstPasEncoreUneAdresseNeDitRien() = test {
        parcours.entrer()
        parcours.saisir("residence")
        advanceUntilIdle()
        assertEquals(0, serveur.requestCount)
        assertNull(parcours.etat.value.erreur)
    }

    @Test
    fun reconnueApresLaPauseDeFrappeUneSeuleFois() = test {
        residence("Les Glycines")
        parcours.entrer()
        parcours.saisir("residence.exemple.f")
        advanceTimeBy(300)
        parcours.saisir("residence.exemple.test")
        advanceTimeBy(ParcoursEntree.PAUSE_DE_FRAPPE_MS - 1)
        assertEquals(0, serveur.requestCount)
        advanceUntilIdle()
        assertEquals(1, serveur.requestCount)
        assertEquals("Les Glycines", parcours.etat.value.reconnue?.nom)
        // Comme l'iOS : une résidence n'est retenue qu'après une connexion réussie.
        assertEquals(emptyList<Installation>(), connues.liste)
    }

    @Test
    fun laDerniereResidencePreRemplitEtSeReconnait() = test {
        connues.retenir(Installation("https://5hostachy.exemple.test", "Ancienne"))
        residence("5Hostachy")
        parcours.entrer()
        advanceUntilIdle()
        assertEquals("5hostachy.exemple.test", parcours.etat.value.saisie)
        assertEquals("5Hostachy", parcours.etat.value.reconnue?.nom)
    }

    /** #10 : la démo, ses rôles, puis la connexion à son adresse, l'identifiant du rôle pré-rempli. */
    @Test
    fun unRoleDeLaDemoOuvreLaConnexionASonAdresse() = test {
        residence("Résidence Les Glycines")
        parcours.enSavoirPlus()
        parcours.entrerDemo()
        assertEquals(EcranEntree.Demo, parcours.etat.value.ecran)
        parcours.choisirPersonnage("user-cs", "Conseil syndical")
        advanceUntilIdle()
        assertEquals(EcranEntree.Connexion, parcours.etat.value.ecran)
        assertEquals("demo.coprofirst.fr", parcours.etat.value.saisie)
        assertEquals("user-cs@demo.coprofirst.fr", parcours.etat.value.email)
        assertEquals("Conseil syndical", parcours.etat.value.aideDemo)
        assertEquals("Résidence Les Glycines", parcours.etat.value.reconnue?.nom)
        // Le retour refait le chemin à l'envers : la démo, la vitrine, l'accueil.
        parcours.retour()
        assertEquals(EcranEntree.Demo, parcours.etat.value.ecran)
        assertNull(parcours.etat.value.aideDemo)
        parcours.retour()
        assertEquals(EcranEntree.EnSavoirPlus, parcours.etat.value.ecran)
        parcours.retour()
        assertEquals(EcranEntree.Accueil, parcours.etat.value.ecran)
        assertEquals(emptyList<EcranEntree>(), parcours.etat.value.pile)
    }

    @Test
    fun unEchecSEffaceDesQueLaSaisieChange() = test {
        serveur.enqueue(MockResponse.Builder().code(421).build())
        parcours.entrer()
        parcours.saisir("inconnue.exemple.test")
        advanceUntilIdle()
        assertSame(ErreurService.HoteInconnu, parcours.etat.value.erreur)
        parcours.saisir("inconnue.exemple.f")
        assertNull(parcours.etat.value.erreur)
        parcours.retour()
        assertEquals(EcranEntree.Accueil, parcours.etat.value.ecran)
    }

    @Test
    fun unHoteJamaisVuSeConfirmeAvantToutMotDePasse() = test {
        pret("jamais-vu.exemple.test")
        parcours.seConnecter()
        advanceUntilIdle()
        // Seul GET /config est parti : le mot de passe attend la confirmation.
        assertEquals(1, serveur.requestCount)
        assertEquals(PreuveHote.Declaree, parcours.etat.value.confirmation?.preuve)
        ok()
        parcours.confirmerHote()
        advanceUntilIdle()
        serveur.takeRequest()
        assertEquals("/api/auth/login", serveur.takeRequest().url.encodedPath)
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        assertEquals("", parcours.etat.value.motDePasse)
        assertEquals("https://jamais-vu.exemple.test", connues.liste.single().url)
    }

    /**
     * #391, comme `Parcours.ouvrirApresLeCompte` de l'iOS : le compte se lit APRÈS la connexion et
     * AVANT l'ouverture — la résidence s'ouvre sur lui (la porte d'un mot de passe temporaire).
     */
    @Test
    fun laConnexionLitLeCompteAvantDOuvrir() = test {
        compte = """{"mot_de_passe_a_changer": true, "id": 7, "nom": "Essai", "prenom": "Camille",
            "email": "a@exemple.test", "statut": "copropriétaire_résident", "role": "résident", "actif": true,
            "onboarding_complete": true, "onboarding_etape": 0, "preferences_notifications": "{}",
            "cree_le": "2026-03-02T10:00:00", "nom_affiche": "Camille ESSAI", "nom_aide_affiche": ""}"""
        connues.retenir(Installation("https://connue.exemple.test", "Les Glycines"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        val chemins = List(serveur.requestCount) { serveur.takeRequest().url.encodedPath }
        assertEquals(listOf("/api/config", "/api/auth/login", "/api/auth/me"), chemins)
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        assertEquals(true, parcours.etat.value.connexion?.compte?.motDePasseAChanger)
    }

    /** #391 : un compte illisible n'empêche pas d'entrer — la résidence le relira. */
    @Test
    fun unCompteIllisibleNEmpechePasDOuvrir() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Les Glycines"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        val ouverte = parcours.etat.value.connexion
        assertNull("rien de lu", ouverte?.compte)
        ok() // POST /auth/logout
        parcours.deconnecter()
        advanceUntilIdle()
        assertNull("la sortie l'oublie", parcours.etat.value.connexion)
        parcours.saisirMotDePasse("secret")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertTrue("chaque connexion ouvre une résidence neuve", parcours.etat.value.connexion?.id != ouverte?.id)
    }

    @Test
    fun sansPreuveIlFautLeSecondAccord() = test {
        preuve = PreuveHote.Absente
        pret("imitation.exemple.test")
        parcours.seConnecter()
        advanceUntilIdle()
        parcours.confirmerHote()
        assertEquals(1, serveur.requestCount)
        parcours.basculerSecondAccord()
        ok()
        parcours.confirmerHote()
        advanceUntilIdle()
        assertEquals("GET /config, POST /auth/login, GET /auth/me (#391)", 3, serveur.requestCount)
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
    }

    @Test
    fun laDemoEtUneResidenceConnueNeSeConfirmentPas() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertNull(parcours.etat.value.confirmation)
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
    }

    /** #6 : une adresse lue dans un QR se confirme TOUJOURS — même connue, même la démo. */
    @Test
    fun uneAdresseScanneeSeConfirmeToujours() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        residence("Connue")
        parcours.entrer()
        parcours.lireQR("https://Connue.exemple.test/")
        advanceUntilIdle()
        assertEquals("connue.exemple.test", parcours.etat.value.saisie)
        assertEquals("Connue", parcours.etat.value.reconnue?.nom)
        parcours.saisirEmail("a@exemple.test")
        parcours.saisirMotDePasse("secret")
        parcours.seConnecter()
        advanceUntilIdle()
        assertEquals("seul GET /config est parti", 1, serveur.requestCount)
        assertEquals(PreuveHote.Declaree, parcours.etat.value.confirmation?.preuve)
    }

    /** Un QR qui ne porte pas d'adresse ne touche à rien ; une adresse retapée redevient une saisie. */
    @Test
    fun lOrigineDuQRNeVautQuePourSonAdresse() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        residence("Connue")
        parcours.entrer()
        advanceUntilIdle()
        val avant = parcours.etat.value
        parcours.lireQR("Bonjour à tous")
        assertEquals(avant, parcours.etat.value)
        residence("Imitation")
        parcours.lireQR("imitation.exemple.test")
        advanceUntilIdle()
        residence("Connue")
        parcours.saisir("connue.exemple.test")
        advanceUntilIdle()
        parcours.saisirEmail("a@exemple.test")
        parcours.saisirMotDePasse("secret")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertNull(parcours.etat.value.confirmation)
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
    }

    /** #7 : un lien vers un hôte jamais vu ouvre la connexion à son adresse, et se confirme. */
    @Test
    fun unLienVersUnHoteJamaisVuSeConfirme() = test {
        residence("Les Glycines")
        parcours.suivreLien("https://jamais-vu.exemple.test/tickets/12#msg-3")
        advanceUntilIdle()
        assertEquals(EcranEntree.Connexion, parcours.etat.value.ecran)
        assertEquals("jamais-vu.exemple.test", parcours.etat.value.saisie)
        assertEquals("Les Glycines", parcours.etat.value.reconnue?.nom)
        parcours.saisirEmail("a@exemple.test")
        parcours.saisirMotDePasse("secret")
        parcours.seConnecter()
        advanceUntilIdle()
        assertEquals("seul GET /config est parti", 1, serveur.requestCount)
        assertEquals(PreuveHote.Declaree, parcours.etat.value.confirmation?.preuve)
        // L'affaire visée attend l'ouverture de SA résidence, puis la porte la pose (#147).
        ok()
        parcours.confirmerHote()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        assertEquals(Destination.Element("affaire", "12"), parcours.etat.value.lien?.destination)
        parcours.lienSuivi()
        assertNull(parcours.etat.value.lien)
    }

    /** #147 : le lien d'une résidence tombe quand c'est une autre qui s'ouvre. */
    @Test
    fun leLienDUneAutreResidenceTombe() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        residence("Les Glycines")
        parcours.suivreLien("https://jamais-vu.exemple.test/tickets/12")
        advanceUntilIdle()
        residence("Connue")
        parcours.saisir("connue.exemple.test")
        advanceUntilIdle()
        parcours.saisirEmail("a@exemple.test")
        parcours.saisirMotDePasse("secret")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        assertNull(parcours.etat.value.lien)
    }

    /** #7 : vers une résidence connue, la connexion habituelle ; vers celle qui est ouverte, l'écran visé seul. */
    @Test
    fun unLienVersUneResidenceConnueOuOuverte() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        residence("Connue")
        parcours.suivreLien("https://connue.exemple.test/actualites")
        advanceUntilIdle()
        assertEquals("connue.exemple.test", parcours.etat.value.saisie)
        parcours.saisirEmail("a@exemple.test")
        parcours.saisirMotDePasse("secret")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        // « /actualites » : la page des affaires, posée par la porte à l'ouverture.
        assertEquals("https://connue.exemple.test", parcours.etat.value.lien?.adresse?.url)
        parcours.lienSuivi()
        val ouverte = parcours.etat.value
        parcours.suivreLien("http://autre.exemple.test/")
        assertSame(ouverte, parcours.etat.value)
        parcours.suivreLien("https://connue.exemple.test/tickets/4")
        advanceUntilIdle()
        assertEquals(ouverte.copy(lien = parcours.etat.value.lien), parcours.etat.value)
        assertEquals(Destination.Element("affaire", "4"), parcours.etat.value.lien?.destination)
        assertEquals("GET /config, POST /auth/login, GET /auth/me (#391)", 3, serveur.requestCount)
    }

    @Test
    fun identifiantsRefusesSansQuitterLEcran() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        serveur.enqueue(MockResponse.Builder().code(422).build())
        parcours.seConnecter()
        advanceUntilIdle()
        assertSame(ErreurService.IdentifiantsRefuses, parcours.etat.value.erreurConnexion)
        assertEquals(EcranEntree.Connexion, parcours.etat.value.ecran)
        parcours.saisirMotDePasse("autre")
        assertNull(parcours.etat.value.erreurConnexion)
    }

    @Test
    fun oublierToutRevientALAccueilCommeNeuve() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        ok() // POST /auth/logout
        parcours.oublierTout()
        advanceUntilIdle()
        assertEquals(EtatEntree(), parcours.etat.value)
        assertEquals(emptyList<Installation>(), connues.liste)
    }

    /**
     * #177 : comme `Parcours.oublierTout` de l'iOS, CHAQUE résidence connue reçoit POST
     * /auth/logout — pas seulement l'ouverte —, et l'écran n'a rien à dire quand toutes répondent.
     */
    @Test
    fun oublierToutFermeLaSessionDeChaqueResidence() = test {
        connues.retenir(Installation("https://autre.exemple.test", "Autre"))
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        ok() // POST /auth/logout, la résidence ouverte
        ok() // POST /auth/logout, l'autre
        parcours.oublierTout()
        advanceUntilIdle()
        val sorties = List(serveur.requestCount) { serveur.takeRequest().url.encodedPath }.filter { it.endsWith("/auth/logout") }
        assertEquals(2, sorties.size)
        assertNull(parcours.etat.value.avisSortie)
    }

    /** #177 : une résidence qui ne répond pas est dite (« Certaines résidences n'ont pas répondu… ») ; tout est effacé quand même. */
    @Test
    fun oublierToutDitLesResidencesInjoignables() = test {
        connues.retenir(Installation("https://autre.exemple.test", "Autre"))
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        serveur.enqueue(MockResponse.Builder().code(503).build())
        ok()
        parcours.oublierTout()
        advanceUntilIdle()
        assertEquals(AvisSortie.ResidencesInjoignables, parcours.etat.value.avisSortie)
        assertEquals(emptyList<Installation>(), connues.liste)
    }

    /** #177 : une session déjà morte côté serveur (401) n'est pas un serveur injoignable. */
    @Test
    fun seDeconnecterSurUneSessionDejaMorteNeDitRien() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        serveur.enqueue(MockResponse.Builder().code(401).addHeader("Content-Type", "application/json").body("{}").build())
        parcours.deconnecter()
        advanceUntilIdle()
        assertNull(parcours.etat.value.avisSortie)
    }

    /** #177 : un serveur injoignable à la déconnexion est dit sur l'écran de connexion ; la connexion suivante l'efface. */
    @Test
    fun seDeconnecterSansReponseLeDitPuisLaConnexionLEfface() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        serveur.enqueue(MockResponse.Builder().code(503).build())
        parcours.deconnecter()
        advanceUntilIdle()
        assertEquals(AvisSortie.SessionInjoignable, parcours.etat.value.avisSortie)
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        assertNull("une connexion réussie efface l'avis", parcours.etat.value.avisSortie)
    }

    /**
     * #180 : changer de résidence quitte l'ouverte SANS la déconnecter — aucun POST
     * /auth/logout — et mène à la connexion de l'autre, reconnue de nouveau ; « Une autre
     * résidence » mène à la connexion vierge.
     */
    @Test
    fun changerDeResidenceSansSeDeconnecter() = test {
        val autre = Installation("https://autre.exemple.test", "Autre")
        connues.retenir(autre)
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        residence("Autre")
        parcours.changerDeResidence(autre)
        advanceUntilIdle()
        val etat = parcours.etat.value
        assertEquals(EcranEntree.Connexion, etat.ecran)
        assertNull(etat.ouverte)
        assertEquals("autre.exemple.test", etat.saisie)
        assertEquals("Autre", etat.reconnue?.nom)
        val chemins = List(serveur.requestCount) { serveur.takeRequest().url.encodedPath }
        assertTrue("aucune déconnexion", chemins.none { it.endsWith("/auth/logout") })
        parcours.changerDeResidence(null)
        assertEquals("", parcours.etat.value.saisie)
        assertNull(parcours.etat.value.reconnue)
    }

    /** #180 : les résidences retenues, avec les rôles relus de chacune. */
    @Test
    fun lesResidencesConnuesPortentLeursRoles() = test {
        val a = Installation("https://a.exemple.test", "A")
        connues.retenir(a)
        parcours.noterRoles(a, "locataire", emptyList())
        assertEquals(listOf("locataire"), parcours.residencesConnues().map { it.roles?.statut })
    }

    /** #144 : ce qui reste de la mesure d'audience part AVANT que la session se ferme. */
    @Test
    fun laMesurePartAvantLaDeconnexion() = test {
        var requetesALaMesure = -1
        val ordre = mutableListOf<String>()
        construire(avantSortie = { requetesALaMesure = serveur.requestCount; ordre += "envoyer" }, apresSortie = { ordre += "jeter ${it.url}" })
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        val avant = serveur.requestCount
        ok() // POST /auth/logout
        parcours.deconnecter()
        advanceUntilIdle()
        assertEquals("la mesure, avant tout envoi de la déconnexion", avant, requetesALaMesure)
        assertEquals(avant + 1, serveur.requestCount)
        // #162 : ce qui reste part d'abord ; la session close, ce qui se noterait encore se jette.
        assertEquals(listOf("envoyer", "jeter https://connue.exemple.test"), ordre)
    }

    /** #57 : la session ouverte parle à la même API que la connexion (le serveur local ici). */
    @Test
    fun laSessionOuverteParleALaMemeApi() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        val avant = serveur.requestCount
        residence("Connue")
        val cles = parcours.sessionOuverte()!!.appeler { b, c -> fr.coprofirst.api.routes.ConfigApi(b, c).getConfig() }
        assertEquals("Connue", cles["site_nom"])
        assertEquals(avant + 1, serveur.requestCount)
    }
}
