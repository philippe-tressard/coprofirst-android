// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.api.modeles.AnnonceHallArchive
import fr.coprofirst.api.modeles.AnnonceHallCreate
import fr.coprofirst.api.modeles.AnnonceHallRead
import fr.coprofirst.api.routes.AnnoncesHallApi
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant

/**
 * Ce que l'onglet « Annonces Hall » demande, comme l'iOS (`ServiceAnnoncesHallCS`) : les
 * affiches déjà produites, leurs gestes, et la création d'une affiche (PDF rendu et envois
 * faits par le serveur).
 */
interface ServiceAnnoncesHall {
    /** GET /annonces-hall?archivees= : les affiches en cours, ou les archivées. */
    suspend fun annonces(archivees: Boolean): List<AnnonceHall>

    /** POST /annonces-hall : l'affiche créée. */
    suspend fun creer(saisie: SaisieAnnonceHall): AnnonceHall

    /** PATCH /annonces-hall/{id} : archiver, ou restaurer une affiche archivée à la main. */
    suspend fun archiver(id: Int, archivee: Boolean)

    /** POST /annonces-hall/{id}/renvoyer-email : de nouveau aux membres du CS du périmètre. */
    suspend fun renvoyer(id: Int)

    /** DELETE /annonces-hall/{id} (administrateur) : la ligne et son PDF ; ses images restent. */
    suspend fun supprimer(id: Int)
}

class ServiceAnnoncesHallApi(private val session: SessionOuverte) : ServiceAnnoncesHall {
    override suspend fun annonces(archivees: Boolean) =
        session.appeler { b, c -> AnnoncesHallApi(b, c).getAnnoncesHall(archivees).map { it.lu() } }

    override suspend fun creer(saisie: SaisieAnnonceHall) = session.appeler { b, c ->
        val corps = AnnonceHallCreate(
            titre = saisie.titre.trim(),
            message = TexteDuSite.html(saisi = saisie.message),
            formatDemande = saisie.format,
            images = emptyList(),
            envoyerCs = saisie.envoyerCS,
            envoyerSyndic = saisie.envoyerSyndic,
            envoyerAuteur = saisie.envoyerAuteur,
            partagerWhatsapp = false,
        )
        AnnoncesHallApi(b, c).postAnnoncesHall(corps).lu()
    }

    override suspend fun archiver(id: Int, archivee: Boolean) {
        session.appeler { b, c -> AnnoncesHallApi(b, c).patchAnnoncesHallAnnonceId(id, AnnonceHallArchive(archivee)) }
    }

    override suspend fun renvoyer(id: Int) {
        session.appeler { b, c -> AnnoncesHallApi(b, c).postAnnoncesHallAnnonceIdRenvoyerEmail(id) }
    }

    override suspend fun supprimer(id: Int) {
        session.appeler { b, c -> AnnoncesHallApi(b, c).deleteAnnoncesHallAnnonceId(id) }
    }
}

/** Les affiches en cours, puis les archivées, lues ensemble. */
suspend fun ServiceAnnoncesHall.toutes(): List<AnnonceHall> = coroutineScope {
    val actives = async { annonces(archivees = false) }
    val archivees = async { annonces(archivees = true) }
    actives.await() + archivees.await()
}

/** Une affiche, telle que le serveur la sert (`AnnonceHallRead`). */
data class AnnonceHall(
    val id: Int,
    val titre: String,
    /** Le texte de l'éditeur du site (HTML) : `TexteRiche`, jamais `Text(html)`. */
    val message: String,
    /** Le texte simple, composé par le serveur pour une carte. */
    val apercu: String,
    val formatLabel: String,
    val destinataires: List<String> = emptyList(),
    val envoyeLe: Instant? = null,
    /** L'état EFFECTIF : archivée à la main, ou par le temps. */
    val archivee: Boolean = false,
    /** L'actualité reprise, s'il y en a une. */
    val ticketId: Int? = null,
    val creeLe: Instant? = null,
    val auteurNom: String = "",
    val assisteIa: Boolean = false,
    /** Les gestes que le serveur ouvre au compte qui lit. */
    val peutArchiver: Boolean = false,
    val peutRestaurer: Boolean = false,
    val peutSupprimer: Boolean = false,
    /** Le lieu servi : `""` pour le défaut, qui se tait (`BadgePerimetre` du site). */
    val perimetreLibelle: String = "",
) {
    val lieu: String? get() = perimetreLibelle.takeIf { it.isNotBlank() }
}

/** Ce qu'on peut faire d'une affiche, depuis sa carte. */
enum class GesteAnnonceHall { Renvoyer, Archiver, Supprimer }

/**
 * La saisie de « Nouvelle annonce » (`AnnonceHallCreate`), sans photo ni assistant — ceux-là
 * restent au site, comme l'iOS. La Diffusion : la valeur par défaut d'un envoi est « ne
 * pas envoyer ».
 */
data class SaisieAnnonceHall(
    val titre: String = "",
    /** Du texte simple, envoyé en paragraphes (`TexteDuSite.html(saisi)`). */
    val message: String = "",
    val format: String = TablesEspaceCS.formatsAffiche.first().first,
    val envoyerCS: Boolean = false,
    val envoyerSyndic: Boolean = false,
    val envoyerAuteur: Boolean = false,
) {
    /** La règle de `_valider` côté API : un titre, et un message (sans photo, ici). */
    val complete: Boolean get() = titre.isNotBlank() && message.isNotBlank()
}

internal fun AnnonceHallRead.lu() = AnnonceHall(
    id, titre, message, apercu, formatLabel, destinataires, envoyeLe?.toInstant(), archivee, ticketId,
    creeLe.toInstant(), auteurNom, assisteIa == true, peutArchiver, peutRestaurer, peutSupprimer, perimetreLibelle,
)
