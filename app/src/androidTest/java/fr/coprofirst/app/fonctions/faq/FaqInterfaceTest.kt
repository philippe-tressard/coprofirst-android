// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.faq

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Des questions inventées, et le droit de rédiger SERVI (ou illisible). */
private class FaqSimulee(private val redige: Boolean?) : ServiceFaq {
    override suspend fun questions(): List<QuestionFaq> {
        delay(100)
        return listOf(
            QuestionFaq(1, "🏠 Vie en résidence", "Comment signaler une panne dans les parties communes ?", "<p>Créez une affaire.</p>"),
            QuestionFaq(2, "🏠 Vie en résidence", "Où déposer les encombrants ?", "<p>Au local du bâtiment B.</p><ul><li>Pas de gravats</li></ul>"),
        )
    }

    override suspend fun peutRediger() = redige ?: throw ErreurService.Introuvable
}

/** La FAQ, comme les tests d'interface de l'iOS (`testLaFAQEtLeProfilSOuvrent`). */
@RunWith(AndroidJUnit4::class)
class FaqInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private fun ouvrir(redige: Boolean?) = ecran.setContent {
        ThemeCoproFirst { EcranFaq(FaqSimulee(redige), LiensFaq.depuis("/faq", "https://residence.test")) }
    }

    private fun attendre(tag: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun compte(tag: String) = ecran.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun uneQuestionSeDeplieEtLaRedactionSeDitAQuiLePeut() {
        ouvrir(redige = true)
        attendre(ReperesFaq.question(2))
        ecran.toucherTag(ReperesFaq.question(2))
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasText("Pas de gravats", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        ecran.amenerTag(ReperesFaq.REDACTION).assertIsDisplayed()
    }

    /** Un droit illisible vaut « non » : la FAQ se lit, sans l'indication de rédaction (#37). */
    @Test
    fun sansDroitServiLaRedactionNeSeDitPas() {
        ouvrir(redige = null)
        attendre(ReperesFaq.question(1))
        attendre(ReperesFaq.MANUEL)
        assertEquals(0, compte(ReperesFaq.REDACTION))
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
