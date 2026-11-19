// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Actualisable
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.fonctions.notifications.push.NotificationsPush
import fr.coprofirst.app.fonctions.notifications.push.OffrePush
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.CompteurNonLues
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.session.Installation
import java.time.Instant

/**
 * Les alertes du compte dans la résidence ouverte, comme `NotificationsView` de l'iOS :
 * sections « Aujourd'hui » et « Plus anciennes », comme le site.
 *
 * Les gestes se VOIENT : « Tout marquer lu » en haut dès qu'une alerte attend ; « Supprimer
 * les lues » en tête de la liste dès qu'il y en a une ; « Modifier » en haut, qui permet de
 * choisir plusieurs alertes et de les supprimer. Le balayage reste un raccourci, et un
 * toucher marque lu. Toute suppression se confirme : elle est définitive.
 *
 * « Tout marquer lu » et « Modifier » sont dans la barre du haut, « 12 non lues » sous son
 * titre, comme l'iOS (`ActionsNotifications`, et sa divergence déclarée). Un échec se dit en
 * une phrase (`MessageErreur`, `TextesNotifications.echec`). Les lues supprimées, l'avis du
 * site le dit (« Notifications lues supprimées », `AvisEphemere`), comme l'iOS. La cloche,
 * qui ouvre cet écran en feuille avec « OK », est commune (`navigation/Cloche.kt`) : la page
 * lui note le nombre de non lues à chaque lecture et à chaque geste (`CompteurNonLues`).
 * En tête, l'offre des notifications sur le téléphone (`OffrePush`, #280), tant que le relais
 * le permet et que la résidence sert le contrat 2.10.0 (`NotificationsPush.offreDue`).
 */
@Composable
fun EcranNotifications(contexte: ContexteDeMenu) {
    val session = contexte.session
    val residence = session.installation.url
    val parcours: ParcoursNotifications = viewModel(
        key = "notifications@${System.identityHashCode(session)}",
        factory = viewModelFactory {
            initializer { ParcoursNotifications(ServiceNotificationsApi(session)) { CompteurNonLues.partage.noter(residence, it) } }
        },
    )
    EcranNotifications(parcours, contexte.barre, session.installation)
}

