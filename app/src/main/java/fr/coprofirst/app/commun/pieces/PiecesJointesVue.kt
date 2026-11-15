// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.SessionOuverte

/** `Grand` : l'élément lui-même (intitulé, mosaïque haute) ; `Compact` : une pièce d'un fil — suite, message — sous son texte, sans intitulé. */
enum class FormatPieces {
    Grand,
    Compact,
}

/**
 * Les pièces jointes d'un élément OUVERT — affaire, suite, message, résumé d'une carte du
 * fil, annonce —, écrites une fois, comme l'iOS (`PiecesJointesView`, `PiecesJointes.svelte`
 * du site) : une seule section « Pièces jointes », les photos en mosaïque, puis les documents
 * en lignes. Un toucher ouvre la pièce en grand (`Ouverture`). Rien sans pièce.
 *
 * Pas d'AJOUT de pièce ici, comme l'iOS : les écrans disent qu'elles s'ajoutent depuis le
 * site. Sur une carte FERMÉE, c'est `VignettePieces` qui les signale.
 */
@Composable
fun PiecesJointesVue(
    pieces: PiecesJointes,
    session: SessionOuverte,
    modifier: Modifier = Modifier,
    format: FormatPieces = FormatPieces.Grand,
) {
    if (pieces.vide) return
    val couleurs = LocalCouleurs.current
    val ouverture = rememberOuverture(session)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (format == FormatPieces.Grand) {
            Libelle(stringResource(R.string.pieces_titre, pieces.nombre), Modifier.semantics { heading() })
        }
        if (pieces.photos.isNotEmpty()) {
            MosaiquePhotos(
                chemins = pieces.photos,
                session = session,
                hauteur = if (format == FormatPieces.Grand) Mise.mosaique else Mise.mosaiqueCompacte,
                enCours = ouverture.enCours,
            ) { rang -> ouverture.piece(pieces.photos[rang]) }
        }
        if (pieces.documents.isNotEmpty()) {
            Column {
                pieces.documents.forEachIndexed { rang, chemin ->
                    if (rang > 0) HorizontalDivider(color = couleurs.border)
                    LigneDocument(chemin, enCours = ouverture.enCours == chemin, modifier = Modifier.testTag("pieces.document.$rang")) {
                        ouverture.piece(chemin)
                    }
                }
            }
        }
        AvisOuverture(ouverture)
    }
}

/**
 * Un document joint, comme l'iOS (`LigneDocument`, `PastilleFichier` du site) : son type
 * d'abord — on cherche « le PDF » —, son nom sans extension, sa taille quand elle est
 * connue (`FormatsDuSite.octets` : un document du fil la sert, une pièce jointe non), et
 * l'ouverture. `nom` : le chemin servi ou le nom du fichier.
 */
@Composable
fun LigneDocument(
    nom: String,
    enCours: Boolean,
    modifier: Modifier = Modifier,
    taille: Long? = null,
    ouvrir: () -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val type = Fichiers.typeAffiche(nom)
    val titre = Fichiers.nomSansExtension(nom)
    val annonce = listOfNotNull(type, titre, taille?.let { FormatsDuSite.octets(it) }).joinToString(", ")
    val action = stringResource(R.string.pieces_ouvrir_document)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Mise.cible)
            .cliquable(action, ouvrir)
            .clearAndSetSemantics { contentDescription = annonce }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            type,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = couleurs.primary,
            modifier = Modifier
                .background(couleurs.infoFond, RoundedCornerShape(Mise.rayon / 3))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(titre, style = MaterialTheme.typography.bodyMedium, color = couleurs.text, maxLines = 3)
            if (taille != null) {
                Text(FormatsDuSite.octets(taille), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (enCours) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = couleurs.primary)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = couleurs.textMuted)
        }
    }
}
