// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.fil

import fr.coprofirst.api.modeles.FluxItem
import fr.coprofirst.app.charte.NatureAffaire
import fr.coprofirst.app.charte.NatureDuFil
import fr.coprofirst.app.charte.NaturesAffaire
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.pieces.PiecesJointes
import fr.coprofirst.app.commun.texte.Recherche
import java.time.Instant
import java.time.LocalDate

/**
 * Ce qu'ouvre un toucher sur une carte du fil, comme l'iOS (`CibleDuFil`) — la règle du site
 * (`typeLink`, `front/src/lib/flux.ts`) : une affaire (et une actualité, qui EST une affaire)
 * s'ouvre sur son détail ; un sondage sur le sondage ; un document qui a une page, sur son
 * fichier ; tout le reste — ou ce qui n'a pas de page sur le site — sur un résumé.
 */
sealed interface CibleDuFil {
    data class Affaire(val id: Int) : CibleDuFil
    data class Sondage(val id: Int) : CibleDuFil
    data class Document(val id: Int, val nom: String?) : CibleDuFil

    /** `lien` : la page du site, s'il en a une (« Voir sur le site »). */
    data class Resume(val lien: String?) : CibleDuFil

    companion object {
        val TYPES_AFFAIRE = setOf("ticket_ouvert", "ticket_resolu", "ticket_mis_a_jour", "publication")
        val TYPES_SONDAGE = setOf("sondage_ouvert", "sondage_clos")

        fun pour(type: String, meta: MetaDuFil, lien: String?): CibleDuFil = when {
            type in TYPES_AFFAIRE && meta.ticketId != null -> Affaire(meta.ticketId)
            type in TYPES_SONDAGE && meta.sondageId != null -> Sondage(meta.sondageId)
            // Un document SANS lien n'a pas de page sur le site (catégorie non exposée) : le site
            // n'en montre rien de plus, l'app non plus.
            type == "document" && meta.documentId != null && lien != null -> Document(meta.documentId, meta.fichierNom)
            else -> Resume(lien)
        }
    }
}

/** Un élément du fil de la résidence — lu dans `FluxItem` de GET /flux, comme l'iOS (`ElementDuFil`). */
data class ElementDuFil(
    /** `type:id`, JAMAIS l'id seul (`cleFluxItem` du site) : deux tables peuvent servir le même id. */
    val id: String,
    val type: String,
    val date: Instant,
    val titre: String,
    /** Du HTML de l'éditeur du site : `TexteDuSite.apercu` sur la carte, `TexteRiche` au résumé. */
    val detail: String?,
    val badges: List<String> = emptyList(),
    val meConcerne: Boolean = false,
    val cible: CibleDuFil,
    val meta: MetaDuFil = MetaDuFil(),
) {
    val nature: NatureDuFil get() = NatureDuFil.de(type)
    val echeance: EcheanceDuFil? get() = meta.echeance
    val pieces: PiecesJointes get() = meta.pieces

    /** La règle du site (`estUrgent`) : une affaire ouverte de priorité haute, ou une actualité urgente non résolue. */
    val urgent: Boolean
        get() = (type == "ticket_ouvert" && "urgent" in badges) ||
            (type == "publication" && meta.urgente && meta.statut != "resolu")

    /**
     * La NATURE de l'affaire que porte la carte (`natureDe` du site, #78) : l'actualité prime,
     * puis la date — l'ordre de la table. `null` pour une rubrique sans nature.
     */
    val natureDeLAffaire: NatureAffaire?
        get() = if (meta.natures.isEmpty()) null else NaturesAffaire.toutes.firstOrNull { it.code in meta.natures } ?: NaturesAffaire.toutes.last()

    /** Le pictogramme de la gouttière (`classeDuFlux` du site) : celui de la nature d'une affaire, de la rubrique sinon. */
    val pictogramme: String get() = natureDeLAffaire?.icone ?: nature.icone

    /**
     * La nature à montrer en pastille : celle de l'affaire, sauf si la rubrique la dit déjà
     * (« Affaire mise à jour » pour une nature « Affaire ») — rien ne s'écrit deux fois.
     */
    val natureEnPastille: NatureAffaire?
        get() {
            val classe = natureDeLAffaire ?: return null
            return classe.takeUnless { Recherche.replier(nature.libelle).startsWith(Recherche.replier(classe.libelle)) }
        }

    companion object {
        fun depuis(item: FluxItem): ElementDuFil {
            val meta = MetaDuFil.lire(item.meta)
            val cle = item.id?.takeIf { it.isNotEmpty() } ?: item.date.toEpochSecond().toString()
            return ElementDuFil(
                id = "${item.type}:$cle",
                type = item.type,
                date = item.date.toInstant(),
                titre = item.titre,
                detail = item.detail,
                badges = item.badges.orEmpty().filterNotNull(),
                meConcerne = item.meConcerne ?: false,
                cible = CibleDuFil.pour(item.type, meta, item.lien),
                meta = meta,
            )
        }
    }
}

/** Une journée du fil : son jour à Paris (`2026-11-15`), ses éléments du plus récent au plus ancien. */
data class JourDuFil(val jour: String, val elements: List<ElementDuFil>)

/** Le fil rangé jour par jour, comme l'iOS (`Chronologie`) — le jour de Paris, comme tout le site. */
object Chronologie {
    enum class Veille { Aujourdhui, Hier, Avant }

    fun parJour(elements: List<ElementDuFil>): List<JourDuFil> =
        elements.groupBy { FormatsDuSite.jourIso(it.date) }
            .map { (jour, liste) -> JourDuFil(jour, liste.sortedByDescending { it.date }) }
            .sortedByDescending { it.jour }

    /** Ce jour est-il aujourd'hui, hier, ou avant ? */
    fun quand(jour: String, maintenant: Instant = Instant.now()): Veille {
        val aujourdhui = FormatsDuSite.jourIso(maintenant)
        return when (jour) {
            aujourdhui -> Veille.Aujourdhui
            LocalDate.parse(aujourdhui).minusDays(1).toString() -> Veille.Hier
            else -> Veille.Avant
        }
    }
}
