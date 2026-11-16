// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.api.NOTIFICATIONS_SELECTION_PAR_REQUETE
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.REPERE_AVIS
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.navigation.ActionsDeclarees
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Une boîte simulée, comme l'API simulée de l'iOS : des alertes inventées (aucune
 * personne), le volume d'un compte réel — 150 alertes, 12 non lues —, et la borne du
 * contrat sur la sélection (422 au-delà).
 */
private class BoiteSimulee(nombre: Int = 150) : ServiceNotifications {
    var liste = initiale(nombre)
    val selections = mutableListOf<Int>()

    override suspend fun notifications(): List<NotificationRecue> {
        delay(150)
        return liste
    }

    override suspend fun marquerLue(id: Int) {
        liste = liste.map { if (it.id == id) it.copy(lue = true) else it }
    }

    override suspend fun toutMarquerLu() {
        liste = liste.map { it.copy(lue = true) }
    }

    override suspend fun supprimerNotification(id: Int) {
        liste = liste.filterNot { it.id == id }
    }

    override suspend fun supprimerNotifications(ids: Set<Int>) {
        if (ids.size !in 1..NOTIFICATIONS_SELECTION_PAR_REQUETE) throw ErreurService.Inattendue
        selections += ids.size
        liste = liste.filterNot { it.id in ids }
    }

    override suspend fun supprimerLues() {
        liste = liste.filterNot { it.lue }
    }

    private companion object {
        fun initiale(nombre: Int): List<NotificationRecue> {
            val maintenant = Instant.now()
            fun il(heures: Double) = maintenant.minusSeconds((heures * 3600).toLong())
            val recentes = listOf(
                NotificationRecue(5, "ticket", "Votre affaire avance", "<p>L'affaire est passée chez le prestataire.</p>", false, false, il(1.5)),
                NotificationRecue(4, "publication", "Coupure d'eau jeudi matin", "<p>L'eau sera coupée de 8 h à 11 h.</p>", false, true, il(5.0)),
                NotificationRecue(3, "sondage", "Nouveau sondage", "<p>Quel jour pour la fête des voisins ?</p>", true, false, il(30.0)),
                NotificationRecue(2, "compte", "Compte validé", "<p>Le conseil syndical a validé votre compte.</p>", true, false, il(24.0 * 6)),
            )
            val modeles = listOf("Votre affaire avance", "Nouvelle actualité", "Sondage clôturé", "Nouveau document")
            val anciennes = (0 until (nombre - recentes.size).coerceAtLeast(0)).map { rang ->
                NotificationRecue(
                    100 + rang, "t", modeles[rang % modeles.size], "<p>Une alerte plus ancienne.</p>",
                    lue = rang % 15 != 0, urgente = false, creeLe = il(24.0 * 7 + rang * 12),
                )
            }
            return recentes + anciennes
        }
    }
}

/** L'écran des notifications, comme les tests d'interface de l'iOS (`CommunauteUITests`). */
@RunWith(AndroidJUnit4::class)
class NotificationsInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private fun ouvrir(boite: BoiteSimulee = BoiteSimulee()): BoiteSimulee {
        val parcours = ParcoursNotifications(boite)
        val barre = BarreDuHaut()
        // La barre du haut de la résidence : les actions que l'écran y déclare.
        ecran.setContent {
            ThemeCoproFirst {
                Column {
                    Row { ActionsDeclarees(barre) }
                    EcranNotifications(parcours, barre)
                }
            }
        }
        return boite
    }

    private fun attendre(tag: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun attendreTexte(texte: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasText(texte, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun disparait(tag: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
    }

    private fun el(tag: String): SemanticsNodeInteraction = ecran.onNodeWithTag(tag, useUnmergedTree = true)

    private fun toucher(tag: String) = ecran.toucherTag(tag, useUnmergedTree = true)

    /** « Tout marquer lu » et « Modifier » se voient dans la barre du haut ; tout se marque lu. */
    @Test
    fun toutSeMarqueLu() {
        ouvrir()
        attendre("notifications.toutMarquerLu")
        el("notifications.toutMarquerLu").assertIsDisplayed()
        el("notifications.modifier").assertIsDisplayed()
        attendreTexte("12 non lues")
        toucher("notifications.toutMarquerLu")
        disparait("notifications.toutMarquerLu")
    }

    /** « Modifier », trois alertes choisies, supprimées après confirmation — en une requête. */
    @Test
    fun plusieursNotificationsSeSuppriment() {
        val boite = ouvrir()
        attendre("notifications.modifier")
        toucher("notifications.modifier")
        listOf(5, 4, 3).forEach { ecran.toucherTag("notification.$it") }
        attendreTexte("Supprimer (3)")
        el("notifications.supprimerSelection").assertIsDisplayed().toucher()
        attendre("notifications.confirmerSuppression")
        toucher("notifications.confirmerSuppression")
        disparait("notification.5")
        assertEquals(listOf(3), boite.selections)
        toucher("notifications.modifier")
        attendreTexte("10 non lues")
        ecran.amenerTag("notification.2").assertIsDisplayed()
    }

    /** « Supprimer les lues » : direct, confirmé — la confirmation dit ce qui part —, dit par l'avis, puis absent. */
    @Test
    fun lesLuesSeSupprimentDUnGeste() {
        ouvrir()
        attendre("notifications.supprimerLues")
        el("notifications.supprimerLues").assertIsDisplayed().toucher()
        attendreTexte("Les 138 notifications lues")
        toucher("notifications.confirmerSuppression")
        attendreTexte("Notifications lues supprimées")
        el(REPERE_AVIS).assertIsDisplayed()
        disparait("notification.3")
        ecran.amenerTag("notification.5").assertIsDisplayed()
        disparait("notifications.supprimerLues")
    }

    /** Plus d'alertes choisies qu'une requête n'en accepte : tout part, sans erreur. */
    @Test
    fun uneGrandeSelectionSeSupprimeEntiere() {
        val nombre = NOTIFICATIONS_SELECTION_PAR_REQUETE + 20
        val boite = ouvrir(BoiteSimulee(nombre))
        attendre("notifications.modifier")
        toucher("notifications.modifier")
        toucher("notifications.toutSelectionner")
        attendreTexte("Supprimer ($nombre)")
        toucher("notifications.supprimerSelection")
        toucher("notifications.confirmerSuppression")
        attendreTexte("Aucune notification")
        assertEquals(listOf(NOTIFICATIONS_SELECTION_PAR_REQUETE, 20), boite.selections)
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
