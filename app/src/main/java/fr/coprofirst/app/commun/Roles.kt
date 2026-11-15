// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

/** Le libellé et la pastille d'un statut ou d'un rôle (`ATTRIBUTS_STATUT`, `ATTRIBUTS_ROLE`, générés). */
data class AttributRole(val libelle: String, val badge: String, val abrege: String? = null)

/** Une option du thème de l'appareil (`OPTIONS_THEME`, générée). */
data class OptionTheme(val valeur: String, val libelle: String, val description: String)

/**
 * Le libellé d'un statut ou d'un rôle servi, comme l'iOS (`RolesUtilisateur.libelleStatut`,
 * `libelleRole`) : sa table, puis les libellés hérités, puis l'autre table ; une clé
 * inconnue est rendue telle quelle, en gris — un libellé manquant doit se voir.
 */
object Roles {
    fun statut(valeur: String): LibelleTeinte =
        teinte(valeur, TablesCommunes.statuts, TablesCommunes.roles)

    fun role(valeur: String): LibelleTeinte =
        teinte(valeur, TablesCommunes.roles, TablesCommunes.statuts)

    /** Le libellé court d'un statut (`abrege`), sinon son libellé. */
    fun statutAbrege(valeur: String): String = TablesCommunes.statuts[valeur]?.let { it.abrege ?: it.libelle } ?: statut(valeur).libelle

    private fun teinte(valeur: String, table: Map<String, AttributRole>, autre: Map<String, AttributRole>): LibelleTeinte {
        table[valeur]?.let { return LibelleTeinte(it.libelle, it.badge) }
        TablesCommunes.libellesHerites[valeur]?.let { return LibelleTeinte.brut(it) }
        autre[valeur]?.let { return LibelleTeinte(it.libelle, it.badge) }
        return LibelleTeinte.brut(valeur)
    }
}
