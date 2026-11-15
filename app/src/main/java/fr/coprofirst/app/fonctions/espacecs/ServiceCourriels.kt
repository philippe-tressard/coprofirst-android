// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.api.modeles.JournalCourriels
import fr.coprofirst.api.routes.CourrielsAffairesApi
import fr.coprofirst.app.commun.LibelleTeinte
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant

/**
 * Ce que l'onglet « Courriels affaires » demande, comme l'iOS (`ServiceCourrielsCS`) : le
 * journal de la relève des messages reçus à l'adresse des affaires. Jamais le texte d'un
 * message ; l'expéditeur n'est lu en entier que par l'administrateur — le serveur ne
 * l'envoie pas au conseil.
 */
interface ServiceCourriels {
    /** GET /courriels-affaires. */
    suspend fun journal(): JournalDeReleve
}

class ServiceCourrielsApi(private val session: SessionOuverte) : ServiceCourriels {
    override suspend fun journal() = session.appeler { b, c -> CourrielsAffairesApi(b, c).getCourrielsAffaires().lu() }
}

/** Le journal : le nombre de messages qu'il garde (paramètre du site), du plus récent au plus ancien. */
data class JournalDeReleve(val limite: Int, val messages: List<CourrielReleve>)

data class CourrielReleve(
    val id: Int,
    val releveLe: Instant?,
    val envoyeLe: Instant?,
    val expediteur: String,
    val objet: String,
    /** `accepte`, `relance`, `refuse`, `ignore` ou `a_classer` (`TablesEspaceCS.verdicts`). */
    val decision: String,
    val motif: String,
    val ticketId: Int? = null,
    /** Le numéro de l'affaire (`TK-E00066`), quand le message en a rejoint une. */
    val affaire: String? = null,
) {
    val verdict: LibelleTeinte get() = TablesEspaceCS.verdicts[decision] ?: LibelleTeinte.brut(decision)
}

internal fun JournalCourriels.lu() = JournalDeReleve(
    limite,
    messages.map {
        CourrielReleve(
            it.id, it.releveLe.toInstant(), it.envoyeLe?.toInstant(), it.expediteur, it.objet, it.decision, it.motif,
            it.ticketId, it.affaire,
        )
    },
)
