// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException

/*
 * LE geste des tests d'interface : un toucher ne s'injecte qu'à l'écran. Un élément hors de
 * l'écran (l'onglet au bout d'une barre qui défile, le bas d'une page en `verticalScroll`, la
 * ligne d'une liste paresseuse pas encore composée) recevait un `performClick()` perdu, et le
 * test tombait plus loin, sans dire pourquoi (trois chutes le même soir, #119). Ici seul on
 * touche : `verifier-depot.sh` refuse `performClick` partout ailleurs dans app/src/androidTest.
 *
 * Les trois cas :
 * - `LazyColumn` / `LazyRow` : la ligne n'existe pas tant qu'elle n'est pas composée —
 *   `performScrollToNode` sur chaque liste (`hasScrollToNodeAction`) la fait composer ;
 * - `Column(Modifier.verticalScroll)`, `Row(Modifier.horizontalScroll)` (la barre d'onglets) :
 *   le nœud existe, hors de la vue — `performScrollTo` le ramène ;
 * - rien qui défile (`NavigationBar`, une feuille, une confirmation) : `performScrollTo`
 *   échouerait (« no parent layout with a Scroll SemanticsAction ») — il est sauté.
 * Puis `assertIsDisplayed()` : un nœud que rien n'a pu ramener à l'écran échoue ICI, en
 * disant lequel, au lieu d'un toucher perdu.
 *
 * Et d'abord, le CLAVIER se ferme : juste après une frappe, le bouton au pied d'une feuille
 * est sous lui — le test le croit affiché (la sémantique ignore le clavier) et le toucher
 * tombe sur le clavier (la cause la plus fréquente des rouges du 16/11, #113).
 *
 * Et enfin, le nœud doit être IMMOBILE : une page qui se met en place à l'arrivée d'une
 * donnée servie le déplace, et un toucher calculé sur l'ancienne position tombe à côté
 * (Espace CS, #112). Le geste attend que sa position soit la même deux lectures de suite.
 * L'effet du toucher, lui, se vérifie dans le test (« pastille retenue ») : il dépend de lui.
 */

/** Le temps qu'un nœud a pour paraître (chargement servi, animation) : celui des tests. */
const val DELAI_GESTE_MS = 10_000L

/** Le pas d'attente entre deux recherches, quand le nœud n'est pas encore là. */
private const val PAS_MS = 250L

/** Fait défiler jusqu'au nœud s'il est dans un conteneur qui défile ; sinon, rien. */
fun SemanticsNodeInteraction.amener(): SemanticsNodeInteraction {
    if (fetchSemanticsNode("Le nœud à amener à l'écran est introuvable.").aUnAncetreDefilant()) performScrollTo()
    return this
}

/** Le geste unique : le clavier fermé, amené à l'écran, vérifié affiché, puis touché. */
fun SemanticsNodeInteraction.toucher(): SemanticsNodeInteraction {
    fermerLeClavier()
    return amener().attendreImmobile().assertIsDisplayed().performClick()
}

/** Le temps qu'un nœud a pour cesser de bouger, et le pas entre deux lectures de sa position. */
private const val IMMOBILE_MS = 3_000L
private const val PAS_IMMOBILE_MS = 100L

/**
 * Attend que la position du nœud soit la même deux lectures de suite (chaque lecture attend
 * que l'interface soit au repos). Toujours en mouvement au bout de [IMMOBILE_MS] : échec qui
 * le dit, plutôt qu'un toucher à côté.
 */
private fun SemanticsNodeInteraction.attendreImmobile(): SemanticsNodeInteraction {
    val limite = System.nanoTime() + IMMOBILE_MS * 1_000_000
    var avant = fetchSemanticsNode().boundsInRoot
    while (true) {
        Thread.sleep(PAS_IMMOBILE_MS)
        val apres = fetchSemanticsNode().boundsInRoot
        if (apres == avant) return this
        check(System.nanoTime() < limite) { "Toucher : le nœud bouge encore après $IMMOBILE_MS ms ($avant → $apres)." }
        avant = apres
    }
}

/** Ferme le clavier s'il est ouvert, et attend qu'il le soit ; sans activité au premier plan, rien. */
private fun fermerLeClavier() {
    try {
        Espresso.closeSoftKeyboard()
    } catch (_: NoActivityResumedException) {
        // Pas d'écran au premier plan : pas de clavier non plus.
    }
}

private fun SemanticsNode.aUnAncetreDefilant(): Boolean {
    var noeud = parent
    while (noeud != null) {
        if (SemanticsActions.ScrollBy in noeud.config) return true
        noeud = noeud.parent
    }
    return false
}

/**
 * Trouve le nœud, l'attend s'il n'est pas encore là, et le fait composer s'il est dans une
 * liste paresseuse (chaque liste est essayée) ; puis l'amène à l'écran. Introuvable au bout
 * de [delaiMs] : échec qui nomme le critère.
 */
fun ComposeTestRule.amener(
    critere: SemanticsMatcher,
    useUnmergedTree: Boolean = false,
    delaiMs: Long = DELAI_GESTE_MS,
): SemanticsNodeInteraction {
    fun existe() = onAllNodes(critere, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
    val limite = System.nanoTime() + delaiMs * 1_000_000
    while (!existe()) {
        val listes = onAllNodes(hasScrollToNodeAction(), useUnmergedTree)
        for (i in listes.fetchSemanticsNodes().indices) {
            runCatching { listes[i].performScrollToNode(critere) }
            if (existe()) break
        }
        if (existe()) break
        if (System.nanoTime() > limite) {
            throw AssertionError("Toucher : aucun nœud « ${critere.description} » à l'écran ni dans une liste, après $delaiMs ms.")
        }
        runCatching { waitUntil(PAS_MS) { existe() } }
    }
    return onNode(critere, useUnmergedTree).amener()
}

/** Le geste unique, par critère : trouvé ([amener]), affiché, touché. */
fun ComposeTestRule.toucher(
    critere: SemanticsMatcher,
    useUnmergedTree: Boolean = false,
    delaiMs: Long = DELAI_GESTE_MS,
): SemanticsNodeInteraction {
    // Le clavier fermé AVANT de chercher : la page se remet en place, le nœud aussi.
    fermerLeClavier()
    waitForIdle()
    return amener(critere, useUnmergedTree, delaiMs).toucher()
}

/** Par repère (`testTag`, les identifiants d'accessibilité de l'iOS). */
fun ComposeTestRule.amenerTag(repere: String, useUnmergedTree: Boolean = false) = amener(hasTestTag(repere), useUnmergedTree)

fun ComposeTestRule.toucherTag(repere: String, useUnmergedTree: Boolean = false) = toucher(hasTestTag(repere), useUnmergedTree)

/** Par texte affiché. */
fun ComposeTestRule.toucherTexte(texte: String, substring: Boolean = false) = toucher(hasText(texte, substring))

/** Par description (une icône sans texte : « Retour »). */
fun ComposeTestRule.toucherDescription(description: String) = toucher(hasContentDescription(description))
