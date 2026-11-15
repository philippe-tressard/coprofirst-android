// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import fr.coprofirst.api.NOTIFICATIONS_SELECTION_PAR_REQUETE
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/**
 * Un serveur en mémoire, qui borne la sélection comme le contrat (422 au-delà) : il garde
 * ce qu'il a reçu, refuse tout si `refuse`, et perd le réseau à la suppression groupée de
 * rang `enPanne` (1 : la première).
 */
private class ServeurFactice(
    var liste: List<NotificationRecue>,
    private val refuse: Boolean = false,
    private val enPanne: Int? = null,
) : ServiceNotifications {
    val recues = mutableListOf<Set<Int>>()
    var requetes = 0

    private fun geste() {
        requetes++
        if (refuse) throw ErreurService.Inattendue
    }

    override suspend fun notifications() = liste
    override suspend fun marquerLue(id: Int) {
        geste()
        liste = liste.map { if (it.id == id) it.copy(lue = true) else it }
    }

    override suspend fun toutMarquerLu() {
        geste()
        liste = liste.map { it.copy(lue = true) }
    }

    override suspend fun supprimerNotification(id: Int) {
        geste()
        liste = liste.filterNot { it.id == id }
    }

    override suspend fun supprimerNotifications(ids: Set<Int>) {
        geste()
        if (ids.size !in 1..NOTIFICATIONS_SELECTION_PAR_REQUETE) throw ErreurService.Inattendue
        recues += ids
        if (recues.size == enPanne) throw ErreurService.Injoignable
        liste = liste.filterNot { it.id in ids }
    }

    override suspend fun supprimerLues() {
        geste()
        liste = liste.filterNot { it.lue }
    }
}

private val MAINTENANT: Instant = Instant.ofEpochSecond(1_800_000_000)

private fun alerte(id: Int, ilYA: Double, lue: Boolean = false) = NotificationRecue(
    id = id, type = "t", titre = "Alerte $id", corps = "", lue = lue, urgente = false,
    creeLe = MAINTENANT.minusSeconds((ilYA * 3600).toLong()),
)

/** L'état et les gestes de l'écran, comme `NotificationsTests` de l'iOS. */
class ParcoursNotificationsTest {
    private val limite = NOTIFICATIONS_SELECTION_PAR_REQUETE

    private suspend fun ouvert(serveur: ServeurFactice) = ParcoursNotifications(serveur).also { it.charger() }

    @Test
    fun laBorneEstCelleDuContrat() = assertEquals("maxItems de SuppressionNotifications.ids", 500, limite)

    @Test
    fun aujourdhuiCeSontLesVingtQuatreDernieresHeures() {
        val etat = EtatNotifications(listOf(alerte(1, 1.0), alerte(2, 23.9), alerte(3, 24.1)))
        val (aujourdhui, anciennes) = etat.sections(MAINTENANT)
        assertEquals("fenêtre glissante, comme le site", listOf(1, 2), aujourdhui.map { it.id })
        assertEquals(listOf(3), anciennes.map { it.id })
    }

    @Test
    fun laDateDUneLigneEstLHeureDansLes24Heures() {
        val recente = MAINTENANT.minusSeconds(3600)
        val ancienne = MAINTENANT.minusSeconds(30 * 3600)
        assertEquals(FormatsDuSite.heure(recente), dateDeLigne(recente, MAINTENANT))
        assertEquals(FormatsDuSite.dateCourte(ancienne), dateDeLigne(ancienne, MAINTENANT))
    }

    @Test
    fun unePremiereLectureEchoueeSeDit() = runTest {
        val parcours = ParcoursNotifications(object : ServiceNotifications by ServeurFactice(emptyList()) {
            override suspend fun notifications(): List<NotificationRecue> = throw ErreurService.Injoignable
        })
        try {
            parcours.charger()
            fail("la lecture a échoué : l'écran doit le dire")
        } catch (e: ErreurService) {
            assertEquals(ErreurService.Injoignable, e)
        }
    }

    @Test
    fun unGesteRefuseRendLaListe() = runTest {
        val serveur = ServeurFactice(listOf(alerte(1, 1.0), alerte(2, 2.0, lue = true)), refuse = true)
        val parcours = ouvert(serveur)
        assertEquals(1, parcours.etat.value.nonLues)

        parcours.toutMarquerLu()
        assertEquals("le serveur a refusé : rien n'est marqué lu", 1, parcours.etat.value.nonLues)
        assertEquals("et une phrase le dit", ErreurService.Inattendue, parcours.etat.value.echec)
        assertEquals("aucune confirmation", 0, parcours.etat.value.confirmations)

        parcours.supprimer(parcours.etat.value.liste[0])
        assertEquals("la notification revient", 2, parcours.etat.value.liste.size)
        assertFalse(parcours.supprimerLues())
        assertEquals("les lues reviennent aussi", listOf(1, 2), parcours.etat.value.liste.map { it.id })
    }

