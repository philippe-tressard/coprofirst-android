// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

/** D'où vient l'adresse de la résidence : un QR collé dans un hall peut mener à un imitateur. */
enum class OrigineAdresse {
    /** Saisie, ou pré-remplie depuis une résidence connue. */
    Saisie,

    /** Lue dans un QR code (#6). */
    Scan,

    /** Venue d'un lien (#7) : n'importe quelle page peut en tendre un. */
    Lien,
}

/**
 * La garde du mot de passe, en un seul endroit, comme l'iOS (`GardeHote`) : avant le
 * premier mot de passe envoyé à un hôte JAMAIS VU, l'écran de confirmation montre
 * l'hôte en entier et la preuve qu'il sert. Toujours après un QR ou un lien ; pour une
 * saisie, quand l'hôte n'est ni une résidence connue (une connexion y a déjà réussi)
 * ni la démonstration. Une confirmation vit en mémoire, le temps du lancement.
 *
 * @param connues les résidences où une connexion a déjà réussi sur cet appareil.
 */
class GardeHote(private val connues: () -> List<Installation>) {
    private val confirmes = mutableSetOf<AdresseInstallation>()

    /** Faut-il l'écran de confirmation avant d'envoyer un mot de passe à cette adresse ? */
    fun exigeConfirmation(adresse: AdresseInstallation, origine: OrigineAdresse): Boolean {
        if (adresse in confirmes) return false
        // Un QR ou un lien : l'adresse n'a pas été écrite par l'utilisateur.
        if (origine != OrigineAdresse.Saisie) return true
        return adresse != AdresseInstallation.DEMO && connues().none { it.adresse == adresse }
    }

    /**
     * « Me connecter à cette adresse ». Refusé sans preuve lue ; sans preuve reconnue,
     * refusé sans le second accord. Rend `true` si l'hôte est confirmé.
     */
    fun confirmer(adresse: AdresseInstallation, preuve: PreuveHote?, secondAccord: Boolean): Boolean {
        if (preuve == null) return false
        if (!preuve.reconnue && !secondAccord) return false
        confirmes += adresse
        return true
    }
}
