// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree.vitrine

import android.content.Context

/**
 * Ce qui est PROPRE à l'app dans la vitrine, comme `ContenuVitrine` de l'iOS : les `id`
 * du site auxquels elle s'accroche, et l'ordre de ce qu'elle montre. Les textes viennent
 * de coprofirst.fr (`contenu.json`) ; la devise et les boutons, absents du site, sont dans
 * les ressources de l'app.
 */
object ContenuVitrine {
    const val HEROS = "heros"
    const val FONCTIONS = "fonctions"
    const val DEMO = "demo"

    /** La page publique où le site publie les identifiants de la démo. */
    const val PAGE_IDENTIFIANTS_DEMO = "https://coprofirst.fr/demo.html"

    /** Les fonctions de « En savoir plus », dans l'ordre de l'app (les six de la maquette de l'iOS). */
    val rangees = listOf("demande", "documents", "point-syndic", "badges", "carnet", "communaute")

    /**
     * Un rôle de la démo : son `id` sur le site, et la partie locale de son identifiant
     * public (`app/demo/personnages.py` de coprofirst). ⚠️ Recopiés faute de route, comme
     * l'iOS : aucun point de l'API ne sert les personnages (coprofirst#2460). Les mots de
     * passe, publics mais sensibles par nature, ne sont PAS dans ce dépôt.
     */
    data class RoleDemo(val id: String, val code: String)

    val roles = listOf(
        RoleDemo("conseil-syndical", "user-cs"),
        RoleDemo("coproprietaire", "user-copro"),
        RoleDemo("locataire", "user-loc"),
    )

    /** Chaque `id` dont l'app dépend (`section` ou `section/élément`), protégé par le site (coprofirst-site#66). */
    val idsRequis: List<String> =
        listOf(HEROS, FONCTIONS) + rangees.map { "$FONCTIONS/$it" } + DEMO + roles.map { "$DEMO/${it.id}" }
}

/**
 * Les deux écrans de la vitrine, mis en pages depuis `contenu.json` : « En savoir plus » et
 * la démo. Un contenu auquel manque un `id` requis, un titre ou un texte attendu ne donne
 * PAS de pages — jamais un écran à moitié vide.
 */
data class PagesVitrine(val enSavoirPlus: PageEnSavoirPlus, val demo: PageDemo) {
    companion object {
        private const val CHEMIN = "vitrine/contenu.json"

        @Volatile private var embarquees: PagesVitrine? = null

        /** Les pages de la copie embarquée (`scripts/importer-vitrine.sh`), lues une fois. */
        fun embarquees(contexte: Context): PagesVitrine = embarquees ?: checkNotNull(
            lire(contexte.assets.open(CHEMIN).use { it.readBytes().decodeToString() }),
        ) { "copie embarquée de contenu.json incomplète — PagesVitrineTest la refuse" }.also { embarquees = it }

        /** Les pages d'un `contenu.json`, ou `null` s'il est illisible, d'un format inconnu ou incomplet. */
        fun lire(texte: String): PagesVitrine? = ContenuDuSite.lire(texte)?.let(::mettreEnPages)

        private fun mettreEnPages(contenu: ContenuDuSite): PagesVitrine? {
            val heros = contenu.section(ContenuVitrine.HEROS) ?: return null
            val fonctions = contenu.section(ContenuVitrine.FONCTIONS) ?: return null
            val demo = contenu.section(ContenuVitrine.DEMO) ?: return null
            if (demo.textes.size < 2) return null
            val rangees = ContenuVitrine.rangees.map { id ->
                val element = fonctions.element(id) ?: return null
                PageEnSavoirPlus.Rangee(id, element.titre ?: return null, element.textes.firstOrNull() ?: return null)
            }
            val personnages = ContenuVitrine.roles.map { role ->
                val element = demo.element(role.id) ?: return null
                PageDemo.Personnage(role.code, element.titre ?: return null, element.textes.firstOrNull() ?: return null)
            }
            return PagesVitrine(
                PageEnSavoirPlus(
                    titre = heros.titre ?: return null,
                    chapeau = premierePhrase(heros.textes.firstOrNull() ?: return null),
                    section = fonctions.titre ?: return null,
                    sousTitre = fonctions.textes.firstOrNull() ?: return null,
                    rangees = rangees,
                ),
                PageDemo(demo.titre ?: return null, demo.textes[0], demo.textes[1], personnages),
            )
        }
    }
}

/** « En savoir plus » : `heros` (titre, première phrase), puis `fonctions` et ses rangées. */
data class PageEnSavoirPlus(
    val titre: String,
    val chapeau: String,
    val section: String,
    val sousTitre: String,
    val rangees: List<Rangee>,
) {
    data class Rangee(val id: String, val titre: String, val ligne: String)
}

/** La démo : `demo` — son titre, son premier texte (le chapeau), son deuxième (l'avertissement). */
data class PageDemo(val titre: String, val chapeau: String, val avertissement: String, val personnages: List<Personnage>) {
    /** `code` : l'identifiant public du rôle (`user-cs`) ; `role` : son nom sur le site. */
    data class Personnage(val code: String, val role: String, val ligne: String)
}