    @Test
    fun marquerLuPuisToutMarquerLu() = runTest {
        val serveur = ServeurFactice(listOf(alerte(1, 1.0), alerte(2, 2.0), alerte(3, 30.0, lue = true)))
        val parcours = ouvert(serveur)
        parcours.marquerLue(parcours.etat.value.liste[0])
        assertEquals(1, parcours.etat.value.nonLues)
        parcours.marquerLue(parcours.etat.value.liste[0])
        assertEquals("déjà lue : aucune requête", 1, serveur.requetes)
        parcours.toutMarquerLu()
        assertEquals(0, parcours.etat.value.nonLues)
        assertEquals("le geste se confirme", 1, parcours.etat.value.confirmations)
        assertNull(parcours.etat.value.echec)
    }

    @Test
    fun lesLuesSeSupprimentDUnGeste() = runTest {
        val serveur = ServeurFactice(listOf(alerte(5, 1.0), alerte(3, 30.0, lue = true), alerte(2, 40.0, lue = true)))
        val parcours = ouvert(serveur)
        assertEquals(2, parcours.etat.value.lues)
        assertTrue(parcours.supprimerLues())
        assertEquals("les non lues restent", listOf(5), parcours.etat.value.liste.map { it.id })
        assertFalse("plus de lue : aucune requête", parcours.supprimerLues())
        assertEquals(1, serveur.requetes)
    }

    @Test
    fun lesLotsRespectentLaBorne() {
        val ids = (1..(2 * limite + 1)).toSet()
        val lots = ParcoursNotifications.lots(ids)
        assertEquals("trois requêtes, aucune au-delà de la borne", listOf(limite, limite, 1), lots.map { it.size })
        assertEquals("aucun identifiant deux fois", ids.size, lots.sumOf { it.size })
        assertEquals("et aucun oublié", ids, lots.flatten().toSet())
        assertEquals(listOf(setOf(1, 2), setOf(3)), ParcoursNotifications.lots(setOf(3, 1, 2), taille = 2))
    }

    @Test
    fun uneGrandeSelectionPartEnPlusieursRequetes() = runTest {
        val choisies = (1..(2 * limite + 1)).toSet()
        val serveur = ServeurFactice((1..(2 * limite + 4)).map { alerte(it, it.toDouble(), lue = it > 2) })
        val parcours = ouvert(serveur)
        assertTrue(parcours.supprimer(choisies))
        assertEquals(listOf(limite, limite, 1), serveur.recues.map { it.size })
        assertEquals("les autres restent", listOf(2 * limite + 2, 2 * limite + 3, 2 * limite + 4), parcours.etat.value.liste.map { it.id })
        assertNull(parcours.etat.value.echec)
        assertEquals(1, parcours.etat.value.confirmations)
    }

    @Test
    fun uneSuppressionInterrompueNeRendQueCeQuiReste() = runTest {
        val choisies = (1..(2 * limite + 1)).toSet()
        val serveur = ServeurFactice((1..(2 * limite + 4)).map { alerte(it, it.toDouble(), lue = it > 2) }, enPanne = 2)
        val parcours = ouvert(serveur)
        assertFalse("tout n'est pas supprimé", parcours.supprimer(choisies))
        assertEquals("la troisième requête ne part pas", 2, serveur.recues.size)
        val etat = parcours.etat.value
        assertEquals("le premier lot a disparu, le reste demeure", ((limite + 1)..(2 * limite + 4)).toList(), etat.liste.map { it.id })
        assertEquals((1..limite).toSet(), etat.interrompue?.parties)
        assertEquals(choisies.size, etat.interrompue?.demandees)
        assertEquals("la raison reste lisible", ErreurService.Injoignable, etat.echec)
        assertEquals("les deux non lues sont parties", 0, etat.nonLues)

        // On réessaie ce qui reste : le réseau est revenu, la phrase s'efface.
        assertTrue(parcours.supprimer(choisies - (1..limite).toSet()))
        assertEquals(listOf(limite, 1), serveur.recues.drop(2).map { it.size })
        assertEquals(listOf(2 * limite + 2, 2 * limite + 3, 2 * limite + 4), parcours.etat.value.liste.map { it.id })
        assertNull(parcours.etat.value.echec)
        assertNull(parcours.etat.value.interrompue)
    }

    @Test
    fun unEchecDesLaPremiereRequeteRendToutesLesLignes() = runTest {
        val serveur = ServeurFactice((1..(limite + 2)).map { alerte(it, it.toDouble()) }, enPanne = 1)
        val parcours = ouvert(serveur)
        assertFalse(parcours.supprimer((1..(limite + 1)).toSet()))
        assertEquals("la seconde requête ne part pas", 1, serveur.recues.size)
        assertEquals("elles reviennent toutes, à leur place", (1..(limite + 2)).toList(), parcours.etat.value.liste.map { it.id })
        assertNull("pas « 0 notification sur … »", parcours.etat.value.interrompue)
        assertEquals(ErreurService.Injoignable, parcours.etat.value.echec)
    }

    @Test
    fun uneSelectionVideNEnvoieRien() = runTest {
        val serveur = ServeurFactice(listOf(alerte(1, 1.0)))
        assertFalse(ouvert(serveur).supprimer(emptySet<Int>()))
        assertEquals(0, serveur.requetes)
    }
}
