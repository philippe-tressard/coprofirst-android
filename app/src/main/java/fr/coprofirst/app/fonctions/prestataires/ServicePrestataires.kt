// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import fr.coprofirst.api.routes.DocumentsApi
import fr.coprofirst.api.routes.EquipementsApi
import fr.coprofirst.api.routes.PrestatairesApi
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce que « Prestataires » demande à la résidence (#19), en LECTURE, comme l'iOS
 * (`ServicePrestataires`). Aucune route n'est écrite ici : chacune est une opération du
 * client GÉNÉRÉ. Les gestes du conseil (créer, corriger, archiver, noter, relever un
 * compteur) restent sur le site, comme l'iOS (#207 : les avis se LISENT) : l'écran le dit.
 */
interface ServicePrestataires {
    /** GET /prestataires, ou /prestataires/archives. */
    suspend fun prestataires(archives: Boolean): List<Prestataire>

    /** GET /prestataires/contrats, ou /prestataires/contrats/archives. */
    suspend fun contrats(archives: Boolean): List<Contrat>

    /**
     * GET /prestataires/{id}/interventions : le récapitulatif de ses avis, par exercice et
     * par intervention — regroupé et compté par l'API.
     */
    suspend fun interventions(prestataire: Int): List<ExerciceAvis>

    /** GET /equipements : les équipements (libellés et ordre). */
    suspend fun equipements(): List<Equipement>

    /** GET /documents?contrat_id= : les documents d'un contrat. */
    suspend fun documents(contrat: Int): List<DocumentContrat>

    /** GET /prestataires/compteurs-config : les catégories de compteurs. */
    suspend fun compteurs(): List<Compteur>

    /** GET /prestataires/releves?type_compteur= : les relevés d'un compteur. */
    suspend fun releves(typeCompteur: String): List<Releve>
}

/** « Prestataires » par le client généré, à travers LA porte de la résidence. */
class ServicePrestatairesApi(private val session: SessionOuverte) : ServicePrestataires {
    override suspend fun prestataires(archives: Boolean) = session.appeler { b, c ->
        val api = PrestatairesApi(b, c)
        (if (archives) api.getPrestatairesArchives() else api.getPrestataires()).map { it.lu() }
    }

    override suspend fun contrats(archives: Boolean) = session.appeler { b, c ->
        val api = PrestatairesApi(b, c)
        (if (archives) api.getPrestatairesContratsArchives() else api.getPrestatairesContrats()).map { it.lu() }
    }

    override suspend fun interventions(prestataire: Int) =
        session.appeler { b, c -> PrestatairesApi(b, c).getPrestatairesPIdInterventions(prestataire).map { it.lu() } }

    override suspend fun equipements() = session.appeler { b, c -> EquipementsApi(b, c).getEquipements().map { it.lu() } }

    override suspend fun documents(contrat: Int) =
        session.appeler { b, c -> DocumentsApi(b, c).getDocuments(contratId = contrat).map { it.lu() } }

    override suspend fun compteurs() =
        session.appeler { b, c -> PrestatairesApi(b, c).getPrestatairesCompteursConfig().map { it.lu() } }

    override suspend fun releves(typeCompteur: String) =
        session.appeler { b, c -> PrestatairesApi(b, c).getPrestatairesReleves(typeCompteur = typeCompteur).map { it.lu() } }
}
