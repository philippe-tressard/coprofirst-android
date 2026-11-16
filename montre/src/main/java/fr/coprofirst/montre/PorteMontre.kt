// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.app.fonctions.notifications.montre.LiaisonMontre
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.montre.cloche.Cloche
import fr.coprofirst.montre.cloche.LectureCloche
import fr.coprofirst.montre.liaison.ClocheParJeton
import fr.coprofirst.montre.liaison.CoffreLiaison
import fr.coprofirst.montre.liaison.JetonRefuse
import fr.coprofirst.montre.liaison.LiaisonGardee
import fr.coprofirst.montre.liaison.Liaisons
import kotlin.coroutines.cancellation.CancellationException

/**
 * La montre à son ouverture : la liaison gardée, si elle vaut encore (`Liaisons.valide`), puis
 * la cloche lue avec son jeton. Un jeton refusé efface la liaison : la montre redit de
 * l'ouvrir par le téléphone. Rien ne se marque comme lu.
 */
@Composable
fun PorteMontre(
    coffre: CoffreLiaison,
    lire: suspend (LiaisonGardee) -> List<NotificationRecue> = ::lireParJeton,
    actif: Boolean = LiaisonMontre.ACTIF,
) {
    var liaison by remember { mutableStateOf(Liaisons.valide(coffre)) }
    val etat = EtatMontre.auLancement(liaison?.let { EtatMontre.Reliee(it.adresse, it.residence) }, actif)
    val lecture by produceState<LectureCloche>(LectureCloche.EnCours, etat) {
        val gardee = liaison?.takeIf { etat is EtatMontre.Reliee } ?: return@produceState
        value = try {
            LectureCloche.Lue(Cloche.de(lire(gardee)))
        } catch (annulee: CancellationException) {
            throw annulee
        } catch (_: JetonRefuse) {
            coffre.effacer()
            liaison = null
            LectureCloche.EnCours
        } catch (_: Exception) {
            LectureCloche.Injoignable
        }
    }
    EcranMontre(etat, lecture)
}

/** La lecture réelle : l'adresse gardée se relit comme une installation, puis la cloche par le jeton. */
private suspend fun lireParJeton(liaison: LiaisonGardee) =
    ClocheParJeton(requireNotNull(AdresseInstallation.relire(liaison.adresse)).baseApi, liaison.jeton).lire()
