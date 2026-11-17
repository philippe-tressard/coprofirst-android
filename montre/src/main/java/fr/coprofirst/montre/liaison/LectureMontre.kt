// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.liaison

import android.content.Context
import fr.coprofirst.api.routes.NotificationsApi
import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.app.fonctions.notifications.montre.LiaisonMontre
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ChiffrementKeystore
import fr.coprofirst.app.session.GarderLesLectures
import fr.coprofirst.app.session.LectureDisponible
import fr.coprofirst.app.session.LecturesGardees
import fr.coprofirst.app.session.ListeDesLectures
import fr.coprofirst.montre.cloche.Cloche
import java.io.File
import java.io.IOException
import java.time.Instant

/** Ce que la montre sait de la cloche, pour l'app, la tuile et la complication. */
sealed interface EtatLecture {
    /** Pas reliée, liaison échue ou éteinte, jeton refusé : la montre attend le téléphone. */
    data object NonReliee : EtatLecture

    data class Lue(val residence: String, val adresse: String, val cloche: Cloche) : EtatLecture

    /** Sans réseau : la dernière lecture gardée, et l'heure de sa mise à jour (#28, comme le téléphone). */
    data class HorsLigne(val residence: String, val adresse: String, val cloche: Cloche, val aJourAu: Instant) : EtatLecture

    /** Sans réseau et sans lecture gardée (ou périmée) : rien à montrer. */
    data class Injoignable(val residence: String, val adresse: String) : EtatLecture
}

/**
 * LA lecture de la montre, la même pour l'app, la tuile et la complication : la liaison
 * gardée (`CoffreLiaison`), la cloche lue par le jeton de montre, et la dernière lecture
 * GARDÉE pour le hors-ligne. Le magasin chiffré et sa porte sont ceux du téléphone
 * (`LecturesGardees`, `GarderLesLectures`, :partage-android, #206), avec la liste blanche de
 * la montre : `GET /notifications`, rien d'autre.
 */
class LectureMontre(
    private val coffre: CoffreLiaison,
    private val lectures: LecturesGardees,
    private val lire: suspend (base: String, jeton: String, gardien: GarderLesLectures) -> List<NotificationRecue> =
        { base, jeton, gardien -> ClocheParJeton(base, jeton, gardien = gardien).lire() },
    private val actif: Boolean = LiaisonMontre.ACTIF,
) {
    suspend fun lire(): EtatLecture {
        val liaison = Liaisons.valide(coffre)?.takeIf { actif } ?: return EtatLecture.NonReliee
        val adresse = AdresseInstallation.relire(liaison.adresse) ?: return EtatLecture.NonReliee
        val hote = adresse.url
        val gardien = GarderLesLectures(hote, lectures, LISTE)
        // Chaque lecture retente le réseau : la consultation hors ligne d'avant se referme.
        lectures.quitterLaConsultation(hote)
        return try {
            EtatLecture.Lue(liaison.residence, hote, Cloche.de(lire(adresse.baseApi, liaison.jeton, gardien)))
        } catch (_: JetonRefuse) {
            coffre.effacer()
            lectures.effacer(hote)
            EtatLecture.NonReliee
        } catch (_: IOException) {
            when (val gardee = lectures.consulter(hote)) {
                is LectureDisponible.Gardee -> try {
                    EtatLecture.HorsLigne(liaison.residence, hote, Cloche.de(lire(adresse.baseApi, liaison.jeton, gardien)), gardee.aJourAu)
                } catch (_: IOException) {
                    EtatLecture.Injoignable(liaison.residence, hote)
                }
                else -> EtatLecture.Injoignable(liaison.residence, hote)
            }
        }
    }

    companion object {
        /** Ce que la montre garde : la cloche, seule (le client GÉNÉRÉ en donne le chemin). */
        val LISTE = ListeDesLectures(
            routes = listOf(NotificationsApi("", ListeDesLectures.muet).getNotificationsRequestConfig(null, null).path),
        )

        /** L'alias de la clé du Keystore qui chiffre les lectures gardées de la montre. */
        private const val ALIAS = "coprofirst.montre.lectures"

        /** La lecture de l'app : préférences et fichiers privés, hors de toute sauvegarde. */
        fun de(contexte: Context): LectureMontre {
            val coffre = CoffrePreferences(contexte, ChiffrementKeystore(CoffrePreferences.ALIAS))
            val lectures = LecturesGardees(
                File(contexte.noBackupFilesDir, "lectures"),
                ChiffrementKeystore(ALIAS),
                sessionGardee = { hote -> Liaisons.valide(coffre)?.adresse == hote },
            )
            return LectureMontre(coffre, lectures)
        }
    }
}
