// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * La phrase brève qui suit un geste (le « toast » du site), comme `AvisDuGeste` de l'iOS :
 * l'écran la DIT (`avis.dire(…)`), seul `AvisEphemere` la montre et la retire, au bout du
 * temps du site (`AvisDuSite.DUREE_MS`, généré). Une phrase dite remplace celle qui est à
 * l'écran ; le retrait d'une phrase n'efface jamais celle qui l'a remplacée.
 */
@Stable
class AvisDuGeste {
    /** La phrase à l'écran, ou `null`. */
    var phrase: PhraseAffichee? by mutableStateOf(null)
        private set

    /** La phrase d'une feuille qui se ferme, en attendant la fin de sa fermeture. */
    private var enAttente: PhraseAvis? = null
    private var numero = 0L

    val texte: String? get() = phrase?.texte

    /** Dire la phrase ; elle remplace celle qui est à l'écran (et sa minuterie repart). */
    fun dire(texte: String, ton: TonAvis = TonAvis.Succes) {
        numero += 1
        phrase = PhraseAffichee(texte, ton, numero)
    }

    /** Dire une phrase servie avec son ton (l'`annonce` d'un geste). */
    fun dire(phrase: PhraseAvis) = dire(phrase.texte, phrase.ton)

    /**
     * Dite par une FEUILLE qui va se fermer : la phrase attend la fin de la fermeture
     * (`avis.feuilleFermee()` dans le `fermer` de la feuille). Dite tout de suite, elle
     * passerait l'essentiel de son temps derrière la feuille.
     */
    fun direApresFermeture(texte: String, ton: TonAvis = TonAvis.Succes) {
        enAttente = PhraseAvis(texte, ton)
    }

    fun direApresFermeture(phrase: PhraseAvis) {
        enAttente = phrase
    }

    /** La feuille est fermée : la phrase qu'elle a laissée se dit maintenant. */
    fun feuilleFermee() {
        val laissee = enAttente ?: return
        enAttente = null
        dire(laissee)
    }

    /** Retirer CETTE phrase — pas celle qui l'a remplacée entre-temps. */
    internal fun retirer(affichee: PhraseAffichee) {
        if (phrase == affichee) phrase = null
    }
}

/**
 * Le ton d'un avis, celui du site (`success | warning | error | info`) : l'`annonce` d'un
 * geste le sert. Il choisit le pictogramme et le retour haptique, comme l'iOS ; la bulle
 * garde les couleurs de l'avis du site, quel que soit le ton.
 */
enum class TonAvis(val valeur: String) {
    Succes("success"),
    Avertissement("warning"),
    Erreur("error"),
    Info("info"),
    ;

    companion object {
        /** Le ton servi ; un ton inconnu se dit comme une information. */
        fun depuis(valeur: String?): TonAvis = entries.firstOrNull { it.valeur == valeur } ?: Info
    }
}

/** Une phrase et son ton. */
data class PhraseAvis(val texte: String, val ton: TonAvis = TonAvis.Succes)

/** Une phrase à l'écran : son `numero` la distingue d'une même phrase redite. */
data class PhraseAffichee(val texte: String, val ton: TonAvis, val numero: Long)
