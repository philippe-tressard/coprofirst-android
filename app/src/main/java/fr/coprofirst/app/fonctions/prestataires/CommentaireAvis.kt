// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import fr.coprofirst.app.fonctions.prestataires.TablesNotations.AVANT_LIBRE
import fr.coprofirst.app.fonctions.prestataires.TablesNotations.ENTRE_GROUPES
import fr.coprofirst.app.fonctions.prestataires.TablesNotations.ENTRE_MOTIFS
import fr.coprofirst.app.fonctions.prestataires.TablesNotations.FIN_DE_GROUPE
import fr.coprofirst.app.fonctions.prestataires.TablesNotations.PREFIXE_FAIBLES
import fr.coprofirst.app.fonctions.prestataires.TablesNotations.PREFIXE_FORTS

/**
 * Le commentaire d'un avis tel que le site le COMPOSE et le RELIT (`notations.ts` :
 * `phraseGeneree`, `commentaireComplet`, `decomposerCommentaire`), comme l'iOS (`AvisSaisi`,
 * coprofirst-ios#260) : la phrase des cases cochées, puis le texte libre. Les motifs et la
 * forme de la phrase se lisent dans la table publiée (`TablesNotations`, `commentaireAvis`,
 * coprofirst#2816) — rien n'en est recopié ici.
 */
object CommentaireAvis {
    /** « Points forts : a, b. Points faibles : c. » — dans l'ordre du site ; vide si rien n'est coché. */
    fun phrase(forts: Set<String>, faibles: Set<String>): String =
        listOf(PREFIXE_FORTS to TablesNotations.pointsForts.filter { it in forts },
            PREFIXE_FAIBLES to TablesNotations.pointsFaibles.filter { it in faibles })
            .filter { it.second.isNotEmpty() }
            .joinToString(ENTRE_GROUPES) { (prefixe, motifs) -> prefixe + motifs.joinToString(ENTRE_MOTIFS) + FIN_DE_GROUPE }

    /** Le commentaire enregistré : la phrase, puis le texte libre. */
    fun complet(phrase: String, libre: String): String =
        listOf(phrase, libre.trim()).filter { it.isNotEmpty() }.joinToString(AVANT_LIBRE)

    private val formePhrase = Regex(
        "^(?:" + Regex.escape(PREFIXE_FORTS) + "(.+?)" + Regex.escape(FIN_DE_GROUPE) + ")?\\s*" +
            "(?:" + Regex.escape(PREFIXE_FAIBLES) + "(.+?)" + Regex.escape(FIN_DE_GROUPE) + ")?$",
    )

    /**
     * Relit un commentaire enregistré : la première ligne n'est reprise comme phrase que si
     * CHACUN de ses motifs est dans les listes — sinon c'est un texte libre qui lui
     * ressemble, et il reste libre en entier.
     */
    fun decomposer(commentaire: String?): Decompose {
        val texte = commentaire.orEmpty()
        val trouve = formePhrase.find(texte.substringBefore(AVANT_LIBRE).trim()) ?: return Decompose(libre = texte)
        fun motifs(groupe: Int) = trouve.groups[groupe]?.value?.split(ENTRE_MOTIFS).orEmpty()
        val (forts, faibles) = motifs(1) to motifs(2)
        val reconnus = forts.size + faibles.size > 0 &&
            TablesNotations.pointsForts.containsAll(forts) && TablesNotations.pointsFaibles.containsAll(faibles)
        if (!reconnus) return Decompose(libre = texte)
        return Decompose(forts.toSet(), faibles.toSet(), if (AVANT_LIBRE in texte) texte.substringAfter(AVANT_LIBRE) else "")
    }

    /** Un commentaire relu : les cases qu'il cochait, et le texte libre qui suit. */
    data class Decompose(val forts: Set<String> = emptySet(), val faibles: Set<String> = emptySet(), val libre: String)
}
