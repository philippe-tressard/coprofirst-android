// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Pictogrammes
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.session.SessionOuverte

/**
 * La résidence ouverte, comme `OngletsView` de l'iOS : la barre du bas (les 4 premières
 * pages de l'ordre servi, puis « Plus »), et la page choisie, ouverte par le registre.
 */
@Composable
fun EcranResidence(session: SessionOuverte, sortie: GestesDeSortie, apparence: Apparence) {
    val parcours: ParcoursResidence = viewModel(
        key = session.installation.url,
        factory = viewModelFactory { initializer { ParcoursResidence(session, ServiceCompteApi(session)) } },
    )
    val etat by parcours.etat.collectAsStateWithLifecycle()
    val pages = parcours.pagesOuvertes(etat.droits)
    val barre = pages.take(ParcoursResidence.VISIBLES)
    val plus = pages.drop(ParcoursResidence.VISIBLES)
    // Une page que les droits retirent (relus) ne reste pas ouverte.
    val onglet = etat.onglet.takeIf { it == EtatResidence.PLUS || barre.any { p -> p.id == it } } ?: barre.firstOrNull()?.id
    var pageDePlus by rememberSaveable(onglet) { mutableStateOf<String?>(null) }

    val ouverte: PageDeNavigation? = when {
        onglet != EtatResidence.PLUS -> barre.firstOrNull { it.id == onglet }
        else -> pageDePlus?.let { id -> plus.firstOrNull { it.id == id } ?: PagesDePlus.fixe(id) }
    }
    BackHandler(enabled = pageDePlus != null) { pageDePlus = null }
    CadreResidence(
        titre = ouverte?.titre ?: stringResource(R.string.plus),
        retour = if (pageDePlus != null) ({ pageDePlus = null }) else null,
        barre = {
            NavigationBar {
                barre.forEach { page ->
                    NavigationBarItem(
                        selected = onglet == page.id,
                        onClick = { parcours.choisir(page.id) },
                        icon = { Icon(Pictogrammes.icone(page.icone), contentDescription = null) },
                        label = { Text(page.libelle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
                NavigationBarItem(
                    selected = onglet == EtatResidence.PLUS,
                    onClick = { parcours.choisir(EtatResidence.PLUS) },
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                    label = { Text(stringResource(R.string.plus)) },
                )
            }
        },
    ) {
        if (ouverte != null) {
            RegistreDesMenus.Ecran(ContexteDeMenu(ouverte, session, etat.droits, sortie, apparence))
        } else {
            ListeDePlus(plus, ouvrir = { pageDePlus = it }, deconnecter = sortie::deconnecter)
        }
    }
}

/** Les entrées fixes de « Plus », comme l'iOS : Profil et À propos (leurs écrans : #21, #27). */
object PagesDePlus {
    val profil = PageDeNavigation("profil", "/profil", "Profil", "Profil", "user")
    val aPropos = PageDeNavigation("a-propos", "/a-propos", "À propos", "À propos", "info")

    fun fixe(id: String): PageDeNavigation? = listOf(profil, aPropos).firstOrNull { it.id == id }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CadreResidence(
    titre: String,
    retour: (() -> Unit)?,
    barre: @Composable () -> Unit,
    contenu: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titre, style = Typo.titre(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (retour != null) {
                        IconButton(onClick = retour) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retour))
                        }
                    }
                },
            )
        },
        bottomBar = barre,
    ) { marges -> Box(Modifier.fillMaxSize().padding(marges)) { contenu() } }
}
