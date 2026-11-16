// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import fr.coprofirst.api.infrastructure.Serializer
import fr.coprofirst.api.modeles.FluxResponse
import fr.coprofirst.app.charte.NatureDuFil
import fr.coprofirst.app.commun.fil.Chronologie
import fr.coprofirst.app.commun.fil.CibleDuFil
import fr.coprofirst.app.commun.fil.EcheanceDuFil
import fr.coprofirst.app.commun.fil.ElementDuFil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * `ElementDuFil` lu depuis des réponses de GET /flux À LA FORME DU SCHÉMA (`FluxResponse`),
 * décodées par le client généré — comme le service les lit. Tout est inventé.
 */
class ElementDuFilTest {
    private fun lire(items: String): List<ElementDuFil> =
        Serializer.kotlinxSerializationJson.decodeFromString(FluxResponse.serializer(), """{"delai_recent_jours": 7, "items": [$items]}""")
            .items.orEmpty().map(ElementDuFil::depuis)

    private fun un(item: String) = lire(item).single()

    private fun item(type: String, id: String = "1", meta: String = "{}", extra: String = "") =
        """{"type": "$type", "id": "$id", "date": "2026-11-15T08:30:00Z", "titre": "T", "meta": $meta $extra}"""

    @Test
    fun laCleEstLeTypeEtLId() {
        val element = un(item("ticket_ouvert", "41"))
        assertEquals("ticket_ouvert:41", element.id)
        assertEquals(Instant.parse("2026-11-15T08:30:00Z"), element.date)
        // Sans id servi : la date, jamais une clé vide qui confondrait deux éléments.
        val sansId = un("""{"type": "faq", "date": "2026-11-15T08:30:00Z", "titre": "Q"}""")
        assertEquals("faq:${Instant.parse("2026-11-15T08:30:00Z").epochSecond}", sansId.id)
    }

