// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.api.modeles.AppRoutersTicketsMessagesSchemasMessageRead
import fr.coprofirst.api.modeles.CrPublieRead
import fr.coprofirst.api.modeles.SyntheseEtat
import fr.coprofirst.api.modeles.TicketEvolutionRead
import fr.coprofirst.api.modeles.TicketRead
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.lecture.LieuServi
import fr.coprofirst.app.commun.texte.Separateur
import java.time.Instant

/**
 * Une affaire — ou une actualité, qui en EST une de catégorie Actualité — telle que sa
 * fiche la montre (GET /tickets/{id}, ses suites, ses messages), comme l'iOS
 * (`DetailAffaire`). Les gestes permis sont SERVIS (`peut_*`) : jamais déduits du profil.
 *
 * ⚠️ Pour l'heure dans le paquet des affaires (la fiche se déclare à la porte : `"affaire"`) : l'Espace CS (#24) la réclamera par un
 * ticket `commun` quand il ouvrira une affaire à son tour.
 */
data class DetailAffaire(
    val id: Int,
    val numero: String,
    val titre: String,
    /** Du HTML de l'éditeur du site : il passe par `TexteRiche`. */
    val description: String,
    val categorie: String,
    val statut: String,
    val statutLibelle: String? = null,
    val urgente: Boolean = false,
    val creeLe: Instant,
    val auteur: String? = null,
    /** Le lieu SERVI (`perimetre_libelle`) ; `null` pour le périmètre par défaut. */
    val lieu: String? = null,
    /** Ses photos et documents servis (`photos_urls` + `fichiers_urls`). */
    val pieces: List<String> = emptyList(),
    val suites: List<SuiteAffaire> = emptyList(),
    /** Le fil des messages — vide sur une actualité. */
    val messages: List<MessageAffaire> = emptyList(),
    val droits: DroitsServisAffaire = DroitsServisAffaire(),
    val epingle: Boolean = false,
    val archivee: Boolean = false,
    /** Fusionnée dans une autre : on n'y répond plus. */
    val fusionnee: Boolean = false,
    val perimetres: List<String> = emptyList(),
    /** Le suivi d'une actualité (`suivi_actualite`) et son libellé servi ; vide sans suivi. */
    val suiviActualite: String? = null,
    val suiviActualiteLibelle: String? = null,
    /** Ses prestataires, dans l'ordre servi (`prestataires`) : le premier est celui du Kanban. */
    val prestataires: List<PrestataireAffaire> = emptyList(),
    /** Le rythme SERVI (`frequence_libelle`) : celui du contrat sous contrat, celui de l'affaire sinon. */
    val rythme: String? = null,
    /** Close au sens du SERVEUR (`TicketRead.close`). */
    val close: Boolean = false,
    /** Les référentiels lus avec elle : libellés des états de sa suite. */
    val referentiels: ReferentielsAffaires = ReferentielsAffaires.VIDES,
    /** Les comptes rendus où elle a été abordée ; `null` si leur lecture a échoué (l'écran le dit). */
    val comptesRendus: List<CompteRenduAffaire>? = emptyList(),
    /** Sa synthèse, d'une affaire close seulement. */
    val synthese: SyntheseAffaire? = null,
    /** « Qui la lit », servie (`lecture`). */
    val lecture: Lecture? = null,
) {
    val estActualite: Boolean get() = categorie == TablesAffaires.CATEGORIE_ACTUALITE

    /** Une ligne par prestataire, le rythme après le dernier — rien sans prestataire. */
    val intervenants: List<String>
        get() {
            val noms = prestataires.map { it.nom }.filter { it.isNotBlank() }
            if (noms.isEmpty()) return emptyList()
            val dernier = listOfNotNull(noms.last(), rythme?.takeIf { it.isNotBlank() }).joinToString(Separateur.point)
            return noms.dropLast(1) + dernier
        }

    /**
     * L'état que la fiche montre (`etatAffiche` du site) : le statut d'une affaire, le suivi
     * d'une actualité — aucun pour une actualité sans suivi.
     */
    val etat: EtatServi?
        get() {
            val libelleSuivi = suiviActualiteLibelle
            if (!estActualite || libelleSuivi == null) return EtatServi(statut, referentiels.libelle(statut, statutLibelle))
            val suivi = suiviActualite
            return if (suivi == null || libelleSuivi.isEmpty()) null else EtatServi(suivi, libelleSuivi)
        }

    companion object {
        /** L'affaire servie et ses suites, par les types générés. */
        fun lue(t: TicketRead, evolutions: List<TicketEvolutionRead>) = DetailAffaire(
            id = t.id, numero = t.numero, titre = t.titre, description = t.description, categorie = t.categorie,
            statut = t.statut, statutLibelle = t.statutLibelle, urgente = t.urgente, creeLe = t.creeLe.toInstant(),
            auteur = t.saisiPourAffichage ?: t.auteurNom, lieu = LieuServi.lieu(t.perimetreLibelle),
            pieces = t.photosUrls.orEmpty() + t.fichiersUrls.orEmpty(),
            suites = evolutions.map(SuiteAffaire::depuis).sortedBy { it.date },
            droits = DroitsServisAffaire(
                repondre = t.peutRepondre ?: false, changerEtat = t.peutChangerEtat ?: false,
                modifier = t.peutModifier ?: false, archiver = t.peutArchiver ?: false,
                corriger = t.peutCorriger ?: (t.peutModifier ?: false),
                changerPrestataires = t.peutChangerPrestataires ?: false,
            ),
            epingle = t.epingle ?: false, archivee = t.archivee ?: false, fusionnee = t.fusionnee ?: false,
            perimetres = t.perimetreCible.orEmpty(), suiviActualite = t.suiviActualite,
            suiviActualiteLibelle = t.suiviActualiteLibelle,
            prestataires = t.prestataires.orEmpty().map { PrestataireAffaire(it.id, it.nom) },
            rythme = t.contrat?.frequenceLibelle ?: t.frequenceLibelle, close = t.close,
            lecture = t.lecture?.let(Lecture::depuis),
        )
    }
}

