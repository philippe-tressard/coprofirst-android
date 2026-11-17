// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.miseenpage

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise

/**
 * La colonne où va la fiche d'une liste, en fenêtre large (#69), comme `ColonneDeFiche` de
 * l'iOS (coprofirst-ios#261) : FOURNIE par qui tient la page (la barre latérale), jamais
 * décidée par l'appareil. Une liste qui paraît l'OCCUPE (`OccuperLaColonneDeFiche`) : sa page
 * se tient alors à gauche, à la largeur d'une liste, et la fiche qu'elle ouvre à droite —
 * « Aucune sélection » tant que rien n'est choisi. Une page sans liste ne l'ouvre jamais, et
 * garde toute la largeur.
 */
class ColonneDeFiche(retenueInitiale: Boolean = false) {
    /**
     * Une liste occupait la colonne : SAUVÉ avec l'écran (`Sauvegarde`) — la reconstruction (rotation,
     * pli, multi-fenêtre) recompose la page à côté de la fiche rouverte, et sa liste se redéclare ;
     * sans lui, la fiche prendrait toute la largeur. Posé par la liste qui entre, effacé par la
     * dernière qui sort ; la sauvegarde en garde la VALEUR, que la destruction qui suit ne touche pas.
     */
    private var retenue by mutableStateOf(retenueInitiale)

    private var occupants by mutableIntStateOf(0)

    /** Une liste occupe la colonne : sa page et la fiche se tiennent côte à côte. */
    val occupee: Boolean get() = occupants > 0

    /** Une liste l'occupait avant la reconstruction de l'écran : la page se recompose à côté de la fiche. */
    val retenueALaReconstruction: Boolean get() = retenue

    /** La fiche ouverte dans la colonne (sa nature, son identifiant) ; `null` : aucune. */
    var ouverte by mutableStateOf<Pair<String, String>?>(null)

    /** Cette fiche est-elle celle de la colonne ? (la rangée qui l'ouvre se souligne). */
    fun estOuverte(nature: String, id: String): Boolean = ouverte == (nature to id)

    /**
     * Le marque-page rouvre une fiche qu'une liste tenait (#242) : comme après une reconstruction,
     * la page se recompose à côté d'elle, et sa liste se redéclare.
     */
    internal fun retenir() {
        retenue = true
    }

    internal fun entrer() {
        occupants++
        retenue = true
    }

    internal fun sortir() {
        occupants--
        if (occupants == 0) retenue = false
    }

    companion object {
        /** Ce que l'écran sauve de la colonne : qu'une liste l'occupait. */
        val Sauvegarde: Saver<ColonneDeFiche, Boolean> = Saver(save = { it.retenue }, restore = { ColonneDeFiche(it) })

        /** La largeur idéale d'une liste (`Mise.listeIdeale` de l'iOS : 360, bornée 300–440). */
        val LISTE_IDEALE = 360.dp
        private val LISTE_MIN = 300.dp
        private val LISTE_MAX = 440.dp

        /** La largeur de la liste dans une fenêtre de cette largeur : jamais plus de la moitié. */
        fun largeurDeListe(fenetre: Dp): Dp = minOf(LISTE_IDEALE.coerceIn(LISTE_MIN, LISTE_MAX), fenetre / 2)
    }
}

/** La colonne de fiche de la page affichée ; `null` : une fenêtre étroite, la fiche se pousse. */
val LocalColonneDeFiche = staticCompositionLocalOf<ColonneDeFiche?> { null }

/** Une liste qui ouvre des fiches le dit : en fenêtre large, sa fiche ira dans la colonne. */
@Composable
fun OccuperLaColonneDeFiche() {
    val colonne = LocalColonneDeFiche.current ?: return
    DisposableEffect(colonne) {
        colonne.entrer()
        onDispose { colonne.sortir() }
    }
}

/**
 * La rangée dont la fiche est ouverte dans la colonne (`.rangeeChoisie` de l'iOS) : soulignée
 * d'un filet de la teinte d'accent, et dite « sélectionnée » au lecteur d'écran.
 */
@Composable
fun Modifier.rangeeChoisie(nature: String, id: String): Modifier {
    val choisie = LocalColonneDeFiche.current?.estOuverte(nature, id) == true
    if (!choisie) return this
    return this
        .border(2.dp, LocalCouleurs.current.primary, RoundedCornerShape(Mise.rayon))
        .semantics { selected = true }
}
