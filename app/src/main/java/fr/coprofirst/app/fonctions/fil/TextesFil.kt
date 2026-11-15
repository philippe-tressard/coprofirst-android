// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.lecture.enClair
import fr.coprofirst.app.commun.texte.TexteDuSite
import java.time.Instant
import java.util.Locale

/** Les phrases composées du fil, comme l'iOS (`Chronologie.entete`, `CarteDuFil.libelleVocal`, `EcheanceDuFil.enClair`). */
object TextesFil {
    /**
     * L'en-tête d'un jour : « AUJOURD'HUI · DIM. 15 NOV. », « HIER · SAM. 14 NOV. », puis
     * « VEN. 13 NOV. » (l'année s'ajoute hors de l'année en cours).
     *
     * ⚠️ Divergence déclarée : l'iOS écrit le jour et le mois en entier (« DIMANCHE 15 »,
     * « VENDREDI 13 NOVEMBRE ») ; `FormatsDuSite` n'a pas ce format (la couche commune, #15),
     * l'en-tête prend `jourCourt`, le format court du site.
     */
    @Composable
    fun entete(jour: JourDuFil, maintenant: Instant): String {
        val date = FormatsDuSite.jourCourt(jour.elements.first().date, maintenant = maintenant)
        val texte = when (Chronologie.quand(jour.jour, maintenant)) {
            Chronologie.Veille.Aujourdhui -> stringResource(R.string.fil_entete, stringResource(R.string.fil_aujourdhui), date)
            Chronologie.Veille.Hier -> stringResource(R.string.fil_entete, stringResource(R.string.fil_hier), date)
            Chronologie.Veille.Avant -> date
        }
        return texte.uppercase(Locale.FRANCE)
    }

    /** « Urgent » pour une urgence, la rubrique sinon (« Affaire mise à jour »). */
    @Composable
    fun rubrique(element: ElementDuFil): String = if (element.urgent) stringResource(R.string.fil_urgent) else element.nature.libelle

    /** Ce que lit le lecteur d'écran d'une échéance : « J-8, prévu le 17 novembre 2026 ». */
    @Composable
    fun echeance(echeance: EcheanceDuFil): String {
        val quand = echeance.fin?.let {
            stringResource(R.string.fil_prevu_du_au, FormatsDuSite.dateLongue(echeance.debut), FormatsDuSite.dateLongue(it))
        } ?: stringResource(R.string.fil_prevu_le, FormatsDuSite.dateLongue(echeance.debut))
        return listOfNotNull(echeance.libelle, quand).joinToString(", ")
    }

    /** Toute la carte en une phrase, pour le lecteur d'écran : rubrique, titre, nature, heure, échéance, lecture, lieu, texte, pièces. */
    @Composable
    fun carte(element: ElementDuFil): String {
        val morceaux = mutableListOf(rubrique(element), element.titre)
        element.natureEnPastille?.let { morceaux += it.libelle }
        morceaux += FormatsDuSite.heure(element.date)
        element.echeance?.let { morceaux += echeance(it) }
        Lecture.aMontrer(element.meta.lecture)?.let { morceaux += enClair(it) }
        element.meta.lieu?.let { morceaux += enClair(it) }
        element.detail?.let(TexteDuSite::apercu)?.takeIf { it.isNotEmpty() }?.let { morceaux += it }
        element.pieces.enClair?.let { morceaux += it }
        return morceaux.joinToString(", ")
    }

    /** Ce que fait un toucher, dit au lecteur d'écran (`typeVoirLabel` du site). */
    @Composable
    fun action(destination: DestinationDuFil): String = when (destination) {
        DestinationDuFil.Resume -> stringResource(R.string.fil_voir_resume)
        is DestinationDuFil.Document -> stringResource(R.string.pieces_ouvrir_document)
        is DestinationDuFil.Sondage -> stringResource(R.string.fil_voir_sondage)
    }
}
