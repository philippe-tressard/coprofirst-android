// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.coprofirst.app.R

/** Les gestes des écrans de l'entrée — une interface, pour que les aperçus s'en passent. */
interface ActionsEntree {
    fun entrer()
    fun entrerDemo()
    fun saisir(texte: String)
    fun retour()

    /** Pour les aperçus : aucun geste ne fait rien. */
    object Inertes : ActionsEntree {
        override fun entrer() = Unit
        override fun entrerDemo() = Unit
        override fun saisir(texte: String) = Unit
        override fun retour() = Unit
    }
}

/**
 * Le passage d'un écran à l'autre : un fondu et un léger glissement dans le sens du
 * parcours, 220 ms, courbe ease-out marquée. Il est rare (quelques fois par session) :
 * il dit d'où l'on vient. L'échelle d'animation du système le réduit ou le supprime.
 */
private val SORTIE_RAPIDE = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val DUREE_MS = 220

@Composable
fun NavigationEntree(parcours: ParcoursEntree = viewModel(factory = ParcoursEntree.Fabrique)) {
    val etat by parcours.etat.collectAsStateWithLifecycle()
    BackHandler(enabled = etat.ecran != EcranEntree.Accueil) { parcours.retour() }
    Surface(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = etat.ecran,
            transitionSpec = {
                val sens = if (targetState >= initialState) 1 else -1
                (fadeIn(tween(DUREE_MS, easing = SORTIE_RAPIDE)) +
                    slideInHorizontally(tween(DUREE_MS, easing = SORTIE_RAPIDE)) { sens * it / 12 })
                    .togetherWith(fadeOut(tween(DUREE_MS / 2)))
            },
            label = "entree",
        ) { ecran ->
            when (ecran) {
                EcranEntree.Accueil -> EcranAccueil(parcours)
                EcranEntree.Connexion -> EcranConnexion(etat, parcours)
            }
        }
    }
}

/** Le cadre d'un écran de l'entrée : le retour à l'accueil ; le titre est dans la page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadreEcran(retour: () -> Unit, contenu: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = retour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retour))
                    }
                },
            )
        },
        content = contenu,
    )
}
