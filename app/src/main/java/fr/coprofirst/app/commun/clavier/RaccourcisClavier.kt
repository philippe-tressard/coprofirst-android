// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.clavier

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import fr.coprofirst.app.R

/**
 * Les raccourcis du clavier matériel d'une tablette (#69, coprofirst-ios#223) : ceux de l'iOS,
 * sous Ctrl au lieu de ⌘ — la touche d'Android. ⚠️ Divergence déclarée : la liste des raccourcis
 * est celle du système (Meta + /, `onProvideKeyboardShortcuts`), pas celle de ⌘ maintenue.
 */
enum class Raccourci(val touche: Int, @param:StringRes val libelle: Int, val dernierSeulement: Boolean = false) {
    /** Ctrl+N : « Nouvelle affaire », dans Mes affaires. */
    NouvelleAffaire(KeyEvent.KEYCODE_N, R.string.nouvelle_titre),

    /** Ctrl+R : « Actualiser », sur tout écran qu'on tire pour actualiser (`Actualisable`). */
    Actualiser(KeyEvent.KEYCODE_R, R.string.raccourci_actualiser),

    /**
     * Ctrl+F : la recherche de l'écran (`ChampRecherche`), comme ⌘F de l'iOS. La DERNIÈRE inscrite
     * seule — celle qu'on voit ; deux champs ne prennent pas le focus à la fois. ⚠️ Divergence
     * déclarée : dans une feuille (`ModalBottomSheet`, sa propre fenêtre), Ctrl+touche n'atteint pas
     * l'activité, et Ctrl+F n'y fait rien — l'iOS, lui, y ouvre la recherche de la feuille.
     */
    Rechercher(KeyEvent.KEYCODE_F, R.string.raccourci_rechercher, dernierSeulement = true),
}

/**
 * Ce que le clavier peut déclencher, ici et maintenant : chaque écran affiché INSCRIT ses
 * raccourcis (`RaccourciClavier`) et les retire en partant. L'activité y adresse Ctrl+touche
 * (`onKeyShortcut`) — aucun focus n'est demandé, l'écran n'a rien à capter — et en tire la liste
 * du système. Un raccourci inscrit plusieurs fois (la liste et sa fiche côte à côte) les
 * déclenche tous : Ctrl+R actualise tout ce qui est à l'écran.
 */
class RaccourcisClavier {
    private val inscrits = mutableListOf<Pair<Raccourci, () -> Unit>>()

    /** Inscrit `action` ; rend de quoi la retirer. */
    fun inscrire(raccourci: Raccourci, action: () -> Unit): () -> Unit {
        val inscription = raccourci to action
        inscrits += inscription
        return { inscrits.remove(inscription) }
    }

    /** Les raccourcis offerts à l'instant, dans l'ordre de `Raccourci` : la liste du système. */
    fun offerts(): List<Raccourci> = Raccourci.entries.filter { r -> inscrits.any { it.first == r } }

    /**
     * Ctrl+`touche` : déclenche ce qui est inscrit sous elle — tout, ou la dernière inscription
     * seule (`dernierSeulement`). Vrai si quelque chose l'a été ; sans Ctrl, ou avec une autre
     * touche de modification, rien (Ctrl+Maj+R n'est pas Ctrl+R).
     */
    fun declencher(touche: Int, ctrl: Boolean, autresModificateurs: Boolean = false): Boolean {
        if (!ctrl || autresModificateurs) return false
        val sous = inscrits.filter { it.first.touche == touche }
        val actions = (if (sous.lastOrNull()?.first?.dernierSeulement == true) sous.takeLast(1) else sous).map { it.second }
        actions.forEach { it() }
        return actions.isNotEmpty()
    }
}

/** Les raccourcis de l'activité ; `null` hors d'elle (tests, aperçus) : rien ne s'inscrit. */
val LocalRaccourcisClavier = staticCompositionLocalOf<RaccourcisClavier?> { null }

/** L'écran affiché offre `raccourci` tant qu'il est composé et `actif`. */
@Composable
fun RaccourciClavier(raccourci: Raccourci, actif: Boolean = true, action: () -> Unit) {
    val raccourcis = LocalRaccourcisClavier.current ?: return
    val actuelle by rememberUpdatedState(action)
    DisposableEffect(raccourcis, raccourci, actif) {
        val retirer = if (actif) raccourcis.inscrire(raccourci) { actuelle() } else null
        onDispose { retirer?.invoke() }
    }
}
