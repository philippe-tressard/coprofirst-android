// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

/**
 * Les réponses du Reporting, en JSON, telles que `GET /suivi-affaires` et
 * `GET /reporting/renouvellements` les renvoient — reprises de l'iOS
 * (`ServiceReportingCS+Simule`, `FixturesRenouvellements`). Des affaires, des
 * prestataires et des contrats inventés. Dans le build de débogage seulement.
 */
object FixturesReporting {
    private fun tranche(cle: String, libelle: String, teinte: String) = """{"cle": "$cle", "libelle": "$libelle", "nombre": 1, "teinte": "$teinte"}"""

    private fun barre(cle: String, libelle: String, vararg tranches: String) =
        """{"cle": "$cle", "libelle": "$libelle", "nombre": ${tranches.size}, "tranches": [${tranches.joinToString(",")}]}"""

    private val batA = tranche("bat_a", "Bât. A", "p1")
    private val batB = tranche("bat_b", "Bât. B", "p2")
    private val plusieurs = tranche("plusieurs", "Plusieurs bâtiments", "plusieurs")
    private val ascenseur = tranche("ascenseur", "Ascenseur", "s1")
    private val communes = tranche("parties_communes", "Parties communes", "s2")

    private fun affaire(
        id: Int, titre: String, age: Int, statut: String, libelleStatut: String, categorie: String, apercu: String,
        extra: String = "",
        ia: Boolean = false,
        evolue: Boolean = false,
    ): String {
        val anciennete = if (age > 90) "ancienne" else if (age > 30) "moyenne" else "recente"
        return """
            {"id": $id, "numero": "TK-A000$id", "titre": "$titre", "descriptif": "$apercu", "categorie": "parties_communes",
             "libelle_categorie": "$categorie", "statut": "$statut", "libelle_statut": "$libelleStatut",
             "ouverte_le": "2026-0${(id % 9) + 1}-10", "age_jours": $age, "anciennete": "$anciennete", "perimetre_cible": [],
             "jours_sans_avancee": 3, "a_relancer": false, "relances": [], "apercu": "$apercu", "apercu_ia": $ia,
             "batiments": [], "a_evolue": $evolue, "perimetre_libelle": ""$extra}
        """.trimIndent()
    }

    private fun suivi(mode: String, total: Int, anciennes: Int, fusionnees: Int, categories: String, batiments: String, affaires: List<String>) = """
        {"jour": "2026-11-15", "mode": "$mode", "total": $total, "anciennes": $anciennes, "delai_jours": 90,
         "fusionnees": $fusionnees, "resumes_disponibles": false, "par_categorie": [$categories],
         "par_batiment": [$batiments], "affaires": [${affaires.joinToString(",")}]}
    """.trimIndent()

    /** GET /suivi-affaires?mode=affaires → `SuiviAffairesRead`. */
    val AFFAIRES = suivi(
        "affaires", 3, 2, 1,
        listOf(barre("ascenseur", "Ascenseur", batA, batB), barre("parties_communes", "Parties communes", batA, plusieurs)).joinToString(","),
        listOf(barre("bat_a", "Bât. A", ascenseur, communes), barre("bat_b", "Bât. B", ascenseur)).joinToString(","),
        listOf(
            affaire(
                12, "Fuite au plafond du parking", 143, "en_cours", "Chez le syndic", "Parties communes",
                "Le syndic a mandaté un étancheur.", """, "motif_evolution": "reponse"""", ia = true, evolue = true,
            ),
            affaire(18, "Ascenseur du bâtiment B souvent à l'arrêt", 97, "chez_prestataire", "Chez le prestataire", "Ascenseur", "Trois pannes en un mois."),
            affaire(31, "Taille des haies côté rue", 12, "ouvert", "Ouvert", "Espaces verts", "À prévoir avant la fin du mois."),
        ),
    )

    /** GET /suivi-affaires?mode=entretien. */
    val ENTRETIEN = suivi(
        "entretien", 1, 0, 0, barre("ascenseur", "Ascenseur", batA), barre("bat_a", "Bât. A", ascenseur),
        listOf(
            affaire(40, "Visite semestrielle de l'ascenseur A", 20, "ouvert", "Ouvert", "Ascenseur", "Visite prévue.", """, "etat_visite": "a_venir""""),
        ),
    )

    private fun contrat(id: Int, libelle: String, fin: String?, preavis: String?, urgence: String?, dansLAnnee: Boolean, extra: String = "") = """
        {"id": $id, "copropriete_id": 1, "prestataire_id": ${id + 100}, "type_equipement": "ascenseur", "libelle": "$libelle",
         "date_debut": "2023-12-01", "actif": true, "prestataire_nom": "Ascenseurs Exemple",
         "date_fin": ${fin?.let { "\"$it\"" } ?: "null"}, "date_preavis": ${preavis?.let { "\"$it\"" } ?: "null"},
         "urgence_echeance": ${urgence?.let { "\"$it\"" } ?: "null"}, "echeance_dans_l_annee": $dansLAnnee$extra}
    """.trimIndent()

    private fun diagnostic(id: Int, nom: String, echeance: String?, urgence: String?, permanent: Boolean = false) = """
        {"id": $id, "code": "D$id", "nom": "$nom", "texte_legislatif": "Article d'exemple", "ordre": $id,
         "permanent": $permanent, "frequence": ${if (permanent) "null" else "\"10 ans\""}, "dernier_rapport_le": "2016-05-02",
         "prochaine_echeance": ${echeance?.let { "\"$it\"" } ?: "null"}, "urgence_echeance": ${urgence?.let { "\"$it\"" } ?: "null"}}
    """.trimIndent()

    /** GET /reporting/renouvellements → `ReleveRenouvellements`. */
    val RENOUVELLEMENTS = """
        {"annee": 2026, "preavis_mois": 3,
         "compteurs": {"contrats_en_preavis": 1, "echeances_contrats_annee": 1, "audits_a_planifier": 1},
         "contrats": [
           ${contrat(7, "Entretien des ascenseurs", "2026-11-30", "2026-08-31", "preavis", true, """, "numero_contrat": "ASC-2023-07", "note_moyenne": 2.5, "nb_notations": 4, "reconduit": true""")},
           ${contrat(8, "Nettoyage des parties communes", "2028-03-31", "2027-12-31", "futur", false)},
           ${contrat(9, "Espaces verts", null, null, null, false)}
         ],
         "diagnostics": [
           ${diagnostic(1, "Diagnostic technique global", "2026-05-02", "depasse")},
           ${diagnostic(2, "Contrôle des ascenseurs", "2031-01-15", "futur")},
           ${diagnostic(3, "Repérage amiante", null, null, permanent = true)}
         ]}
    """.trimIndent()
}
