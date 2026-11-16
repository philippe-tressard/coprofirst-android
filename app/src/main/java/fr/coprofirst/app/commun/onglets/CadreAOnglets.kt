// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.onglets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.OngletDuSite

/**
 * Le CADRE d'une page à onglets, comme `CadreAOnglets` de l'iOS — barre d'onglets dans
 * l'ordre du site, descriptif de l'onglet, défilement, « tirer pour actualiser », avis
 * éphémère — et l'onglet choisi, que la table de la page inscrit (`PageAOnglets.ecrans`).
 * Écrit une fois : l'Espace CS et le Paramétrage le partagent. Le titre est celui de la
 * barre du haut (`EcranResidence`) : le cadre n'en pose pas.
 * ```
 * @Composable
 * fun EcranEspaceCS(contexte: ContexteDeMenu) = CadreAOnglets(
 *     contexte,
 *     PageAOnglets("espace-cs", mapOf("validations" to { OngletComptesCS(it) }), R.string.espacecs_reserve),
 * )
 * ```
 * Une page réservée (`PagesOuvertes`) le redit si on l'atteint autrement que par la barre :
 * « page réservée », avec la phrase de la page ; le serveur, lui, refuse chaque route à qui
 * n'a pas le rôle.
 */
@Composable
fun CadreAOnglets(contexte: ContexteDeMenu, page: PageAOnglets) {
    val surLeSite = OngletDuSite(OngletsSurLeSite.ID, stringResource(R.string.onglets_sur_le_site), null, true)
    val onglets = remember(page, contexte.droits, surLeSite) {
        OngletsDeLaPage(page.id, page.ecrans.keys, page.renvoieAuSite, contexte.droits, surLeSite)
    }
    var choisi by rememberSaveable(page.id) { mutableStateOf<String?>(null) }
    val actif = onglets.actif(choisi)
    val fond = Modifier.fillMaxSize().background(LocalCouleurs.current.bg)
    if (actif == null) {
        Column(fond.testTag(ReperesOnglets.RESERVEE), verticalArrangement = Arrangement.Center) {
            EtatVide(contexte.page.titre, icone = Icons.Outlined.Shield, explication = stringResource(page.reserve))
        }
        return
    }
    Contenu(contexte, page, onglets, actif, fond) { choisi = it }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Contenu(
    contexte: ContexteDeMenu,
    page: PageAOnglets,
    onglets: OngletsDeLaPage,
    actif: OngletDuSite,
    modifier: Modifier,
    choisir: (String) -> Unit,
) {
    val avis = remember { AvisDuGeste() }
    var actualisation by rememberSaveable { mutableIntStateOf(0) }
    val descriptif = page.descriptifs[actif.id]
        ?: stringResource(R.string.onglets_sur_le_site_descriptif).takeIf { actif.id == OngletsSurLeSite.ID }
    AvisEphemere(avis, modifier) {
        PullToRefreshBox(isRefreshing = false, onRefresh = { actualisation += 1 }, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = LARGEUR_MAX).fillMaxWidth().padding(bottom = 32.dp)) {
                    BarreOnglets(onglets.barre, actif.id, marges = PaddingValues(horizontal = Mise.marge), choisir = choisir)
                    Column(
                        Modifier.padding(horizontal = Mise.marge).padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (descriptif != null) {
                            Text(
                                TexteDuSite.apercu(descriptif),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalCouleurs.current.textMuted,
                            )
                        }
                        Crossfade(actif, animationSpec = tween(DUREE_TRANSITION_MS), label = "onglet") { onglet ->
                            Onglet(contexte, page, onglets, onglet, avis, actualisation)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Onglet(
    contexte: ContexteDeMenu,
    page: PageAOnglets,
    onglets: OngletsDeLaPage,
    onglet: OngletDuSite,
    avis: AvisDuGeste,
    actualisation: Int,
) {
    val ecran = page.ecrans[onglet.id]
    when {
        onglet.id == OngletsSurLeSite.ID ->
            OngletSurLeSite(onglets.ailleurs, page.descriptifs, page.routes, contexte.session.installation.url)
        ecran != null ->
            ecran(ContexteOnglet(contexte.page, onglet, contexte.session, contexte.droits, avis, actualisation, contexte.barre, contexte.ouvrir))
        else -> OngletBientot(onglet)
    }
}

/** La largeur de lecture d'une page à onglets (tablette, paysage), celle de l'iOS. */
private val LARGEUR_MAX = 720.dp
private const val DUREE_TRANSITION_MS = 220
