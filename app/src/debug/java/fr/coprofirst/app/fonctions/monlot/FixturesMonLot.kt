// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

/**
 * Les réponses du serveur pour « Mes lots & accès », en JSON, telles que les routes les
 * renvoient — servies par un serveur local aux tests JVM et d'interface, et lues par le
 * client GÉNÉRÉ puis par le MÊME branchement que l'app (`lu()`).
 *
 * 🔴 Pourquoi du JSON et non des modèles construits à la main (leçon de l'iOS, bogue du
 * 05/11/2026) : une simulation fournissait un champ `type` que `/acces/mes-vigiks` ne sert
 * pas ; les tests passaient, l'appareil affichait une erreur. Ici, le décodeur généré
 * refuse toute réponse à laquelle manque un champ requis.
 *
 * Tout est inventé : aucun nom de personne, des numéros de la plage réservée à la fiction
 * (01 99 00 …), des adresses `exemple.fr`. Dans le build de débogage seulement.
 */
object FixturesMonLot {
    /** GET /auth/me : `statut`, `role`, `est_moderateur` disent ce que le compte EST. */
    fun moi(statut: String, role: String, moderateur: Boolean = false) = """
        {"id": 7, "nom": "Exemple", "prenom": "Compte", "email": "compte@exemple.fr", "statut": "$statut",
         "role": "$role", "roles": ["$role"], "est_moderateur": $moderateur, "actif": true,
         "onboarding_complete": true, "onboarding_etape": 0, "preferences_notifications": "{}",
         "cree_le": "2025-01-01T09:00:00", "nom_affiche": "Compte EXEMPLE", "nom_aide_affiche": ""}
    """.trimIndent()

    /** Le compte du conseil syndical, sans lot à louer : les quatre onglets, la vue standard. */
    val moiConseil = moi("mandataire", "conseil_syndical", moderateur = true)
    val moiLocataire = moi("locataire", "résident")
    val moiBailleur = moi("copropriétaire_bailleur", "propriétaire")

    /** GET /lots/mes-lots → `[LotRead]`. */
    const val MES_LOTS = """
        [
          {"id": 15, "numero": "15", "type": "appartement", "type_appartement": "T3", "etage": 2,
           "superficie": 64.5, "batiment_id": 1, "batiment_nom": "Bât. A", "est_logement_de_reference": true,
           "type_lien": "propriétaire", "nature": "logement", "numero_usage": "21",
           "libelle_type": "Appartement", "libelle_type_complet": "Appartement – T3", "libelle_nature": "Logement",
           "nom_du_lot": "Logement 15", "etage_libelle": "2ème",
           "perimetres": [
             {"code": "A", "libelle": "Bâtiment A", "relation": "situe_dans", "derive": true},
             {"code": "A-ESC", "libelle": "Escalier A", "relation": "desservi_par", "derive": true}
           ],
           "alertes": [
             {"code": "etage_declare", "gravite": "information",
              "message": "L'étage de ce lot vient de votre déclaration ; le fichier du syndic ne le précise pas.",
              "lots": ["15"]}
           ]},
          {"id": 417, "numero": "417", "type": "cave", "etage": -1, "superficie": 4, "batiment_nom": "Bât. A",
           "type_lien": "propriétaire", "libelle_type": "Cave", "libelle_type_complet": "Cave",
           "nom_du_lot": "Cave 417", "etage_libelle": "SS 1",
           "perimetres": [{"code": "A-SS", "libelle": "Sous-sol A", "relation": "situe_dans"}], "alertes": []},
          {"id": 450, "numero": "450", "type": "parking", "etage": -2, "batiment_nom": "Bât. B",
           "type_lien": "propriétaire", "numero_usage": "5 & 6", "libelle_type": "Parking",
           "libelle_type_complet": "Parking", "nom_du_lot": "Place 450", "etage_libelle": "SS 2",
           "perimetres": [{"code": "B-P2", "libelle": "Parking niveau -2", "relation": "situe_dans"}], "alertes": []}
        ]
    """

    /** GET /acces/mes-vigiks → `[AccesPorteur]` (sans `type` : c'est la route qui le dit). */
    const val MES_VIGIKS = """
        [
          {"id": 11, "code": "A4F21C07", "statut": "actif", "chez_locataire": false, "lot_id": 15,
           "perimetre_cible": [], "perimetre_libelle": "", "lot_libelle": "Logement 15", "cree_le": "2025-01-10T09:00:00"},
          {"id": 12, "code": "A4F21C08", "statut": "actif", "chez_locataire": false, "lot_id": 15,
           "perimetre_cible": [], "perimetre_libelle": "", "lot_libelle": "Logement 15", "cree_le": "2025-01-10T09:00:00"},
          {"id": 13, "code": "9B0E33D1", "statut": "perdu", "chez_locataire": true, "bail_id": 3, "lot_id": 15,
           "perimetre_cible": [], "perimetre_libelle": "", "lot_libelle": "Logement 15", "cree_le": "2024-06-02T14:30:00"}
        ]
    """

