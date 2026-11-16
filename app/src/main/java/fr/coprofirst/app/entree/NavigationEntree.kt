// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.isSystemInDarkTheme
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Mouvement
import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.navigation.EcranResidence

/** Les gestes des écrans de l'entrée — une interface, pour que les aperçus s'en passent. */
interface ActionsEntree {
    fun entrer()
    fun entrerDemo()
    fun saisir(texte: String)

    /** Le texte d'un QR code lu par la caméra (#6) : il remplit « Votre résidence ». */
    fun lireQR(texte: String)
    fun saisirEmail(texte: String)
    fun saisirMotDePasse(texte: String)
    fun basculerVoirMotDePasse()
    fun seConnecter()
    fun basculerSecondAccord()
    fun confirmerHote()
    fun renoncerHote()
    fun deconnecter()
    fun retour()
    fun ouvrirDOffice()
    fun ouvrirSessionGardee()
    fun utiliserMotDePasse()
    fun activerGarde()
    fun fermerGarde()

    /** Pour les aperçus : aucun geste ne fait rien. */
    object Inertes : ActionsEntree {
        override fun entrer() = Unit
        override fun entrerDemo() = Unit
        override fun saisir(texte: String) = Unit
        override fun lireQR(texte: String) = Unit
        override fun saisirEmail(texte: String) = Unit
        override fun saisirMotDePasse(texte: String) = Unit
        override fun basculerVoirMotDePasse() = Unit
        override fun seConnecter() = Unit
        override fun basculerSecondAccord() = Unit
        override fun confirmerHote() = Unit
        override fun renoncerHote() = Unit
        override fun deconnecter() = Unit
        override fun retour() = Unit
        override fun ouvrirDOffice() = Unit
        override fun ouvrirSessionGardee() = Unit
        override fun utiliserMotDePasse() = Unit
        override fun activerGarde() = Unit
        override fun fermerGarde() = Unit
    }
}

/**
 * Le passage d'un écran à l'autre : un fondu et un léger glissement dans le sens du
 * parcours, 220 ms, courbe ease-out marquée. Il est rare (quelques fois par session) :
 * il dit d'où l'on vient. L'échelle d'animation du système le réduit ou le supprime.
 */
private const val DUREE_MS = 220

@Composable
fun NavigationEntree(apparence: Apparence, parcours: ParcoursEntree = viewModel(factory = ParcoursEntree.Fabrique)) {
    val etat by parcours.etat.collectAsStateWithLifecycle()
    // La biométrie se demande par l'activité affichée : posée ici, retirée à sa sortie.
    val deverrouillage = rememberDeverrouillage(etat.reconnue)
    DisposableEffect(parcours, deverrouillage) {
        parcours.deverrouillage = deverrouillage
        onDispose { if (parcours.deverrouillage === deverrouillage) parcours.deverrouillage = null }
    }
    // L'espace d'une résidence ouverte ne se quitte que par « Se déconnecter » ; le retour
    // système y ferme l'app, comme sur un écran racine.
    BackHandler(enabled = etat.ecran == EcranEntree.Connexion) { parcours.retour() }
    // La palette de la résidence s'applique dès qu'elle est reconnue, comme sur iOS.
    val palette = Palettes.pour((etat.ouverte ?: etat.reconnue)?.codePalette)
    // Le thème de l'appareil (Profil › Apparence), appliqué à la racine comme sur iOS.
    val choix by apparence.choix.collectAsStateWithLifecycle()
    ThemeCoproFirst(palette, sombre = apparence.sombre(choix, isSystemInDarkTheme())) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = etat.ecran,
                transitionSpec = {
                    val sens = if (targetState >= initialState) 1 else -1
                    (fadeIn(tween(DUREE_MS, easing = Mouvement.sortie)) +
                        slideInHorizontally(tween(DUREE_MS, easing = Mouvement.sortie)) { sens * it / 12 })
                        .togetherWith(fadeOut(tween(DUREE_MS / 2)))
                },
                label = "entree",
            ) { ecran ->
                when (ecran) {
                    EcranEntree.Accueil -> EcranAccueil(parcours)
                    EcranEntree.Retour -> EcranRetour(etat, parcours)
                    EcranEntree.Connexion -> EcranConnexion(etat, parcours)
                    EcranEntree.Espace -> parcours.sessionOuverte()?.let { EcranResidence(it, parcours, apparence) }
                }
            }
            // « Garder la session ? », proposée une fois après la première connexion.
            val ouverte = etat.ouverte
            if (etat.proposerGarde && etat.ecran == EcranEntree.Espace && ouverte != null) {
                FeuilleGardeSession(ouverte, etat, parcours)
            }
        }
    }
}

/** Le cadre d'un écran de l'entrée : le retour, s'il y en a un ; le titre est dans la page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadreEcran(retour: (() -> Unit)?, contenu: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (retour != null) {
                        IconButton(onClick = retour) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retour))
                        }
                    }
                },
            )
        },
        content = contenu,
    )
}
