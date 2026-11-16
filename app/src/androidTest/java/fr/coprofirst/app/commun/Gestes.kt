// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.toSize
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
import androidx.test.platform.app.InstrumentationRegistry

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
 * - `Column(Modifier.verticalScroll)`, `Row(Modifier.horizontalScroll)` (la barre d'onglets),
 *   et leurs imbrications (une rangée horizontale sous le pli d'une page) : le nœud existe,
 *   hors de la vue — chaque ancêtre défilant le ramène, de l'extérieur vers l'intérieur ;
 * - rien qui défile (`NavigationBar`, une feuille, une confirmation) : rien à faire défiler.
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

/**
 * Fait défiler CHAQUE ancêtre défilant, de l'extérieur vers l'intérieur, jusqu'au nœud : la
 * page jusqu'à la rangée, puis la rangée jusqu'à l'élément. `performScrollTo` ne fait défiler
 * que l'ancêtre le plus proche : une pastille d'une rangée horizontale sous le pli d'une page
 * verticale restait hors de l'écran (Espace CS, #112). Sans ancêtre défilant : rien.
 */
fun SemanticsNodeInteraction.amener(): SemanticsNodeInteraction {
    val ancetres = attendrePlace().ancetresDefilants().map { it.id }
    for (id in ancetres.asReversed()) {
        val noeud = fetchSemanticsNode()
        val ancetre = noeud.ancetresDefilants().firstOrNull { it.id == id } ?: continue
        // La place NON rognée du nœud (`boundsInRoot` est rogné par les ancêtres : hors de
        // la vue, il vaut zéro) contre la partie visible de l'ancêtre.
        val (dx, dy) = ecart(Rect(noeud.positionInRoot, noeud.size.toSize()), ancetre.boundsInRoot)
        if (dx != 0f || dy != 0f) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ancetre.config[SemanticsActions.ScrollBy].action?.invoke(dx, dy)
            }
        }
    }
    // Puis le chemin éprouvé de Compose pour l'ancêtre le plus proche : il cale le nœud
    // exactement (et ne fait rien s'il est déjà visible).
    if (ancetres.isNotEmpty()) performScrollTo()
    return this
}

/**
 * Le nœud, attendu composé ET placé jusqu'au délai commun : il paraît souvent un instant après
 * le geste précédent (une feuille qui s'ouvre, une liste relue). Introuvable : échec qui dit
 * ce que Compose en dit (le sélecteur), au lieu d'un toucher perdu.
 */
private fun SemanticsNodeInteraction.attendrePlace(): SemanticsNode {
    val limite = System.nanoTime() + DELAI_GESTE_MS * 1_000_000
    var raison: Throwable? = null
    while (System.nanoTime() < limite) {
        val noeud = try {
            fetchSemanticsNode()
        } catch (e: AssertionError) {
            raison = e
            null
        }
        if (noeud != null && noeud.layoutInfo.isPlaced) return noeud
        Thread.sleep(PAS_MS)
    }
    throw AssertionError("Toucher : nœud introuvable ou non placé après $DELAI_GESTE_MS ms — ${raison?.message ?: "composé mais non placé"}", raison)
}

/** Les ancêtres qui défilent, du plus proche au plus lointain. */
private fun SemanticsNode.ancetresDefilants(): List<SemanticsNode> =
    generateSequence(parent) { it.parent }.filter { SemanticsActions.ScrollBy in it.config }.toList()

/** Ce qu'il faut faire défiler pour que `cible` entre dans `vue` (par le bord le plus proche). */
private fun ecart(cible: Rect, vue: Rect): Pair<Float, Float> {
    fun axe(debut: Float, fin: Float, vueDebut: Float, vueFin: Float) = when {
        debut < vueDebut -> debut - vueDebut
        fin > vueFin -> minOf(fin - vueFin, debut - vueDebut)
        else -> 0f
    }
    return axe(cible.left, cible.right, vue.left, vue.right) to axe(cible.top, cible.bottom, vue.top, vue.bottom)
}

/** Le geste unique : le clavier fermé, amené à l'écran, vérifié affiché, puis touché. */
fun SemanticsNodeInteraction.toucher(): SemanticsNodeInteraction {
    fermerLeClavier()
    return amener().attendreImmobile().afficheOuDitOu().performClick()
}

/**
 * `assertIsDisplayed`, qui en cas d'échec dit OÙ était le nœud (sa place, celle de ses
 * ancêtres défilants) : un « non affiché » nu ne dit pas s'il était sous le pli, sous une
 * barre ou hors de la fenêtre.
 */
