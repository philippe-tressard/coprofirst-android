// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.apropos

import fr.coprofirst.api.routes.ConfigApi
import fr.coprofirst.app.commun.texte.Separateur
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Ce que « À propos » lit de la résidence ouverte, comme l'iOS (`AProposResidence`) : la
 * mention de son pied de page (`CLES_PIED` de GET /config) et ses deux textes légaux
 * (GET /config/legal), en HTML, déjà remplis par le serveur avec l'identité de la copropriété.
 */
data class AProposResidence(
    /** L'année de création que l'administration a réglée (« © 2025–2026 »), si elle l'a fait. */
    val anneeDebut: Int? = null,
    /** Le texte libre du pied de page, s'il y en a un. */
    val texte: String? = null,
    val mentionsLegales: String? = null,
    val confidentialite: String? = null,
) {
    companion object {
        /** Les deux réponses publiques, lues telles que le serveur les sert ; un texte vide se tait. */
        fun depuis(config: Map<String, String?>, legal: Map<String, String?>) = AProposResidence(
            anneeDebut = config[TextesDuPied.cleAnneeDebut]?.trim()?.toIntOrNull(),
            texte = config[TextesDuPied.cleTexte]?.trim()?.ifEmpty { null },
            mentionsLegales = legal["mentions_legales"]?.ifBlank { null },
            confidentialite = legal["politique_confidentialite"]?.ifBlank { null },
        )

        /** `mentionAnnee` du site : « © 2026 », ou « © 2025–2026 » quand la création est antérieure. */
        fun mentionAnnee(debut: Int?, courante: Int): String =
            if (debut != null && debut < courante) "© $debut–$courante" else "© $courante"

        /** La mention du pied : l'année, le texte libre, puis la version de l'app (« app 0.8.13 »). */
        fun mention(lu: AProposResidence?, courante: Int, version: String?): String =
            listOfNotNull(mentionAnnee(lu?.anneeDebut, courante), lu?.texte, version?.let { "app $it" }).joinToString(Separateur.point)
    }
}

/** GET /config et GET /config/legal : deux routes publiques, lues ensemble. */
interface ServiceAPropos {
    suspend fun aPropos(): AProposResidence
}

/** Par le client GÉNÉRÉ, à travers LA porte de la résidence. */
class ServiceAProposApi(private val session: SessionOuverte) : ServiceAPropos {
    override suspend fun aPropos(): AProposResidence = coroutineScope {
        val config = async { session.appeler { b, c -> ConfigApi(b, c).getConfig() } }
        val legal = async { session.appeler { b, c -> ConfigApi(b, c).getConfigLegal() } }
        AProposResidence.depuis(config.await(), legal.await())
    }
}