/** `installation` : la résidence, pour l'offre des notifications push ; `null` (tests) : jamais d'offre. */
@Composable
fun EcranNotifications(parcours: ParcoursNotifications, barre: BarreDuHaut, installation: Installation? = null) {
    Chargement(charger = { parcours.charger() }, squelette = { SqueletteNotifications() }) { _, _ ->
        ListeNotifications(parcours, barre, installation)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListeNotifications(parcours: ParcoursNotifications, barre: BarreDuHaut, installation: Installation?) {
    val etat by parcours.etat.collectAsStateWithLifecycle()
    var enEdition by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<Int>()) }
    var aConfirmer by remember { mutableStateOf<SuppressionDemandee?>(null) }
    val haptique = LocalHapticFeedback.current
    val avis = remember { AvisDuGeste() }
    val luesSupprimees = stringResource(R.string.notifications_lues_supprimees)
    val echec = TextesNotifications.echec(etat)
    // Le dernier « Supprimer les lues » déjà dit : un écran recomposé (rotation, retour
    // sur la page) ne redit pas un avis passé.
    var avisDits by rememberSaveable { mutableIntStateOf(etat.luesSupprimees) }
    val push = NotificationsPush.de(LocalContext.current)
    val etatPush by push.etat.collectAsStateWithLifecycle()
    LaunchedEffect(push) { if (push.relaisActif) push.lireAutorisation() }

    // Une alerte partie ne reste pas choisie ; une liste vide n'a plus rien à modifier.
    LaunchedEffect(etat.liste) {
        val presentes = etat.liste.mapTo(HashSet()) { it.id }
        selection = selection.filterTo(HashSet()) { it in presentes }
        if (etat.liste.isEmpty()) enEdition = false
    }
    LaunchedEffect(etat.confirmations) {
        if (etat.confirmations > 0) haptique.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    LaunchedEffect(etat.luesSupprimees) {
        if (etat.luesSupprimees > avisDits) {
            avisDits = etat.luesSupprimees
            avis.dire(luesSupprimees)
        }
    }

    ActionsNotifications(
        barre = barre,
        etat = etat,
        enEdition = enEdition,
        toutesChoisies = etat.liste.isNotEmpty() && selection.size == etat.liste.size,
        toutMarquerLu = { parcours.lancer { toutMarquerLu() } },
        toutChoisir = { toutes -> selection = if (toutes) emptySet() else etat.liste.mapTo(HashSet()) { it.id } },
        basculer = {
            enEdition = !enEdition
            selection = emptySet()
        },
    )
    AvisEphemere(avis, Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Actualisable(
                enCours = etat.rafraichissement,
                actualiser = parcours::rafraichir,
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Mise.marge, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(Mise.ecart),
                ) {
                    if (installation != null && push.offreDue(installation, etatPush) && !enEdition) {
                        item(key = "push") { OffrePush(push, Modifier.animateItem()) }
                    }
                    echec?.let { phrase ->
                        item(key = "echec") { MessageErreur(phrase, Modifier.testTag("notifications.echec")) }
                    }
                    if (etat.lues > 0) {
                        item(key = "lues") {
                            GesteSurLesLues(actif = !enEdition) { aConfirmer = SuppressionDemandee.Lues(etat.lues) }
                        }
                    }
                    if (etat.liste.isEmpty() && echec == null) {
                        item(key = "vide") {
                            EtatVide(
                                titre = stringResource(R.string.notifications_vide),
                                icone = Icons.Outlined.NotificationsOff,
                                explication = stringResource(R.string.notifications_vide_detail),
                            )
                        }
                    }
                    val (aujourdhui, anciennes) = etat.sections()
                    val ligne: @Composable (NotificationRecue) -> Unit = { n ->
                        LigneNotification(
                            notification = n,
                            gestes = if (enEdition) null else GestesLigne(
                                marquerLue = if (n.lue) null else ({ parcours.lancer { marquerLue(n) } }),
                                supprimer = { aConfirmer = SuppressionDemandee.Une(n) },
                            ),
                            choisie = if (enEdition) n.id in selection else null,
                            choisir = {
                                selection = if (n.id in selection) selection - n.id else selection + n.id
                                haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            },
                        )
                    }
                    section(R.string.notifications_aujourdhui, aujourdhui, ligne)
                    section(R.string.notifications_anciennes, anciennes, ligne)
                }
            }
            AnimatedVisibility(visible = enEdition) {
                BarreDeSuppression(selection.size) { aConfirmer = SuppressionDemandee.Selection(selection) }
            }
        }
    }

    aConfirmer?.let { demande ->
        ConfirmationSuppression(demande, annuler = { aConfirmer = null }) {
            aConfirmer = null
            if (demande is SuppressionDemandee.Selection) selection = emptySet()
            parcours.lancer { supprimer(demande) }
        }
    }
}

private fun LazyListScope.section(titre: Int, liste: List<NotificationRecue>, ligne: @Composable (NotificationRecue) -> Unit) {
    if (liste.isEmpty()) return
    item(key = "section-$titre") {
        Libelle(stringResource(titre))
    }
    items(liste, key = { it.id }) { n -> Box(Modifier.animateItem()) { ligne(n) } }
}

/** Quatre lignes factices, à la forme d'une alerte, pendant la première lecture. */
@Composable
private fun SqueletteNotifications() {
    val squelette = NotificationRecue(
        id = 0, type = "", titre = stringResource(R.string.notifications_squelette_titre),
        corps = stringResource(R.string.notifications_squelette_corps), lue = true, urgente = false, creeLe = Instant.now(),
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = Mise.marge, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Mise.ecart),
    ) {
        repeat(4) { CarteNotification(squelette, choisie = null) {} }
    }
}
