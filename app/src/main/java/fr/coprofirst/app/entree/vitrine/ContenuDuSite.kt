// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree.vitrine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Le contenu de l'accueil de coprofirst.fr, tel que le site le publie (`contenu.json`,
 * coprofirst-site#42), comme `ContenuDuSite` de l'iOS. Un champ inconnu s'ignore ; un
 * `format` plus récent que [FORMAT_CONNU] se refuse.
 */
@Serializable
data class ContenuDuSite(val format: Int, val sections: List<BlocDeContenu>) {
    fun section(id: String): BlocDeContenu? = sections.firstOrNull { it.id == id }

    companion object {
        const val FORMAT_CONNU = 1
        private val json = Json { ignoreUnknownKeys = true }

        /** Le contenu d'un `contenu.json`, ou `null` s'il est illisible ou d'un format inconnu. */
        fun lire(texte: String): ContenuDuSite? =
            runCatching { json.decodeFromString<ContenuDuSite>(texte) }.getOrNull()?.takeIf { it.format <= FORMAT_CONNU }
    }
}

/**
 * Une section ou l'un de ses éléments : les deux ont la même forme. L'app n'en lit que le
 * texte — ni `liens`, ni `images`, ni `adresse` (aucune adresse venue du contenu n'est
 * ouverte ni contactée par l'app). Un champ vide est omis par le site.
 */
@Serializable
data class BlocDeContenu(
    val id: String,
    val titre: String? = null,
    val textes: List<String> = emptyList(),
    val elements: List<BlocDeContenu> = emptyList(),
) {
    fun element(id: String): BlocDeContenu? = elements.firstOrNull { it.id == id }
}

/**
 * La première phrase d'un paragraphe : le site écrit un chapeau de plusieurs phrases, la
 * vitrine de l'app n'en montre que la première. Une phrase finit sur `.`, `!`, `?` ou `…`
 * suivis d'une espace et d'une majuscule — « Où en est ma demande ? » n'est donc pas coupé.
 */
fun premierePhrase(texte: String): String =
    FIN_DE_PHRASE.find(texte)?.let { texte.substring(0, it.range.last + 1) } ?: texte

// Les espaces insécables du site comptent comme des espaces. Pas de drapeau `(?U)` : Android
// ne le connaît pas (l'expression ne se compilerait pas, et le fichier ne s'initialiserait pas).
private val FIN_DE_PHRASE = Regex("""[.!?…](?=[\s  ]+\p{Lu})""")
