// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import fr.coprofirst.app.commun.ConstantesDuServeur
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** « Ce que j'affiche » (#47) : la lecture des préférences, ce qui part, et l'accord des deux tables générées. */
class PreferencesProfilTest {
    private val defauts = ConstantesDuServeur.defautsPreferences

    @Test
    fun unCompteJamaisRegleHeriteDeChaqueDefautDuServeur() {
        val lue = LecturePreferences.depuis("{}")
        assertEquals(defauts, lue.valeurs)
        assertEquals(defauts.keys, lue.heritees)
    }

    @Test
    fun uneCleRegleeVautSonChoixEtLeResteHerite() {
        val cle = defauts.keys.first()
        val lue = LecturePreferences.depuis("""{"$cle": ${!defauts.getValue(cle)}}""")
        assertEquals(!defauts.getValue(cle), lue.valeurs[cle])
        assertEquals(defauts.keys - cle, lue.heritees)
    }

    /** Comme l'iOS (`as? Bool`) : une chaîne, un nombre, un JSON illisible ne règlent rien. */
    @Test
    fun ceQuiNEstPasUnBooleenNeRegleRien() {
        val cle = defauts.keys.first()
        for (json in listOf("""{"$cle": "true"}""", """{"$cle": 1}""", "pas du json", "[]", null)) {
            val lue = LecturePreferences.depuis(json)
            assertEquals(json, defauts, lue.valeurs)
            assertEquals(json, defauts.keys, lue.heritees)
        }
    }

    /** Ce qui s'enregistre : toutes les clés, réglées, en ordre — y compris celle qu'on vient de changer. */
    @Test
    fun leJsonEnregistrePorteToutesLesCles() {
        val cle = defauts.keys.last()
        val json = Json.parseToJsonElement(LecturePreferences.depuis("{}").avec(cle, !defauts.getValue(cle)).json).jsonObject
        assertEquals(defauts.keys.sorted(), json.keys.toList())
        assertEquals(!defauts.getValue(cle), json.getValue(cle).jsonPrimitive.boolean)
    }

    /**
     * Les cases (`canauxNotifications`, du site) et les défauts (`DEFAUTS`, du serveur) sont
     * publiés à part : une case sans défaut, ou un défaut sans case, est un désaccord des deux
     * tables à porter dans coprofirst, pas à combler ici.
     */
    @Test
    fun chaqueCaseAUnDefautEtChaqueDefautUneCase() {
        val cases = TablesProfil.canauxNotifications.flatMap { listOf(it.mon, it.autres) }.toSet()
        assertEquals(defauts.keys, cases)
    }

    @Test
    fun lEnregistrementPartSeulSurLeCompte() = runTest {
        val serveur = MockWebServer().apply { start() }
        try {
            val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
            val session = SessionOuverte(
                Installation("https://residence.exemple.test", "Les Glycines"), SessionsParHote(OkHttpClient()), Dispatchers.Unconfined, base,
            )
            serveur.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(FixturesProfil.MOI).build())
            ServiceProfilApi(session).enregistrerPreferences("""{"a":true}""", restreindre = true)
            val requete = serveur.takeRequest()
            assertEquals("PATCH", requete.method)
            assertTrue(requete.url.encodedPath.endsWith("/auth/me"))
            val envoye = Json.parseToJsonElement(requete.body!!.utf8()).jsonObject
            assertEquals(setOf("preferences_notifications", "restreindre_a_mes_batiments"), envoye.keys)
            assertEquals("""{"a":true}""", envoye.getValue("preferences_notifications").jsonPrimitive.content)
            assertEquals(true, envoye.getValue("restreindre_a_mes_batiments").jsonPrimitive.boolean)
        } finally {
            serveur.close()
        }
    }
}
