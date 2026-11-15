// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import java.net.URI

/**
 * Ce qu'ouvre un toucher sur une carte du fil DANS L'APP ANDROID — le pendant de
 * `DestinationDuFil` de l'iOS, qui pousse le détail d'une affaire, la fiche d'un sondage, le
 * document, ou ouvre le résumé en feuille.
 *
 * ⚠️ Divergences déclarées : la fiche d'un sondage s'ouvre EN FEUILLE (`FeuilleSondage`) — l'iOS
 * la pousse ; la navigation commune n'a pas de pile par page (#11). Le détail d'une affaire
 * (menu `mes-demandes`, #16) n'a pas encore d'écran Android : ces cartes restent NON OUVRABLES
 * (ni chevron, ni toucher) jusqu'à lui (#85) ; l'iOS les ouvre.
 */
sealed interface DestinationDuFil {
    /** Le résumé en feuille, et « Voir sur le site » si le site a une page. */
    data object Resume : DestinationDuFil

    /** Le document, ouvert par l'app de l'appareil (`Ouverture.document`). */
    data class Document(val id: Int, val nom: String?) : DestinationDuFil

    /** La fiche du sondage, en feuille (`FeuilleSondage`). */
    data class Sondage(val id: Int) : DestinationDuFil

    companion object {
        /** `null` : la carte ne s'ouvre pas encore sur Android (l'affaire, voir plus haut). */
        fun pour(cible: CibleDuFil): DestinationDuFil? = when (cible) {
            is CibleDuFil.Resume -> Resume
            is CibleDuFil.Document -> Document(cible.id, cible.nom)
            is CibleDuFil.Sondage -> Sondage(cible.id)
            is CibleDuFil.Affaire -> null
        }

        /**
         * La page du site d'un élément, sous l'adresse de la résidence : un chemin servi
         * (`/annonces?…`), ou une adresse absolue du MÊME hôte — jamais un autre site, comme
         * le résumé de l'iOS. `null` sinon.
         */
        fun surLeSite(lien: String?, adresse: String): String? {
            lien ?: return null
            OngletsSurLeSite.lien(lien, adresse)?.let { return it }
            val cible = runCatching { URI(lien) }.getOrNull() ?: return null
            val base = runCatching { URI(adresse) }.getOrNull() ?: return null
            val memeHote = cible.scheme == "https" && cible.rawAuthority != null && cible.rawAuthority == base.rawAuthority
            return lien.takeIf { memeHote }
        }
    }
}