private fun SemanticsNodeInteraction.afficheOuDitOu(): SemanticsNodeInteraction {
    try {
        assertIsDisplayed()
    } catch (e: AssertionError) {
        val noeud = fetchSemanticsNode()
        val place = Rect(noeud.positionInRoot, noeud.size.toSize())
        val ancetres = noeud.ancetresDefilants().joinToString { it.boundsInRoot.toString() }
        // Le premier ancêtre qui coupe le nœud (sa partie visible ne le contient pas) : le coupable.
        val coupe = generateSequence(noeud.parent) { it.parent }.firstOrNull { !it.boundsInRoot.contains(place.center) }
        val visible = noeud.boundsInWindow
        throw AssertionError(
            "${e.message} — nœud $place (non rogné), visible $visible, placé ${noeud.layoutInfo.isPlaced}, " +
                "ancêtres défilants visibles [$ancetres], coupé par ${coupe?.let { "${it.config} ${it.boundsInRoot}" } ?: "aucun"}",
            e,
        )
    }
    return this
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

/**
 * Ferme le clavier s'il est ouvert, et attend qu'il le soit — SANS « Retour ». Le clavier se
 * voit par sa fenêtre (UiAutomation), sans attendre qu'une fenêtre de l'app ait le focus
 * (`Espresso.closeSoftKeyboard` attendait 10 s une feuille qui ne le prend jamais). Il se
 * cache par `InputMethodManager` sur la fenêtre qui a le focus (`WindowInspector`) : un
 * « Retour » envoyé pendant que le clavier se refermait atteignait l'app, refermait la
 * feuille ou quittait l'écran (rouges intermittents, #119).
 */
private fun fermerLeClavier() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automate = instrumentation.uiAutomation
    automate.serviceInfo = automate.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    fun ouvert() = automate.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    // WindowInspector date de l'API 29 ; les tests tournent sur l'API 35 (CI et poste).
    if (!ouvert() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    instrumentation.runOnMainSync {
        val saisie = instrumentation.targetContext.getSystemService(InputMethodManager::class.java)
        WindowInspector.getGlobalWindowViews().filter { it.hasWindowFocus() }.forEach { vue ->
            saisie.hideSoftInputFromWindow(vue.windowToken, 0)
        }
    }
    val limite = System.nanoTime() + 2_000_000_000L
    while (ouvert() && System.nanoTime() < limite) Thread.sleep(50)
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
    // « Existe » = composé ET placé : une liste paresseuse garde composée une ligne qu'elle ne
    // place plus (sa dernière position, hors de la vue réelle) — elle se fait alors venir par
    // la liste, comme une ligne non composée (Profil, « Se déconnecter », #126).
    fun existe() = onAllNodes(critere, useUnmergedTree).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }
    val limite = System.nanoTime() + delaiMs * 1_000_000
    while (!existe()) {
        val listes = onAllNodes(hasScrollToNodeAction(), useUnmergedTree)
        for (i in listes.fetchSemanticsNodes().indices) {
            runCatching { listes[i].performScrollToNode(critere) }
            if (existe()) break
        }
        if (existe()) break
        if (System.nanoTime() > limite) {
            throw AssertionError("Toucher : aucun nœud « ${critere.description} » placé à l'écran ni dans une liste, après $delaiMs ms.")
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

/**
 * Attend qu'un nœud réponde au critère — LA seule attente des tests d'interface (#139) : un
 * test ne réécrit pas `waitUntil { onAllNodes(…) }` dans une aide privée, `verifier-depot.sh`
 * le refuse. `useUnmergedTree` : un repère posé sous un nœud fusionné.
 */
fun ComposeTestRule.attendre(critere: SemanticsMatcher, useUnmergedTree: Boolean = false, delaiMs: Long = DELAI_GESTE_MS) =
    waitUntil(delaiMs) { onAllNodes(critere, useUnmergedTree).fetchSemanticsNodes().isNotEmpty() }

/** Attend qu'aucun nœud ne réponde plus au critère : un élément retiré, une feuille fermée. */
fun ComposeTestRule.attendreDisparition(critere: SemanticsMatcher, useUnmergedTree: Boolean = false, delaiMs: Long = DELAI_GESTE_MS) =
    waitUntil(delaiMs) { onAllNodes(critere, useUnmergedTree).fetchSemanticsNodes().isEmpty() }

/** Par repère ; `texte` : le repère, et ce texte en lui (une partie suffit). */
fun ComposeTestRule.attendreTag(
    repere: String,
    texte: String? = null,
    useUnmergedTree: Boolean = false,
    delaiMs: Long = DELAI_GESTE_MS,
) = attendre(if (texte == null) hasTestTag(repere) else hasTestTag(repere) and hasText(texte, substring = true), useUnmergedTree, delaiMs)

fun ComposeTestRule.attendreDisparitionTag(repere: String, useUnmergedTree: Boolean = false) =
    attendreDisparition(hasTestTag(repere), useUnmergedTree)

/** Attend qu'un texte affiché apparaisse (une partie suffit). */
fun ComposeTestRule.attendreTexte(texte: String, delaiMs: Long = DELAI_GESTE_MS, useUnmergedTree: Boolean = false) =
    attendre(hasText(texte, substring = true), useUnmergedTree, delaiMs)

/** Attend une description (une carte lue d'un bloc, une icône, un document qui la porte). */
fun ComposeTestRule.attendreDescription(texte: String) = attendre(hasContentDescription(texte, substring = true))

/** Un texte affiché, ou la description d'un élément qui le remplace (`LigneDocument`). */
fun ComposeTestRule.attendreTexteOuDescription(texte: String) =
    attendre(hasText(texte, substring = true) or hasContentDescription(texte, substring = true))

/**
 * Attend une condition nommée : l'échec dit ce qu'on attendait, `contexte` ce que le test en
 * sait, et l'arbre de l'écran — le rapport de la CI le garde.
 */
fun ComposeTestRule.attendreQue(quoi: String, delaiMs: Long = DELAI_GESTE_MS, contexte: () -> String = { "" }, condition: () -> Boolean) {
    try {
        waitUntil(delaiMs, condition)
    } catch (e: ComposeTimeoutException) {
        val dit = contexte().takeIf { it.isNotEmpty() }?.let { " ; $it" }.orEmpty()
        throw AssertionError("$quoi : non vu en $delaiMs ms$dit\n${onRoot().printToString(maxDepth = Int.MAX_VALUE)}", e)
    }
}
