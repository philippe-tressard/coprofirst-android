// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.ProtectionSession

/**
 * Les écrans de l'entrée, dans leur ordre — ceux de l'app iOS. `Retour` : la dernière
 * résidence, dont la session est gardée sous la biométrie (`RetourView`).
 */
enum class EcranEntree { Accueil, Retour, Connexion, Espace }

/** Ce qu'a rendu « Activer » sur la feuille de la session gardée. */
sealed interface IssueGarde {
    data class Gardee(val protection: ProtectionSession) : IssueGarde

    /** L'appareil n'a pas pu garder la session : le mot de passe, la prochaine fois. */
    data object Echec : IssueGarde
}

/** Ce que montrent les écrans de l'entrée. */
data class EtatEntree(
    val ecran: EcranEntree = EcranEntree.Accueil,
    /** L'adresse saisie dans « Votre résidence ». */
    val saisie: String = "",
    /** La reconnaissance court. */
    val enCours: Boolean = false,
    /** La résidence reconnue à cette adresse — `null` tant qu'aucune ne l'est. */
    val reconnue: Installation? = null,
    /** Pourquoi la dernière reconnaissance n'a pas abouti. */
    val erreur: ErreurService? = null,
    val email: String = "",
    val motDePasse: String = "",
    val voirMotDePasse: Boolean = false,
    /** La connexion court. */
    val connexionEnCours: Boolean = false,
    /** Pourquoi la dernière connexion n'a pas abouti. */
    val erreurConnexion: ErreurService? = null,
    /** La confirmation de l'hôte, ouverte avant un premier mot de passe — `null` sinon. */
    val confirmation: ConfirmationHote? = null,
    /** La résidence où une session est ouverte. */
    val ouverte: Installation? = null,
    /** L'utilisateur a choisi le mot de passe : la connexion ne redemande pas la biométrie. */
    val parMotDePasse: Boolean = false,
    /** La reprise d'une session gardée court (biométrie, puis POST /auth/refresh). */
    val repriseEnCours: Boolean = false,
    /** Pourquoi la dernière reprise n'a pas abouti (réseau…) ; la session reste gardée. */
    val erreurReprise: ErreurService? = null,
    /** La feuille « Garder la session ? », proposée une fois après la première connexion. */
    val proposerGarde: Boolean = false,
    /** Ce qu'a rendu « Activer » ; `null` : la question. */
    val issueGarde: IssueGarde? = null,
    val activationEnCours: Boolean = false,
) {
    /** « Se connecter » répond : une résidence reconnue, un courriel, un mot de passe. */
    val connexionPossible: Boolean
        get() = reconnue != null && email.isNotBlank() && motDePasse.isNotEmpty() && !connexionEnCours
}

/** L'écran de confirmation de l'hôte : l'hôte, puis la preuve qu'il sert. */
data class ConfirmationHote(
    val installation: Installation,
    /** `null` tant que la vérification court. */
    val preuve: PreuveHote? = null,
    val secondAccord: Boolean = false,
) {
    /** « Me connecter à cette adresse » répond : preuve lue, et reconnue ou second accord. */
    val confirmable: Boolean get() = preuve != null && (preuve.reconnue || secondAccord)
}
