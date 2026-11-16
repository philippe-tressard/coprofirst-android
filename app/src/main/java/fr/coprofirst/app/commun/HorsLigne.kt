// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Mouvement
import fr.coprofirst.app.commun.formats.FormatsDuSite
import java.time.Instant

/**
 * Hors ligne, vu des écrans (#28, #158) — l'`@Environment` de l'iOS (`Parcours.consultation`,
 * `Reseau.enLigne`) : `consultation`, l'heure de la dernière lecture quand la résidence est
 * ouverte SANS réseau (`null` en ligne) ; `enLigne`, le réseau de l'appareil. Posé une fois,
 * sur l'espace de la résidence (`EspaceHorsLigne`) ; un écran le lit, il ne le reçoit pas.
 */
@Immutable
data class EtatHorsLigne(val consultation: Instant? = null, val enLigne: Boolean = true)

/** L'état hors ligne de l'espace ouvert ; en dehors (tests, aperçus), en ligne. */
val LocalHorsLigne = compositionLocalOf { EtatHorsLigne() }

/**
 * UN geste qui a besoin du serveur (répondre, voter, déposer, exporter, modifier…), comme
 * `gesteEnLigne` de l'iOS : en consultation hors ligne, il garde sa place, désactivé, et le
 * lecteur d'écran dit pourquoi. Le contrôle prend `actif` pour son `enabled` et `modifier`
 * sur lui-même — il n'écrit jamais `if (horsLigne)`. Le verrou, lui, n'est pas ici : en
 * consultation, la porte des lectures (`GarderLesLectures`) refuse toute écriture avant
 * qu'elle parte.
 */
@Immutable
data class GesteEnLigne(val actif: Boolean, val modifier: Modifier)

/** Ce contrôle agit sur le serveur : `actif` en ligne ; désactivé, en place, en consultation. */
@Composable
fun gesteEnLigne(actif: Boolean = true): GesteEnLigne {
    if (LocalHorsLigne.current.consultation == null) return GesteEnLigne(actif, Modifier)
    val raison = stringResource(R.string.hors_ligne_raison)
    return GesteEnLigne(false, Modifier.semantics { stateDescription = raison })
}

/**
 * Une page qui dit le hors ligne sous sa barre (`BandeauHorsLigne` de l'iOS, posé par Fil,
 * Affaires, Annuaire, Profil, Ma résidence et la fiche d'une affaire) : le bandeau décide
 * seul de paraître, la page garde le reste de la hauteur.
 */
@Composable
fun PageHorsLigne(contenu: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BandeauHorsLigne()
        Box(Modifier.weight(1f)) { contenu() }
    }
}

/**
 * Hors ligne, dit une fois, en haut, tant que ça dure — jamais une alerte : la résidence
 * ouverte SANS réseau, sur sa dernière lecture — d'où viennent les données et pourquoi les
 * gestes sont grisés ; ou l'appareil qui perd le réseau, l'app ouverte — ce qui est affiché
 * peut dater. Une seule annonce au lecteur d'écran. Il paraît en glissant (ease-out, moins
 * de 300 ms) : c'est un changement d'état rare, qu'on doit voir arriver ; sans animation si
 * l'appareil les coupe.
 */
@Composable
fun BandeauHorsLigne() {
    val etat = LocalHorsLigne.current
    val coupees = animationsCoupees()
    val entree = if (coupees) snap() else tween<Float>(DUREE_BANDEAU_MS, easing = Mouvement.sortie)
    AnimatedVisibility(
        visible = etat.consultation != null || !etat.enLigne,
        enter = expandVertically(if (coupees) snap() else tween(DUREE_BANDEAU_MS, easing = Mouvement.sortie)) + fadeIn(entree),
        exit = shrinkVertically(if (coupees) snap() else tween(DUREE_BANDEAU_MS, easing = Mouvement.sortie)) + fadeOut(entree),
    ) {
        Bandeau(etat.consultation)
    }
}

@Composable
private fun Bandeau(consultation: Instant?) {
    val couleurs = LocalCouleurs.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Mise.marge, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(REPERE_BANDEAU),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = couleurs.text, modifier = Modifier.size(16.dp))
            Text(
                if (consultation != null) {
                    stringResource(R.string.hors_ligne_a_jour, FormatsDuSite.dateCourte(consultation), FormatsDuSite.heure(consultation))
                } else {
                    stringResource(R.string.hors_ligne_peut_dater)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = couleurs.text,
                textAlign = TextAlign.Center,
            )
        }
        if (consultation != null) {
            Text(
                stringResource(R.string.hors_ligne_consultation),
                style = MaterialTheme.typography.labelSmall,
                color = couleurs.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Le repère du bandeau — l'identifiant d'accessibilité de l'iOS. */
const val REPERE_BANDEAU = "horsligne.bandeau"

private const val DUREE_BANDEAU_MS = 220
