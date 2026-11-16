// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree.vitrine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.BoutonContour
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.GRANDE_ECHELLE
import fr.coprofirst.app.entree.ActionsEntree
import fr.coprofirst.app.entree.CadreEcran

/** Le pictogramme de chaque rangée et de chaque rôle — propre à l'app, comme les symboles de l'iOS. */
private val icones: Map<String, ImageVector> = mapOf(
    "demande" to Icons.Filled.Feedback,
    "documents" to Icons.Filled.Description,
    "point-syndic" to Icons.Filled.People,
    "badges" to Icons.Filled.Key,
    "carnet" to Icons.AutoMirrored.Filled.MenuBook,
    "communaute" to Icons.Filled.Groups,
    "user-cs" to Icons.Filled.AdminPanelSettings,
    "user-copro" to Icons.Filled.Home,
    "user-loc" to Icons.Filled.Key,
)

@Composable
private fun pages(): PagesVitrine {
    val contexte = LocalContext.current
    return remember { PagesVitrine.embarquees(contexte.applicationContext) }
}

/**
 * « En savoir plus sur CoproFirst », comme `EnSavoirPlusView` de l'iOS : la page de
 * coprofirst.fr raccourcie (`contenu.json`), d'un seul tenant ; les deux portes restent
 * collées en bas. ⚠️ Divergences déclarées (#153) : l'aperçu du fil (trois cartes, rendues
 * par l'app) n'y est pas encore — la carte du fil est celle d'un menu, pas de la couche
 * commune ; et le contenu est celui EMBARQUÉ, l'iOS le relisant aussi sur le site.
 */
@Composable
fun EcranEnSavoirPlus(parcours: ActionsEntree) {
    val page = pages().enSavoirPlus
    val couleurs = LocalCouleurs.current
    PageAuPied(
        retour = parcours::retour,
        pied = {
            BoutonPlein(R.string.accueil_entrer, onClick = parcours::entrer)
            BoutonContour(R.string.vitrine_essayer_demo, onClick = parcours::entrerDemo)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                page.titre,
                style = Typo.titre(MaterialTheme.typography.headlineLarge),
                color = couleurs.text,
                modifier = Modifier.semantics { heading() }.testTag("vitrine.titre"),
            )
            Text(page.chapeau, style = MaterialTheme.typography.titleMedium, color = couleurs.textMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                page.section,
                style = Typo.titre(MaterialTheme.typography.titleLarge),
                color = couleurs.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(page.sousTitre, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            page.rangees.forEach { rangee ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("vitrine.rangee.${rangee.id}"),
                ) {
                    icones[rangee.id]?.let { Icon(it, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(30.dp)) }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(rangee.titre, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = couleurs.text)
                        Text(rangee.ligne, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
                    }
                }
            }
        }
    }
}

/**
 * La démonstration, comme `DemoView` de l'iOS : trois rôles, un toucher pour entrer —
 * la connexion, l'identifiant public du rôle pré-rempli. L'avertissement (« Données
 * fictives, effacées chaque nuit… ») reste collé en bas, ou passe dans la page quand le
 * texte est si grand qu'un bandeau couvrirait l'écran.
 */
@Composable
fun EcranDemo(parcours: ActionsEntree) {
    val page = pages().demo
    val couleurs = LocalCouleurs.current
    val grand = LocalDensity.current.fontScale >= GRANDE_ECHELLE
    PageAuPied(
        retour = parcours::retour,
        pied = if (grand) null else ({ Avertissement(page.avertissement) }),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                page.titre,
                style = Typo.titre(MaterialTheme.typography.headlineLarge),
                color = couleurs.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(page.chapeau, style = MaterialTheme.typography.bodyLarge, color = couleurs.textMuted)
        }
        page.personnages.forEach { personnage ->
            CartePersonnage(personnage, grand) { parcours.choisirPersonnage(personnage.code, personnage.role) }
        }
        if (grand) Avertissement(page.avertissement)
    }
}

@Composable
private fun CartePersonnage(personnage: PageDemo.Personnage, grand: Boolean, entrer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Carte(
        ouvrir = entrer,
        action = stringResource(R.string.demo_entrer),
        modifier = Modifier.testTag("demo.${personnage.code}"),
    ) {
        // Aux grandes tailles de texte, le rôle passe sous son pictogramme au lieu de se couper.
        val pictogramme = @Composable {
            Box(
                Modifier.size(44.dp).background(couleurs.primaryLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                icones[personnage.code]?.let { Icon(it, contentDescription = null, tint = couleurs.primary) }
            }
        }
        val textes = @Composable { modifier: Modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(personnage.role, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = couleurs.text)
                Text(personnage.ligne, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
            }
        }
        if (grand) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pictogramme()
                textes(Modifier)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                pictogramme()
                textes(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = couleurs.primary)
            }
        }
    }
}

@Composable
private fun Avertissement(texte: String) {
    val couleurs = LocalCouleurs.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Science, contentDescription = null, tint = couleurs.text, modifier = Modifier.size(18.dp))
        Text(texte, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = couleurs.text)
    }
}

/** Une page de la vitrine : le contenu défile, le `pied` reste collé en bas, sur la surface. */
@Composable
private fun PageAuPied(retour: () -> Unit, pied: (@Composable ColumnScope.() -> Unit)?, contenu: @Composable ColumnScope.() -> Unit) {
    val couleurs = LocalCouleurs.current
    CadreEcran(retour = retour) { marges ->
        Column(Modifier.padding(marges).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Mise.marge)
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                content = contenu,
            )
            if (pied != null) {
                HorizontalDivider(color = couleurs.border)
                Column(
                    modifier = Modifier.fillMaxWidth().background(couleurs.surface).padding(horizontal = Mise.marge, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = pied,
                )
            }
        }
    }
}
