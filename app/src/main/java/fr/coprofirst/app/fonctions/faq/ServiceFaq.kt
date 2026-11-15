// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.faq

import fr.coprofirst.api.modeles.FaqItemRead
import fr.coprofirst.api.routes.FaqApi
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte

/** Une question de la FAQ, telle que GET /faq la sert. La réponse est du HTML de l'éditeur. */
data class QuestionFaq(val id: Int, val categorie: String, val question: String, val reponse: String)

/** Une catégorie et ses questions, dans l'ordre du serveur. */
data class CategorieFaq(val nom: String, val questions: List<QuestionFaq>)

/** La FAQ lue : ses catégories, et le droit SERVI de la rédiger (`peut_rediger`). */
data class FaqLue(val categories: List<CategorieFaq>, val peutRediger: Boolean)

/**
 * Ce que l'écran de la FAQ demande à la résidence (#20), comme l'iOS (`ServiceFAQ`) : la
 * lecture seule. La rédaction reste au site ; le serveur dit à qui il l'ouvre.
 */
interface ServiceFaq {
    /** GET /faq : les questions actives, rangées par le serveur (catégorie, ordre). */
    suspend fun questions(): List<QuestionFaq>

    /** GET /faq/gestes → `peut_rediger` : ce lecteur rédige-t-il la FAQ (sur le site) ? */
    suspend fun peutRediger(): Boolean
}

/** La FAQ par le client GÉNÉRÉ, à travers LA porte de la résidence. */
class ServiceFaqApi(private val session: SessionOuverte) : ServiceFaq {
    override suspend fun questions() = session.appeler { b, c -> FaqApi(b, c).getFaq().map { it.lue() } }

    override suspend fun peutRediger() = session.appeler { b, c -> FaqApi(b, c).getFaqGestes().peutRediger }
}

/** La catégorie affichée est celle du SERVEUR (`categorie_libelle`), jamais composée ici. */
private fun FaqItemRead.lue() = QuestionFaq(id, categorieLibelle, question, reponse)

object ReglesFaq {
    /**
     * Regroupe sans retrier : le serveur a déjà rangé. Une catégorie sans libellé se lit
     * `general` (« Général », comme sur le site).
     */
    fun regrouper(questions: List<QuestionFaq>, general: String): List<CategorieFaq> =
        questions.groupBy { it.categorie.trim().ifEmpty { general } }.map { (nom, liste) -> CategorieFaq(nom, liste) }

    /**
     * La FAQ, et le droit de la rédiger. Illisible (route absente, refus, réseau), le droit
     * vaut « non » : la FAQ se lit quand même, sans l'indication réservée à qui la rédige.
     */
    suspend fun lire(service: ServiceFaq, general: String): FaqLue {
        val questions = service.questions()
        val redige = try {
            service.peutRediger()
        } catch (_: ErreurService) {
            false
        }
        return FaqLue(regrouper(questions, general), redige)
    }
}
