// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.navigation.ConnexionFraiche
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.OrigineAdresse
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.ProtectionSession
import java.time.Instant

/**
 * Les écrans de l'entrée, dans leur ordre — ceux de l'app iOS (le sens du passage en
 * dépend). `Retour` : la dernière résidence, dont la session est gardée sous la biométrie
 * (`RetourView`) — une racine, comme l'accueil.
 */
enum class EcranEntree { Accueil, Retour, EnSavoirPlus, Demo, Connexion, Espace }

/** Ce qu'a rendu « Activer » sur la feuille de la session gardée. */
sealed interface IssueGarde {
    data class Gardee(val protection: ProtectionSession) : IssueGarde

    /** L'appareil n'a pas pu garder la session : le mot de passe, la prochaine fois. */
    data object Echec : IssueGarde
}

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
    /** Elle s'est ouverte par le mot de passe : le compte lu avant (#391) ; `null` par une autre voie. */
    val connexion: ConnexionFraiche? = null,
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
    /**
     * La résidence est ouverte SANS réseau, sur sa dernière lecture gardée, à jour à cette
     * heure (#28) : on consulte, on n'agit pas. `null` : en ligne.
     */
    val consultation: Instant? = null,
    /** Sans réseau, la lecture gardée était trop ancienne : elle vient d'être effacée. */
    val lecturePerimee: Boolean = false,
    /** L'adresse lue dans un QR ou venue d'un lien, et d'où : elle se confirme TOUJOURS. */
    val venue: AdresseVenue? = null,
    /** L'écran qu'un lien d'application vise, en attente que sa résidence soit ouverte (#147). */
    val lien: LienEnAttente? = null,
    /** Une sortie dont le serveur n'a pas été joint (#177), dite sur la connexion jusqu'à la suivante. */
    val avisSortie: AvisSortie? = null,
) {
    /** Vers cet écran, celui-ci gardé pour le retour. */
    fun vers(suivant: EcranEntree): EtatEntree = copy(ecran = suivant, pile = pile + ecran)

    /** « Se connecter » répond : une résidence reconnue, un courriel, un mot de passe. */
    val connexionPossible: Boolean
        get() = reconnue != null && email.isNotBlank() && motDePasse.isNotEmpty() && !connexionEnCours
}

/** Ce que la connexion dit d'une sortie sans réponse du serveur (`Parcours.avis` de l'iOS). */
enum class AvisSortie {
    /** « Se déconnecter » : la résidence n'a pas répondu. */
    SessionInjoignable,

    /** « Oublier toutes les résidences » : certaines n'ont pas répondu. */
    ResidencesInjoignables,
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
