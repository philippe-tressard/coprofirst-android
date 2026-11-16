// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

/**
 * Les réponses du serveur pour l'Espace CS, en JSON, telles que les routes les renvoient —
 * servies par un serveur local aux tests JVM et d'interface, lues par le client GÉNÉRÉ puis
 * par le MÊME branchement que l'app (`lu()`), comme l'iOS (`FixturesComptesCS`,
 * `FixturesBadgesCS`). Un champ requis qui manque fait échouer le décodeur généré.
 *
 * Tout est inventé : aucun nom de personne réelle, des adresses `exemple.test`. Dans le
 * build de débogage seulement.
 */
object FixturesEspaceCS {
    private fun compte(id: Int, prenom: String, nom: String, statut: String, role: String, cree: String, extra: String = "", aide: String = "") = """
        {"id": $id, "prenom": "$prenom", "nom": "$nom", "email": "${prenom.lowercase()}@exemple.test", "actif": false,
         "nom_affiche": "$prenom ${nom.uppercase()}", "nom_aide_affiche": "$aide", "est_moderateur": false,
         "role": "$role", "statut": "$statut", "cree_le": "$cree", "onboarding_complete": false,
         "onboarding_etape": 1, "preferences_notifications": "{}"$extra}
    """.trimIndent()

    /** GET /admin/comptes-en-attente → `[UserRead]`. Le 503 est un aidant, avec son aidé. */
    val COMPTES = "[" + listOf(
        compte(501, "Camille", "Exemple", "copropriétaire_résident", "propriétaire", "2026-11-06T08:00:00", """, "batiment_nom": "Bâtiment A""""),
        compte(502, "Dominique", "Fictif", "locataire", "résident", "2026-11-07T08:00:00", """, "batiment_nom": "Bâtiment B""""),
        compte(503, "Alix", "Modèle", "aidant", "externe", "2026-11-08T07:00:00", """, "prenom_aide": "Claude", "nom_aide": "Fictif"""", aide = "Claude FICTIF"),
    ).joinToString(",") + "]"

    /** GET /admin/commandes-acces → `[CommandeAccesEnAttente]`. */
    const val COMMANDES = """
        [
          {"id": 71, "user_id": 31, "lot_id": 12, "type": "vigik", "quantite": 2, "statut": "en_attente",
           "demandeur_nom": "Sacha Exemple", "lot": "Lot 12 — appartement", "batiment": "Bâtiment A",
           "cree_le": "2026-11-03T10:00:00"},
          {"id": 72, "user_id": 32, "lot_id": 104, "type": "telecommande", "quantite": 1, "statut": "en_attente",
           "demandeur_nom": "Morgan Fictif", "lot": "Lot 104 — parking", "batiment": null,
           "cree_le": "2026-11-07T15:00:00"}
        ]
    """

    /**
     * POST /admin/comptes/{id}/traiter → `CompteTraiteResult`. Le 502 n'est pas dans
     * l'import : l'avertissement du serveur, que l'app montre tel quel.
     */
    fun traite(id: Int, valider: Boolean): String {
        val annonce = when {
            !valider -> "null"
            id == 502 -> """{"ton": "warning", "texte": "Compte activé. ⚠️ Aucun lot trouvé dans l'import."}"""
            else -> """{"ton": "success", "texte": "Compte activé — 1 lot rattaché."}"""
        }
        return """{"user": ${compte(id, "Compte", "Traite", "copropriétaire_résident", "propriétaire", "2026-11-06T08:00:00")}, "annonce": $annonce}"""
    }

    /** POST /admin/utilisateurs/{id}/accueil-arrivant → `AccueilArrivantResultat`. */
    const val ACCUEIL = """{"ok": true, "notifications_envoyees": 2, "email_syndic": true, "ticket_suivi": null,
        "annonce_publiee": true, "consignes_transmises": true}"""

    /** POST /admin/commandes-acces/{id}/traiter → `CommandeTraitee`. */
    fun commandeTraitee(accepter: Boolean) = """{"statut": "${if (accepter) "acceptee" else "refusee"}"}"""

    /**
     * GET /acces/admin/vigiks → `[AccesAdminOut]`. Les rangs suivent le NOM : « Basile
     * Fictif » (rang 1) passe avant « Alix Exemple » (rang 2) bien que son prénom vienne après.
     */
    const val VIGIKS = """
        [
          {"id": 1, "code": "4521", "porteur_id": 21, "porteur_nom": "Alix Exemple", "porteur_rang": 2,
           "lot_id": 12, "lot_libelle": "Lot 12 — appartement", "perimetre_cible": ["copropriete", "bat_a"],
           "perimetre_libelle": "Bât. A", "statut": "actif", "chez_locataire": false,
           "cree_le": "2026-02-10T09:00:00"},
          {"id": 2, "code": "417D5927", "porteur_id": 22, "porteur_nom": "Basile Fictif", "porteur_rang": 1,
           "lot_id": 21, "lot_libelle": "Lot 21 — appartement", "perimetre_cible": ["copropriete", "bat_b"],
           "perimetre_libelle": "Bât. B", "statut": "perdu", "chez_locataire": true,
           "cree_le": "2026-03-02T09:00:00"},
          {"id": 4, "code": "7731", "porteur_id": 23, "porteur_nom": "Céleste Inventé", "porteur_rang": 3,
           "lot_id": null, "lot_libelle": null, "perimetre_cible": [], "perimetre_libelle": "", "statut": "suspendu",
           "chez_locataire": false, "cree_le": "2025-12-01T09:00:00"}
        ]
    """

    /** GET /acces/admin/telecommandes → `[AccesAdminOut]` ; la 5 n'a pas de porteur. */
    const val TELECOMMANDES = """
        [
          {"id": 3, "code": "TC-0042", "porteur_id": 21, "porteur_nom": "Alix Exemple", "porteur_rang": 2,
           "lot_id": 104, "lot_libelle": "Lot 104 — parking", "perimetre_cible": ["parking"],
           "perimetre_libelle": "Parking", "statut": "actif", "chez_locataire": false,
           "cree_le": "2026-04-18T09:00:00"},
          {"id": 5, "code": "TC-0007", "porteur_id": null, "porteur_nom": "", "porteur_rang": 0,
           "lot_id": 105, "lot_libelle": "Lot 105 — parking", "perimetre_cible": ["parking"],
           "perimetre_libelle": "Parking", "statut": "actif", "chez_locataire": false,
           "cree_le": "2026-05-09T09:00:00"}
        ]
    """
}
