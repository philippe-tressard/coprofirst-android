// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.perimetres

import fr.coprofirst.app.commun.texte.Separateur

/**
 * L'arbre des périmètres tel que le CHOIX le parcourt, comme `ArbreDesPerimetres` de l'iOS
 * (`perimetresNiveau1` du site, `PerimetrePicker.svelte`) : une rangée de TÊTES — le défaut,
 * puis chaque racine ciblable et chaque enfant d'un regroupement (« Bâtiments » n'est pas
 * une cible, on choisit un bâtiment) —, puis les ESPACES d'une seule tête.
 *
 * Toute la règle tient ici, sans vue : `ChoixPerimetres` ne fait que la dessiner.
 */
class ArbreDesPerimetres(
    /** Les nœuds actifs, regroupements compris : sans eux, pas de têtes. */
    val noeuds: List<Perimetre>,
) {
    private val parCode: Map<String, Perimetre> = LinkedHashMap<String, Perimetre>().apply {
        noeuds.forEach { putIfAbsent(it.code, it) }
    }

    /**
     * Ce qui se PROPOSE : les nœuds sélectionnables jusqu'au niveau que le serveur admet
     * (`Perimetre.aSaisir`) — l'arbre entier, lui, reste pour trouver les têtes.
     */
    private val proposables: Set<String> = Perimetre.aSaisir(noeuds).mapTo(HashSet()) { it.code }

    /** Le périmètre que le serveur désigne par défaut, s'il en désigne un. */
    val defaut: Perimetre? = noeuds.firstOrNull { it.parDefaut }

    /** Les têtes, hors le défaut (il a sa propre pastille). */
    val tetes: List<Perimetre> = noeuds.filter { noeud ->
        noeud.code in proposables && !noeud.parDefaut && (noeud.parent == null || estRegroupement(noeud.parent))
    }

    private val codesDesTetes: Set<String> = tetes.mapTo(HashSet()) { it.code }

    /** `estRegroupement` du site (`teinte.ts`) : une racine qui ne se cible pas. */
    private fun estRegroupement(code: String?): Boolean {
        val noeud = code?.let { parCode[it] } ?: return false
        return noeud.parent == null && !noeud.selectionnable
    }

    /** Les espaces ciblables d'une tête : ses enfants directs, comme le site. */
    fun espaces(tete: String): List<Perimetre> = noeuds.filter { it.parent == tete && it.code in proposables }

    /** La tête dont dépend `code` (elle-même si c'en est une), s'il y en a une. */
    fun tete(code: String): String? {
        var courant = parCode[code]
        val vus = HashSet<String>()
        while (courant != null && vus.add(courant.code)) {
            if (courant.code in codesDesTetes) return courant.code
            courant = courant.parent?.let { parCode[it] }
        }
        return null
    }

    fun noeud(code: String): Perimetre? = parCode[code]
}

/**
 * Une sélection de périmètres et la tête dont la rangée d'espaces est ouverte, comme
 * `ChoixDePerimetres` de l'iOS — la règle `basculer()` du site, en cinq lignes, aucune ne
 * dépendant de l'ordre des touchers :
 *
 * | Toucher | Sélection | Rangée |
 * |---|---|---|
 * | une tête qui a des espaces retenus | inchangée | s'ouvre |
 * | une tête sans espace retenu, non retenue | elle est ajoutée | s'ouvre |
 * | une tête retenue | elle est retirée | se ferme |
 * | un espace | il est basculé ; sa tête entière est retirée | reste ouverte |
 * | le défaut | tout est vidé | se ferme |
 *
 * « Tout le bâtiment 3 » et « le toit du bâtiment 3 » sont deux cibles exclusives.
 * Immuable : chaque geste rend le nouvel état — l'état d'un écran se garde tel quel
 * (`mutableStateOf(ChoixDePerimetres())`).
 */
data class ChoixDePerimetres(
    /** Les codes retenus ; vide ou le seul défaut : « toute la copropriété ». */
    val codes: Set<String> = emptySet(),
    /** La tête touchée en dernier (`parentTouche` du site). */
    val touchee: String? = null,
) {
    fun estDefaut(arbre: ArbreDesPerimetres): Boolean = codes.isEmpty() || codes == setOfNotNull(arbre.defaut?.code)

    /** La tête dont les espaces se montrent : la dernière touchée si elle en a, sinon celle d'une valeur retenue. */
    fun ouverte(arbre: ArbreDesPerimetres): String? {
        if (touchee != null && arbre.espaces(touchee).isNotEmpty()) return touchee
        return codes.sorted().asSequence().mapNotNull { arbre.tete(it) }.firstOrNull { arbre.espaces(it).isNotEmpty() }
    }

    /** Les espaces retenus sous une tête — le résumé de sa pastille. */
    fun espacesRetenus(tete: String, arbre: ArbreDesPerimetres): List<Perimetre> =
        arbre.espaces(tete).filter { it.code in codes }

    /** La pastille d'une tête est pleine : la tête ou l'un de ses espaces est retenu. */
    fun teteRetenue(tete: String, arbre: ArbreDesPerimetres): Boolean =
        !estDefaut(arbre) && (tete in codes || espacesRetenus(tete, arbre).isNotEmpty())

    /**
     * « Bâtiment 1 › Hall · Toit » : la tête porte ce qui y est retenu, rangée ouverte ou
     * non — l'accusé de réception du toucher (le site, 18/08/2026).
     */
    fun libelleDeTete(tete: Perimetre, arbre: ArbreDesPerimetres): String {
        val retenus = espacesRetenus(tete.code, arbre)
        if (retenus.isEmpty()) return tete.libelle
        return tete.libelle + " › " + retenus.joinToString(Separateur.point) { it.libelleCourt ?: it.libelle }
    }

    /** L'aide du choix : la description du seul code retenu, s'il en a une. */
    fun aide(arbre: ArbreDesPerimetres): String? =
        codes.singleOrNull()?.let { arbre.noeud(it)?.description }?.takeIf { it.isNotEmpty() }

    fun choisirDefaut(arbre: ArbreDesPerimetres) = ChoixDePerimetres(setOfNotNull(arbre.defaut?.code), touchee = null)

    fun basculer(code: String, arbre: ArbreDesPerimetres): ChoixDePerimetres {
        val racine = arbre.tete(code)
        val estTete = racine == code
        val retenus = codes.toMutableSet()
        arbre.defaut?.code?.let { retenus.remove(it) }
        val espaces = retenus.filter { it != code && arbre.tete(it) == code }

        // 1. On vient REVOIR ses espaces : rien ne bouge.
        if (estTete && espaces.isNotEmpty() && code !in retenus) return copy(touchee = code)
        var tete = touchee
        if (code in retenus) {
            retenus.remove(code) // 2 et 3. Retirer ; retirer une tête referme sa rangée.
            if (estTete) tete = null
        } else {
            tete = racine
            if (!estTete && racine != null) retenus.remove(racine) // 4. Un espace remplace sa tête.
            retenus.add(code)
        }
        return ChoixDePerimetres(if (retenus.isEmpty()) setOfNotNull(arbre.defaut?.code) else retenus, tete)
    }
}
