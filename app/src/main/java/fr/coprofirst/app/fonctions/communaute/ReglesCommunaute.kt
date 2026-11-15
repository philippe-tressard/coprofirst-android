// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import fr.coprofirst.api.modeles.AnnonceRead
import fr.coprofirst.api.modeles.IdeeRead
import fr.coprofirst.app.commun.LibelleTeinte
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.navigation.ouverts

/** Qui regarde la Communauté (GET /auth/me). */
data class MoiCommunaute(
    val id: Int,
    val droits: DroitsOnglet,
    /**
     * La CONCLUSION du serveur (`communaute_motif_refus`) : remplie quand la Communauté est
     * fermée à ce compte — profil de gestionnaire (l'Avis reste ouvert) ou suspension
     * (tout est fermé). L'app ne recalcule pas la règle.
     */
    val motifRefus: String?,
)

/** Ce que la Communauté ouvre à un compte, comme l'iOS (`EtatCommunaute.Acces`). */
sealed interface AccesCommunaute {
    data object Ouvert : AccesCommunaute

    /** Tout est fermé ; la phrase est celle du serveur, mot pour mot. */
    data class Ferme(val motif: String) : AccesCommunaute

    companion object {
        const val PAGE = "communaute"

        /**
         * Un motif de refus que la réserve des onglets n'explique pas (une suspension) ferme
         * tout, l'Avis compris ; celui d'un gestionnaire laisse l'Avis, que la règle GÉNÉRÉE
         * des onglets lui ouvre.
         */
        fun depuis(moi: MoiCommunaute): AccesCommunaute {
            val motif = moi.motifRefus ?: return Ouvert
            val ouverts = OngletsDuSite.ouverts(PAGE, moi.droits)
            return if (!moi.droits.gestionnaire || ouverts.isEmpty()) Ferme(motif) else Ouvert
        }
    }
}

/** Les règles de la Communauté que le contrat ne sert pas — sur les tables GÉNÉRÉES. */
object ReglesCommunaute {
    /** Un code connu → son libellé ; un code inconnu se lit tel quel (un manque doit se voir). */
    fun libelle(code: String, table: List<Pair<String, String>>): String = table.firstOrNull { it.first == code }?.second ?: code

    /** Un code connu → sa pastille ; inconnu, il s'affiche brut, en gris. */
    fun teinte(code: String, table: List<Pair<String, LibelleTeinte>>): LibelleTeinte =
        table.firstOrNull { it.first == code }?.second ?: LibelleTeinte.brut(code)

    /** Sous le seuil du serveur, l'avis dit ce qui a manqué. */
    fun demandeRemarque(note: Int) = note in TablesCommunaute.NOTE_MIN until TablesCommunaute.NOTE_REMARQUE

    /** À partir de l'autre seuil, l'auteur dit s'il accepte la diffusion. */
    fun demandeConsentement(note: Int) = note >= TablesCommunaute.NOTE_PUBLIABLE

    fun noteValable(note: Int) = note in TablesCommunaute.NOTE_MIN..TablesCommunaute.NOTE_MAX

    /** La boîte à idées : les plus soutenues d'abord, puis le filtre par état (comme le site). */
    fun idees(idees: List<IdeeRead>, statut: String?): List<IdeeRead> =
        idees.sortedByDescending { it.nbVotes ?: 0 }.filter { statut == null || it.statut == statut }

    /** Le vote se voit tout de suite (une bascule) ; le serveur confirme ou défait. */
    fun basculerVote(idees: List<IdeeRead>, id: Int): List<IdeeRead> = idees.map { idee ->
        if (idee.id != id) return@map idee
        val vote = idee.monVote != true
        idee.copy(monVote = vote, nbVotes = ((idee.nbVotes ?: 0) + if (vote) 1 else -1).coerceAtLeast(0))
    }

    /**
     * Les annonces filtrées et triées. Le prix absent passe toujours après les prix (le site
     * le compte 999 999 en croissant, 0 en décroissant : même place).
     */
    fun annonces(annonces: List<AnnonceRead>, type: String?, categorie: String?, tri: String): List<AnnonceRead> {
        val retenues = annonces.filter {
            (type == null || it.typeAnnonce.value == type) && (categorie == null || it.categorie.value == categorie)
        }
        return when (tri) {
            TRI_PRIX_CROISSANT -> retenues.sortedBy { it.prix?.toDouble() ?: Double.MAX_VALUE }
            TRI_PRIX_DECROISSANT -> retenues.sortedByDescending { it.prix?.toDouble() ?: -1.0 }
            else -> retenues.sortedByDescending { it.creeLe }
        }
    }

    /** Le tri par défaut : le premier du site. */
    val triParDefaut: String get() = TablesCommunaute.trisAnnonce.firstOrNull()?.first ?: TRI_RECENT

    private const val TRI_RECENT = "recent"
    private const val TRI_PRIX_CROISSANT = "prix_asc"
    private const val TRI_PRIX_DECROISSANT = "prix_desc"
}

/** Ce qu'on saisit pour une annonce (POST /annonces). */
data class SaisieAnnonce(
    val titre: String = "",
    val description: String = "",
    val type: String = VENTE,
    val categorie: String = DIVERS,
    /** Le prix tel que saisi (virgule ou point) ; lu par `prixLu`. */
    val prix: String = "",
    val negociable: Boolean = false,
    val coordonneesVisibles: Boolean = true,
) {
    /** Le prix lu ; `null` s'il est vide ou illisible. */
    val prixLu: Double? get() = prix.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    /** Le prix est vide, ou un nombre positif. */
    val prixValide: Boolean get() = prix.isBlank() || (prixLu?.let { it >= 0 } ?: false)

    val complete: Boolean get() = titre.isNotBlank() && description.isNotBlank() && (type != VENTE || prixValide)

    /** La description, au HTML de l'éditeur du site. */
    val descriptionHtml: String get() = TexteDuSite.html(saisi = description.trim())

    companion object {
        const val VENTE = "vente"
        const val DIVERS = "divers"
    }
}

/** Ce qu'on saisit pour un avis (POST /avis, PATCH /avis/{id}). */
data class SaisieAvis(
    val note: Int = 0,
    val titre: String = "",
    val description: String = "",
    val remarque: String = "",
    /** Sans réponse par défaut, comme sur le site. */
    val consentement: Boolean? = null,
) {
    /** La remarque n'est envoyée que sous le seuil ; hors seuil, rien. */
    val remarqueEnvoyee: String? get() = if (ReglesCommunaute.demandeRemarque(note)) remarque.trim() else null

    /** L'accord de diffusion n'est envoyé qu'à partir de l'autre seuil. */
    val consentementEnvoye: Boolean? get() = if (ReglesCommunaute.demandeConsentement(note)) consentement else null

    val descriptionHtml: String get() = TexteDuSite.html(saisi = description.trim())

    /** Le bouton s'ouvre quand le serveur n'a plus de raison de refuser. */
    val complete: Boolean get() = titre.isNotBlank() && description.isNotBlank() && ReglesCommunaute.noteValable(note) &&
        (!ReglesCommunaute.demandeRemarque(note) || !remarqueEnvoyee.isNullOrEmpty()) &&
        (!ReglesCommunaute.demandeConsentement(note) || consentement != null)
}
