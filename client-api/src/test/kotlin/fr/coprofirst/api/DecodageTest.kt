// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.api

import fr.coprofirst.api.infrastructure.Serializer
import fr.coprofirst.api.modeles.Annonce
import fr.coprofirst.api.modeles.AutoMatchRelance
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le client GÉNÉRÉ décode ce que le serveur sert — y compris d'une version plus récente. */
class DecodageTest {
    private val json = Serializer.kotlinxSerializationJson

    @Test
    fun objetLibreConserveSonContenu() {
        val reponse = json.decodeFromString<AutoMatchRelance>(
            """{"auto_match": {"rattache": true, "lot": {"n": 3}},
                "annonce": {"ton": "success", "texte": "Rattaché."}}""",
        )
        assertEquals(3, reponse.autoMatch.getValue("lot").jsonObject.getValue("n").jsonPrimitive.int)
        assertEquals(Annonce.Ton.SUCCESS, reponse.annonce.ton)
        assertEquals("Rattaché.", reponse.annonce.texte)
        // `ok` absent : la valeur par défaut du schéma.
        assertEquals(true, reponse.ok)
    }

    @Test
    fun champInconnuDUnServeurPlusRecentIgnore() {
        // L'app parle à des installations plus récentes que son plancher : un champ
        // ajouté depuis ne doit pas faire échouer le décodage.
        val reponse = json.decodeFromString<Annonce>(
            """{"ton": "info", "texte": "Bonjour", "champ_ajoute_plus_tard": 1}""",
        )
        assertTrue(reponse.texte == "Bonjour")
    }
}
