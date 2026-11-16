// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.StockageApparencePreferences
import fr.coprofirst.app.commun.pieces.PiecesLocales
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.ParcoursEntree

/**
 * Point d'entrée de l'app : le parcours d'entrée (qui pose le thème de sa résidence), et
 * les liens d'application qui l'ouvrent (#7) — au lancement comme app ouverte.
 */
class ActiviteAccueil : ComponentActivity() {
    /** Le même parcours que celui de `NavigationEntree` : un par activité, gardé à la rotation. */
    private val parcours: ParcoursEntree by lazy { ViewModelProvider(this, ParcoursEntree.Fabrique)[ParcoursEntree::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Portrait au téléphone, libre sur grand écran ; un pliable qu'on déplie recrée
        // l'activité (changement de taille), et l'orientation se redécide (#69).
        requestedOrientation = Orientation.voulue(resources.configuration.smallestScreenWidthDp)
        enableEdgeToEdge()
        // Un lancement neuf (pas une rotation) : rien ne reste des pièces ouvertes avant.
        if (savedInstanceState == null) PiecesLocales.effacer(applicationContext)
        // Le thème de l'appareil se lit une fois, ici, et se partage (Profil › Apparence).
        val apparence = Apparence(StockageApparencePreferences(applicationContext))
        setContent { NavigationEntree(apparence, parcours) }
        // Un lien suivi une fois : pas de nouveau après une rotation.
        if (savedInstanceState == null) suivre(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        suivre(intent)
    }

    private fun suivre(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(parcours::suivreLien)
    }
}
