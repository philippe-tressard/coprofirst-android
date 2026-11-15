// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.api.modeles.QuestionCreate
import fr.coprofirst.api.routes.CarnetEntretienApi
import fr.coprofirst.api.routes.CopropritApi
import fr.coprofirst.api.routes.DiagnosticsApi
import fr.coprofirst.api.routes.DocumentsApi
import fr.coprofirst.api.routes.EquipementsApi
import fr.coprofirst.api.routes.PerimetresApi
import fr.coprofirst.api.routes.ReglementApi
import fr.coprofirst.api.routes.RglesRsidenceApi
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.async
import okhttp3.Call
import java.io.File
import kotlinx.coroutines.coroutineScope

/**
 * Ce que « Ma résidence » demande à la résidence (#17), comme l'iOS (`ServiceResidence`).
 * Aucune route n'est écrite ici : chacune est une opération du client GÉNÉRÉ.
 */
interface ServiceResidence {
    /** La Fiche, en une vague : copropriété, bâtiments, règles, trois rubriques de documents, diagnostics, annexes. */
    suspend fun fiche(): ContenuFiche

    /** GET /carnet-entretien, filtré par périmètre (`null` : toute la résidence), et GET /equipements pour nommer les groupes. */
    suspend fun carnet(perimetre: String?): ContenuCarnet

    /** GET /perimetres : les périmètres ACTIFS, le filtre du carnet. */
    suspend fun perimetres(): List<Perimetre>

    /** GET /reglement/acces : `false` masque l'onglet Règlement, comme sur le site. */
    suspend fun accesReglement(): Boolean

    /** GET /reglement et GET /reglement/questions. */
    suspend fun reglement(): Pair<EtatReglement, List<QuestionReglement>>

    /** POST /reglement/questions : l'assistant lit le règlement et répond. */
    suspend fun poser(question: String): QuestionReglement
}

/** « Ma résidence » par le client généré, à travers LA porte de la résidence. */
class ServiceResidenceApi(private val session: SessionOuverte) : ServiceResidence {
    override suspend fun fiche(): ContenuFiche = coroutineScope {
        fun documents(categorie: String? = null, annexes: String? = null) = async {
            session.appeler { b, c -> DocumentsApi(b, c).getDocuments(categorie = categorie, annexesDe = annexes).map { it.lu() } }
        }
        val copropriete = async { session.appeler { b, c -> CopropritApi(b, c).getCopropriete().lue() } }
        val batiments = async { tolere { session.appeler { b, c -> CopropritApi(b, c).getCoproprieteBatiments().map { it.lu() } } } }
        val regles = async { tolere { session.appeler { b, c -> RglesRsidenceApi(b, c).getReglesResidence().map { it.lue() } } } }
        val diagnostics = async { tolere { session.appeler { b, c -> DiagnosticsApi(b, c).getDiagnosticsTypes().map { it.lu() } } } }
        val plans = documents(CATEGORIE_PLAN)
        val reglements = documents(CATEGORIE_REGLEMENT)
        val comptesRendus = documents(CATEGORIE_PV_AG)
        val annexes = ANNEXES.map { de -> async { tolere { documents(annexes = de).await() } } }
        ContenuFiche(
            fiche = copropriete.await(),
            plans = plans.await(),
            reglements = reglements.await(),
            comptesRendus = comptesRendus.await(),
            // Une rubrique qui échoue ne vide pas les autres (#522 du site) : elle est vide.
            batiments = batiments.await(),
            regles = regles.await(),
            diagnostics = diagnostics.await(),
            annexes = annexes.flatMap { it.await() },
        )
    }

    override suspend fun carnet(perimetre: String?): ContenuCarnet = coroutineScope {
        val equipements = async {
            tolere { session.appeler { b, c -> EquipementsApi(b, c).getEquipements().map { it.code to it.libelle } } }
        }
        val carnet = session.appeler { b, c -> CarnetEntretienApi(b, c).getCarnetEntretien(perimetre = perimetre) }
        // Un code en double garde son premier libellé, comme l'iOS.
        val libelles = equipements.await().asReversed().toMap()
        ContenuCarnet(carnet.entrees.map { it.lue() }, libelles)
    }

    override suspend fun perimetres() = session.appeler { b, c -> PerimetresApi(b, c).getPerimetres().actifs() }

    override suspend fun accesReglement() = session.appeler { b, c -> ReglementApi(b, c).getReglementAcces().acces }

    override suspend fun reglement() = coroutineScope {
        val questions = async { session.appeler { b, c -> ReglementApi(b, c).getReglementQuestions().map { it.lue() } } }
        val etat = session.appeler { b, c -> ReglementApi(b, c).getReglement() }
        EtatReglement(etat.disponible, etat.texteDisponible) to questions.await()
    }

    override suspend fun poser(question: String) =
        session.appeler { b, c -> ReglementApi(b, c).postReglementQuestions(QuestionCreate(question)).lue() }

    /** Une rubrique secondaire : son échec la laisse vide, sans masquer le reste. */
    private suspend fun <T> tolere(lire: suspend () -> List<T>): List<T> = try {
        lire()
    } catch (_: ErreurService) {
        emptyList()
    }

    companion object {
        /** Les catégories de documents des trois rubriques de la Fiche (`categorie` du site). */
        const val CATEGORIE_PLAN = "plan_residence"
        const val CATEGORIE_REGLEMENT = "reglement_copropriete"
        const val CATEGORIE_PV_AG = "pv_ag"

        /** Les fiches qui portent des annexes (`annexes_de`). */
        val ANNEXES = listOf("document", "regle", "diagnostic")

        /** GET /diagnostics/rapports/{id}/télécharger : le rapport, pour `Ouverture.fichier`. */
        fun rapport(id: Int): (String, Call.Factory) -> File =
            { b, c -> DiagnosticsApi(b, c).getDiagnosticsRapportsRapportIdTlcharger(id) }
    }
}
