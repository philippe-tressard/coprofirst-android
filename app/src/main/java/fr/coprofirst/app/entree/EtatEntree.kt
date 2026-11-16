// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.OrigineAdresse
import fr.coprofirst.app.session.PreuveHote

/** Les écrans de l'entrée, dans leur ordre — ceux de l'app iOS (le sens du passage en dépend). */
enum class EcranEntree { Accueil, EnSavoirPlus, Demo, Connexion, Espace }

/** Ce que montrent les écrans de l'entrée. */
data class EtatEntree(
    val ecran: EcranEntree = EcranEntree.Accueil,
    /** Les écrans sous celui-ci, que le retour retrouve (la pile de navigation de l'iOS). */
    val pile: List<EcranEntree> = emptyList(),
    /** Le rôle de la démo choisi (« Conseil syndical ») : où lire son mot de passe public. */
    val aideDemo: String? = null,
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
) {
    /** Vers cet écran, celui-ci gardé pour le retour. */
    fun vers(suivant: EcranEntree): EtatEntree = copy(ecran = suivant, pile = pile + ecran)

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
