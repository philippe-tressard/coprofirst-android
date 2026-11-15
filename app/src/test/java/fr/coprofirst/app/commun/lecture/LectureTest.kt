// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.lecture

import fr.coprofirst.api.infrastructure.Serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import fr.coprofirst.api.modeles.Lecture as LectureGeneree

/** « Qui la lit » et le lieu, tels que le serveur les sert — jamais recomposés (comme l'iOS). */
class LectureTest {
    private fun json(texte: String) = Json.parseToJsonElement(texte)

    @Test
    fun laPastilleServieSeLitParLeTypeGenereOuDansUnMetaLibre() {
        val generee = Serializer.kotlinxSerializationJson.decodeFromString(LectureGeneree.serializer(), COPROS)
        val typee = Lecture.depuis(generee)
        assertEquals(typee, Lecture.lire(json(COPROS)))
        assertEquals("Copropriétaires", typee.court)
        assertEquals(listOf("key-round"), typee.icones)
        assertEquals(
            "Lue par les copropriétaires, dans toute la copropriété. Pas les bailleurs (mandataires), ni les locataires.",
            typee.phraseEntiere,
        )
        assertSame(typee, Lecture.aMontrer(typee))
    }

    @Test
    fun personneExcluOuRienDeServiRienAMontrer() {
        val tous = Lecture.lire(json(COPROS.replace("\"par_defaut\": false", "\"par_defaut\": true")))
        assertNull(Lecture.aMontrer(tous))
        assertNull(Lecture.aMontrer(null))
        // Le meta du fil ne sert pas toujours l'avertissement : la pastille reste.
        assertNull(Lecture.lire(json(COPROS.replace(", \"avertissement\": \"\"", "")))?.avertissement)
        // Illisible : rien, sans erreur.
        for (mauvais in listOf("""{"court": 3}""", "\"CS\"", "null", COPROS.replace("\"icones\": [\"key-round\"]", "\"icones\": [1]"))) {
            assertNull(mauvais, Lecture.lire(json(mauvais)))
        }
        assertNull(Lecture.lire(null))
    }

    @Test
    fun leLieuServiFaitFoi() {
        assertEquals("Bât. 2 › Toit", LieuServi.lieu("Bât. 2 › Toit"))
        assertNull("le périmètre par défaut se tait", LieuServi.lieu(""))
        assertNull(LieuServi.lieu(null))
    }

    private companion object {
        const val COPROS = """{"cle": "coproprietaires", "icones": ["key-round"], "court": "Copropriétaires",
            "long": "Copropriétaires (occupants et bailleurs)", "titre": "Copropriétaires (occupants et bailleurs)",
            "profils": ["occupants", "copro_bailleurs"], "perimetre_reserve": false,
            "hors_perimetre": ["occupants", "copro_bailleurs"], "du_perimetre": "",
            "phrase": "Lue par les copropriétaires, dans toute la copropriété.",
            "exclus": "Pas les bailleurs (mandataires), ni les locataires.", "par_defaut": false, "avertissement": ""}"""
    }
}
