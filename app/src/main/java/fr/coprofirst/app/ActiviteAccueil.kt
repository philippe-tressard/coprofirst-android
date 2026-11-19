// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.WindowInfoTracker
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.StockageApparencePreferences
import fr.coprofirst.app.commun.clavier.LocalRaccourcisClavier
import fr.coprofirst.app.commun.clavier.RaccourcisClavier
import fr.coprofirst.app.commun.pieces.PiecesLocales
import fr.coprofirst.app.entree.FabriqueParcoursEntree
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.ParcoursEntree
import fr.coprofirst.app.entree.vitrine.LocalVitrine
import fr.coprofirst.app.entree.vitrine.VitrineDuSite
import fr.coprofirst.app.fonctions.notifications.push.NotificationsPush
import fr.coprofirst.app.navigation.LocalMarquesPages
import fr.coprofirst.app.navigation.LocalPosture
import fr.coprofirst.app.navigation.MarquesPages
import fr.coprofirst.app.navigation.Posture
import fr.coprofirst.app.navigation.posture
import kotlinx.coroutines.flow.map

/**
 * Point d'entrée de l'app : le parcours d'entrée (qui pose le thème de sa résidence), les
 * liens d'application qui l'ouvrent (#7) et les notifications push touchées (#280) — au
 * lancement comme app ouverte.
 */
class ActiviteAccueil : ComponentActivity() {
    /** Le même parcours que celui de `NavigationEntree` : un par activité, gardé à la rotation. */
    private val parcours: ParcoursEntree by lazy { ViewModelProvider(this, FabriqueParcoursEntree)[ParcoursEntree::class.java] }

    /** Les raccourcis du clavier matériel que l'écran affiché offre (#69). */
    private val raccourcis = RaccourcisClavier()

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
        // La vitrine relue sur coprofirst.fr (#153) : posée ici, absente des tests d'interface.
        val vitrine = VitrineDuSite.deLApp(this)
        // Le pli d'un pliable (#242) : sa posture suit la fenêtre ; le marque-page, l'appareil.
        val pliable = MarquesPages.appareilPliable(this)
        val postures = WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this).map { it.posture(pliable) }
        val marques = MarquesPages.de(applicationContext)
        setContent {
            val posture by remember { postures }.collectAsStateWithLifecycle(Posture(pliable))
            CompositionLocalProvider(
                LocalVitrine provides vitrine,
                LocalPosture provides posture,
                LocalMarquesPages provides marques,
                LocalRaccourcisClavier provides raccourcis,
            ) {
                NavigationEntree(apparence, parcours)
            }
        }
        // Un lien suivi une fois : pas de nouveau après une rotation.
        if (savedInstanceState == null) suivre(intent)
    }

    /** Ctrl+touche que l'écran n'a pas prise (#69) : un raccourci qu'il offre. */
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val autres = event.metaState and (KeyEvent.META_SHIFT_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK) != 0
        return raccourcis.declencher(keyCode, event.isCtrlPressed, autres) || super.onKeyShortcut(keyCode, event)
    }

    /** La liste des raccourcis du système (Meta + /) : ceux que l'écran affiché offre. */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        val offerts = raccourcis.offerts().map { KeyboardShortcutInfo(getString(it.libelle), it.touche, KeyEvent.META_CTRL_ON) }
        if (offerts.isNotEmpty()) data += KeyboardShortcutGroup(getString(R.string.nom_app), offerts)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        suivre(intent)
    }

    private fun suivre(intent: Intent?) {
        intent ?: return
        // Une notification touchée : son seul abonnement, posé par Android (app en arrière-plan)
        // ou par `AvertissementPush`. Il ne se suit qu'une fois.
        intent.getStringExtra(NotificationsPush.CLE_ABONNEMENT)?.let { abonnement ->
            intent.removeExtra(NotificationsPush.CLE_ABONNEMENT)
            NotificationsPush.de(applicationContext).residenceDe(abonnement)?.let(parcours::suivreNotification)
            return
        }
        if (intent.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(parcours::suivreLien)
    }
}
