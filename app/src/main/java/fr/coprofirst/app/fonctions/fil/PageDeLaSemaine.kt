// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.animationsCoupees
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.fil.ElementDuFil
import fr.coprofirst.app.commun.fil.JourDuFil
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.miseenpage.DoublePage
import fr.coprofirst.app.navigation.LocalPosture
import fr.coprofirst.app.navigation.coupe
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * La semaine du carnet (#286), comme `PageDeLaSemaine` de l'iOS (coprofirst-ios#277) : ouverte
 * d'un pincement sur la page du jour, sur TOUTE la double page — du lundi au jeudi à gauche du
 * pli, du vendredi au dimanche à droite, chaque carte au jour que l'API lui sert
 * (`GET /flux?du=&au=`). Un jour vide : « Rien ce jour ». Écarter les doigts sur un jour, ou
 * toucher son en-tête, rouvre ce jour ; une carte touchée s'ouvre sur la page de son jour. On
 * tourne les semaines au pied de page ou d'un balayage, jamais avant celle du plus ancien jour du fil.
 *
 * @param jours le fil, pour la borne de la semaine précédente.
 */
@Composable
internal fun PageDeLaSemaine(
    lundi: LocalDate,
    jours: List<JourDuFil>,
    lire: suspend (LocalDate) -> List<ElementDuFil>,
    carte: @Composable (ElementDuFil, LocalDate, Modifier) -> Unit,
    changer: (LocalDate) -> Unit,
    ouvrirLeJour: (LocalDate) -> Unit,
) {
    val precedente = SemaineDuFil.precedente(lundi, jours)?.let { { changer(it) } }
    val suivante = { changer(SemaineDuFil.suivante(lundi)) }
    val fige = animationsCoupees()
    val coupe = LocalPosture.current.coupe()
    Column(Modifier.fillMaxSize().testTag(REPERE_SEMAINE)) {
        Box(Modifier.weight(1f).tournerLaPage(lundi.toString(), precedente, suivante)) {
            Chargement(charger = { lire(lundi) }, cle = lundi, squelette = { Box(Modifier.fillMaxSize()) }) { elements, _ ->
                val semaine = remember(elements, lundi) { SemaineDuFil.ranger(lundi, elements) }
                val colonne: @Composable (List<JourDeLaSemaine>, Boolean) -> Unit = { jourS, titre ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (titre) TitreDeLaSemaine(lundi)
                        jourS.forEach { JourDeSemaine(it, fige, carte, ouvrirLeJour) }
                    }
                }
                DoublePage(
                    coupe,
                    gauche = { colonne(semaine.take(SemaineDuFil.A_GAUCHE), true) },
                    droite = { colonne(semaine.drop(SemaineDuFil.A_GAUCHE), false) },
                )
            }
        }
        PiedDeFeuillet(
            stringResource(R.string.carnet_semaine_precedente), precedente, "$REPERE_SEMAINE.precedente",
            stringResource(R.string.carnet_semaine_suivante), suivante, "$REPERE_SEMAINE.suivante",
        )
    }
}

/** « Semaine du 16 novembre 2026 » (la date longue du site), en titre. */
@Composable
private fun TitreDeLaSemaine(lundi: LocalDate) {
    Text(
        stringResource(R.string.carnet_semaine_du, FormatsDuSite.dateLongue(midi(lundi))),
        Modifier.padding(horizontal = Mise.marge).semantics { heading() },
        style = Typo.titre(),
        color = LocalCouleurs.current.text,
    )
}

/**
 * Un jour de la semaine : son en-tête (« LUN. 16 NOV. »), qui le rouvre d'un toucher — « Ouvrir ce
 * jour » au lecteur d'écran —, puis ses cartes ; écarter les doigts dessus le rouvre aussi.
 */
@Composable
private fun JourDeSemaine(
    jour: JourDeLaSemaine,
    fige: Boolean,
    carte: @Composable (ElementDuFil, LocalDate, Modifier) -> Unit,
    ouvrirLeJour: (LocalDate) -> Unit,
) {
    val couleurs = LocalCouleurs.current
    Column(
        Modifier.fillMaxWidth().zoomDuCarnet(ZoomDuCarnet.Sens.Ecarter, fige) { ouvrirLeJour(jour.jour) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Libelle(
            FormatsDuSite.jourCourt(midi(jour.jour)).uppercase(Locale.FRANCE),
            Modifier
                .fillMaxWidth()
                .heightIn(min = Mise.cible)
                .cliquable(stringResource(R.string.carnet_ouvrir_ce_jour)) { ouvrirLeJour(jour.jour) }
                .padding(horizontal = Mise.marge, vertical = 12.dp)
                .semantics { heading() }
                .testTag("$REPERE_SEMAINE.${jour.jour}"),
        )
        if (jour.elements.isEmpty()) {
            Text(
                stringResource(R.string.carnet_rien_ce_jour),
                Modifier.padding(horizontal = Mise.marge),
                style = MaterialTheme.typography.bodyMedium,
                color = couleurs.textMuted,
            )
        }
        jour.elements.forEach { element -> carte(element, jour.jour, Modifier.padding(horizontal = Mise.marge)) }
    }
}

/** Le midi d'un jour, à Paris : l'instant que les formats du site attendent. */
private fun midi(jour: LocalDate): Instant = jour.atTime(12, 0).atZone(FormatsDuSite.PARIS).toInstant()

/** Le repère de la semaine (`carnet.semaine` de l'iOS) ; ses jours : `carnet.semaine.<AAAA-MM-JJ>`. */
const val REPERE_SEMAINE = "carnet.semaine"

/** Le repère de la page du jour du carnet (`carnet.jour` de l'iOS). */
const val REPERE_JOUR = "carnet.jour"

