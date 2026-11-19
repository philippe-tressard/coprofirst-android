// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import android.os.Build
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.coprofirst.app.commun.audience.AudienceDeLApp
import fr.coprofirst.app.commun.pieces.PiecesLocales
import fr.coprofirst.app.fonctions.notifications.montre.LiaisonsMontre
import fr.coprofirst.app.fonctions.notifications.push.NotificationsPush
import fr.coprofirst.app.navigation.MarquesPages
import fr.coprofirst.app.session.AbonnesSession
import fr.coprofirst.app.session.ChiffrementKeystore
import fr.coprofirst.app.session.CoffreKeystore
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.LecturesGardees
import fr.coprofirst.app.session.ReseauDuSysteme
import fr.coprofirst.app.session.SessionsGardees
import fr.coprofirst.app.session.SessionsGardeesKeystore
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.StockagePreferences
import java.io.File

/**
 * Le parcours d'entrée de l'app, monté sur l'appareil : le stockage, le coffre et les
 * sessions gardées du Keystore, les lectures gardées, le réseau, la mesure d'audience.
 * Sorti de `ParcoursEntree` pour le garder sous 500 lignes (standards/02 §6).
 */
val FabriqueParcoursEntree = viewModelFactory {
    initializer {
        val application = this[APPLICATION_KEY]!!
        val stockage = StockagePreferences(application)
        // Garder une session demande Android 11 (le code de l'appareil y déverrouille
        // une clé) : avant, la connexion seule (divergence déclarée, `SessionsGardeesKeystore`).
        val gardees = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SessionsGardeesKeystore(application)
        } else {
            SessionsGardees.Indisponibles
        }
        // UNE session HTTP par hôte pour toute l'app : la connexion y pose ses
        // cookies, les écrans des menus les y relisent.
        // Les lectures gardées pour la consultation hors ligne (#28) : chiffrées, hors
        // de toute sauvegarde, seulement pour une résidence dont la session est gardée.
        val lectures = LecturesGardees(
            File(application.noBackupFilesDir, "lectures-gardees"),
            ChiffrementKeystore("fr.coprofirst.app.lectures"),
            sessionGardee = gardees::contient,
        )
        val sessions = SessionsParHote(gardees = gardees, lectures = lectures)
        ParcoursEntree(
            Decouverte(), sessions, Connexion(sessions), InstallationsConnues(stockage, CoffreKeystore(application)),
            reseau = ReseauDuSysteme(application),
            effacerLesPieces = { PiecesLocales.effacer(application) },
            avantSortie = { AudienceDeLApp.de(application).vider() },
            apresSortie = { AudienceDeLApp.de(application).oublier(it.url) },
            // Les abonnés au cycle des résidences (#150) : la seule liste.
            abonnes = AbonnesSession(listOf(NotificationsPush.de(application), LiaisonsMontre.de(application), MarquesPages.de(application))),
        )
    }
}
