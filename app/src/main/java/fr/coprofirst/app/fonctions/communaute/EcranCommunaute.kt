// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.onglets.CadreAOnglets
import fr.coprofirst.app.commun.onglets.PageAOnglets
import fr.coprofirst.app.navigation.ActionsDeBarre
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.ContexteDeMenu

/**
 * La Communauté (page `communaute`, #20), comme l'iOS (`CommunauteView`) : Sondages, Boîte
 * à idées, Petites annonces et Avis, dans l'ordre et selon la réserve du site (le cadre à
 * onglets commun). Ce que le serveur ferme ne s'offre pas : un gestionnaire ne voit que
 * l'Avis, un compte suspendu lit le motif du serveur, mot pour mot.
 */
@Composable
fun EcranCommunaute(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceCommunauteApi(contexte.session) }
    EcranCommunaute(contexte, service)
}

@Composable
internal fun EcranCommunaute(contexte: ContexteDeMenu, service: ServiceCommunaute) {
    Chargement(
        charger = { service.moi() },
        modifier = Modifier.fillMaxSize().background(LocalCouleurs.current.bg),
        squelette = { Column(Modifier.padding(Mise.marge)) { Carte { Text(contexte.page.titre) } } },
    ) { moi, _ ->
        when (val acces = AccesCommunaute.depuis(moi)) {
            is AccesCommunaute.Ferme -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                EtatVide(contexte.page.titre, icone = Icons.Outlined.Block, explication = stringResource(R.string.communaute_ferme, acces.motif))
            }
            AccesCommunaute.Ouvert -> CadreAOnglets(contexte, rememberPage(service, moi.id))
        }
    }
}

/** La table des onglets ; `moi` : l'identifiant du compte, pour ne pas signaler ce qu'il a écrit. */
@Composable
private fun rememberPage(service: ServiceCommunaute, moi: Int) = remember(service, moi) {
    PageAOnglets(
        AccesCommunaute.PAGE,
        mapOf(
            "sondages" to { onglet -> OngletSondages(onglet, service) },
            "idees" to { onglet -> OngletIdees(onglet, service, moi) },
            "annonces" to { onglet -> OngletAnnonces(onglet, service, moi) },
            "avis" to { onglet -> OngletAvis(onglet, service) },
        ),
        reserve = R.string.communaute_reserve,
    )
}

/**
 * « + » dans la barre du haut, comme l'iOS (`boutonCreer`) : ce que l'onglet affiché permet
 * de créer à ce compte, posé tant que l'onglet est à l'écran.
 */
@Composable
fun BoutonCreer(barre: BarreDuHaut, libelle: Int, creer: () -> Unit) {
    ActionsDeBarre(barre) {
        val couleurs = LocalCouleurs.current
        FilledIconButton(
            onClick = creer,
            modifier = Modifier.testTag(ReperesCommunaute.CREER),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = couleurs.primaryPlein, contentColor = couleurs.surPlein),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(libelle))
        }
    }
}
