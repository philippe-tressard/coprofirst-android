// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.perimetres

/**
 * L'arbre des périmètres que la résidence simulée sert (GET /perimetres), en JSON, à la
 * forme de la route — lu par le client GÉNÉRÉ puis par `Perimetre.lus`, comme l'app. Celui
 * de l'iOS (`FixturesPerimetres`), à la forme de l'arbre semé (`seed/patrimoine.py`) : une
 * racine désignée par défaut, le regroupement « Bâtiments » (non ciblable) et ses
 * bâtiments, deux lieux racines ; sous le bâtiment A, trois espaces au niveau 3 (hall,
 * toit, terrasses), et sous les terrasses un nœud écrit avant le plafond (niveau 4)
 * qu'aucun sélecteur ne propose. En plus de l'iOS : une loge INACTIVE, que la lecture écarte.
 *
 * Variante de mise au point seulement, jamais livrée. Ses codes sont ceux des autres
 * fixtures (`perimetre_cible` : `copropriete`, `bat_a`).
 */
object FixturesPerimetres {
    private fun noeud(
        id: Int, code: String, parent: String?, libelle: String, court: String, profondeur: Int, ordre: Int,
        niveau: Int, libres: Int, selectionnable: Boolean = true, parDefaut: Boolean = false, actif: Boolean = true,
    ) = """
        {"id": $id, "code": "$code", "parent": ${parent?.let { "\"$it\"" } ?: "null"}, "libelle": "$libelle",
         "libelle_court": "$court", "description": "", "icone": null, "batiment_id": null, "profondeur": $profondeur,
         "ordre": $ordre, "actif": $actif, "portee_globale": $parDefaut, "concerne_tous": $parDefaut,
         "selectionnable": $selectionnable, "privatif": false, "hors_copropriete": false, "utilise": false,
         "par_defaut": $parDefaut, "niveau": $niveau, "niveaux_libres": $libres, "hauteur": 0}
    """.trimIndent()

    /** GET /perimetres → `[PerimetreRead]`, dans l'ordre de l'arbre. */
    val ARBRE = "[" + listOf(
        noeud(1, "copropriete", null, "Toute la copropriété", "Copropriété", 0, 0, 1, 2, parDefaut = true),
        noeud(8, "batiments", null, "Bâtiments", "Bâtiments", 0, 10, 1, 2, selectionnable = false),
        noeud(2, "bat_a", "batiments", "Bâtiment A", "Bât. A", 1, 1, 2, 1),
        noeud(9, "bat_a/hall", "bat_a", "Hall d'entrée", "Hall", 2, 1, 3, 0),
        noeud(10, "bat_a/toit", "bat_a", "Toit", "Toit", 2, 2, 3, 0),
        noeud(6, "bat_a_terrasses", "bat_a", "Terrasses Balcons", "Terrasses", 2, 3, 3, 0),
        noeud(7, "bat_a_terrasse_nord", "bat_a_terrasses", "Terrasse nord", "Nord", 3, 1, 4, 0),
        noeud(11, "bat_a/loge", "bat_a", "Ancienne loge", "Loge", 2, 4, 3, 0, actif = false),
        noeud(3, "bat_b", "batiments", "Bâtiment B", "Bât. B", 1, 2, 2, 1),
        noeud(4, "parking", null, "Parking", "Parking", 0, 3, 1, 2),
        noeud(5, "jardin", null, "Jardin", "Jardin", 0, 4, 1, 2),
    ).joinToString(",") + "]"

    /** Le code du défaut de l'arbre. */
    const val CODE_PAR_DEFAUT = "copropriete"

    /** POST /perimetres/libelle → `LibellePerimetre` : « le toit du bâtiment A »… */
    const val LIBELLE = """{"libelle": "Bâtiment A › Toit", "badge": "Bâtiment A › Toit"}"""

    /** … et le défaut : le libellé en clair, le badge vide (rien sur une carte). */
    const val LIBELLE_PAR_DEFAUT = """{"libelle": "Toute la copropriété", "badge": ""}"""

    /** La réponse de POST /perimetres/libelle : le défaut seul a la sienne, toute autre sélection celle du toit. */
    fun libelle(codes: List<String>) = if (codes == listOf(CODE_PAR_DEFAUT)) LIBELLE_PAR_DEFAUT else LIBELLE
}
