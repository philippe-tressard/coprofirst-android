// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.annotation.StringRes
import fr.coprofirst.api.modeles.FiltreGestionnaire as FiltreLu
import fr.coprofirst.api.modeles.IndicateursTelemetrie
import fr.coprofirst.api.modeles.TableauTelemetrie as TableauLu
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.Separateur
import java.time.Instant

/** Les quatre vues du site : le jour, 30 jours, 12 mois, 10 ans par année (`scope` de la route). */
enum class PorteeTelemetrie(val code: String, @StringRes val libelle: Int) {
    Jour("jour", R.string.admin_telemetrie_vue_jour),
    Mois("mois", R.string.admin_telemetrie_vue_mois),
    Annee("annee", R.string.admin_telemetrie_vue_annee),
    Total("total", R.string.admin_telemetrie_vue_total),
}

/**
 * Le tableau de bord, tel que le serveur le sert (`utils/telemetrie_tableau`) — ce que
 * l'app en montre, comme l'iOS : les nombres clés, le graphe, les pages et les
 * utilisateurs actifs, et la répartition par accès — site ou application (`canaux`,
 * coprofirst#2726, #127). Le reste (erreurs, « Qui vient », retour des comptes, gestes,
 * arrivées, durées) se lit sur le site.
 */
data class TableauTelemetrie(
    val kpi: IndicateursTelemetrie,
    val graphe: List<Baton>,
    val titreGraphe: String,
    val pages: List<PageVue>,
    val utilisateurs: List<UtilisateurActif>,
    val filtre: FiltreGestionnaire,
    /** Les vues de la période par canal d'accès, telles que servies ; vide : un serveur qui ne la sert pas. */
    val canaux: List<PartCanal> = emptyList(),
) {
    /** Un bâton : une heure, un jour (« MM-JJ »), un mois (« AAAA-MM ») ou une année. */
    data class Baton(val label: String, val total: Int, val uniques: Int?)

    data class PageVue(val page: String, val total: Int, val uniques: Int, val canaux: List<VuesCanal> = emptyList())

    /**
     * Un canal d'accès (« Site web », « App iPhone », « App Android ») : ses vues, sa part en %
     * et ses utilisateurs, calculés par le serveur ; `visiteurs` absent au-delà de 30 jours.
     */
    data class PartCanal(val canal: String, val libelle: String, val vues: Int, val part: Int, val visiteurs: Int?)

    /** Les vues d'une page du palmarès par un canal. */
    data class VuesCanal(val canal: String, val libelle: String, val vues: Int)

    /** La colonne « Accès » du palmarès n'a de sens qu'avec PLUSIEURS canaux (`TopPages.svelte`). */
    val plusieursCanauxParPage: Boolean get() = pages.flatMap { p -> p.canaux.map { it.canal } }.toSet().size > 1

    data class UtilisateurActif(val nom: String, val statut: String?, val total: Int, val pages: Int, val derniereConnexion: Instant?)

    /** Ce que le serveur dit du filtre « sans le gestionnaire du site ». */
    data class FiltreGestionnaire(
        /** Proposé seulement s'il changerait quelque chose. */
        val propose: Boolean,
        val gestionnaireDesigne: Boolean,
        /** Ce qui a été APPLIQUÉ : le serveur applique « avec » à un « sans » qui ne changerait rien. */
        val sansApplique: Boolean,
        val nonDistingueJusquAu: String?,
    )

    /** Rien à montrer : aucune vue, aucun bâton. */
    val vide: Boolean get() = kpi.vues <= 0 && graphe.isEmpty()
}

internal fun TableauLu.lu() = TableauTelemetrie(
    kpi = kpi,
    graphe = chart.map { TableauTelemetrie.Baton(it.label, it.total, it.uniques) },
    titreGraphe = chartLabel,
    pages = topPages.map { p ->
        TableauTelemetrie.PageVue(p.page, p.total, p.uniques, p.canaux.orEmpty().map { TableauTelemetrie.VuesCanal(it.canal, it.libelle, it.vues) })
    },
    utilisateurs = topUsers.map {
        TableauTelemetrie.UtilisateurActif(it.nom, it.statut, it.total, it.pages, it.derniereConnexion?.toInstant())
    },
    filtre = filtreGestionnaire.lu(),
    canaux = canaux.orEmpty().map { TableauTelemetrie.PartCanal(it.canal, it.libelle, it.vues, it.part, it.visiteurs) },
)

private fun FiltreLu.lu() = TableauTelemetrie.FiltreGestionnaire(propose, gestionnaireDesigne, applique == "sans", nonDistingueJusquAu)

/**
 * Un nombre clé : sa valeur mise en forme, son libellé (et la date qui s'y insère) ;
 * `enUtilisateurs` : la valeur se dit « N utilisateurs » (un record).
 */