    /** GET /acces/mes-telecommandes → `[AccesPorteur]`. */
    const val MES_TELECOMMANDES = """
        [
          {"id": 21, "code": "TC-2048", "statut": "actif", "chez_locataire": false, "lot_id": 450,
           "perimetre_cible": [], "perimetre_libelle": "", "lot_libelle": "Place 450", "cree_le": "2025-03-01T10:00:00"}
        ]
    """

    /** GET /bailleur/mes-acces-recus → `[AccesOut]`. */
    const val ACCES_RECUS = """
        [
          {"id": 31, "code": "A4F21C09", "type": "vigik", "lot_id": 15, "statut": "actif", "chez_locataire": true,
           "bail_id": 3, "cree_le": "2025-09-01T10:00:00"}
        ]
    """

    /** GET /bailleur/tous-les-baux ou /bailleur/mes-baux → `[BailOut]`. */
    const val BAUX = """
        [
          {"id": 3, "lot_id": 15, "bailleur_id": 70, "locataire_id": 81, "locataire_nom": "du lot 15",
           "locataire_prenom": "Locataire", "locataire_email": "locataire.15@exemple.fr",
           "locataire_nom_affiche": "Locataire DU LOT 15",
           "locataire_telephone": "01 99 00 00 15", "date_entree": "2025-09-01", "date_sortie_prevue": null,
           "date_sortie_reelle": null, "statut": "actif",
           "notes": "<p>Préavis d'<strong>un mois</strong> (zone tendue).</p>",
           "cree_le": "2025-08-20T10:00:00", "mis_a_jour_le": "2025-09-01T10:00:00",
           "objets": [
             {"id": 1, "bail_id": 3, "type": "cle", "libelle": "Clé de la porte palière", "quantite": 2,
              "reference": null, "statut": "en_possession", "remis_le": "2025-09-01", "rendu_le": null,
              "notes": null, "cree_le": "2025-09-01T10:00:00"}
           ]},
          {"id": 4, "lot_id": 32, "bailleur_id": 71, "locataire_id": null, "locataire_nom": "du lot 32",
           "locataire_prenom": "Locataire", "locataire_email": null, "locataire_telephone": null,
           "locataire_nom_affiche": "Locataire DU LOT 32",
           "date_entree": "2023-02-15", "date_sortie_prevue": "2026-11-30", "date_sortie_reelle": null,
           "statut": "en_cours_sortie", "notes": null, "cree_le": "2023-02-01T10:00:00",
           "mis_a_jour_le": "2026-10-01T10:00:00", "objets": []},
          {"id": 2, "lot_id": 15, "bailleur_id": 70, "locataire_id": 80, "locataire_nom": "du lot 15",
           "locataire_prenom": "Ancien locataire", "locataire_email": null, "locataire_telephone": null,
           "locataire_nom_affiche": "Ancien locataire DU LOT 15",
           "date_entree": "2021-03-01", "date_sortie_prevue": "2025-06-30", "date_sortie_reelle": "2025-07-02",
           "statut": "termine", "notes": null, "cree_le": "2021-02-15T10:00:00",
           "mis_a_jour_le": "2025-07-02T10:00:00",
           "objets": [
             {"id": 3, "bail_id": 2, "type": "telecommande", "libelle": "Télécommande du parking", "quantite": 1,
              "reference": null, "statut": "rendu", "remis_le": "2021-03-01", "rendu_le": "2025-07-02",
              "notes": null, "cree_le": "2021-03-01T10:00:00"}
           ]}
        ]
    """

    /** GET /bailleur/mon-bail (compte locataire) → `BailLocataireOut`. */
    const val MON_BAIL = """
        {"id": 3, "lot_id": 15, "statut": "actif", "lot_batiment_nom": "Bât. A", "lot_numero": "15",
         "lot_type": "appartement", "lot_type_appartement": "T3", "lot_etage": 2, "lot_etage_libelle": "2ème",
         "lot_superficie": 64.5, "date_entree": "2025-09-01", "date_sortie_prevue": null,
         "bailleur_prenom": "Sacha", "bailleur_nom": "Exemple", "bailleur_nom_affiche": "Sacha EXEMPLE",
         "bailleur_email": "bailleur@exemple.fr", "bailleur_telephone": "01 99 00 00 70", "acces": []}
    """

    /** POST /acces/commandes → `CommandeAccesRead`. */
    const val COMMANDE = """{"id": 5, "user_id": 7, "lot_id": 15, "type": "vigik", "quantite": 1, "statut": "en_attente", "cree_le": "2026-11-15T10:00:00"}"""

    /** POST /acces/declarer-badge → `BadgeDeclare`. */
    fun declare(code: String, type: String, importResolu: Boolean = false) =
        """{"type": "$type", "id": 99, "code": "$code", "import_resolu": $importResolu}"""

    /** PATCH /acces/…/perdu → `AccesSignalePerdu`. */
    const val PERDU = """{"statut": "perdu"}"""
}
