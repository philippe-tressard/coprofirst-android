// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.magasin

import fr.coprofirst.api.VERSION_CONTRAT_PLANCHER
import fr.coprofirst.app.fonctions.fil.FilInterfaceTest
import fr.coprofirst.app.fonctions.monlot.FixturesMonLot
import fr.coprofirst.app.fonctions.profil.FixturesProfil
import java.time.Instant
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * La résidence fictive des visuels du magasin (`CapturesPlay`) et du parcours d'avant
 * soumission (`ParcoursSoumissionInterfaceTest`, #4). Chaque route répond par sa fixture ;
 * une route inconnue répond 404.
 */
class ResidenceFictive : Dispatcher() {
    private val routes = mapOf(
        "/config" to """{"site_nom": "$NOM", "version_contrat": "$VERSION_CONTRAT_PLANCHER"}""",
        "/auth/login" to "{}",
        "/auth/me" to FixturesProfil.MOI,
        "/flux" to FilInterfaceTest.fil(Instant.now()),
        "/notifications" to "[]",
        "/lots/mes-lots" to FixturesMonLot.MES_LOTS,
        "/acces/mes-vigiks" to FixturesMonLot.MES_VIGIKS,
        "/acces/mes-telecommandes" to FixturesMonLot.MES_TELECOMMANDES,
        "/bailleur/mes-acces-recus" to FixturesMonLot.ACCES_RECUS,
        "/bailleur/mes-baux" to FixturesMonLot.BAUX,
        "/bailleur/tous-les-baux" to FixturesMonLot.BAUX,
    )

    override fun dispatch(request: RecordedRequest): MockResponse {
        val chemin = request.url.encodedPath.removePrefix("/api")
        val corps = routes[chemin] ?: return MockResponse.Builder().code(404).body("""{"detail": "Introuvable."}""").build()
        return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(corps).build()
    }

    companion object {
        const val NOM = "Les Glycines"
        const val ADRESSE = "residence.exemple.test"
    }
}
