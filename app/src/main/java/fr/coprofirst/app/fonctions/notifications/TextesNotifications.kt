// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.R

/** Les phrases composées de l'écran, comme `TextesNotifications` de l'iOS. */
object TextesNotifications {
    /** « 1 non lue », « 3 non lues ». */
    @Composable
    fun nonLues(nombre: Int): String = pluralStringResource(R.plurals.notifications_non_lues, nombre, nombre)

    /** « Supprimer », puis « Supprimer (3) » dès qu'une sélection existe. */
    @Composable
    fun supprimerSelection(nombre: Int): String =
        if (nombre == 0) stringResource(R.string.notifications_supprimer) else stringResource(R.string.notifications_supprimer_nombre, nombre)

    /** « Cette notification », « Ces 3 notifications », « Les 138 notifications lues ». */
    @Composable
    fun quoi(demande: SuppressionDemandee): String = when (demande) {
        is SuppressionDemandee.Une -> stringResource(R.string.notifications_quoi_une)
        is SuppressionDemandee.Selection ->
            if (demande.ids.size > 1) stringResource(R.string.notifications_quoi_selection, demande.ids.size)
            else stringResource(R.string.notifications_quoi_une)
        is SuppressionDemandee.Lues ->
            if (demande.nombre > 1) stringResource(R.string.notifications_quoi_lues, demande.nombre)
            else stringResource(R.string.notifications_quoi_une_lue)
    }

    /** Le message du site (`SUPPRESSION(quoi)`) : ce qui part, puis l'avertissement. */
    @Composable
    fun message(demande: SuppressionDemandee): String = quoi(demande) + "\n\n" + stringResource(R.string.notifications_irreversible)

    /** Le bouton qui confirme : « Supprimer », « Supprimer (3) » pour une sélection. */
    @Composable
    fun libelle(demande: SuppressionDemandee): String =
        if (demande is SuppressionDemandee.Selection) supprimerSelection(demande.ids.size) else stringResource(R.string.notifications_supprimer)

    /**
     * « 500 notifications sur 1200 ont été supprimées ; les 700 autres sont toujours là. »
     * La phrase de la raison suit, dite par `MessageErreur`.
     */
    @Composable
    fun interrompue(e: SuppressionInterrompue): String {
        val parties = e.parties.size
        val reste = e.demandees - parties
        val fait = pluralStringResource(R.plurals.notifications_interrompue_fait, parties, parties, e.demandees)
        val demeure = if (reste > 1) stringResource(R.string.notifications_interrompue_reste, reste)
        else stringResource(R.string.notifications_interrompue_reste_un)
        return stringResource(R.string.notifications_interrompue, fait, demeure)
    }
}
