// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.ConfirmationDeSortie
import fr.coprofirst.app.commun.FeuillePleine
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.GesteDeSortie
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.commun.Section
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.RolesConnus

/**
 * « Où suis-je », en haut à droite de chaque écran de la résidence, comme l'iOS
 * (`PastilleOuSuisJe`, #180) : le logo et le nom de la résidence, un toucher pour en changer.
 * Aux grandes tailles de texte, le logo seul — le nom, tronqué de toute façon, prendrait la
 * place du titre ; le lecteur d'écran le dit.
 *
 * ⚠️ Divergence déclarée : l'iOS montre le logo de la RÉSIDENCE (`LogoResidence`) ; il n'est
 * pas encore porté (#137) — celui de CoproFirst tient sa place.
 */
@Composable
internal fun PastilleResidence(installation: Installation, ouvrir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val logoSeul = LocalDensity.current.fontScale >= GRAND_TEXTE
    val annonce = stringResource(R.string.residence_pastille, installation.nom)
    Row(
        Modifier.heightIn(min = Mise.cible).padding(horizontal = 4.dp).testTag(REPERE_PASTILLE)
            .cliquable(annonce, ouvrir)
            .semantics { contentDescription = annonce },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(22.dp))
        if (!logoSeul) {
            Text(
                installation.nom,
                Modifier.widthIn(max = 120.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = couleurs.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * La pastille touchée, comme l'iOS (`FeuilleResidence`) : « Vous êtes ici » — la résidence
 * ouverte, ce qu'on y est, « Se déconnecter » ; puis « Mes autres résidences », chacune avec
 * SES rôles, qu'un toucher ouvre sans se déconnecter d'ici ; puis « Une autre résidence ».
 * Ouverte, elle relit le compte (`rafraichirProfil` de l'iOS).
 */
@Composable
internal fun FeuilleResidences(
    ouverte: Installation,
    roles: RolesConnus?,
    sortie: GestesDeSortie,
    relire: suspend () -> Unit,
    fermer: () -> Unit,
) {
    val couleurs = LocalCouleurs.current
    var geste by remember { mutableStateOf<GesteDeSortie?>(null) }
    val autres = remember { sortie.residencesConnues().filter { it.installation.url != ouverte.url } }
    LaunchedEffect(Unit) { relire() }
    FeuillePleine(stringResource(R.string.residence_feuille_titre), REPERE_FEUILLE_OK, fermer) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(stringResource(R.string.residence_vous_etes_ici), enRetrait = false) {
                Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LigneResidence(ouverte, grand = true)
                    RolesDe(roles)
                }
                HorizontalDivider(color = couleurs.border)
                Ligne(stringResource(R.string.se_deconnecter), Icons.AutoMirrored.Filled.Logout, couleurs.danger, REPERE_DECONNECTER) {
                    geste = GesteDeSortie.Deconnecter(ouverte.nom)
                }
            }
            if (autres.isNotEmpty()) {
                Section(stringResource(R.string.residence_autres), enRetrait = false) {
                    autres.forEachIndexed { i, autre ->
                        if (i > 0) HorizontalDivider(color = couleurs.border)
                        Column(
                            Modifier.fillMaxWidth().testTag(repereAutre(autre.installation))
                                .cliquable(autre.installation.nom) {
                                    fermer()
                                    sortie.changerDeResidence(autre.installation)
                                }
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LigneResidence(autre.installation, grand = false)
                            RolesDe(autre.roles)
                        }
                    }
                }
            }
            Carte {
                Ligne(stringResource(R.string.residence_une_autre), Icons.Filled.AddCircle, couleurs.primary, REPERE_UNE_AUTRE) {
                    fermer()
                    sortie.changerDeResidence(null)
                }
            }
        }
    }
    ConfirmationDeSortie(geste, sortie) { geste = null }
}

/** Le logo, le nom et l'hôte d'une résidence (`LigneResidence` de l'iOS). */
@Composable
private fun LigneResidence(installation: Installation, grand: Boolean) {
    val couleurs = LocalCouleurs.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(if (grand) 44.dp else 32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(installation.nom, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
            installation.adresse?.enSaisie?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted) }
        }
    }
}

/** Le statut et les rôles mis en avant (`PastillesRoles` de l'iOS) ; rien tant qu'ils n'ont pas été relus. */
@Composable
private fun RolesDe(roles: RolesConnus?) {
    val pastilles = roles?.let { Roles.affiches(it.statut, it.roles) }.orEmpty()
    if (pastilles.isEmpty()) return
    FlowCapsules { pastilles.forEach { PastilleTeinte(it, compacte = true) } }
}

@Composable
private fun Ligne(libelle: String, icone: androidx.compose.ui.graphics.vector.ImageVector, couleur: androidx.compose.ui.graphics.Color, repere: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(repere).cliquable(libelle, onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icone, contentDescription = null, tint = couleur)
        Text(libelle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = couleur)
    }
}

/** Les repères des tests d'interface : ceux de l'iOS (`pastille.residence`, `residence.deconnecter`). */
internal const val REPERE_PASTILLE = "pastille.residence"
internal const val REPERE_DECONNECTER = "residence.deconnecter"
internal const val REPERE_UNE_AUTRE = "residence.uneAutre"
internal const val REPERE_FEUILLE_OK = "residence.ok"
internal fun repereAutre(installation: Installation) = "residence.autre.${installation.adresse?.enSaisie ?: installation.url}"

/** À partir de cette échelle de texte, la pastille ne montre que le logo (`isAccessibilitySize` de l'iOS). */
private const val GRAND_TEXTE = 1.5f
