// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.app.commun.pieces.Ouverture
import fr.coprofirst.app.commun.pieces.documentChoisi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import java.time.Instant

// Les pièces que la Fiche, le Carnet et le Règlement partagent, comme l'iOS
// (`ElementsResidence`) : la gouttière d'une carte, l'en-tête de rubrique, une capsule
// neutre, la liste des annexes.

/** La nature d'une carte : son pictogramme et l'abrégé du site (`Gouttiere.svelte`). */
enum class Nature(val icone: ImageVector, @param:StringRes val abrege: Int) {
    Plan(Icons.Outlined.Layers, R.string.residence_nature_plan),
    Reglement(Icons.AutoMirrored.Outlined.MenuBook, R.string.residence_nature_reglement),
    Assemblee(Icons.Outlined.Groups, R.string.residence_nature_ag),
    Diagnostic(Icons.Outlined.MonitorHeart, R.string.residence_nature_diagnostic),
    Regle(Icons.AutoMirrored.Outlined.Assignment, R.string.residence_nature_reglement),
    Contrat(Icons.Outlined.Description, R.string.residence_nature_contrat),
    Intervention(Icons.Outlined.Build, R.string.residence_nature_intervention),
    Affaire(Icons.Outlined.Construction, R.string.residence_nature_affaire),
    ;

    companion object {
        /** Où mène une entrée du carnet (`ORIGINES` du site). */
        fun duCarnet(origine: String) = when (origine) {
            "contrat" -> Contrat
            "intervention" -> Intervention
            else -> Affaire
        }
    }
}

/** `fmtDate` en trois morceaux — « 2 », « avr. », « 2026 » —, comme `partiesDate` du site. */
fun partiesDate(date: Instant): List<String> = FormatsDuSite.date(date).split(Regex("\\s+"))

/** La gouttière d'une carte : la date de l'acte en trois morceaux, puis la nature. Dite par sa date seule. */
@Composable
fun Gouttiere(nature: Nature, date: Instant? = null, mention: String? = null) {
    val couleurs = LocalCouleurs.current
    val dite = listOfNotNull(date?.let(FormatsDuSite::date), mention).joinToString(", ")
    Column(
        Modifier.width(52.dp).clearAndSetSemantics { if (dite.isNotEmpty()) contentDescription = dite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val parties = date?.let(::partiesDate)
        if (parties != null && parties.size == 3) {
            Text(parties[0], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = couleurs.textMuted)
            Text(parties[1], style = MaterialTheme.typography.labelSmall, color = couleurs.textMuted)
            Text(parties[2], style = MaterialTheme.typography.labelSmall, color = couleurs.textMuted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Icon(nature.icone, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(18.dp))
        Text(stringResource(nature.abrege), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = couleurs.textMuted)
        // Sous la nature, ce que le site y dit encore (« À venir » d'un contrat, #220).
        mention?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = couleurs.primary, textAlign = TextAlign.Center) }
    }
}

/** Une carte de rubrique : la gouttière à gauche, le contenu à droite. */
@Composable
fun CarteAGouttiere(
    nature: Nature,
    modifier: Modifier = Modifier,
    date: Instant? = null,
    ouvrir: (() -> Unit)? = null,
    action: String? = null,
    mention: String? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Carte(modifier, ouvrir = ouvrir, action = action) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Gouttiere(nature, date, mention)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = contenu)
        }
    }
}

/** L'en-tête d'une rubrique, en serif de la charte, dit comme un en-tête ; le compte à droite. */
@Composable
fun EnTeteRubrique(titre: String, modifier: Modifier = Modifier, compte: Int? = null) {
    val couleurs = LocalCouleurs.current
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.Bottom) {
        Text(titre, style = Typo.titre(MaterialTheme.typography.titleLarge), color = couleurs.text, modifier = Modifier.weight(1f))
        if (compte != null && compte > 0) {
            Text(FormatsDuSite.nombre(compte), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
        }
    }
}

/** Une liste vide, dite par la phrase du site. */
@Composable
fun RubriqueVide(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
}

/** Une capsule qui DÉCRIT (bâtiment, année, fréquence, numéro) : grise, elle ne signale pas — `badge-gray`. */
@Composable
fun PastilleNeutre(libelle: String) = PastilleTeinte(libelle, BADGE_NEUTRE, compacte = true)

/** La classe neutre du site. */
const val BADGE_NEUTRE = "badge-gray"

/** Les annexes d'une fiche : chacune s'ouvre dans la visionneuse de l'écran (#274). */
@Composable
fun ListeAnnexes(annexes: List<DocumentResidence>, ouvrir: (DocumentResidence) -> Unit) {
    if (annexes.isEmpty()) return
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Libelle(stringResource(R.string.residence_annexes))
        annexes.forEach { annexe ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Mise.cible).documentChoisi(Ouverture.cleDocument(annexe.id))
                    .cliquable(stringResource(R.string.residence_ouvrir_document)) { ouvrir(annexe) },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(18.dp))
                Text(annexe.titre, style = MaterialTheme.typography.bodyMedium, color = couleurs.primary)
            }
        }
    }
}
