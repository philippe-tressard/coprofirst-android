// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.formats

import fr.coprofirst.app.commun.formats.FormatsDuSite as F
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Les rendus de la table de l'iOS (`FormatsDuSite`), au caractère près. */
class FormatsDuSiteTest {
    private val nnbsp = ' '
    private val nbsp = ' '
    private val avril = Instant.parse("2026-04-02T14:30:00Z") // 16:30 à Paris (heure d'été)

    @Test
    fun dates() {
        assertEquals("2 avr. 2026", F.date(avril))
        assertEquals("02/04/2026", F.dateCourte(avril))
        assertEquals("2 avril 2026", F.dateLongue(avril))
        assertEquals("16:30", F.heure(avril))
        assertEquals("2 avr. 2026, 16:30", F.dateHeure(avril))
        assertEquals("02/04/2026 16:30", F.dateHeureCourte(avril))
        assertEquals("novembre 2026", F.moisAnnee("2026-11"))
        assertEquals("nov.", F.moisCourt(11))
        assertEquals(F.TIRET, F.date(null as Instant?))
        assertEquals(F.TIRET, F.date("pas une date"))
    }

    @Test
    fun jourCourtEtAnnee() {
        val maintenant = Instant.parse("2026-11-15T10:00:00Z")
        assertEquals("mar. 17 nov.", F.jourCourt(Instant.parse("2026-11-17T09:00:00Z"), maintenant = maintenant))
        assertEquals("19 nov.", F.jourCourt(Instant.parse("2026-11-19T09:00:00Z"), semaine = false, maintenant = maintenant))
        assertEquals("ven. 15 janv. 2027", F.jourCourt(Instant.parse("2027-01-15T09:00:00Z"), maintenant = maintenant))
    }

    @Test
    fun lectureCommeLeNavigateur() {
        // Un jour seul est minuit UTC ; sans fuseau, l'heure de Paris ; avec Z, absolu.
        assertEquals(Instant.parse("2026-04-10T00:00:00Z"), F.lire("2026-04-10"))
        assertEquals(Instant.parse("2026-04-10T14:00:00Z"), F.lire("2026-04-10T16:00:00"))
        assertEquals(Instant.parse("2026-04-10T16:00:00Z"), F.lire("2026-04-10T16:00:00Z"))
        assertEquals(Instant.parse("2026-04-10T14:00:00Z"), F.lire("2026-04-10T16:00:00+02:00"))
        assertEquals(Instant.parse("2026-04-10T14:00:00.123456Z"), F.lire("2026-04-10T16:00:00.123456"))
    }

    @Test
    fun jourDeParisJamaisUtc() {
        // 23 h 30 UTC le 9 avril est déjà le 10 à Paris.
        assertEquals("2026-04-10", F.jourIso(Instant.parse("2026-04-09T23:30:00Z")))
    }

    @Test
    fun nombresEtMontants() {
        assertEquals("47${nnbsp}047", F.nombre(47047))
        assertEquals("64,5", F.nombre(64.5))
        assertEquals("12,345", F.nombre(12.345))
        // Arrondi sur l'écriture décimale la plus courte, comme Intl : 12,345 € → 12,35 €.
        assertEquals("12,35${nbsp}€", F.montant(12.345))
        assertEquals("1${nnbsp}234,5${nbsp}€", F.montant(1234.5))
        assertEquals("0,0012${nbsp}€", F.montant(0.00123, decimales = 4))
        assertEquals(F.TIRET, F.nombre(null as Double?))
    }

    @Test
    fun octets() {
        assertEquals("512 o", F.octets(512))
        assertEquals("512 Ko", F.octets(512L * 1024))
        assertEquals("3,4 Mo", F.octets((3.4 * 1024 * 1024).toLong()))
        assertEquals("1,25 Go", F.octets((1.25 * 1024 * 1024 * 1024).toLong()))
        assertEquals("1,0 Mo", F.octets(1023L * 1024 + 900))
    }

    /** Le nom servi seul : rogné, « — » vide ou absent — jamais de repli (#356). */
    @Test
    fun nomServiSeul() {
        assertEquals("Camille EXEMPLE", F.nomServi("  Camille EXEMPLE "))
        assertEquals(F.TIRET, F.nomServi(""))
        assertEquals(F.TIRET, F.nomServi("   "))
        assertEquals(F.TIRET, F.nomServi(null))
    }

    /** Le repli de l'annuaire : le nom servi d'abord, puis prénom et nom saisis. */
    @Test
    fun nomServiDabord() {
        assertEquals("Camille EXEMPLE", F.nom("Camille EXEMPLE", "x", "y"))
        assertEquals("Camille Exemple", F.nom("  ", "Camille", "Exemple"))
        assertEquals(F.TIRET, F.nom(null, null, " "))
    }

    /** La date relative de l'iOS (`.relative(presentation: .named)`) : la plus grande unité, nommée quand elle vaut un. */
    @Test
    fun relatifCommeLiOS() {
        val maintenant = Instant.parse("2026-11-17T10:00:00Z")
        fun il(iso: String) = F.relatif(Instant.parse(iso), maintenant)
        assertEquals("maintenant", il("2026-11-17T10:00:00Z"))
        assertEquals("il y a 30 secondes", il("2026-11-17T09:59:30Z"))
        assertEquals("il y a 1 minute", il("2026-11-17T09:59:00Z"))
        assertEquals("il y a 9 heures", il("2026-11-17T01:00:00Z"))
        assertEquals("hier", il("2026-11-16T08:00:00Z"))
        assertEquals("avant-hier", il("2026-11-15T09:00:00Z"))
        assertEquals("il y a 3 jours", il("2026-11-14T09:00:00Z"))
        assertEquals("la semaine dernière", il("2026-11-09T10:00:00Z"))
        assertEquals("il y a 2 semaines", il("2026-11-02T10:00:00Z"))
        assertEquals("le mois dernier", il("2026-10-10T10:00:00Z"))
        assertEquals("il y a 3 mois", il("2026-08-01T10:00:00Z"))
        assertEquals("l’année dernière", il("2025-11-01T10:00:00Z"))
        assertEquals("il y a 2 ans", il("2024-11-01T10:00:00Z"))
        assertEquals("demain", il("2026-11-18T12:00:00Z"))
        assertEquals("dans 2 heures", il("2026-11-17T12:00:00Z"))
        assertEquals(F.TIRET, F.relatif(null as Instant?, maintenant))
    }
}
