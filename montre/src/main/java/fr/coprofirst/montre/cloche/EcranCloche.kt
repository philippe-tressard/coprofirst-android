// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.cloche

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import fr.coprofirst.app.charte.TracesDuSite
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.app.fonctions.notifications.TypesNotification
import fr.coprofirst.montre.R
import fr.coprofirst.app.partage.R as RCommun
import java.time.Instant

/**
 * La liste de la cloche : en tête le nom de la résidence, puis les non lues (urgentes
 * devant), puis « Déjà lues », repliée. Un toucher ouvre le détail ; rien ne se marque
 * comme lu (décision de Philippe, jeton en lecture seule).
 */
@Composable
fun EcranCloche(residence: String, lecture: LectureCloche, ouvrir: (Int) -> Unit) {
    val etat = rememberTransformingLazyColumnState()
    var luesOuvertes by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(scrollState = etat) { marges ->
        TransformingLazyColumn(state = etat, contentPadding = marges, modifier = Modifier.testTag("montre.cloche")) {
            // Le repère va sur l'en-tête : il fusionne ses enfants, un repère posé sur le texte s'y perd.
            item { ListHeader(modifier = Modifier.testTag("montre.residence")) { Text(residence, textAlign = TextAlign.Center) } }
            when (lecture) {
                LectureCloche.EnCours -> item { Chargement() }
                LectureCloche.Injoignable -> item { Message(stringResource(R.string.montre_injoignable), "montre.injoignable") }
                is LectureCloche.Lue -> cloche(lecture.cloche, luesOuvertes, { luesOuvertes = !luesOuvertes }, ouvrir)
                is LectureCloche.HorsLigne -> {
                    item { HorsLigneDate(lecture.aJourAu) }
                    cloche(lecture.cloche, luesOuvertes, { luesOuvertes = !luesOuvertes }, ouvrir)
                }
            }
        }
    }
}

private fun TransformingLazyColumnScope.cloche(cloche: Cloche, luesOuvertes: Boolean, basculer: () -> Unit, ouvrir: (Int) -> Unit) {
    if (cloche.nonLues.isEmpty()) {
        item { Vide() }
    }
    items(cloche.nonLues.size, key = { cloche.nonLues[it].id }) { LigneAlerte(cloche.nonLues[it], ouvrir) }
    if (cloche.lues.isEmpty()) return
    item { BoutonDejaLues(cloche.lues.size, luesOuvertes, basculer) }
    if (luesOuvertes) {
        items(cloche.lues.size, key = { cloche.lues[it].id }) { LigneAlerte(cloche.lues[it], ouvrir) }
    }
}

/** Une ligne : le titre sur deux lignes au plus, l'âge relatif, la marque d'urgence. */
@Composable
private fun LigneAlerte(alerte: NotificationRecue, ouvrir: (Int) -> Unit) {
    val urgent = stringResource(R.string.montre_urgent)
    TitleCard(
        onClick = { ouvrir(alerte.id) },
        title = {
            IconeDuType(alerte.type)
            Text(alerte.titre, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        time = { Text(FormatsDuSite.relatif(alerte.creeLe)) },
        subtitle = if (alerte.urgente) {
            // La marque de danger de la charte (ThemeMontre : `error` = `danger`).
            { Text(urgent, color = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth().testTag("montre.alerte.${alerte.id}"),
    )
}

/**
 * L'icône du type d'alerte : la table publiée par coprofirst (`TypesNotification`, #2802) et les
 * tracés du site (`TracesDuSite`), ceux du téléphone. Son libellé sert au lecteur d'écran.
 */
@Composable
private fun IconeDuType(type: String) {
    val decrit = TypesNotification.de(type)
    val trace = TracesDuSite.icone(decrit.pictogramme) ?: return
    Icon(
        trace,
        contentDescription = decrit.libelle,
        // La teinte du téléphone (#252) : textMuted de la charte (ThemeMontre : onSurfaceVariant).
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 6.dp).size(18.dp).testTag("montre.type.$type"),
    )
}

@Composable
private fun BoutonDejaLues(nombre: Int, ouvertes: Boolean, basculer: () -> Unit) {
    val etat = stringResource(if (ouvertes) R.string.montre_deja_lues_ouvertes else R.string.montre_deja_lues_repliees)
    FilledTonalButton(
        onClick = basculer,
        label = { Text(stringResource(R.string.montre_deja_lues)) },
        secondaryLabel = { Text(nombre.toString()) },
        modifier = Modifier.fillMaxWidth().testTag("montre.dejaLues").semantics { stateDescription = etat },
    )
}

/** « Hors ligne — à jour au 17/11 à 14:32 » : le libellé du téléphone (partagé, #206). */
@Composable
private fun HorsLigneDate(aJourAu: Instant) = Message(
    stringResource(RCommun.string.hors_ligne_a_jour, FormatsDuSite.dateCourte(aJourAu), FormatsDuSite.heure(aJourAu)),
    "montre.horsLigne",
)

@Composable
private fun Vide() = Message(stringResource(R.string.montre_aucune_alerte), "montre.aucuneAlerte")

@Composable
private fun Message(texte: String, repere: String) {
    Text(texte, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag(repere))
}

@Composable
private fun Chargement() {
    val libelle = stringResource(R.string.montre_chargement)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = libelle })
    }
}
