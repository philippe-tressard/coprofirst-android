// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.fil.CibleDuFil
import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import java.net.URI

/**
 * Ce qu'ouvre un toucher sur une carte du fil DANS L'APP ANDROID — le pendant de
 * `DestinationDuFil` de l'iOS, qui pousse le détail d'une affaire, la fiche d'un sondage, le
 * document, ou ouvre le résumé en feuille.
 *
 * L'affaire et le sondage s'ouvrent par la porte (`Destination.Element`, #85), poussés comme
 * sur l'iOS — si leur menu a déclaré sa fiche (`RegistreDesMenus.fiches`). ⚠️ Divergence
 * déclarée : tant que Mes affaires (#16) n'a pas déclaré la sienne, une carte d'affaire reste
 * NON OUVRABLE (ni chevron, ni toucher) ; l'iOS l'ouvre.
 */
sealed interface DestinationDuFil {
    /** Le résumé en feuille, et « Voir sur le site » si le site a une page. */
    data object Resume : DestinationDuFil

    /** Le document, ouvert par l'app de l'appareil (`Ouverture.document`). */
    data class Document(val id: Int, val nom: String?) : DestinationDuFil

    /** La fiche d'un élément, par la porte : sa nature (`affaire`, `sondage`) et son identifiant. */
    data class Element(val nature: String, val id: Int) : DestinationDuFil

    companion object {
        const val AFFAIRE = "affaire"
        const val SONDAGE = "sondage"

        /**
         * @param fiches les natures dont un menu a déclaré la fiche (`RegistreDesMenus.fiches`).
         * `null` : la carte ne s'ouvre pas sur Android (voir plus haut).
         */
        fun pour(cible: CibleDuFil, fiches: Set<String>): DestinationDuFil? = when (cible) {
            is CibleDuFil.Resume -> Resume
            is CibleDuFil.Document -> Document(cible.id, cible.nom)
            is CibleDuFil.Sondage -> Element(SONDAGE, cible.id).takeIf { SONDAGE in fiches }
            is CibleDuFil.Affaire -> Element(AFFAIRE, cible.id).takeIf { AFFAIRE in fiches }
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

/** Ce que fait un toucher, dit au lecteur d'écran (`typeVoirLabel` du site). */
@Composable
fun actionDuFil(destination: DestinationDuFil): String = when (destination) {
    DestinationDuFil.Resume -> stringResource(R.string.fil_voir_resume)
    is DestinationDuFil.Document -> stringResource(R.string.pieces_ouvrir_document)
    is DestinationDuFil.Element ->
        stringResource(if (destination.nature == DestinationDuFil.SONDAGE) R.string.fil_voir_sondage else R.string.fil_voir_affaire)
}
