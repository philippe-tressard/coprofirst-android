// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.delegations

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Une résidence simulée, comme l'API simulée de l'iOS (`FixturesDelegations`) : le compte
 * qui lit est l'aidant n° 7 et siège au conseil ; les gestes sont ceux que le serveur lui
 * ouvre. Tout est inventé : aucun nom de personne réelle.
 */
private class DelegationsSimulees : ServiceDelegations {
    var liste = listOf(
        Delegation(12, "Lucie EXEMPLE", "Camille ESSAI", "en_attente", "Assistance pendant une convalescence", "2026-11-06", "2027-02-04", true, true),
        Delegation(8, "Marc DÉMO", "Lucie EXEMPLE", "active", "Assistance personne âgée", "2026-07-11", null, false, true),
        Delegation(5, "Paul FICTIF", "Marc DÉMO", "revoquee", "", "2025-10-04", "2026-10-09"),
    )
    val creees = mutableListOf<SaisieDelegation>()

    override suspend fun delegations(): List<Delegation> {
        delay(100)
        return liste
    }

    override suspend fun residentsDelegables() = listOf(
        ResidentDelegable(3, "Lucie EXEMPLE", "lucie@exemple.test", true),
        ResidentDelegable(4, "Marc DÉMO", "marc@exemple.test", true),
        ResidentDelegable(7, "Camille ESSAI", "camille@exemple.test", true),
        ResidentDelegable(9, "Paul FICTIF", "paul@exemple.test", false),
    ).liables()

    override suspend fun creer(saisie: SaisieDelegation): Delegation {
        creees += saisie
        val cree = Delegation(20, "Marc DÉMO", "Camille ESSAI", "en_attente", saisie.motif, "2026-11-15", null, true, true)
        liste = listOf(cree) + liste
        return cree
    }

    override suspend fun accepter(id: Int) = changer(id) { it.copy(statut = "active", peutAccepter = false) }

    override suspend fun revoquer(id: Int) = changer(id) { it.copy(statut = "revoquee", peutAccepter = false, peutRevoquer = false) }

    private fun changer(id: Int, geste: (Delegation) -> Delegation): Delegation {
        val avant = liste.firstOrNull { it.id == id } ?: throw ErreurService.Introuvable
        val apres = geste(avant)
        liste = liste.map { if (it.id == id) apres else it }
        return apres
    }
}

/** « Délégations aidant » à l'écran, comme l'iOS (`DelegationsCSUITests`). */
@RunWith(AndroidJUnit4::class)
class DelegationsInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private fun ouvrir(conseil: Boolean = true): DelegationsSimulees {
        val residence = DelegationsSimulees()
        ecran.setContent { ThemeCoproFirst { EcranDelegations(residence, conseil) } }
        return residence
    }

    private fun attendre(repere: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasTestTag(repere)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun disparait(repere: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasTestTag(repere)).fetchSemanticsNodes().isEmpty()
    }

    private fun attendreTexte(texte: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasText(texte, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun lesDelegationsSAcceptentEtSeRevoquent() {
        ouvrir()
        attendre(ReperesDelegations.accepter(12))
        assertEquals(
            "pas d'« Accepter » sur celle d'un autre aidant",
            0,
            ecran.onAllNodes(hasTestTag(ReperesDelegations.accepter(8))).fetchSemanticsNodes().size,
        )
        attendreTexte("Du 06/11/2026 au 04/02/2027")
        attendreTexte("Du 11/07/2026 — illimité")
        ecran.toucherTag(ReperesDelegations.accepter(12))
        disparait(ReperesDelegations.accepter(12))
        attendreTexte("Délégation acceptée")

        // Révoquer demande une confirmation.
        ecran.toucherTag(ReperesDelegations.revoquer(8))
        attendre(ReperesDelegations.REVOQUER_CONFIRMER)
        ecran.toucherTag(ReperesDelegations.REVOQUER_CONFIRMER)
        disparait(ReperesDelegations.revoquer(8))
        attendreTexte("Délégation révoquée")
    }

    @Test
    fun uneNouvelleDelegationAttendDeuxPersonnes() {
        val residence = ouvrir()
        ecran.toucherTag(ReperesDelegations.NOUVELLE)
        attendre(ReperesDelegations.ENREGISTRER)
        ecran.onNodeWithTag(ReperesDelegations.ENREGISTRER).assertIsNotEnabled()

        ecran.toucherTag(ReperesDelegations.MANDANT)
        // Le compte inactif ne se propose pas.
        attendre(ReperesDelegations.resident(4))
        assertEquals(0, ecran.onAllNodes(hasTestTag(ReperesDelegations.resident(9))).fetchSemanticsNodes().size)
        ecran.onNodeWithTag(ReperesDelegations.RECHERCHE).performTextInput("marc")
        disparait(ReperesDelegations.resident(3))
        ecran.toucherTag(ReperesDelegations.resident(4))
        attendreTexte("Marc DÉMO (marc@exemple.test)")
        ecran.onNodeWithTag(ReperesDelegations.ENREGISTRER).assertIsNotEnabled()

        ecran.toucherTag(ReperesDelegations.AIDANT)
        attendre(ReperesDelegations.resident(7))
        // La personne aidée ne peut pas être son propre aidant.
        assertEquals(0, ecran.onAllNodes(hasTestTag(ReperesDelegations.resident(4))).fetchSemanticsNodes().size)
        ecran.toucherTag(ReperesDelegations.resident(7))
        ecran.onNodeWithTag(ReperesDelegations.MOTIF).performTextInput("Assistance personne âgée")
        ecran.amenerTag(ReperesDelegations.ENREGISTRER).assertIsEnabled().toucher()

        attendre(ReperesDelegations.carte(20))
        attendreTexte("Délégation créée")
        assertEquals(listOf(SaisieDelegation(4, 7, "Assistance personne âgée", null)), residence.creees)
    }

    @Test
    fun horsDuConseilPasDeCreation() {
        ouvrir(conseil = false)
        attendre(ReperesDelegations.carte(12))
        assertEquals(0, ecran.onAllNodes(hasTestTag(ReperesDelegations.NOUVELLE)).fetchSemanticsNodes().size)
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
