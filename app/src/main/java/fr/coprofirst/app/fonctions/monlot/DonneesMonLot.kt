// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletDuSite
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Ce que « Mes lots & accès » charge une fois et que ses onglets partagent, comme l'iOS
 * (`EtatAcces`) : les droits relus, les lots du compte, le bail du locataire, les baux gérés.
 */
data class DonneesMonLot(
    val droits: DroitsMonLot,
    val lots: List<LotDuCompte> = emptyList(),
    /** Non nulle = on n'a PAS pu regarder les lots — distinct de « aucun lot » (#816 du site). */
    val erreurLots: ErreurService? = null,
    val bail: BailDuLocataire? = null,
    val baux: List<Bail> = emptyList(),
) {
    val bauxActifs: List<Bail> get() = baux.filter { it.enCours }
    val bauxTermines: List<Bail> get() = baux.filter { it.termine }
    val onglets: List<OngletDuSite> get() = droits.onglets(bauxTermines.size)

    companion object {
        /**
         * Le profil d'abord (ce que le compte EST), puis les lots ; le bail et les baux ne
         * bloquent pas l'écran (le site les signale à part), et l'échec des lots ne ferme
         * que les onglets qui les montrent : les accès se lisent à part.
         */
        suspend fun lire(service: ServiceMonLot, droitsConnus: DroitsOnglet): DonneesMonLot = coroutineScope {
            // Un profil illisible garde les droits connus, comme l'iOS (`rafraichirProfil`).
            val droits = essai { service.droits() } ?: DroitsMonLot(null, droitsConnus)
            val lus = async {
                try {
                    Result.success(service.mesLots())
                } catch (erreur: ErreurService) {
                    Result.failure(erreur)
                }
            }
            val bail = if (droits.locataire) essai { service.monBail() } else null
            val lecture = droits.baux
            val baux = lecture?.let { essai { service.baux(tous = it == DroitsMonLot.LectureBaux.Tous) } }.orEmpty()
            val lots = lus.await()
            DonneesMonLot(droits, lots.getOrDefault(emptyList()), lots.exceptionOrNull() as ErreurService?, bail, baux)
        }

        /** Une lecture qui ne bloque pas l'écran : son échec (toujours une `ErreurService`) rend `null`. */
        private suspend fun <T> essai(lire: suspend () -> T): T? = try {
            lire()
        } catch (_: ErreurService) {
            null
        }
    }
}

/**
 * Ce que les onglets d'accès lisent : les deux familles (la vue par locataire les
 * réunit), et, au locataire, ce que son bailleur lui a confié.
 */
data class DonneesAcces(
    val vigiks: List<AccesDetenu>,
    val telecommandes: List<AccesDetenu>,
    val recus: List<AccesDetenu>,
) {
    fun detenus(type: TypeAcces): List<AccesDetenu> = if (type == TypeAcces.Vigik) vigiks else telecommandes

    /** Comme le site : le statut passe à « perdu » sans relire la liste. */
    fun marquerPerdu(acces: AccesDetenu): DonneesAcces {
        fun marque(liste: List<AccesDetenu>) = liste.map { if (it.id == acces.id) it.copy(statut = STATUT_PERDU) else it }
        return copy(vigiks = marque(vigiks), telecommandes = marque(telecommandes))
    }

    companion object {
        const val STATUT_ACTIF = "actif"
        const val STATUT_PERDU = "perdu"

        suspend fun lire(service: ServiceMonLot, droits: DroitsMonLot): DonneesAcces = coroutineScope {
            val vigiks = async { service.mesAcces(TypeAcces.Vigik) }
            val telecommandes = async { service.mesAcces(TypeAcces.Telecommande) }
            val recus = if (droits.locataire) {
                try {
                    service.accesRecus()
                } catch (_: ErreurService) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            DonneesAcces(vigiks.await(), telecommandes.await(), recus)
        }
    }
}
