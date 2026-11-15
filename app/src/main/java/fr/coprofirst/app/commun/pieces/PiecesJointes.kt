// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import androidx.compose.runtime.Immutable

/**
 * Les pièces jointes d'un élément — affaire, suite, message, carte du fil, annonce —,
 * rangées comme le site les range (`separerFichiers`) et comme l'iOS (`PiecesJointes`) :
 * les photos d'un côté, les documents de l'autre, dans l'ordre servi, sans doublon.
 *
 * Le serveur sert des listes d'URL (`photos_urls`, `fichiers_urls`) où une photo peut se
 * trouver dans l'une ou l'autre : seule l'extension décide (`Fichiers.estImage`). On les
 * donne donc toutes ensemble : `PiecesJointes(affaire.photosUrls + affaire.fichiersUrls)`.
 */
@Immutable
class PiecesJointes(urls: List<String> = emptyList()) {
    val photos: List<String>
    val documents: List<String>

    init {
        val (images, autres) = urls.filter { it.isNotEmpty() }.distinct().partition(Fichiers::estImage)
        photos = images
        documents = autres
    }

    val vide: Boolean get() = photos.isEmpty() && documents.isEmpty()

    /** Le nombre de pièces, photos et documents ensemble. */
    val nombre: Int get() = photos.size + documents.size

    /** « 2 photos, 1 pièce jointe » — ce que dit une carte au lecteur d'écran ; `null` sans pièce. */
    val enClair: String?
        get() {
            val morceaux = buildList {
                if (photos.isNotEmpty()) add(if (photos.size > 1) "${photos.size} photos" else "1 photo")
                if (documents.isNotEmpty()) {
                    add(if (documents.size > 1) "${documents.size} pièces jointes" else "1 pièce jointe")
                }
            }
            return morceaux.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }

    override fun equals(other: Any?): Boolean =
        other is PiecesJointes && other.photos == photos && other.documents == documents

    override fun hashCode(): Int = 31 * photos.hashCode() + documents.hashCode()

    override fun toString(): String = "PiecesJointes(photos=$photos, documents=$documents)"

    companion object {
        val AUCUNE = PiecesJointes()
    }
}