    @Test
    fun laCibleSuitLaRegleDuSite() {
        assertEquals(CibleDuFil.Affaire(41), un(item("ticket_mis_a_jour", meta = """{"ticket_id": 41}""")).cible)
        assertEquals(CibleDuFil.Affaire(12), un(item("publication", meta = """{"ticket_id": 12.0}""")).cible)
        assertEquals(CibleDuFil.Sondage(3), un(item("sondage_clos", meta = """{"sondage_id": 3}""")).cible)
        assertEquals(
            CibleDuFil.Document(7, "pv.pdf"),
            un(item("document", meta = """{"document_id": 7, "fichier_nom": "pv.pdf"}""", extra = """, "lien": "/documents"""")).cible,
        )
        // Un document sans page sur le site : son résumé.
        assertEquals(CibleDuFil.Resume(null), un(item("document", meta = """{"document_id": 7}""")).cible)
        assertEquals(CibleDuFil.Resume("/annonces"), un(item("annonce", extra = """, "lien": "/annonces"""")).cible)
        // Un identifiant d'un autre type : rien ne s'ouvre de travers.
        assertEquals(CibleDuFil.Resume(null), un(item("ticket_ouvert", meta = """{"ticket_id": "41"}""")).cible)
    }

    @Test
    fun lUrgenceEstCelleDuSite() {
        assertTrue(un(item("ticket_ouvert", extra = """, "badges": ["urgent"]""")).urgent)
        assertFalse(un(item("ticket_resolu", extra = """, "badges": ["urgent"]""")).urgent)
        assertTrue(un(item("publication", meta = """{"urgente": true, "statut": "ouvert"}""")).urgent)
        assertFalse(un(item("publication", meta = """{"urgente": true, "statut": "resolu"}""")).urgent)
        assertTrue(un(item("ticket_ouvert", extra = """, "me_concerne": true""")).meConcerne)
    }

    @Test
    fun lEcheanceEstServieJamaisComptee() {
        val datee = un(item("ticket_mis_a_jour", meta = AFFAIRE_DATEE)).echeance
        assertEquals(EcheanceDuFil("J-8", passee = false, debut = "2026-11-23T08:00:00Z", fin = "2026-11-25T16:00:00Z"), datee)
        val maintenant = Instant.parse("2026-11-15T10:00:00Z")
        assertEquals("lun. 23 nov.${EcheanceDuFil.FLECHE}25 nov.", datee?.dates(maintenant))
        // Le même jour : la fin se tait ; passée, le délai perd sa teinte ; close, pas de délai.
        val passee = un(item("ticket_mis_a_jour", meta = """{"affaire": {"debut": "2026-11-12T08:00:00Z", "fin": "2026-11-12T16:00:00Z",
            "echeance": {"etat": "passee", "libelle": "Passé", "plusieurs_jours": false}}}""")).echeance
        assertEquals(EcheanceDuFil("Passé", passee = true, debut = "2026-11-12T08:00:00Z", fin = null), passee)
        assertEquals("jeu. 12 nov.", passee?.dates(maintenant))
        val close = un(item("ticket_resolu", meta = """{"affaire": {"debut": "2026-11-11T08:00:00Z", "echeance": {"etat": "close", "libelle": null}}}"""))
        assertNull(close.echeance?.libelle)
        // Sans échéance ni début (installation antérieure, affaire sans date) : rien.
        assertNull(un(item("ticket_ouvert", meta = """{"affaire": {"natures": ["activite"]}}""")).echeance)
        assertNull(un(item("ticket_ouvert", meta = """{"affaire": {"echeance": {"etat": "a_venir"}}}""")).echeance)
    }

    @Test
    fun laNatureEnPastilleNeRepetePasLaRubrique() {
        val activite = un(item("ticket_mis_a_jour", meta = """{"affaire": {"natures": ["activite"]}}"""))
        assertEquals("wrench", activite.pictogramme)
        assertNull("« Affaire mise à jour » dit déjà « Affaire »", activite.natureEnPastille)
        val calendrier = un(item("ticket_mis_a_jour", meta = """{"affaire": {"natures": ["calendrier"]}}"""))
        assertEquals("Calendrier", calendrier.natureEnPastille?.libelle)
        assertEquals("badge-purple", calendrier.natureEnPastille?.badge)
        assertEquals("calendar-days", calendrier.pictogramme)
        // Une actualité datée porte deux natures : l'actualité prime, et la rubrique la dit déjà.
        val actualite = un(item("publication", meta = """{"affaire": {"natures": ["calendrier", "actualite"]}}"""))
        assertEquals("actualite", actualite.natureDeLAffaire?.code)
        assertNull(actualite.natureEnPastille)
        // Sans nature (document, annonce…) : le pictogramme de la rubrique.
        val document = un(item("document"))
        assertNull(document.natureDeLAffaire)
        assertEquals("file-text", document.pictogramme)
        // Un type inconnu : neutre, jamais une erreur.
        assertEquals(NatureDuFil.inconnue, un(item("nouveau_type")).nature)
    }

    @Test
    fun laLectureLeLieuEtLesPiecesSontServis() {
        val element = un(item("ticket_ouvert", meta = """{"affaire": {"lecture": $LECTURE_CS, "perimetre_libelle": "Bât. 2 › Toit"},
            "photos_urls": ["/uploads/tickets/1.jpg"], "fichiers_urls": ["/uploads/tickets/devis.pdf"]}"""))
        assertEquals("CS", element.meta.lecture?.court)
        assertEquals("Bât. 2 › Toit", element.meta.lieu)
        assertEquals(listOf("/uploads/tickets/1.jpg"), element.pieces.photos)
        assertEquals(listOf("/uploads/tickets/devis.pdf"), element.pieces.documents)
        // Le périmètre par défaut se tait ; une lecture illisible n'emporte pas la carte.
        val defaut = un(item("ticket_ouvert", meta = """{"affaire": {"lecture": {"court": 3}, "perimetre_libelle": ""}, "photos_urls": "x"}"""))
        assertNull(defaut.meta.lecture)
        assertNull(defaut.meta.lieu)
        assertTrue(defaut.pieces.vide)
    }

    @Test
    fun leFilSeRangeJourParJourAParis() {
        val elements = lire(
            listOf("2026-11-14T23:30:00Z", "2026-11-15T08:00:00Z", "2026-11-14T21:00:00Z", "2026-11-13T09:00:00Z")
                .mapIndexed { rang, date -> """{"type": "faq", "id": "$rang", "date": "$date", "titre": "Q"}""" }
                .joinToString(","),
        )
        val jours = Chronologie.parJour(elements)
        // 23 h 30 UTC le 14 : déjà le 15 à Paris (UTC+1).
        assertEquals(listOf("2026-11-15", "2026-11-14", "2026-11-13"), jours.map { it.jour })
        assertEquals(listOf("faq:1", "faq:0"), jours.first().elements.map { it.id })
        val maintenant = Instant.parse("2026-11-15T10:00:00Z")
        assertEquals(Chronologie.Veille.Aujourdhui, Chronologie.quand("2026-11-15", maintenant))
        assertEquals(Chronologie.Veille.Hier, Chronologie.quand("2026-11-14", maintenant))
        assertEquals(Chronologie.Veille.Avant, Chronologie.quand("2026-11-13", maintenant))
    }

    @Test
    fun uneFicheNeSOuvreQueDeclaree() {
        val sondage = setOf(DestinationDuFil.SONDAGE)
        assertEquals(DestinationDuFil.Resume, DestinationDuFil.pour(CibleDuFil.Resume(null), sondage))
        assertEquals(DestinationDuFil.Document(7, "pv.pdf"), DestinationDuFil.pour(CibleDuFil.Document(7, "pv.pdf"), sondage))
        assertEquals(DestinationDuFil.Element("sondage", 3), DestinationDuFil.pour(CibleDuFil.Sondage(3), sondage))
        // L'affaire attend la fiche de Mes affaires (#16) ; déclarée, elle s'ouvre comme le sondage.
        assertNull(DestinationDuFil.pour(CibleDuFil.Affaire(41), sondage))
        assertEquals(DestinationDuFil.Element("affaire", 41), DestinationDuFil.pour(CibleDuFil.Affaire(41), setOf("affaire")))
    }

    @Test
    fun voirSurLeSiteResteSurLaResidence() {
        val adresse = "https://residence.example.com"
        assertEquals("https://residence.example.com/annonces?id=4", DestinationDuFil.surLeSite("/annonces?id=4", adresse))
        assertEquals("https://residence.example.com/faq", DestinationDuFil.surLeSite("https://residence.example.com/faq", adresse))
        assertEquals("https://residence.example.com/sous/faq", DestinationDuFil.surLeSite("/faq", "https://residence.example.com/sous"))
        assertNull(DestinationDuFil.surLeSite("https://ailleurs.example.com/faq", adresse))
        assertNull(DestinationDuFil.surLeSite("javascript:alert(1)", adresse))
        assertNull(DestinationDuFil.surLeSite(null, adresse))
    }

    private companion object {
        const val AFFAIRE_DATEE = """{"ticket_id": 38, "affaire": {"debut": "2026-11-23T08:00:00Z", "fin": "2026-11-25T16:00:00Z",
            "echeance": {"etat": "a_venir", "jours": 8, "libelle": "J-8", "plusieurs_jours": true}, "natures": ["calendrier"]}}"""
        const val LECTURE_CS = """{"cle": "cs", "icones": ["shield-check"], "court": "CS", "long": "Conseil syndical seul",
            "titre": "Conseil syndical seul", "profils": [], "perimetre_reserve": false, "hors_perimetre": [], "du_perimetre": "",
            "phrase": "Personne d'autre que le conseil syndical ne la lit.", "exclus": "", "par_defaut": false, "avertissement": ""}"""
    }
}
