// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

/**
 * Les réponses du serveur pour « Prestataires », en JSON, telles que les routes les
 * renvoient (contrat 2.0.0, le plancher v3.101.0) — servies par un serveur local aux tests JVM et
 * d'interface, et lues par le client GÉNÉRÉ puis par le MÊME branchement que l'app (`lu()`).
 * Celles de l'iOS (`FixturesPrestataires`), à la forme du plancher.
 *
 * Tout est inventé : des entreprises, aucun nom de personne, des numéros de la plage
 * réservée à la fiction (01 99 00 …), des adresses `.test`. Dans le build de débogage seulement.
 */
object FixturesPrestataires {
    /** GET /prestataires → `[PrestataireRead]`. */
    const val PRESTATAIRES = """
        [
          {"id": 1, "nom": "Ascenseurs du Parc (fictif)", "specialite": "ascenseur",
           "type_prestataire": "maintenance_depannage", "actif": true, "note_moyenne": 4.33, "nb_notations": 3,
           "telephone": "01 99 00 00 11, 01 99 00 00 12", "telephones": ["01 99 00 00 11", "01 99 00 00 12"],
           "email": "contact@ascenseurs.exemple.test", "adresse": "12 rue de l'Exemple, 75000 Paris",
           "description": "<p>Astreinte <strong>24 h/24</strong> pour les personnes bloquées en cabine.</p>",
           "contacts": [{"fonction": "Astreinte", "telephone": "01 99 00 00 13", "nom_affiche": ""}]},
          {"id": 2, "nom": "Contrôles Sécurité Exemple", "specialite": "incendie",
           "type_prestataire": "reglementaire", "actif": true,
           "telephone": "01 99 00 00 21", "telephones": ["01 99 00 00 21"],
           "email": "planning@controles.exemple.test"},
          {"id": 3, "nom": "Eaux de la Ville (fictif)", "specialite": "eau", "type_prestataire": "gestion",
           "actif": true, "telephone": "01 99 00 00 31", "telephones": ["01 99 00 00 31"]},
          {"id": 4, "nom": "Peinture & Façades Exemple", "specialite": "facade", "type_prestataire": "travaux",
           "actif": true, "telephone": null, "telephones": []},
          {"id": 5, "nom": "Protection Incendie Exemple", "specialite": "incendie",
           "type_prestataire": "reglementaire", "actif": true, "telephone": "01 99 00 00 51",
           "telephones": ["01 99 00 00 51"]}
        ]
    """

    /** GET /prestataires/archives → `[PrestataireRead]`. */
    const val ARCHIVES = """
        [
          {"id": 9, "nom": "Ancien jardinier (fictif)", "specialite": "espaces_verts",
           "type_prestataire": "maintenance_depannage", "actif": false, "archivee": true, "telephones": []}
        ]
    """

