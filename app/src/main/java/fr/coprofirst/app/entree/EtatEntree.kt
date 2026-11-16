// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.OrigineAdresse
import fr.coprofirst.app.session.PreuveHote

/** Les écrans de l'entrée, dans leur ordre — ceux de l'app iOS. */
enum class EcranEntree { Accueil, Connexion, Espace }

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
    /** L'adresse lue dans un QR ou venue d'un lien, et d'où : elle se confirme TOUJOURS. */
    val venue: AdresseVenue? = null,
    /** L'écran qu'un lien d'application vise, en attente que sa résidence soit ouverte (#147). */
    val lien: LienEnAttente? = null,
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

/** Une adresse que l'utilisateur n'a pas écrite (QR, lien) et son origine, pour la garde de l'hôte. */
data class AdresseVenue(val adresse: AdresseInstallation, val origine: OrigineAdresse)

/**
 * L'écran qu'un lien vise dans SA résidence (`Parcours.porte.enAttente` de l'iOS) : il ne se
 * pose que dans la résidence à cette adresse — celui d'une autre résidence tombe.
 */
data class LienEnAttente(val adresse: AdresseInstallation, val destination: Destination)
