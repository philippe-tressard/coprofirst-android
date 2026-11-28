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

    private fun moi(roles: String, refus: Boolean = false, temporaire: Boolean? = null) = Serializer.kotlinxSerializationJson.decodeFromString<UserRead>(
        """{${temporaire?.let { "\"mot_de_passe_a_changer\": $it, " } ?: ""}"opt_out_telemetrie": $refus, "id": 7, "nom": "Essai", "prenom": "Camille", "email": "camille@residence.example.test",
            "statut": "copropriétaire_résident", "role": "résident", "roles": $roles, "actif": true,
            "onboarding_complete": true, "onboarding_etape": 0, "preferences_notifications": "{}",
            "cree_le": "2026-03-02T10:00:00", "nom_affiche": "Camille ESSAI", "nom_aide_affiche": ""}""",
    )

    /** Le serveur, tel qu'il répond au fil des relectures : une réponse par appel, ou un refus. */
    private class Compte(vararg reponses: UserRead?) : ServiceCompte {
        private val file = ArrayDeque(reponses.toList())
        var lectures = 0
        override suspend fun moi(): UserRead = (lectures++).let { file.removeFirst() } ?: throw ErreurService.SessionExpiree
        override suspend fun nonLues() = 0
    }

    private fun parcours(compte: ServiceCompte, lu: UserRead? = null) = ParcoursResidence(
        SessionOuverte(Installation("https://residence.test", "Les Glycines"), SessionsParHote(OkHttpClient()), Dispatchers.Unconfined),
        compte,
        CompteurNonLues(),
        lu,
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
        assertFalse("l'échec se dit", parcours.relireDroits())
        assertTrue(parcours.etat.value.droits.administrateur)
    }

    /**
     * #371 : `mot_de_passe_a_changer` ferme la résidence derrière « Choisir votre mot de passe » ;
     * le mot de passe choisi, le compte relu fait tomber le drapeau. Absent (un serveur qui ne le
     * sert pas), il vaut faux.
     */
    @Test
    fun leMotDePasseTemporaireFermeLaResidenceJusquASaRelecture() = runTest {
        val parcours = parcours(Compte(moi("""["admin"]""", temporaire = true), null, moi("""["admin"]""", temporaire = false)))
        assertTrue("le drapeau lu à l'ouverture", parcours.etat.value.motDePasseAChanger)
        assertFalse("une relecture ratée le garde", parcours.relireDroits())
        assertTrue(parcours.etat.value.motDePasseAChanger)
        assertTrue(parcours.relireDroits())
        assertFalse("relu, il tombe", parcours.etat.value.motDePasseAChanger)
        assertFalse(parcours(Compte(moi("""["résident"]"""))).etat.value.motDePasseAChanger)
    }

    /**
     * #391 : le compte lu à la connexion, avant d'ouvrir, fait l'état dès la création — la porte,
     * les droits — sans relecture (le serveur ne voit aucun second GET /auth/me).
     */
    @Test
    fun leCompteLuALaConnexionFaitLEtatSansRelecture() = runTest {
        val serveur = Compte()
        val parcours = parcours(serveur, lu = moi("""["admin"]""", temporaire = true))
        assertTrue("la porte, dès la création", parcours.etat.value.motDePasseAChanger)
        assertTrue("les droits du compte, dès la création", parcours.etat.value.droits.administrateur)
        assertEquals("aucune relecture", 0, serveur.lectures)
    }
}