/** Un état d'affaire : son CODE (la teinte) et son libellé en clair, servi. */
data class EtatServi(val code: String, val libelle: String)

/** Ce que la route accepte de CE lecteur, servi avec l'affaire (`peut_*`). Absent : « non ». */
data class DroitsServisAffaire(
    val repondre: Boolean = false,
    val changerEtat: Boolean = false,
    val modifier: Boolean = false,
    val archiver: Boolean = false,
    val corriger: Boolean = false,
    /** Ajouter, retirer, réordonner ses prestataires (`peut_changer_prestataires`, #182) — le droit SERVI seul. */
    val changerPrestataires: Boolean = false,
)

/** Un prestataire d'une affaire, tel que servi (`PrestataireDeLAffaire`, comme l'iOS `PrestataireAffaire`). */
data class PrestataireAffaire(val id: Int?, val nom: String)

/** Une entrée de la suite : une suite écrite, ou un changement d'état. */
data class SuiteAffaire(
    val id: Int,
    val type: String,
    val date: Instant,
    val auteur: String? = null,
    val contenu: String? = null,
    val ancienStatut: String? = null,
    val nouveauStatut: String? = null,
    /** L'état qu'elle donne, en clair, servi (`nouveau_statut_libelle`). */
    val nouveauStatutLibelle: String? = null,
    /** Le lieu qu'elle précise, servi (`perimetre_precise_libelle`) ; `null` sans précision. */
    val perimetrePrecise: String? = null,
    val pieces: List<String> = emptyList(),
    val peutModifier: Boolean = false,
    val peutSupprimer: Boolean = false,
    /** Son auteur (`auteur_id`) : on ne signale pas ce qu'on a écrit ; « Masquer ce que publie… » le vise (#282). */
    val auteurId: Int? = null,
) {
    /** « Ouvert → Chez le syndic » — le nouvel état servi avec elle, l'ancien par les référentiels. */
    fun changementDEtat(servis: ReferentielsAffaires): String? {
        val nouveau = nouveauStatut?.let { servis.libelle(it, nouveauStatutLibelle) } ?: return null
        return ancienStatut?.let { "${servis.libelle(it)}${Separateur.fleche}$nouveau" } ?: nouveau
    }

    companion object {
        fun depuis(e: TicketEvolutionRead) = SuiteAffaire(
            id = e.id, type = e.type, date = e.creeLe.toInstant(), auteur = e.auteurNom, contenu = e.contenu,
            ancienStatut = e.ancienStatut ?: e.statutAvant, nouveauStatut = e.nouveauStatut,
            nouveauStatutLibelle = e.nouveauStatutLibelle, perimetrePrecise = e.perimetrePreciseLibelle.takeIf { it.isNotEmpty() },
            pieces = e.fichiersUrls.orEmpty(), peutModifier = e.peutModifier ?: false, peutSupprimer = e.peutSupprimer ?: false,
            auteurId = e.auteurId,
        )
    }
}

/**
 * Un message du fil (`TicketMessage`) — à ne pas confondre avec une suite : deux tables,
 * deux fils, comme sur le site. `deMoi` : `est_auteur`, tel que le serveur le juge.
 */
data class MessageAffaire(
    val id: Int,
    val contenu: String,
    val date: Instant,
    val deMoi: Boolean,
    val nom: String?,
    val qualite: String?,
    val interne: Boolean,
    val pieces: List<String> = emptyList(),
) {
    /** Le nom servi, puis la qualité servie ; « Vous » se dit par l'écran. */
    val auteurServi: String? get() = nom?.takeIf { it.isNotBlank() } ?: qualite

    companion object {
        fun depuis(m: AppRoutersTicketsMessagesSchemasMessageRead) = MessageAffaire(
            m.id, m.contenu, m.creeLe.toInstant(), m.estAuteur ?: false, m.auteurNom, m.auteurQualite, m.interne,
            m.fichiersUrls.orEmpty(),
        )
    }
}

/** Un compte rendu validé où l'affaire a été abordée (`CrPublieRead`). */
data class CompteRenduAffaire(val id: Int, val debut: Instant, val ou: String, val contenu: String, val conseilSeul: Boolean) {
    companion object {
        fun depuis(c: CrPublieRead) = CompteRenduAffaire(c.crId, c.debut.toInstant(), c.ou, c.contenu, c.conseilSeul)
    }
}

/** La synthèse d'une affaire close : conclusion, difficultés, amélioration. */
data class SyntheseAffaire(
    val statut: String,
    val texte: String?,
    val difficultes: String?,
    val amelioration: String?,
    val valideeLe: Instant?,
) {
    val validee: Boolean get() = statut == "validee"

    companion object {
        /** `null` quand le serveur n'en sert pas. */
        fun depuis(e: SyntheseEtat): SyntheseAffaire? = e.synthese?.let {
            SyntheseAffaire(it.statut, it.synthese, it.difficultes, it.amelioration, it.valideeLe?.toInstant())
        }
    }
}
