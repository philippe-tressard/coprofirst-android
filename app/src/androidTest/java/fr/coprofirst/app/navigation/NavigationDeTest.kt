// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import fr.coprofirst.app.commun.DELAI_GESTE_MS
import androidx.test.espresso.Espresso
import fr.coprofirst.app.commun.toucherDescription
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte

/**
 * Ouvrir une page de « Plus » (Profil, À propos, une page au-delà de la barre) — LE geste des tests,
 * quelle que soit la présentation (#69) : la barre latérale d'une fenêtre large (`barre.<id>`, la
 * tablette de la CI), sinon « Plus » puis son entrée (`plus.<id>`, le téléphone). Aucun test ne
 * recopie « Plus » puis l'entrée (`verifier-depot.sh`).
 */
fun ComposeTestRule.ouvrirLaPageDePlus(id: String) {
    val laterale = hasTestTag("barre.$id")
    waitUntil(DELAI_GESTE_MS) {
        onAllNodes(laterale).fetchSemanticsNodes().isNotEmpty() || onAllNodes(hasText("Plus")).fetchSemanticsNodes().isNotEmpty()
    }
    if (onAllNodes(laterale).fetchSemanticsNodes().isNotEmpty()) {
        toucherTag("barre.$id")
    } else {
        toucherTexte("Plus")
        toucherTag("plus.$id")
    }
}

/**
 * Refermer la fiche ouverte, quelle que soit la présentation (#69) : la flèche « Retour » d'une
 * fiche poussée (le téléphone, une page sans liste) ; dans la colonne d'une fenêtre large, qui n'a
 * pas de flèche, le geste « retour » du système, que la résidence traduit en fermeture.
 */
fun ComposeTestRule.fermerLaFiche() {
    if (onAllNodes(hasContentDescription("Retour")).fetchSemanticsNodes().isNotEmpty()) {
        toucherDescription("Retour")
    } else {
        Espresso.pressBack()
    }
}