    /**
     * GET /prestataires/contrats → `[ContratRead]` : le contrat des extincteurs est résilié
     * au 30 novembre et remplacé par celui d'un autre prestataire dès le 1er décembre — les
     * deux cartes le disent (celles de l'iOS).
     */
    const val CONTRATS = """
        [
          {"id": 1, "copropriete_id": 1, "prestataire_id": 1, "type_equipement": "ascenseur",
           "libelle": "Maintenance des deux ascenseurs", "numero_contrat": "ASC-2024-118",
           "date_debut": "2024-01-01", "duree_initiale_valeur": 3, "duree_initiale_unite": "ans",
           "frequence_type": "mois", "frequence_valeur": 1, "frequence_libelle": "↺ Mensuel",
           "prochaine_visite": "2026-11-18", "actif": true,
           "notes": "<p>Visite mensuelle ; remplacement des câbles prévu en 2027.</p>"},
          {"id": 2, "copropriete_id": 1, "prestataire_id": 2, "type_equipement": "incendie",
           "libelle": "Vérification des extincteurs", "date_debut": "2023-06-01", "frequence_type": "fois_par_an",
           "frequence_valeur": 1, "frequence_libelle": "↺ 1×/an", "prochaine_visite": "2026-09-30", "actif": true,
           "resilie_le": "2026-11-30", "remplace_par_id": 4, "resilie": false,
           "remplace_par": {"id": 4, "libelle": "Vérification des extincteurs", "prestataire_id": 5,
                            "prestataire_nom": "Protection Incendie Exemple", "date_debut": "2026-12-01"},
           "remplace": []},
          {"id": 3, "copropriete_id": 1, "prestataire_id": 3, "type_equipement": "autre",
           "libelle": "Abonnement eau froide", "date_debut": "2020-01-01", "actif": true},
          {"id": 4, "copropriete_id": 1, "prestataire_id": 5, "type_equipement": "incendie",
           "libelle": "Vérification des extincteurs", "date_debut": "2026-12-01", "frequence_type": "fois_par_an",
           "frequence_valeur": 1, "frequence_libelle": "↺ 1×/an", "prochaine_visite": "2026-12-15", "actif": true,
           "remplace": [{"id": 2, "libelle": "Vérification des extincteurs", "prestataire_id": 2,
                         "prestataire_nom": "Contrôles Sécurité Exemple", "date_debut": "2023-06-01",
                         "resilie_le": "2026-11-30"}]}
        ]
    """

    /** GET /prestataires/contrats/archives → `[ContratRead]`. */
    const val CONTRATS_ARCHIVES = """
        [
          {"id": 8, "copropriete_id": 1, "prestataire_id": 9, "type_equipement": "espaces_verts",
           "libelle": "Entretien des espaces verts", "date_debut": "2019-03-01", "actif": false, "archivee": true}
        ]
    """

    /**
     * GET /prestataires/1/interventions → `[ExerciceInterventions]` (ceux de l'iOS) : deux
     * exercices — trois avis, une intervention non notée, un avis sans affaire. L'auteur est
     * « Conseil syndical », aucun nom de personne.
     */
    const val INTERVENTIONS_ASCENSEURS = """
        [
          {"annee": 2026, "libelle": "2026", "nb_interventions": 2, "nb_notees": 1,
           "compte": {"excellent": 2},
           "interventions": [
             {"ticket_id": 45, "numero": "A-0045", "titre": "Contrôle annuel de l'ascenseur",
              "date": "2026-11-10T13:00:00",
              "avis": [{"id": 11, "prestataire_id": 1, "note": 5, "etat": "excellent",
                        "commentaire": "Points forts : délai respecté, travail soigné.",
                        "auteur_nom": "Conseil syndical", "cree_le": "2026-11-12T09:00:00",
                        "ticket_id": 45, "ticket_titre": "Contrôle annuel de l'ascenseur",
                        "exercice_annee": 2026, "exercice_libelle": "2026", "peut_modifier": false}]},
             {"ticket_id": 38, "numero": "A-0038", "titre": "Cabine bloquée au 3e étage",
              "date": "2026-03-02T08:30:00", "avis": []}
           ],
           "avis_generaux": [{"id": 12, "prestataire_id": 1, "note": 5, "etat": "excellent",
                              "commentaire": "Astreinte joignable de nuit.",
                              "auteur_nom": "Conseil syndical", "cree_le": "2026-06-20T18:00:00",
                              "exercice_annee": 2026, "exercice_libelle": "2026", "peut_modifier": true}]},
          {"annee": 2025, "libelle": "2025", "nb_interventions": 1, "nb_notees": 1,
           "compte": {"neutre": 1, "mauvais": 0},
           "interventions": [
             {"ticket_id": 21, "numero": "A-0021", "titre": "Porte palière qui grince",
              "date": "2025-09-14T10:00:00",
              "avis": [{"id": 9, "prestataire_id": 1, "note": 3, "etat": "neutre",
                        "commentaire": "Points faibles : retard.",
                        "auteur_nom": "Conseil syndical", "cree_le": "2025-09-30T11:00:00",
                        "exercice_annee": 2025, "exercice_libelle": "2025", "peut_modifier": false}]}
           ],
           "avis_generaux": []}
        ]
    """

