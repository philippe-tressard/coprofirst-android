// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import fr.coprofirst.app.commun.ConstantesDuServeur
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le Profil : ce qu'il lit et envoie par le client généré, et ses règles de saisie. */
class ProfilTest {
    private val serveur = MockWebServer().apply { start() }
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val session = SessionOuverte(
        Installation("https://residence.exemple.test", "Les Glycines"), SessionsParHote(OkHttpClient()), Dispatchers.Unconfined, base,
    )
    private val service = ServiceProfilApi(session)

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String = "{}") =
        serveur.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build())

    private fun corps(): JsonObject {
        val requete = serveur.takeRequest()
        return Json.parseToJsonElement(requete.body!!.utf8()).jsonObject
    }

    /** Absent du corps, ou `null` : le serveur n'y touche pas. */
    private fun nul(corps: JsonObject, cle: String) = corps[cle] == null || corps[cle] is JsonNull

    @Test
    fun leCompteSeLitEtSonStatutFaitSaPastille() = runTest {
        repondre(200, FixturesProfil.MOI)
        val moi = service.moi()
        assertEquals("Camille EXEMPLE", moi.nomAffiche)
        assertEquals(
            "le statut, puis les rôles mis en avant",
            listOf("Copropriétaire résident", "Conseil syndical"),
            Roles.affiches(moi.statut.value, moi.roles.orEmpty() + moi.role.value).map { it.libelle },
        )
    }

    @Test
    fun uneNouvelleAdresseDemandeLeMotDePasseEtSeuleElleLEnvoie() = runTest {
        val compte = SaisieIdentite(prenom = "Camille", nom = "Exemple", email = "camille@exemple.test")
        assertTrue(compte.complete("camille@exemple.test"))
        assertFalse(compte.copy(prenom = " ").complete("camille@exemple.test"))
        val nouvelle = compte.copy(email = "  Autre@Exemple.test ")
        assertTrue(nouvelle.changeAdresse("camille@exemple.test"))
        assertFalse(compte.copy(email = " CAMILLE@exemple.test").changeAdresse("camille@exemple.test"))
        assertFalse("sans mot de passe", nouvelle.complete("camille@exemple.test"))

        repondre(200, FixturesProfil.MOI)
        assertFalse(service.modifierIdentite(compte.copy(telephone = "  "), "camille@exemple.test"))
        val sans = corps()
        assertTrue(nul(sans, "email"))
        assertTrue("une saisie vide part à null", nul(sans, "telephone"))

        repondre(200, FixturesProfil.MOI)
        assertTrue(service.modifierIdentite(nouvelle.copy(motDePasseActuel = "secret"), "camille@exemple.test"))
        val avec = corps()
        assertEquals("Autre@Exemple.test", avec["email"]!!.jsonPrimitive.content)
        assertEquals("secret", avec["mot_de_passe_actuel"]!!.jsonPrimitive.content)
    }

    @Test
    fun leMotDePasseAttendDansLOrdreDuSiteEtLeRefusPorteLaPhraseDuServeur() = runTest {
        val assez = "n".repeat(ConstantesDuServeur.LONGUEUR_MIN_MOT_DE_PASSE)
        assertEquals(AttenteMotDePasse.Actuel, attenteMotDePasse("", assez, assez))
        assertEquals(AttenteMotDePasse.Nouveau, attenteMotDePasse("ancien", "", ""))
        // Un caractère de moins que le serveur : trop court, dit avant l'envoi (#47).
        val court = assez.dropLast(1)
        assertEquals(AttenteMotDePasse.Nouveau, attenteMotDePasse("ancien", court, court))
        // Compté en caractères, comme le serveur : un emoji vaut un caractère, pas deux.
        val emojis = "🔑".repeat(ConstantesDuServeur.LONGUEUR_MIN_MOT_DE_PASSE)
        assertNull(attenteMotDePasse("ancien", emojis, emojis))
        assertEquals(AttenteMotDePasse.Confirmation, attenteMotDePasse("ancien", assez, assez + "x"))
        assertNull(attenteMotDePasse("ancien", assez, assez))

        repondre(400, """{"detail": "Mot de passe actuel incorrect."}""")
        val refus = try {
            service.changerMotDePasse("faux", "neuf")
            null
        } catch (e: ErreurService) {
            e
        }
        assertEquals(ErreurService.Refus("Mot de passe actuel incorrect.", 400), refus)
    }

    @Test
    fun laDemandeDeSuppressionSeLitSeFaitEtSeRetire() = runTest {
        repondre(200, FixturesProfil.ETAT_SANS_DEMANDE)
        val etat = service.etatSuppression()
        assertNull(etat.demande)
        assertEquals(30, etat.delaiJours)
        assertTrue("ce qui reste est dit", etat.consequences.any { "Ancien résident" in it })

        repondre(400, FixturesProfil.MOT_DE_PASSE_INCORRECT)
        val refus = try {
            service.demanderSuppression("refuse", "")
            null
        } catch (e: ErreurService) {
            e
        }
        assertEquals(ErreurService.Refus("Mot de passe incorrect.", 400), refus)
        serveur.takeRequest()
        serveur.takeRequest()

        repondre(201, FixturesProfil.DEMANDE)
        service.demanderSuppression("secret", "  ")
        val envoye = corps()
        assertEquals("secret", envoye["mot_de_passe"]!!.jsonPrimitive.content)
        assertTrue("un motif vide part à null", nul(envoye, "motif"))

        repondre(200, FixturesProfil.ETAT_AVEC_DEMANDE)
        assertEquals(30, service.etatSuppression().demande?.delaiJours)

        repondre(204, "")
        service.retirerDemandeSuppression()
        serveur.takeRequest()
        assertEquals("DELETE", serveur.takeRequest().method)
    }

    @Test
    fun uneDemandeTraiteeOuRetireeNEstPlusEnCours() = runTest {
        repondre(200, FixturesProfil.ETAT_AVEC_DEMANDE.replace("\"ouverte\"", "\"retiree\""))
        assertNull(service.etatSuppression().demande)
    }
}
