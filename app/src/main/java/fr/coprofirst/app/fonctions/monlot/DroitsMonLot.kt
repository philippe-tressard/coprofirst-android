// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import fr.coprofirst.api.modeles.StatutUtilisateur
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletDuSite
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.navigation.ouverts

/**
 * Ce que l'utilisateur EST dans la résidence, pour choisir ce que l'écran affiche et ce
 * qu'il offre — les questions de `roles.ts` (`estLocataire`…) et de `auth.ts` (`isCS`),
 * comme l'iOS (`DroitsAcces`). ⚠️ Elles disent ce qu'on AFFICHE, jamais ce qu'on
 * autorise : le serveur tranche, et un refus s'affiche avec sa phrase.
 */
data class DroitsMonLot(val statut: String?, val onglet: DroitsOnglet) {
    val locataire: Boolean get() = onglet.locataire

    /** Copropriétaire qui LOUE son lot. */
    val bailleur: Boolean get() = statut == StatutUtilisateur.COPROPRIÉTAIRE_BAILLEUR.value

    /** Copropriétaire qui HABITE son lot. */
    val resident: Boolean get() = statut == StatutUtilisateur.COPROPRIÉTAIRE_RÉSIDENT.value
    val coproprietaire: Boolean get() = bailleur || resident

    /** `isCS` : conseil syndical ou administration. */
    val conseil: Boolean get() = onglet.cs

    /** Quels baux CE compte gère (`lireBaux` de `mon-lot`) : les siens (copropriétaire), tous (conseil), aucun sinon. */
    val baux: LectureBaux?
        get() = when {
            coproprietaire -> LectureBaux.Siens
            conseil -> LectureBaux.Tous
            else -> null
        }

    enum class LectureBaux { Siens, Tous }

    /** L'onglet « Gestion locative » (`peutGererLocation`) : bailleur, conseil, ou résident qui a des baux terminés. */
    fun voitLocation(bauxTermines: Int): Boolean = bailleur || conseil || (resident && bauxTermines > 0)

    /** « Nouvel accès » : POST /acces/commandes exige un lot rattaché au compte — offert à qui en a un. */
    fun peutDemander(lots: List<LotDuCompte>): Boolean = lots.isNotEmpty()

    /**
     * Les onglets que ce compte ouvre (`OngletsDuSite.ouverts`), et « Gestion locative »
     * masquée à qui ne la gère pas : ce masquage dépend des DONNÉES (des baux), seul
     * l'écran le sait.
     */
    fun onglets(bauxTermines: Int): List<OngletDuSite> =
        OngletsDuSite.ouverts(PAGE, onglet).filter { it.id != ONGLET_LOCATION || voitLocation(bauxTermines) }

    companion object {
        const val PAGE = "mon-lot"
        const val ONGLET_LOTS = "lots"
        const val ONGLET_BADGES = "badges"
        const val ONGLET_TELECOMMANDES = "telecommandes"
        const val ONGLET_LOCATION = "location"
    }
}

/** Le regroupement des baux par locataire, comme le site (`locataire_id`, sinon le bail seul). */
object GroupesDeBaux {
    data class Groupe(val cle: String, val baux: List<Bail>)

    fun parLocataire(baux: List<Bail>): List<Groupe> =
        baux.groupBy { it.cleLocataire }.map { (cle, liste) -> Groupe(cle, liste) }
}

/** Un locataire, ses baux en cours et les objets qui lui sont confiés (« Vue par locataire »). */
data class GroupeAcces(
    val id: String,
    val nom: String,
    val email: String?,
    val bauxIds: List<Int>,
    val objets: List<AccesDetenu>,
) {
    companion object {
        /**
         * Le groupement du site : courriel normalisé, sinon « prénom|nom » ; les objets
         * d'un groupe sont ceux confiés (`chez_locataire`) au titre de l'un de ses baux.
         */
        fun grouper(baux: List<Bail>, objets: List<AccesDetenu>): List<GroupeAcces> =
            baux.groupBy { bail ->
                bail.locataireEmail?.trim()?.lowercase().orEmpty()
                    .ifEmpty { "${bail.locatairePrenom.orEmpty()}|${bail.locataireNom.orEmpty()}" }
            }.map { (cle, liste) ->
                val ids = liste.map { it.id }
                GroupeAcces(
                    id = cle,
                    nom = liste.first().nomLocataire,
                    email = liste.first().locataireEmail,
                    bauxIds = ids,
                    objets = objets.filter { it.chezLocataire && it.bailId in ids },
                )
            }
    }
}
