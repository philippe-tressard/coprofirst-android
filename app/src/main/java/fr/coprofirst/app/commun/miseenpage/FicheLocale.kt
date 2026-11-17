// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.miseenpage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.navigation.EnTeteDeBarre

/**
 * La fiche qu'une liste tient ELLE-MÊME (#69, lot 3 de l'iPad, coprofirst-ios#279) : l'avis d'une
 * question du règlement, l'annuaire du syndic, une petite annonce — rien que la porte du registre
 * sache ouvrir par sa nature. La liste garde son choix (`rememberSaveable`) ; la colonne montre
 * `contenu`, et `fermer` rend le choix quand la colonne passe à une autre fiche.
 */
class FicheLocale(val nature: String, val id: String, val contenu: @Composable () -> Unit, val fermer: () -> Unit)

/** Vrai dans la colonne de fiche : une feuille s'y pose à plat, sans « OK » ni voile (`PanneauDeColonne`). */
val LocalDansLaColonne = staticCompositionLocalOf { false }

/**
 * Le choix d'une liste, comme `LienDeFiche` de l'iOS : en fenêtre large, si la liste occupe la
 * colonne (`OccuperLaColonneDeFiche`), `contenu` s'ouvre À CÔTÉ d'elle ; sinon, il se compose
 * ici — sa feuille, comme avant. La rangée choisie se souligne par `rangeeChoisie(nature, id)`.
 */
@Composable
fun FicheOuFeuille(nature: String, id: String, fermer: () -> Unit, contenu: @Composable () -> Unit) {
    val colonne = LocalColonneDeFiche.current
    if (colonne == null || !colonne.occupee) {
        contenu()
        return
    }
    val actuel by rememberUpdatedState(contenu)
    val fermeture by rememberUpdatedState(fermer)
    DisposableEffect(colonne, nature, id) {
        val fiche = FicheLocale(nature, id, { actuel() }, { fermeture() })
        colonne.locale = fiche
        onDispose { if (colonne.locale === fiche) colonne.locale = null }
    }
}

/**
 * La fiche locale de la colonne, à composer DANS la colonne : la feuille s'y pose à plat, et une
 * liste qu'elle contiendrait n'occupe pas la colonne d'à côté (le `choixDeFiche` retiré de sa
 * fiche, à l'iOS). `null` : aucune.
 */
fun ColonneDeFiche.ficheLocale(): (@Composable () -> Unit)? = locale?.let { fiche ->
    {
        CompositionLocalProvider(LocalDansLaColonne provides true, LocalColonneDeFiche provides null) { fiche.contenu() }
    }
}

/**
 * Une fiche posée dans la colonne : SON titre et ses actions en tête de sa colonne, puis son
 * contenu — la page garde la barre du haut, sa pastille et sa cloche, que la fiche ne double pas
 * (coprofirst-ios#279). Ni « OK » ni flèche : une autre rangée la remplace, et le geste « retour »
 * du système la referme (`fermer`, `null` : la résidence s'en charge).
 */
@Composable
fun PanneauDeColonne(
    titre: String,
    sousTitre: String? = null,
    fermer: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contenu: @Composable BoxScope.() -> Unit,
) {
    if (fermer != null) BackHandler(onBack = fermer)
    // Ce que la fiche ouvre à son tour (signaler, répondre) reste une feuille, par-dessus.
    CompositionLocalProvider(LocalDansLaColonne provides false) { PanneauAPlat(titre, sousTitre, actions, contenu) }
}

@Composable
private fun PanneauAPlat(titre: String, sousTitre: String?, actions: @Composable RowScope.() -> Unit, contenu: @Composable BoxScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible).padding(start = Mise.marge, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EnTeteDeBarre(titre, sousTitre, Modifier.weight(1f))
            actions()
        }
        HorizontalDivider(color = LocalCouleurs.current.border)
        Box(Modifier.weight(1f), content = contenu)
    }
}
