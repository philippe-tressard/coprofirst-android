// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.session.ChiffrementAesGcm
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.LecturesGardees
import fr.coprofirst.app.session.SessionsGardeesEnMemoire
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.crypto.spec.SecretKeySpec

/**
 * La consultation hors ligne à l'écran (#28), comme `HorsLigneUITests` de l'iOS : l'appareil
 * sans réseau au lancement, la biométrie passée — la dernière lecture s'ouvre, et le bandeau
 * dit d'où elle vient, sans alerte. La biométrie est simulée (`SessionsGardeesEnMemoire`).
 */
@RunWith(AndroidJUnit4::class)
class HorsLigneInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()
    private val residence = Installation("https://residence.exemple.test", "Les Glycines")
    private val dossier = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "lectures-test")

    @After
    fun fermer() {
        serveur.close()
        dossier.deleteRecursively()
    }

    @Test
    fun sansReseauLaBiometrieOuvreLaDerniereLectureEtDitDOuElleVient() {
        val gardees = SessionsGardeesEnMemoire()
        val lectures = LecturesGardees(dossier, ChiffrementAesGcm { SecretKeySpec(ByteArray(32), "AES") }, gardees::contient)
        val connues = serveur.residencesEnMemoire().apply { retenir(residence) }
        gardees.garder("residence.exemple.test", "r1")
        lectures.garder(ServeurDeTest.FIL_VIDE.toByteArray(), "application/json", "/api/flux", "residence.exemple.test")
        // L'appareil sans réseau : rien n'écoute plus à l'adresse de la résidence.
        serveur.close()
        val parcours = serveur.parcours(gardees = gardees, connues = connues, lectures = lectures)
        ecran.setContent { NavigationEntree(serveur.apparence(), parcours) }
        ecran.attendreTexte("Rien de neuf")
        ecran.attendreTexte("Consultation seule : les gestes reviennent avec le réseau.")
        ecran.onNodeWithTag(REPERE_BANDEAU).assertExists()
        ecran.attendreTexte("Hors ligne — à jour au")
    }
}
