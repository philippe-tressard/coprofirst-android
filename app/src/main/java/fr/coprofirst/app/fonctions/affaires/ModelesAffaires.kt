// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.api.modeles.ReferentielsAffaires as ReferentielsServis
import fr.coprofirst.api.modeles.TicketRead
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.lecture.LieuServi
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Une affaire telle que les onglets Liste, Kanban et Archives l'affichent — lue dans
 * `TicketRead` du contrat, comme l'iOS (`Affaire`). Tout jugement (close, archivée,
 * urgente, colonne, gestes permis) est celui du SERVEUR : l'app ne le recalcule pas.
 */
data class Affaire(
    val id: Int,
    val numero: String,
    val titre: String,
    /** La description servie : du HTML riche — `TexteDuSite.apercu` la lit. */
    val apercu: String,
    val creeLe: Instant,
    val misAJourLe: Instant? = null,
    val statut: String,
    /** L'état en clair que le serveur SERT (`statut_libelle`), jamais vide. */
    val statutLibelle: String,
    val close: Boolean = false,
    val categorie: String,
    /** Le libellé SERVI de la catégorie (`categorie_libelle`). */
    val categorieLibelle: String,
    val urgente: Boolean = false,
    /** `null` : le serveur ne le sait pas — la portée « me concerne » la garde. */
    val meConcerne: Boolean? = null,
    /** Calculé par le serveur (`app/utils/archivage.py`) : jamais recalculé ici. */
    val archivee: Boolean = false,
    val suiviKanban: Boolean = false,
    /** Les natures que le serveur pose : actualité, calendrier, activité. */
    val natures: List<String> = emptyList(),
    /** Ses prestataires, dans l'ordre servi (`prestataires`) — le premier est celui du Kanban. */
    val prestataires: List<String> = emptyList(),
    /** Le lieu lisible SERVI (`perimetre_libelle`) ; `null` pour le périmètre par défaut, qu'il tait. */
    val lieu: String? = null,
    /** La colonne du Kanban SERVIE (`colonne_kanban`) ; `null` hors tableau. */
    val colonne: String? = null,
    /** Glisser au Kanban : ce que la route accepte de CE lecteur (`peut_deplacer_au_kanban`). */
    val peutDeplacer: Boolean = false,
    /** 📦 Archiver (`peut_archiver`), servi lui aussi. */
    val peutArchiver: Boolean = false,
    /** « Qui la lit », servie (`lecture`) ; `null` : aucune pastille. */
    val lecture: Lecture? = null,
) {
    val estActualite: Boolean get() = categorie == TablesAffaires.CATEGORIE_ACTUALITE

    /** La date qui range une affaire aux Archives : sa dernière activité. */
    val dateDActivite: Instant get() = misAJourLe ?: creeLe

    companion object {
        fun depuis(t: TicketRead) = Affaire(
            id = t.id, numero = t.numero, titre = t.titre, apercu = t.description,
            creeLe = t.creeLe.toInstant(), misAJourLe = t.misAJourLe?.toInstant(),
            statut = t.statut, statutLibelle = t.statutLibelle, close = t.close,
            categorie = t.categorie, categorieLibelle = t.categorieLibelle, urgente = t.urgente,
            meConcerne = t.meConcerne, archivee = t.archivee ?: false, suiviKanban = t.suiviKanban ?: false,
            natures = t.natures.orEmpty(), prestataires = t.prestataires.orEmpty().map { it.nom },
            lieu = LieuServi.lieu(t.perimetreLibelle), colonne = t.colonneKanban,
            peutDeplacer = t.peutDeplacerAuKanban ?: false, peutArchiver = t.peutArchiver ?: false,
            lecture = t.lecture?.let(Lecture::depuis),
        )
    }
}

/** Une option du filtre « Suivi » qui réunit plusieurs statuts (`GROUPES_SUIVI`). */
data class GroupeDeSuivi(val code: String, val libelle: String, val statuts: Set<String>)

/** Une colonne du Kanban, telle que le serveur la SERT : une étape du workflow. */
data class ColonneKanban(val id: String, val libelle: String, val libelleCourt: String)

/**
 * Ce que la résidence SERT des affaires (`GET /tickets/referentiels`) : les colonnes du
 * Kanban, les états et les catégories — lus, jamais recalculés ici (iOS : `ReferentielsAffaires`).
 */