    /** GET /equipements → `[EquipementRead]` : l'ordre et le libellé servis. */
    const val EQUIPEMENTS = """
        [
          {"id": 1, "code": "ascenseur", "libelle": "Ascenseurs", "icone": "arrow-up-down", "ordre": 1,
           "actif": true, "perimetres_libelle": ""},
          {"id": 2, "code": "incendie", "libelle": "Sécurité incendie", "icone": "flame", "ordre": 2,
           "actif": true, "perimetres_libelle": ""},
          {"id": 3, "code": "eau", "libelle": "Eau", "icone": "droplet", "ordre": 3, "actif": true,
           "perimetres_libelle": ""}
        ]
    """

    /** GET /documents?contrat_id=1 → `[DocumentRead]`. */
    const val DOCUMENTS = """
        [
          {"id": 41, "titre": "Contrat signé", "fichier_nom": "contrat-ascenseurs.pdf", "mime_type": "application/pdf",
           "perimetre": "residence", "publie_le": "2024-01-02T09:00:00", "perimetre_libelle": "Résidence",
           "taille_octets": 245000, "contrat_id": 1}
        ]
    """

    /** GET /prestataires/compteurs-config → `[CompteurConfigRead]`, dans le désordre (l'écran les range). */
    const val COMPTEURS = """
        [
          {"id": 2, "type_compteur": "eau_chaude", "label": "Eau chaude", "actif": true, "ordre": 2},
          {"id": 1, "type_compteur": "eau_froide", "label": "Eau froide", "actif": true, "ordre": 1, "prestataire_id": 3}
        ]
    """

    /** GET /prestataires/releves?type_compteur=eau_froide → `[ReleveRead]`. */
    const val RELEVES_EAU_FROIDE = """
        [
          {"id": 11, "type_compteur": "eau_froide", "date_releve": "2026-10-01", "cree_le": "2026-10-01T10:00:00",
           "index": 47047, "note": "Relevé du syndic"},
          {"id": 10, "type_compteur": "eau_froide", "date_releve": "2025-10-01", "cree_le": "2025-10-01T10:00:00",
           "index": 41020}
        ]
    """
}

/**
 * Le côté SERVEUR de « Prestataires » pour les tests (JVM et d'interface) : chaque route
 * sert sa réponse de `FixturesPrestataires`. Les tests branchent `repondre` sur leur
 * serveur local ; l'app, elle, passe par son vrai client.
 */
class PrestatairesSimules {
    /** Les requêtes reçues : « MÉTHODE /chemin?requête ». */
    val recues = mutableListOf<String>()

    @Synchronized
    fun repondre(methode: String, chemin: String): Pair<Int, String> {
        recues += "$methode $chemin"
        return when ("$methode ${chemin.removePrefix("/api")}") {
            "GET /prestataires" -> 200 to FixturesPrestataires.PRESTATAIRES
            "GET /prestataires/archives" -> 200 to FixturesPrestataires.ARCHIVES
            "GET /prestataires/contrats" -> 200 to FixturesPrestataires.CONTRATS
            "GET /prestataires/contrats/archives" -> 200 to FixturesPrestataires.CONTRATS_ARCHIVES
            "GET /equipements" -> 200 to FixturesPrestataires.EQUIPEMENTS
            "GET /prestataires/1/interventions" -> 200 to FixturesPrestataires.INTERVENTIONS_ASCENSEURS
            "GET /documents?contrat_id=1" -> 200 to FixturesPrestataires.DOCUMENTS
            "GET /prestataires/compteurs-config" -> 200 to FixturesPrestataires.COMPTEURS
            "GET /prestataires/releves?type_compteur=eau_froide" -> 200 to FixturesPrestataires.RELEVES_EAU_FROIDE
            else -> if (chemin.contains("/documents?") || chemin.contains("/releves?") || chemin.endsWith("/interventions")) 200 to "[]" else 404 to """{"detail": "Not Found"}"""
        }
    }
}
