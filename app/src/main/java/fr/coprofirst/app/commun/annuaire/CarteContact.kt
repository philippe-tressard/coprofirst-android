// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.annuaire

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.pieces.PhotoProtegee
import fr.coprofirst.app.session.SessionOuverte

/** Un rôle mis en avant sur une carte (« Président du Conseil Syndical »). */
data class Medaille(val titre: String, val icone: ImageVector)

/**
 * Les rôles d'un membre du conseil, mis en avant sur sa carte : gestionnaire du site,
 * président — l'Annuaire des résidents et celui de l'Espace CS (#24, #97).
 */
@Composable
fun medaillonsDuConseil(membre: Annuaire.Membre, site: String): List<Medaille> = buildList {
    if (membre.estGestionnaireSite) add(Medaille(stringResource(R.string.annuaire_gestionnaire, site), Icons.Outlined.Apartment))
    if (membre.estPresident) add(Medaille(stringResource(R.string.annuaire_president), Icons.Outlined.Shield))
}

/** L'interlocuteur principal du syndic, mis en avant. */
@Composable
fun medaillonsDuSyndic(membre: Annuaire.Membre): List<Medaille> =
    if (membre.estPrincipal) listOf(Medaille(stringResource(R.string.annuaire_principal), Icons.Outlined.Star)) else emptyList()

/**
 * La carte d'une personne de l'annuaire (`CarteContact.svelte`), comme l'iOS : l'avatar à
 * hauteur du nom, les médaillons, puis ce que la section y ajoute (lieu, fonction,
 * coordonnées). Le gestionnaire principal porte un liseré de la couleur d'accent.
 */
@Composable
fun CarteContact(
    personne: Annuaire.Membre,
    medaillons: List<Medaille>,
    session: SessionOuverte?,
    principal: Boolean = false,
    detail: @Composable ColumnScope.() -> Unit = {},
) {
    val couleurs = LocalCouleurs.current
    val forme = RoundedCornerShape(Mise.rayon)
    Row(
        Modifier.fillMaxWidth().clip(forme).background(couleurs.surface)
            .border(if (principal) 2.dp else 1.dp, if (principal) couleurs.accent else couleurs.border, forme)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(personne, session)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(personne.nomComplet, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
            medaillons.forEach { medaille ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(medaille.icone, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(14.dp))
                    Text(medaille.titre, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = couleurs.primary)
                }
            }
            detail()
        }
    }
}

/** La photo de la personne, ou ses initiales ; décorative. */
@Composable
private fun Avatar(personne: Annuaire.Membre, session: SessionOuverte?) {
    val couleurs = LocalCouleurs.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(couleurs.primaryLight).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val photo = personne.photoUrl
        if (photo != null && session != null) {
            PhotoProtegee(photo, session, Modifier.fillMaxSize())
        } else {
            Text(personne.initiales, style = MaterialTheme.typography.titleMedium, color = couleurs.primary)
        }
    }
}

/** Le courriel et les numéros d'une personne de l'annuaire : chacun s'ouvre d'un toucher. */
@Composable
fun Coordonnees(personne: Annuaire.Membre) = Coordonnees(personne.email, personne.telephones)

/**
 * Un courriel et des numéros, touchables (`mailto:`, `tel:`), sous un titre facultatif —
 * l'annuaire, et les baux de « Mes lots & accès » (bailleur, locataire). Écrit une fois (#97).
 */
@Composable
fun Coordonnees(email: String?, telephones: List<String>, titre: String? = null) {
    val couleurs = LocalCouleurs.current
    val liens = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (titre != null) Text(titre, style = MaterialTheme.typography.titleSmall, color = couleurs.text)
        email?.takeIf { it.isNotBlank() }?.let { courriel ->
            Action(courriel, Icons.Outlined.Email, stringResource(R.string.annuaire_ecrire), ReperesContact.COURRIEL) {
                liens.openUri("mailto:$courriel")
            }
        }
        telephones.filter { it.isNotBlank() }.forEach { numero ->
            val chiffres = numero.filter { it.isDigit() || it == '+' }
            Action(numero, Icons.Outlined.Phone, stringResource(R.string.annuaire_appeler), ReperesContact.APPELER) {
                liens.openUri("tel:$chiffres")
            }
        }
    }
}

/** Les repères des coordonnées, ceux de l'iOS. */
object ReperesContact {
    const val APPELER = "annuaire.appeler"
    const val COURRIEL = "annuaire.courriel"
}

@Composable
private fun Action(texte: String, icone: ImageVector, aide: String, repere: String, ouvrir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).cliquable(aide, ouvrir).testTag(repere),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(18.dp))
        Text(texte, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = couleurs.primary)
    }
}
