// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.api

import fr.coprofirst.api.infrastructure.Serializer
import fr.coprofirst.api.modeles.Annonce
import fr.coprofirst.api.modeles.AutoMatchRelance
import fr.coprofirst.api.modeles.VersionLue
import java.time.OffsetDateTime
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

    /** #44 : le serveur sert ses horodatages SANS fuseau ; ils se lisent comme UTC, comme l'iOS. */
    @Test
    fun dateSansFuseauLueCommeUtc() {
        val sans = json.decodeFromString<VersionLue>("""{"version": "1.0", "publiee_le": "2026-11-12T09:00:00"}""")
        assertEquals(OffsetDateTime.parse("2026-11-12T09:00:00Z"), sans.publieeLe)
        val fraction = json.decodeFromString<VersionLue>("""{"version": "1.0", "publiee_le": "2026-11-12T09:00:00.123456"}""")
        assertEquals(OffsetDateTime.parse("2026-11-12T09:00:00.123456Z"), fraction.publieeLe)
        val avec = json.decodeFromString<VersionLue>("""{"version": "1.0", "publiee_le": "2026-11-12T10:00:00+01:00"}""")
        assertTrue(avec.publieeLe.isEqual(OffsetDateTime.parse("2026-11-12T09:00:00Z")))
    }

    @Test(expected = Exception::class)
    fun dateIllisibleRefusee() {
        json.decodeFromString<VersionLue>("""{"version": "1.0", "publiee_le": "hier"}""")
    }
}