data class Indicateur(
    val valeur: String,
    @StringRes val libelle: Int,
    val detail: String? = null,
    val enUtilisateurs: Boolean = false,
)

/** Ce que l'onglet « Télémétrie » compose du tableau servi, comme l'iOS (`TextesTelemetrie`). */
object LecturesTelemetrie {
    /** Les nombres clés que la vue sert, dans l'ordre du site, chacun avec son libellé. */
    fun indicateurs(kpi: IndicateursTelemetrie, portee: PorteeTelemetrie): List<Indicateur> = buildList {
        add(Indicateur(FormatsDuSite.nombre(kpi.vues), vuesDe(portee)))
        kpi.utilisateurs?.let {
            val libelle = if (portee == PorteeTelemetrie.Jour) {
                R.string.admin_telemetrie_kpi_utilisateurs_jour
            } else {
                R.string.admin_telemetrie_kpi_utilisateurs_pic
            }
            add(Indicateur(FormatsDuSite.nombre(it), libelle))
        }
        add(Indicateur(FormatsDuSite.nombre(kpi.pages), R.string.admin_telemetrie_kpi_pages))
        kpi.heurePointe?.let { add(Indicateur(it, R.string.admin_telemetrie_kpi_heure_pointe)) }
        listOf(
            kpi.moyVuesUtilisateur?.toDouble() to R.string.admin_telemetrie_kpi_moy_vues_utilisateur,
            kpi.moyVuesJour?.toDouble() to R.string.admin_telemetrie_kpi_moy_vues_jour,
            kpi.moyUtilisateursJour?.toDouble() to R.string.admin_telemetrie_kpi_moy_utilisateurs_jour,
            kpi.moisActifs?.toDouble() to R.string.admin_telemetrie_kpi_mois_actifs,
            kpi.moyVuesMois?.toDouble() to R.string.admin_telemetrie_kpi_moy_vues_mois,
            kpi.anneesActives?.toDouble() to R.string.admin_telemetrie_kpi_annees_actives,
            kpi.moyVuesAn?.toDouble() to R.string.admin_telemetrie_kpi_moy_vues_an,
        ).forEach { (valeur, libelle) -> valeur?.let { add(Indicateur(FormatsDuSite.nombre(it), libelle)) } }
        kpi.recordJour?.let {
            add(Indicateur(FormatsDuSite.nombre(it.uniques), R.string.admin_telemetrie_kpi_record_jour, FormatsDuSite.date(it.jour), true))
        }
        kpi.recordMois?.let {
            add(Indicateur(FormatsDuSite.nombre(it.uniques), R.string.admin_telemetrie_kpi_record_mois, FormatsDuSite.moisAnnee(it.mois), true))
        }
    }

    /**
     * L'étiquette d'un bâton sous le graphe : l'heure servie (« 18h »), le numéro du jour
     * (« MM-JJ » → « 4 »), le mois court (« AAAA-MM » → « nov. »), l'année.
     */
    fun etiquette(label: String, portee: PorteeTelemetrie): String {
        val morceaux = morceaux(label)
        return when {
            portee == PorteeTelemetrie.Mois && morceaux.size == 2 -> morceaux[1].toString()
            portee == PorteeTelemetrie.Annee && morceaux.size == 2 -> FormatsDuSite.moisCourt(morceaux[1])
            else -> label
        }
    }

    /** Le même bâton, en entier, pour le lecteur d'écran : « 4 nov. », « novembre 2026 ». */
    fun libelle(label: String, portee: PorteeTelemetrie): String {
        val morceaux = morceaux(label)
        return when {
            portee == PorteeTelemetrie.Mois && morceaux.size == 2 -> "${morceaux[1]} ${FormatsDuSite.moisCourt(morceaux[0])}"
            portee == PorteeTelemetrie.Annee -> FormatsDuSite.moisAnnee(label)
            else -> label
        }
    }

    /** L'accès d'une page du palmarès : « Site web 12 · App iPhone 3 » (le nombre ne quitte pas son libellé). */
    fun accesDe(page: TableauTelemetrie.PageVue): String =
        page.canaux.joinToString(Separateur.point) { "${it.libelle}\u00A0${FormatsDuSite.nombre(it.vues)}" }

    private fun morceaux(label: String): List<Int> = label.split("-").mapNotNull(String::toIntOrNull)

    @StringRes
    private fun vuesDe(portee: PorteeTelemetrie): Int = when (portee) {
        PorteeTelemetrie.Jour -> R.string.admin_telemetrie_kpi_vues_jour
        PorteeTelemetrie.Mois -> R.string.admin_telemetrie_kpi_vues_mois
        PorteeTelemetrie.Annee -> R.string.admin_telemetrie_kpi_vues_annee
        PorteeTelemetrie.Total -> R.string.admin_telemetrie_kpi_vues_total
    }
}
