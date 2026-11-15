// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.api.modeles.AffaireOuverteRead
import fr.coprofirst.api.modeles.BarreRead
import fr.coprofirst.api.modeles.ContratARenouveler
import fr.coprofirst.api.modeles.DiagnosticAEcheance
import fr.coprofirst.api.modeles.ReleveRenouvellements
import fr.coprofirst.api.modeles.SuiviAffairesRead
import fr.coprofirst.api.routes.ReportingApi
import fr.coprofirst.api.routes.SuiviAffairesApi
import fr.coprofirst.app.commun.LibelleTeinte
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce que l'onglet « Reporting » demande, comme l'iOS (`ServiceReportingCS`) : le Suivi des
 * affaires, CALCULÉ au serveur (`GET /suivi-affaires?mode=`) — la règle d'entrée,
 * l'ancienneté et ses seuils, les répartitions y vivent ; l'écran ne recalcule rien —, et
 * le relevé des Renouvellements.
 */
interface ServiceReporting {
    suspend fun suivi(releve: ReleveSuivi): SuiviAffaires

    /** GET /reporting/renouvellements : les échéances des contrats et des diagnostics. */
    suspend fun renouvellements(): Renouvellements
}

class ServiceReportingApi(private val session: SessionOuverte) : ServiceReporting {
    override suspend fun suivi(releve: ReleveSuivi) =
        session.appeler { b, c -> SuiviAffairesApi(b, c).getSuiviAffaires(releve.code).lu() }

    override suspend fun renouvellements() =
        session.appeler { b, c -> ReportingApi(b, c).getReportingRenouvellements().lu() }
}

/** Le relevé que lit un mode du suivi : le Suivi affaires et la relance lisent le même. */
enum class ReleveSuivi(val code: String) {
    Affaires("affaires"),
    Entretien("entretien"),
    ;

    companion object {
        /** Le relevé d'un mode du site (`TablesEspaceCS.modesSuivi`). */
        fun duMode(mode: String) = if (mode == "entretien") Entretien else Affaires
    }
}

data class SuiviAffaires(
    val total: Int,
    /** Celles qui dépassent `delaiJours` (90 jours sur le site). */
    val anciennes: Int,
    val delaiJours: Int,
    val fusionnees: Int,
    val parCategorie: List<Repartition>,
    val parBatiment: List<Repartition>,
    /** La plus ancienne en tête, dans l'ordre servi. */
    val affaires: List<AffaireSuivie>,
)

/** Une barre d'une répartition croisée (`BarreRead`) : son nombre, découpé en tranches. */
data class Repartition(val cle: String, val libelle: String, val nombre: Int, val tranches: List<Tranche>) {
    /** `teinte` : celle que le serveur CHOISIT (`s1`…`s8`, `p1`…`p4`, `autre`, `plusieurs`). */
    data class Tranche(val cle: String, val libelle: String, val nombre: Int, val teinte: String?)
}

/** Une affaire du suivi (`AffaireOuverteRead`), dans ce que l'app en montre. */
data class AffaireSuivie(
    val id: Int,
    val numero: String,
    val titre: String,
    val statut: String,
    /** « Chez le syndic » : le libellé que le serveur SERT. */
    val libelleStatut: String,
    val libelleCategorie: String,
    /** Le jour ISO servi. */
    val ouverteLe: String,
    val ageJours: Int,
    /** `ancienne`, `moyenne` ou `recente` — la tranche vient du serveur, qui garde les seuils. */
    val anciennete: String,
    /** Le résumé de l'assistant, sinon le descriptif — du TEXTE, jamais du HTML. */
    val apercu: String,
    val apercuIA: Boolean = false,
    val aEvolue: Boolean = false,
    val motifEvolution: String? = null,
    /** L'état de la visite, en mode entretien. */
    val etatVisite: String? = null,
) {
    val teinteAge: String get() = TablesEspaceCS.teintesAge[anciennete] ?: "badge-gray"
    val teinteStatut: String get() = TablesEspaceCS.teintesStatutAffaire[statut] ?: "badge-gray"
    val evolution: String
        get() = motifEvolution?.let { TablesEspaceCS.motifsEvolution[it] } ?: TablesEspaceCS.MOTIF_EVOLUTION_PAR_DEFAUT
    val visite: LibelleTeinte? get() = etatVisite?.let { TablesEspaceCS.etatsVisite[it] ?: LibelleTeinte.brut(it) }
}

