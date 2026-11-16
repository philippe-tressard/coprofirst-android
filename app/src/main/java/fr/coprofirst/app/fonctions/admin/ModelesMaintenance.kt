// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import fr.coprofirst.api.modeles.AnomalieRecente
import fr.coprofirst.api.modeles.SanteMaintenance
import fr.coprofirst.api.modeles.SanteTache
import fr.coprofirst.app.commun.LibelleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import java.time.Instant
import fr.coprofirst.api.modeles.DernierExport as DernierExportLu

/**
 * La santé des tâches planifiées, telle que le serveur la juge (`utils/etat_taches`) :
 * une ligne par tâche, son statut, sa dernière exécution — et ce qui a échoué récemment.
 * Les nœuds sont nommés par le SERVEUR : l'app n'en connaît aucun.
 */
data class SanteTaches(
    val taches: List<Tache>,
    val anomaliesRecentes: List<Anomalie>,
    val anomaliesFenetreJours: Int,
) {
    data class Tache(
        val tache: String,
        val statut: String,
        val noeud: String? = null,
        val derniere: Instant? = null,
        /** Le nœud qui décroche alors que la synthèse est saine. */
        val noeudEnRetard: String? = null,
    )

    data class Anomalie(val tache: String, val noeud: String?, val creeLe: Instant?, val erreur: String?)
}

/** Le dernier export de la copropriété dans le volume des sauvegardes ; sans `archive`, il n'y en a aucun. */
data class DernierExport(
    val archive: String?,
    val creeLe: Instant?,
    val octets: Long,
    val lignes: Long,
    val fichiers: Long,
)

/** Ce que l'onglet « Maintenance » dit d'une tâche : les noms et statuts GÉNÉRÉS du site (`TablesAdmin`). */
object LibellesMaintenance {
    /** Le nom d'une tâche servie ; son code s'il est inconnu. */
    fun tache(code: String): String = TablesAdmin.libellesTache[code] ?: code

    /** Le statut d'une tâche, en pastille ; un statut inconnu s'affiche brut, en gris. */
    fun statut(code: String): LibelleTeinte =
        TablesAdmin.statutsTache[code]?.let { LibelleTeinte(it.libelle, it.badge) } ?: LibelleTeinte.brut(code)

    /** Ce que veut dire un statut qui n'est pas « à jour » ; rien pour « à jour » ou un statut inconnu. */
    fun aide(code: String): String? = TablesAdmin.statutsTache[code]?.aide?.takeIf { code != STATUT_OK }

    /** Le nœud en retard, en capitales comme le site. */
    fun noeud(noeud: String): String = noeud.uppercase()

    /** « Copie hors site (manuelle) · nœud-a · 10/11/2026, 01:15 » : la ligne d'une anomalie. */
    fun anomalie(a: SanteTaches.Anomalie): String =
        listOfNotNull(tache(a.tache), a.noeud, a.creeLe?.let(FormatsDuSite::dateHeure)).joinToString(" · ")

    private const val STATUT_OK = "ok"
}

internal fun SanteMaintenance.lu() = SanteTaches(
    taches = taches.map(SanteTache::lu),
    anomaliesRecentes = anomaliesRecentes.map(AnomalieRecente::lu),
    anomaliesFenetreJours = anomaliesFenetreJours,
)

internal fun SanteTache.lu() = SanteTaches.Tache(
    tache = tache,
    statut = statut,
    noeud = noeud,
    derniere = derniere?.toInstant(),
    noeudEnRetard = noeudEnRetard,
)

internal fun AnomalieRecente.lu() = SanteTaches.Anomalie(tache, noeud, creeLe.toInstant(), erreur)

internal fun DernierExportLu.lu() = DernierExport(
    archive = archive,
    creeLe = FormatsDuSite.lire(creeLe),
    octets = (octets ?: 0).toLong(),
    lignes = (lignes ?: 0).toLong(),
    fichiers = (fichiers ?: 0).toLong(),
)
