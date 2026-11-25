// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.formats

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/**
 * Les formats du site, à la MÊME règle que lui et que l'iOS (`FormatsDuSite`) — fr-FR,
 * fuseau de Paris :
 *
 * | Fonction | Site | Rendu |
 * |---|---|---|
 * | `date` | `fmtDate` | « 2 avr. 2026 » |
 * | `dateCourte` | `fmtDateShort` | « 02/04/2026 » |
 * | `dateLongue` | `fmtDateLong` | « 2 avril 2026 » |
 * | `dateHeure` | `fmtDatetime` | « 2 avr. 2026, 16:30 » |
 * | `dateHeureCourte` | `fmtDatetimeShort` | « 02/04/2026 16:30 » |
 * | `heure` | `fmtTime` | « 16:30 » |
 * | `jourCourt` | `fmtJourCourt` | « mar. 17 nov. », « 19 nov. » |
 * | `jourIso` | `jourIso` | « 2026-04-02 » (envoyé) |
 * | `moisAnnee` | `fmtMonthYear` | « novembre 2026 » |
 * | `moisCourt` | `fmtMoisCourt` | « nov. » |
 * | `nombre` | `fmtNombre` | « 47 047 », « 64,5 » |
 * | `montant` | `fmtMontant` | « 1 234,5 € » |
 * | `octets` | `fmtOctets` | « 512 Ko », « 3,4 Mo », « 1,25 Go » |
 *
 * Une valeur absente s'écrit « — », comme sur le site. AUCUN écran n'écrit son propre
 * format de date ou de nombre : il passe par ici (verifier-depot.sh le tient).
 */
object FormatsDuSite {
    const val TIRET = "—"
    private val FR: Locale = Locale.FRANCE
    val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    private fun motif(motif: String) = DateTimeFormatter.ofPattern(motif, FR).withZone(PARIS)
    private val DATE = motif("d MMM yyyy")
    private val DATE_COURTE = motif("dd/MM/yyyy")
    private val DATE_LONGUE = motif("d MMMM yyyy")
    private val HEURE = motif("HH:mm")
    private val JOUR_SEMAINE = motif("EEE d MMM")
    private val JOUR_SEMAINE_ANNEE = motif("EEE d MMM yyyy")
    private val JOUR = motif("d MMM")
    private val JOUR_ANNEE = motif("d MMM yyyy")
    private val MOIS_ANNEE = motif("MMMM yyyy")
    private val MOIS_COURT = motif("MMM")

    // Dates — un instant, ou une chaîne telle que l'API la sert (ISO).
    fun date(valeur: Instant?): String = valeur?.let(DATE::format) ?: TIRET
    fun date(iso: String?): String = date(lire(iso))
    fun dateCourte(valeur: Instant?): String = valeur?.let(DATE_COURTE::format) ?: TIRET
    fun dateCourte(iso: String?): String = dateCourte(lire(iso))
    fun dateLongue(valeur: Instant?): String = valeur?.let(DATE_LONGUE::format) ?: TIRET
    fun dateLongue(iso: String?): String = dateLongue(lire(iso))
    fun heure(valeur: Instant?): String = valeur?.let(HEURE::format) ?: TIRET
    fun heure(iso: String?): String = heure(lire(iso))
    fun dateHeure(valeur: Instant?): String = valeur?.let { "${date(it)}, ${heure(it)}" } ?: TIRET
    fun dateHeure(iso: String?): String = dateHeure(lire(iso))
    fun dateHeureCourte(valeur: Instant?): String = valeur?.let { "${dateCourte(it)} ${heure(it)}" } ?: TIRET
    fun dateHeureCourte(iso: String?): String = dateHeureCourte(lire(iso))

    /**
     * `fmtJourCourt` : « mar. 17 nov. » ; l'année ne s'ajoute que si elle n'est pas celle
     * de `maintenant`, à Paris. `semaine = false` retire le jour de la semaine.
     */
    fun jourCourt(valeur: Instant?, semaine: Boolean = true, maintenant: Instant = Instant.now()): String {
        valeur ?: return TIRET
        val memeAnnee = annee(valeur) == annee(maintenant)
        val format = when {
            semaine && memeAnnee -> JOUR_SEMAINE
            semaine -> JOUR_SEMAINE_ANNEE
            memeAnnee -> JOUR
            else -> JOUR_ANNEE
        }
        return format.format(valeur)
    }

    fun jourCourt(iso: String?, semaine: Boolean = true, maintenant: Instant = Instant.now()) =
        jourCourt(lire(iso), semaine, maintenant)

    /**
     * Une date RELATIVE à `maintenant`, comme l'iOS (`.relative(presentation: .named)`,
     * `RelativeDateTimeFormatter`) : l'écart se compte à Paris en années, mois, semaines,
     * jours, heures, minutes puis secondes, et seule la plus grande unité non nulle se dit —
     * « hier », « il y a 3 jours », « la semaine dernière », « dans 2 heures », « maintenant ».
     */
    fun relatif(valeur: Instant?, maintenant: Instant = Instant.now()): String {
        valeur ?: return TIRET
        val passe = valeur.isBefore(maintenant)
        var de = (if (passe) valeur else maintenant).atZone(PARIS)
        val a = (if (passe) maintenant else valeur).atZone(PARIS)
        for (unite in UNITES_RELATIVES) {
            val n = unite.between(de, a)
            if (n > 0) return Relatif.dire(unite, n, passe)
            de = de.plus(n, unite)
        }
        return "maintenant"
    }

    fun relatif(iso: String?, maintenant: Instant = Instant.now()) = relatif(lire(iso), maintenant)

