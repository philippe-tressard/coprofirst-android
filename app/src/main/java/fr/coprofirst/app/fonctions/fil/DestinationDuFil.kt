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
 * ⚠️ Divergence déclarée (#11) : le détail d'une affaire (menu `mes-demandes`, #16) et la
 * fiche d'un sondage (menu `communaute`, #20) n'ont pas encore d'écran Android, et la
 * navigation commune ne sait pas ouvrir une autre page de la barre — encore moins un élément
 * précis — depuis un écran (`ContexteDeMenu` ne porte aucun geste de navigation). Ces cartes
 * restent donc NON OUVRABLES (ni chevron, ni toucher) jusqu'à ce que ces écrans et ce geste
 * existent ; l'iOS les ouvre toutes.
 */
sealed interface DestinationDuFil {
    /** Le résumé en feuille, et « Voir sur le site » si le site a une page. */
    data object Resume : DestinationDuFil

    /** Le document, ouvert par l'app de l'appareil (`Ouverture.document`). */
    data class Document(val id: Int, val nom: String?) : DestinationDuFil

    companion object {
        /** `null` : la carte ne s'ouvre pas encore sur Android (voir plus haut). */
        fun pour(cible: CibleDuFil): DestinationDuFil? = when (cible) {
            is CibleDuFil.Resume -> Resume
            is CibleDuFil.Document -> Document(cible.id, cible.nom)
            is CibleDuFil.Affaire, is CibleDuFil.Sondage -> null
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
