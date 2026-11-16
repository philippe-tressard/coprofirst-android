// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

/**
 * La saisie d'une affaire, Essentielle ou Complète (#178), comme l'iOS (`FormeDeSaisie`,
 * coprofirst#2595) : le même formulaire, montré de deux façons. En Essentielle, les sections
 * qui ne sont pas essentielles pour la catégorie se rangent, à leur rang, dans « Plus de
 * détails », et partent à leur valeur par défaut ; ce qui part au serveur ne change pas.
 *
 * Rien ne se décide ici : le MODE est celui que le serveur calcule pour le compte
 * (`UserRead.saisie_affaire_effective`, `LocalSaisieAffaire`), les SECTIONS celles qu'il sert
 * pour chaque catégorie (`CategorieServie.sections_essentielles`).
 */
enum class ModeSaisie(val code: String) {
    Essentielle("essentielle"),
    Complete("complete"),
    ;

    companion object {
        /** Un mot inconnu ne range rien : la saisie reste complète, sans phrase qui mentirait. */
        fun servi(code: String?): ModeSaisie? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Une section que la feuille pose et peut RANGER, par son identifiant dans le cadre du site
 * (`TICKET` de `entites/ticket.ts`), dans l'ordre du formulaire. Catégorie, titre et
 * description sont requis pour envoyer : ils restent toujours sous les yeux.
 *
 * ⚠️ Divergence déclarée : l'iOS lit l'ordre du cadre dans les sources de coprofirst
 * (`TablesSaisie.sectionsAffaire`) ; il n'est pas publié avec la release — les deux codes
 * sont écrits ici, dans l'ordre du formulaire (coprofirst#2793).
 */
enum class SectionAffaire(val code: String) {
    Perimetre("perimetre"),
    MiseEnAvant("mise_en_avant"),
}

/** Comment « Nouvelle affaire » et « Modifier » se composent pour ce compte. */
data class FormeDeSaisie(
    val mode: ModeSaisie,
    /** Les sections essentielles par catégorie servie ; vide : rien ne justifie de ranger. */
    private val essentielles: Map<String, Set<String>>,
) {
    /** Le groupe « Plus de détails » existe-t-il pour ce compte ? */
    val range: Boolean get() = mode == ModeSaisie.Essentielle && essentielles.isNotEmpty()

    /**
     * Les sections rangées dans « Plus de détails », dans l'ordre du formulaire. Une section
     * ÉTEINTE (un bogue n'a ni lieu ni urgence) n'a pas d'objet : elle n'y entre pas.
     */
    fun rangees(categorie: String, eteintes: Set<SectionAffaire> = emptySet()): List<SectionAffaire> {
        if (!range) return emptyList()
        val montrees = essentielles[categorie] ?: essentielles.values.reduce { a, b -> a intersect b }
        return SectionAffaire.entries.filter { it.code !in montrees && it !in eteintes }
    }

    companion object {
        /** La forme d'un compte : son mode tel que servi, les sections de TOUTES les catégories servies. `null` : rien à ranger ni à dire. */
        fun de(servi: String?, categories: List<ReferentielsAffaires.Categorie>): FormeDeSaisie? {
            val mode = ModeSaisie.servi(servi) ?: return null
            return FormeDeSaisie(mode, categories.associate { it.code to it.sectionsEssentielles.toSet() })
        }
    }
}
