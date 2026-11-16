// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte

/**
 * L'entrée dans une résidence, de l'accueil à « Me connecter à cette adresse », écrite une
 * fois pour les tests d'interface d'un écran. Les réponses du serveur restent à l'appelant
 * (file ou aiguillage de `ServeurDeTest`) ; `avantConnexion` sert celles de la connexion
 * quand elles se mettent en file.
 */
fun ComposeTestRule.entrerDansLaResidence(adresse: String, nom: String, avantConnexion: () -> Unit = {}) {
    toucherTexte("Entrer dans ma résidence")
    onNodeWithTag(Reperes.RESIDENCE).performTextInput(adresse)
    attendreTexte(nom)
    onNodeWithTag(Reperes.EMAIL).performTextInput("camille@exemple.test")
    onNodeWithTag(Reperes.MOT_DE_PASSE).performTextInput("secret")
    toucherTag(Reperes.VALIDER)
    attendreTexte("Me connecter à cette adresse")
    avantConnexion()
    toucherTexte("Me connecter à cette adresse")
}
