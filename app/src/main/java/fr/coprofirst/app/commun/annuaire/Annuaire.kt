// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.annuaire

import fr.coprofirst.api.modeles.AnnuaireLu
import fr.coprofirst.app.commun.formats.FormatsDuSite

/**
 * L'annuaire de la résidence (GET /admin/annuaire), comme l'iOS (`Annuaire`) : le conseil
 * syndical et le syndic. Chaque champ est celui que le serveur sert. Commun (#97) : l'Annuaire
 * des résidents et l'« Annuaire CS & Syndic » de l'Espace CS rendent la même personne.
 */
data class Annuaire(
    val cs: ConseilSyndical,
    val syndic: Syndic,
    val whatsappUrl: String? = null,
) {
    data class ConseilSyndical(val agAnnee: Int? = null, val agDate: String? = null, val membres: List<Membre> = emptyList())

    data class Syndic(
        val nomSyndic: String? = null,
        val adresse: String? = null,
        val siteWeb: String? = null,
        val membres: List<Membre> = emptyList(),
    )

    /** Une personne de l'annuaire : membre du conseil ou contact du syndic. */
    data class Membre(
        val id: Int,
        val genre: String? = null,
        val prenom: String? = null,
        val nom: String? = null,
        /** « Prénom NOM », servi (`nom_affiche`) — l'app ne le compose pas. */
        val nomServi: String? = null,
        val batimentNom: String? = null,
        val estGestionnaireSite: Boolean = false,
        val estPresident: Boolean = false,
        val photoUrl: String? = null,
        val fonction: String? = null,
        val email: String? = null,
        /** Les numéros, servis dédoublonnés (`telephones`). */
        val telephones: List<String> = emptyList(),
        val estPrincipal: Boolean = false,
    ) {
        /** La civilité puis le nom affiché, comme la carte du site (`CarteContact.svelte`). */
        val nomComplet: String
            get() {
                val lu = FormatsDuSite.nom(nomServi, prenom, nom).takeIf { it != FormatsDuSite.TIRET }
                return listOfNotNull(genre, lu).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            }

        val initiales: String get() = listOfNotNull(prenom, nom).mapNotNull { it.trim().firstOrNull() }.joinToString("").uppercase()
    }

    /**
     * Les membres du conseil par bâtiment, dans l'ordre servi, « sans bâtiment » en dernier
     * — un seul groupe quand la résidence n'a qu'un bâtiment (le site ne groupe pas alors).
     */
    val groupesCS: List<Pair<String?, List<Membre>>>
        get() {
            val groupes = cs.membres.groupBy { it.batimentNom }.toList()
            return groupes.filter { it.first != null } + groupes.filter { it.first == null }
        }
}

private fun String?.nonVide(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** L'annuaire tel que le contrat le type (`AnnuaireLu`). */
fun AnnuaireLu.lu() = Annuaire(
    cs = Annuaire.ConseilSyndical(
        agAnnee = cs.agAnnee,
        agDate = cs.agDate,
        membres = cs.membres.map {
            Annuaire.Membre(
                id = it.id, genre = it.genre.nonVide(), prenom = it.prenom, nom = it.nom, nomServi = it.nomAffiche,
                batimentNom = it.batimentNom.nonVide(), estGestionnaireSite = it.estGestionnaireSite,
                estPresident = it.estPresident, photoUrl = it.photoUrl.nonVide(),
            )
        },
    ),
    syndic = Annuaire.Syndic(
        nomSyndic = syndic.nomSyndic.nonVide(),
        adresse = syndic.adresse.nonVide(),
        siteWeb = syndic.siteWeb.nonVide(),
        membres = syndic.membres.map {
            Annuaire.Membre(
                id = it.id, genre = it.genre.nonVide(), prenom = it.prenom, nom = it.nom, nomServi = it.nomAffiche,
                photoUrl = it.photoUrl.nonVide(), fonction = it.fonction.nonVide(), email = it.email.nonVide(),
                telephones = it.telephones.mapNotNull { t -> t.nonVide() }.distinct(), estPrincipal = it.estPrincipal,
            )
        },
    ),
    whatsappUrl = whatsappUrl.nonVide(),
)
