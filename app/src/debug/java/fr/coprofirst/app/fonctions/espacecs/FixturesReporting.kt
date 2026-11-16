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

    /** GET /reporting/renouvellements → `ReleveRenouvellements` (écrite en entier : le juge des fixtures la lit). */
    val RENOUVELLEMENTS = """
        {"annee": 2026, "preavis_mois": 3,
         "compteurs": {"contrats_en_preavis": 1, "echeances_contrats_annee": 1, "audits_a_planifier": 1},
         "contrats": [
           {"id": 7, "copropriete_id": 1, "prestataire_id": 107, "type_equipement": "ascenseur", "libelle": "Entretien des ascenseurs", "date_debut": "2023-12-01", "actif": true, "prestataire_nom": "Ascenseurs Exemple", "date_fin": "2026-11-30", "date_preavis": "2026-08-31", "urgence_echeance": "preavis", "echeance_dans_l_annee": true, "numero_contrat": "ASC-2023-07", "note_moyenne": 2.5, "nb_notations": 4, "reconduit": true},
           {"id": 8, "copropriete_id": 1, "prestataire_id": 108, "type_equipement": "ascenseur", "libelle": "Nettoyage des parties communes", "date_debut": "2023-12-01", "actif": true, "prestataire_nom": "Ascenseurs Exemple", "date_fin": "2028-03-31", "date_preavis": "2027-12-31", "urgence_echeance": "futur", "echeance_dans_l_annee": false},
           {"id": 9, "copropriete_id": 1, "prestataire_id": 109, "type_equipement": "ascenseur", "libelle": "Espaces verts", "date_debut": "2023-12-01", "actif": true, "prestataire_nom": "Ascenseurs Exemple", "date_fin": null, "date_preavis": null, "urgence_echeance": null, "echeance_dans_l_annee": false}
         ],
         "diagnostics": [
           {"id": 1, "code": "D1", "nom": "Diagnostic technique global", "texte_legislatif": "Article d'exemple", "ordre": 1, "permanent": false, "frequence": "10 ans", "dernier_rapport_le": "2016-05-02", "prochaine_echeance": "2026-05-02", "urgence_echeance": "depasse"},
           {"id": 2, "code": "D2", "nom": "Contrôle des ascenseurs", "texte_legislatif": "Article d'exemple", "ordre": 2, "permanent": false, "frequence": "10 ans", "dernier_rapport_le": "2016-05-02", "prochaine_echeance": "2031-01-15", "urgence_echeance": "futur"},
           {"id": 3, "code": "D3", "nom": "Repérage amiante", "texte_legislatif": "Article d'exemple", "ordre": 3, "permanent": true, "frequence": null, "dernier_rapport_le": "2016-05-02", "prochaine_echeance": null, "urgence_echeance": null}
         ]}
    """.trimIndent()
}