data class ReferentielsAffaires(
    val colonnes: List<ColonneKanban>,
    val statuts: List<Statut>,
    val categories: List<Categorie>,
) {
    /** Un état : sa colonne (`null` hors tableau), s'il clôt, s'il s'offre dans une Suite. */
    data class Statut(val code: String, val libelle: String, val clos: Boolean, val colonne: String?, val proposable: Boolean)

    /** Une catégorie, avec ce que CE compte peut poser (`permise`). */
    data class Categorie(
        val code: String,
        val libelle: String,
        val emoji: String,
        val description: String = "",
        val carnet: Boolean = false,
        val reservee: Boolean = false,
        val permise: Boolean = false,
        val enTete: Boolean = false,
        val sectionsEssentielles: List<String> = emptyList(),
    )

    /** L'état qu'une colonne pose quand on y glisse une carte : le premier servi qui y mène. */
    fun statutPourColonne(colonne: String): Statut? = statuts.firstOrNull { it.colonne == colonne }

    fun categorie(code: String): Categorie? = categories.firstOrNull { it.code == code }

    /** Les catégories que CE compte peut poser, dans l'ordre servi, celles « en tête » d'abord. */
    val permises: List<Categorie> get() = categories.filter { it.permise }.let { p -> p.filter { it.enTete } + p.filterNot { it.enTete } }

    /**
     * Les catégories que « Modifier » propose : les permises, si celle de l'affaire en est —
     * une catégorie que ce compte ne pourrait pas reposer ne se propose pas.
     */
    fun permises(enModifiant: String): List<Categorie> = permises.let { p -> if (p.any { it.code == enModifiant }) p else emptyList() }

    /** Ce compte pose-t-il ce que le serveur réserve au conseil ? Lu des catégories servies. */
    val poseLesReservees: Boolean get() = categories.any { it.reservee && it.permise }

    /** Un état en clair : celui servi avec l'objet d'abord, puis celui des référentiels, puis le code. */
    fun libelle(code: String, servi: String? = null): String =
        servi?.takeIf { it.isNotBlank() } ?: statuts.firstOrNull { it.code == code }?.libelle ?: code

    companion object {
        /** Avant toute lecture : rien de servi, donc rien d'offert. */
        val VIDES = ReferentielsAffaires(emptyList(), emptyList(), emptyList())

        fun depuis(r: ReferentielsServis) = ReferentielsAffaires(
            colonnes = r.colonnesKanban.map { ColonneKanban(it.code, it.libelle, it.libelleCourt) },
            statuts = r.statuts.map { Statut(it.code, it.libelle, it.clos, it.colonneKanban, it.proposable) },
            categories = r.categories.map {
                Categorie(it.code, it.libelle, it.emoji, it.description, it.carnet, it.reservee, it.permise, it.enTete, it.sectionsEssentielles)
            },
        )
    }
}

/** Une option du filtre « Suivi » : un statut, ou un groupe de statuts. */
data class OptionDeSuivi(val code: String, val libelle: String, val statuts: Set<String>) {
    companion object {
        /**
         * Les options offertes : celles des statuts PRÉSENTS parmi les affaires actives, dans
         * l'ordre SERVI (`proposable`), un groupe réunissant ses statuts (`statutsPresents` du site).
         */
        fun presentes(affaires: List<Affaire>, servis: List<ReferentielsAffaires.Statut>): List<OptionDeSuivi> {
            val presents = affaires.mapTo(HashSet()) { it.statut }
            val options = mutableListOf<OptionDeSuivi>()
            for (statut in servis) {
                if (!statut.proposable || statut.code !in presents) continue
                val groupe = TablesAffaires.groupesDeSuivi.firstOrNull { statut.code in it.statuts }
                when {
                    groupe == null -> options += OptionDeSuivi(statut.code, statut.libelle, setOf(statut.code))
                    options.none { it.code == groupe.code } -> options += OptionDeSuivi(groupe.code, groupe.libelle, groupe.statuts)
                }
            }
            return options
        }
    }
}

/** Ce que l'onglet Liste retient : état, nature, portée — la recherche à part. */
data class FiltresAffaires(val suivi: String? = null, val nature: String? = null, val meConcerne: Boolean = false) {
    val actifs: Int get() = (if (nature == null) 0 else 1) + (if (meConcerne) 1 else 0)
}

/** Les archives d'une année, la plus récente activité d'abord. */
data class AnneeDArchives(val annee: Int, val affaires: List<Affaire>)

/** Les règles des trois onglets, comme celles du site (`filtrerAffaires`, `historyTickets`). */
object ReglesAffaires {
    /** Les Archives couvrent trois ans. */
    private const val ANNEES_D_ARCHIVES = 3L

    /**
     * Sans recherche : les affaires actives, dans l'ordre du serveur. Avec : celles qu'il a
     * trouvées, dans SON ordre, archives comprises si demandées. Puis état, nature, portée.
     */
    fun liste(
        affaires: List<Affaire>,
        filtres: FiltresAffaires,
        options: List<OptionDeSuivi>,
        trouvees: List<Int>?,
        avecArchives: Boolean,
    ): List<Affaire> {
        val parId = affaires.associateBy { it.id }
        val base = trouvees?.mapNotNull { parId[it] }?.filter { avecArchives || !it.archivee } ?: affaires.filterNot { it.archivee }
        val suivi = filtres.suivi?.let { code -> options.firstOrNull { it.code == code } }
        return base.filter { a ->
            (filtres.suivi == null || suivi?.statuts?.contains(a.statut) == true) &&
                (filtres.nature?.let { it in a.natures } ?: true) &&
                (!filtres.meConcerne || a.meConcerne != false)
        }
    }

    /** Closes depuis plus du délai (le serveur en décide), sur trois ans, par année. */
    fun archives(affaires: List<Affaire>, au: Instant = Instant.now()): List<AnneeDArchives> {
        val limite = ZonedDateTime.ofInstant(au, FormatsDuSite.PARIS).minusYears(ANNEES_D_ARCHIVES).toInstant()
        return affaires.filter { it.archivee && it.dateDActivite >= limite }
            .sortedByDescending { it.dateDActivite }
            .groupBy { ZonedDateTime.ofInstant(it.dateDActivite, FormatsDuSite.PARIS).year }
            .map { (annee, liste) -> AnneeDArchives(annee, liste) }
            .sortedByDescending { it.annee }
    }

    /** Le Kanban : les affaires suivies, non archivées, auxquelles le serveur sert une colonne. */
    fun auKanban(affaires: List<Affaire>): List<Affaire> = affaires.filter { it.suiviKanban && !it.archivee && it.colonne != null }

    /** Les colonnes où une carte peut aller : celles où un état servi mène, sauf la sienne. */
    fun destinations(affaire: Affaire, referentiels: ReferentielsAffaires?): List<ColonneKanban> =
        referentiels?.colonnes.orEmpty().filter { it.id != affaire.colonne && referentiels?.statutPourColonne(it.id) != null }
}
