// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.fil

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.ZoneId

/**
 * Le fil inventé qu'on montre HORS de toute résidence : l'illustration d'« En savoir plus »
 * (#153), comme l'iOS (`Apercu.fil`, ses trois premiers éléments) — une urgence, une
 * actualité datée, une affaire du jour avec qui la lit et son lieu. Rien n'y vient d'une
 * personne ni d'une adresse réelle ; aucune image du site (les captures portent le logo).
 *
 * `meta` est écrit à la forme que sert coprofirst (`meta.affaire`, `flux/commun`) et relu
 * par `MetaDuFil.lire`, comme un vrai élément du fil : l'aperçu ne peut pas dériver de la carte.
 */
object ApercuDuFil {
    fun elements(maintenant: Instant = Instant.now()): List<ElementDuFil> {
        val aujourdhui = maintenant.atZone(PARIS).toLocalDate()
        fun le(jours: Long, heure: Int): Instant = aujourdhui.plusDays(jours).atTime(heure, 0).atZone(PARIS).toInstant()
        fun element(
            id: Int, type: String, date: Instant, titre: String, detail: String, meta: String,
            badges: List<String> = emptyList(), meConcerne: Boolean = false,
        ) =
            MetaDuFil.lire(Json.parseToJsonElement(meta).jsonObject).let { lu ->
                ElementDuFil(
                    id = "$type:$id", type = type, date = date, titre = titre, detail = detail, badges = badges,
                    meConcerne = meConcerne, cible = CibleDuFil.Affaire(id), meta = lu,
                )
            }
        return listOf(
            element(
                41, "ticket_ouvert", le(0, 9), "Fuite d'eau au plafond du parking",
                "<p>Niveau -1, place 12. Le plombier passe dans la <em>matinée</em>.</p>",
                """{"ticket_id": 41, "affaire": {"natures": ["activite"], "lecture": null, "perimetre_libelle": "Parking"}}""",
                badges = listOf("urgent"), meConcerne = true,
            ),
            element(
                12, "publication", le(0, 8), "Coupure d'eau jeudi de 9 h à 12 h",
                "Remplacement d'une vanne dans la cave du bâtiment A.",
                affaire(debut = le(2, 9), etat = "a_venir", jours = 2, libelle = "J-2", natures = """["actualite", "calendrier"]""", ticket = 12),
            ),
            element(
                44, "ticket_mis_a_jour", le(0, 7), "Ramonage des conduits de cheminée",
                "Le ramoneur passe dans les caves du bâtiment B.",
                affaire(
                    debut = le(0, 9), etat = "jour_j", jours = 0, libelle = "Jour J", natures = """["calendrier"]""", ticket = 44,
                    lecture = LECTURE_COPROS, lieu = "Bâtiment B",
                ),
            ),
        )
    }

    /** `meta` d'une affaire datée, à la forme que sert coprofirst (`meta.affaire`) : le délai écrit par le serveur. */
    private fun affaire(
        debut: Instant, etat: String, jours: Int, libelle: String, natures: String, ticket: Int,
        lecture: String = "null", lieu: String = "",
    ) = """{"ticket_id": $ticket, "affaire": {"debut": "$debut", "fin": null,
        "echeance": {"etat": "$etat", "jours": $jours, "libelle": "$libelle", "plusieurs_jours": false},
        "natures": $natures, "lecture": $lecture, "perimetre_libelle": "$lieu"}}"""

    private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    /** « Qui la lit » : les copropriétaires — la pastille telle que coprofirst la sert (`lecturesServies` de l'iOS). */
    private const val LECTURE_COPROS = """{"cle": "coproprietaires", "icones": ["key-round"], "court": "Copropriétaires",
        "long": "Copropriétaires (occupants et bailleurs)", "titre": "Copropriétaires (occupants et bailleurs)",
        "profils": ["occupants", "copro_bailleurs"], "perimetre_reserve": false,
        "hors_perimetre": ["occupants", "copro_bailleurs"], "du_perimetre": "",
        "phrase": "Lue par les copropriétaires (occupants et bailleurs), dans toute la copropriété.",
        "exclus": "Pas les bailleurs (mandataires), ni les locataires.", "par_defaut": false, "avertissement": ""}"""
}