/**
 * Reporting › Renouvellements : le relevé SERVI — l'année de référence, le préavis, les
 * compteurs, l'ordre des contrats et des diagnostics, ce qui tombe dans l'année. L'app
 * n'en recalcule rien.
 */
data class Renouvellements(
    val annee: Int,
    val preavisMois: Int,
    val contratsEnPreavis: Int,
    val echeancesContratsAnnee: Int,
    val auditsAPlanifier: Int,
    val contrats: List<ContratAEcheance>,
    val diagnostics: List<DiagnosticAEcheanceLu>,
) {
    val contratsDeLAnnee: List<ContratAEcheance> get() = contrats.filter { it.echeanceDansLAnnee }

    /** Les échéances des années suivantes (`futur`, hors de l'année). */
    val contratsFuturs: List<ContratAEcheance>
        get() = contrats.filter { !it.echeanceDansLAnnee && it.dateFin != null && it.urgence == "futur" }

    val contratsSansDate: List<ContratAEcheance> get() = contrats.filter { it.urgence == null }

    /** Les diagnostics à échéance, rangés par l'année de leur échéance servie, dans l'ordre servi ; les permanents à part. */
    val diagnosticsParAnnee: List<Pair<String, List<DiagnosticAEcheanceLu>>>
        get() = diagnostics.filterNot { it.permanent }.groupBy { it.prochaineEcheance.orEmpty().take(4) }.toList()

    val diagnosticsPermanents: List<DiagnosticAEcheanceLu> get() = diagnostics.filter { it.permanent }
}

data class ContratAEcheance(
    val id: Int,
    val libelle: String,
    val prestataireNom: String,
    val numeroContrat: String? = null,
    val noteMoyenne: Double? = null,
    val nbNotations: Int = 0,
    /** Jours ISO (`AAAA-MM-JJ`), tels que servis. */
    val dateDebut: String,
    val dateFin: String? = null,
    val datePreavis: String? = null,
    val echeanceDansLAnnee: Boolean = false,
    /** `preavis`, `annee`, `futur`… tel que le serveur la classe ; `null` : dates manquantes. */
    val urgence: String? = null,
    val reconduit: Boolean = false,
)

data class DiagnosticAEcheanceLu(
    val id: Int,
    val nom: String,
    val code: String,
    val frequence: String? = null,
    val dernierRapportLe: String? = null,
    val prochaineEcheance: String? = null,
    val permanent: Boolean = false,
    /** `depasse`, `annee` ou `futur`, tel que servi ; `null` sans échéance connue. */
    val urgence: String? = null,
)

internal fun SuiviAffairesRead.lu() = SuiviAffaires(
    total, anciennes, delaiJours, fusionnees ?: 0, parCategorie.map { it.lu() }, parBatiment.map { it.lu() },
    affaires.map { it.lu() },
)

internal fun BarreRead.lu() = Repartition(cle, libelle, nombre, tranches.map { Repartition.Tranche(it.cle, it.libelle, it.nombre, it.teinte) })

internal fun AffaireOuverteRead.lu() = AffaireSuivie(
    id, numero, titre, statut, libelleStatut, libelleCategorie, ouverteLe.toString(), ageJours, anciennete, apercu,
    apercuIa, aEvolue, motifEvolution, etatVisite,
)

internal fun ReleveRenouvellements.lu() = Renouvellements(
    annee, preavisMois, compteurs.contratsEnPreavis, compteurs.echeancesContratsAnnee, compteurs.auditsAPlanifier,
    contrats.map { it.lu() }, diagnostics.map { it.lu() },
)

internal fun ContratARenouveler.lu() = ContratAEcheance(
    id, libelle, prestataireNom, numeroContrat, noteMoyenne?.toDouble(), nbNotations ?: 0, dateDebut.toString(),
    dateFin?.toString(), datePreavis?.toString(), echeanceDansLAnnee, urgenceEcheance, reconduit == true,
)

internal fun DiagnosticAEcheance.lu() = DiagnosticAEcheanceLu(
    id, nom, code, frequence, dernierRapportLe?.toString(), prochaineEcheance?.toString(), permanent, urgenceEcheance?.value,
)
