// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.perimetres

import fr.coprofirst.api.modeles.PerimetreRead

/**
 * Un périmètre de la copropriété (`PerimetreRead`, GET /perimetres), comme `Perimetre` de
 * l'iOS : le lieu d'une annonce, d'une affaire, d'une actualité — un seul modèle.
 *
 * Le périmètre PAR DÉFAUT (« toute la copropriété ») est celui que le serveur désigne
 * (`par_defaut`, coprofirst#2514) : l'app ne refait pas le tri des racines.
 */
data class Perimetre(
    val code: String,
    val libelle: String,
    /** Le rang dans l'arbre (0 : une racine). */
    val profondeur: Int = 0,
    /** Proposable comme lieu d'un contenu (un nœud de regroupement ne l'est pas). */
    val selectionnable: Boolean = true,
    /** Le défaut du serveur : un seul nœud au plus, aucun sur un arbre vide. */
    val parDefaut: Boolean = false,
    /** Le code du nœud parent ; `null` pour une racine — les têtes du choix en dépendent. */
    val parent: String? = null,
    /** Le libellé abrégé (« Bât. 3 ») : celui du résumé d'une pastille, qui doit tenir. */
    val libelleCourt: String? = null,
    /** Ce que le nœud implique (« notifie l'ensemble du conseil syndical ») : l'aide du choix. */
    val description: String? = null,
    /**
     * Combien de niveaux ce nœud peut encore porter sous lui (`niveaux_libres`, contrat
     * 1.17.0) ; `null` s'il n'est pas connu. Le plancher du contrat (2.0.0) le sert toujours.
     */
    val niveauxLibres: Int? = null,
) {
    companion object {
        /** Un nœud servi, lu tel quel. */
        fun lu(lu: PerimetreRead) = Perimetre(
            code = lu.code,
            libelle = lu.libelle,
            profondeur = lu.profondeur,
            selectionnable = lu.selectionnable,
            parDefaut = lu.parDefaut == true,
            parent = lu.parent,
            libelleCourt = lu.libelleCourt,
            description = lu.description,
            niveauxLibres = lu.niveauxLibres,
        )

        /** L'arbre servi, ACTIFS seulement — le MÊME branchement pour le service et les tests. */
        fun lus(generes: List<PerimetreRead>): List<Perimetre> = generes.filter { it.actif }.map(::lu)

        /**
         * Les lieux qu'un sélecteur propose : les nœuds sélectionnables, jamais sous un
         * parent qui ne peut plus rien porter (`niveaux_libres` servi à 0) — le plafond se
         * lit sur ce que le serveur sert, il n'est pas recopié ici.
         *
         * `garder` : le lieu déjà enregistré d'un contenu qu'on modifie — proposé même trop
         * profond, pour que le modifier ne le perde pas en silence.
         */
        fun aSaisir(perimetres: List<Perimetre>, garder: String? = null): List<Perimetre> {
            val libres = HashMap<String, Int?>()
            perimetres.forEach { libres.putIfAbsent(it.code, it.niveauxLibres) }
            return perimetres.filter { perimetre ->
                val place = perimetre.parent?.let { libres[it] }
                perimetre.code == garder || (perimetre.selectionnable && (place ?: 1) > 0)
            }
        }

        /** Le code du périmètre que le serveur désigne par défaut, s'il en désigne un. */
        fun codeParDefaut(perimetres: List<Perimetre>): String? = perimetres.firstOrNull { it.parDefaut }?.code
    }
}
