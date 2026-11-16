// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import fr.coprofirst.api.infrastructure.Serializer
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Les droits du compte se relisent depuis un écran, après un rôle qu'on se retire (#87). */
@OptIn(ExperimentalCoroutinesApi::class)
class RelireDroitsTest {
    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun rendre() = Dispatchers.resetMain()

    private fun moi(roles: String, refus: Boolean = false) = Serializer.kotlinxSerializationJson.decodeFromString<UserRead>(
        """{"opt_out_telemetrie": $refus, "id": 7, "nom": "Essai", "prenom": "Camille", "email": "camille@residence.example.test",
            "statut": "copropriétaire_résident", "role": "résident", "roles": $roles, "actif": true,
            "onboarding_complete": true, "onboarding_etape": 0, "preferences_notifications": "{}",
            "cree_le": "2026-03-02T10:00:00", "nom_affiche": "Camille ESSAI", "nom_aide_affiche": ""}""",
    )

    /** Le serveur, tel qu'il répond au fil des relectures : une réponse par appel, ou un refus. */
    private class Compte(vararg reponses: UserRead?) : ServiceCompte {
        private val file = ArrayDeque(reponses.toList())
        override suspend fun moi(): UserRead = file.removeFirst() ?: throw ErreurService.SessionExpiree
        override suspend fun nonLues() = 0
    }

    private fun parcours(compte: ServiceCompte) = ParcoursResidence(
        SessionOuverte(Installation("https://residence.test", "Les Glycines"), SessionsParHote(OkHttpClient()), Dispatchers.Unconfined),
        compte,
        CompteurNonLues(),
    )

    @Test
    fun unRoleRetireASoiMemeVautAussitot() = runTest {
        val parcours = parcours(Compte(moi("""["admin"]"""), moi("""["résident"]""")))
        assertTrue("l'administrateur, à l'ouverture", parcours.etat.value.droits.administrateur)
        parcours.relireDroits()
        assertFalse("le rôle retiré : plus d'administration", parcours.etat.value.droits.administrateur)
    }

    /** Le refus de la mesure d'audience se relit avec les droits : un geste au Profil vaut aussitôt (#144). */
    @Test
    fun leRefusDeLaMesureSeRelit() = runTest {
        val parcours = parcours(Compte(moi("""["résident"]"""), moi("""["résident"]""", refus = true)))
        assertEquals(false, parcours.etat.value.refusMesure)
        parcours.relireDroits()
        assertEquals(true, parcours.etat.value.refusMesure)
    }

    @Test
    fun uneRelectureEchoueeGardeLesDroitsDAvant() = runTest {
        val parcours = parcours(Compte(moi("""["admin"]"""), null))
        parcours.relireDroits()
        assertTrue(parcours.etat.value.droits.administrateur)
    }
}