    private val UNITES_RELATIVES = listOf(
        ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.WEEKS, ChronoUnit.DAYS,
        ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS,
    )

    /** Le JOUR de Paris d'un instant, « 2026-04-10 » — jamais le jour UTC. */
    fun jourIso(valeur: Instant): String = valeur.atZone(PARIS).toLocalDate().toString()

    /** « novembre 2026 » — un instant, ou un mois servi « 2026-11 ». */
    fun moisAnnee(valeur: Instant?): String = valeur?.let(MOIS_ANNEE::format) ?: TIRET
    fun moisAnnee(mois: String?): String = moisAnnee(mois?.let { lire("$it-15") })

    /** « nov. » — le nom court d'un mois, de 1 à 12. */
    fun moisCourt(mois: Int): String =
        if (mois in 1..12) MOIS_COURT.format(LocalDate.of(2000, mois, 15).atStartOfDay(PARIS)) else TIRET

    private fun annee(valeur: Instant) = valeur.atZone(PARIS).year

    /**
     * Une chaîne ISO lue comme le navigateur la lit (`new Date(d)`) : un JOUR seul est
     * minuit UTC ; un horodatage avec `Z` ou un décalage est absolu ; SANS fuseau, c'est
     * l'heure de Paris.
     */
    fun lire(iso: String?): Instant? {
        if (iso.isNullOrEmpty()) return null
        return runCatching {
            when {
                iso.length == 10 -> LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant()
                iso.endsWith("Z") || Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(iso.substringAfter('T')) ->
                    OffsetDateTime.parse(iso).toInstant()
                else -> ZonedDateTime.of(LocalDateTime.parse(iso), PARIS).toInstant()
            }
        }.getOrNull()
    }

    /**
     * Le nom d'une personne : « Prénom NOM » tel que le SERVEUR l'affiche (`nom_affiche`,
     * `locataire_nom_affiche`, `bailleur_nom_affiche`, `nom_aide_affiche`, `auteur_affiche`),
     * jamais composé dans un écran. Le plancher le sert partout ; vide, « — ». Aucun repli
     * (`nom(servi:)` de l'iOS).
     */
    fun nomServi(servi: String?): String = servi?.trim()?.takeIf { it.isNotEmpty() } ?: TIRET

    /**
     * Le nom d'une fiche d'ANNUAIRE, qui peut manquer de nom servi : une fiche du conseil ou
     * du syndic pas encore enregistrée, ou renommée (la feuille, elle, montre le nom que le
     * serveur rend de la saisie, `POST /noms/affiche`). Non servi ou vide : prénom puis nom
     * tels que servis ou saisis, « — » sans l'un ni l'autre. Ce repli vit ICI seulement, pour
     * l'annuaire : `scripts/test_regles_servies.py` le refuse ailleurs (#356).
     */
    fun nom(affiche: String?, prenom: String?, nom: String?): String {
        affiche?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val morceaux = listOfNotNull(prenom?.trim(), nom?.trim()).filter { it.isNotEmpty() }
        return if (morceaux.isEmpty()) TIRET else morceaux.joinToString(" ")
    }

    // Nombres — `Intl.NumberFormat('fr-FR')` : espace fine insécable entre les milliers,
    // virgule décimale, arrondi « au plus proche » sur l'écriture décimale la plus courte.
    fun nombre(valeur: Double?): String = valeur?.let { format(it, 0, 3) } ?: TIRET
    fun nombre(valeur: Long?): String = nombre(valeur?.toDouble())
    fun nombre(valeur: Int?): String = nombre(valeur?.toDouble())

    /** `fmtMontant` : une devise, de zéro à `decimales` décimales. */
    fun montant(valeur: Double?, devise: String = "EUR", decimales: Int = 2): String =
        valeur?.let { montant(decimal(it), devise, decimales) } ?: TIRET

    /** Un montant servi en centimes entiers (`prix_centimes`, #370) : la même règle, sans flottant. */
    fun montantCentimes(centimes: Int?, devise: String = "EUR"): String =
        centimes?.let { montant(BigDecimal.valueOf(it.toLong(), 2), devise, 2) } ?: TIRET

    private fun montant(valeur: BigDecimal, devise: String, decimales: Int): String {
        val f = NumberFormat.getCurrencyInstance(FR) as DecimalFormat
        f.currency = Currency.getInstance(devise)
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = decimales
        f.roundingMode = RoundingMode.HALF_UP
        return f.format(valeur)
    }

    /** `fmtOctets` : octets, Ko arrondis, Mo à une décimale, Go à deux (jamais « 1 024 Ko »). */
    fun octets(valeur: Long?): String {
        valeur ?: return TIRET
        val o = valeur.toDouble()
        if (o < 1024) return "${format(o, 0, 0)} o"
        val ko = o / 1024
        if (ko < 1023.5) return "${format(ko, 0, 0)} Ko"
        val mo = ko / 1024
        if (mo < 1023.95) return "${format(mo, 1, 1)} Mo"
        return "${format(mo / 1024, 2, 2)} Go"
    }

    private fun format(valeur: Double, min: Int, max: Int): String {
        val f = DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(FR))
        f.minimumFractionDigits = min
        f.maximumFractionDigits = max
        f.roundingMode = RoundingMode.HALF_UP
        return f.format(decimal(valeur))
    }

    /** Le nombre tel que JavaScript l'écrit (la plus courte écriture décimale) : 12.345 → « 12,35 ». */
    private fun decimal(valeur: Double): BigDecimal = BigDecimal(valeur.toString())
}
