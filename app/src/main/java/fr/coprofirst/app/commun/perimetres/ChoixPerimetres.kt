// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.perimetres

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Mouvement
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.animationsCoupees

/**
 * Le choix de plusieurs périmètres, en pastilles, comme `ChoixPerimetres` de l'iOS
 * (`PerimetrePicker.svelte` du site) : la rangée des têtes — le défaut d'abord, chaque
 * bâtiment, le parking… —, puis « Préciser dans Bâtiment 1 — facultatif » et ses espaces,
 * pour une seule tête à la fois. Deux touchers suffisent pour « le hall du bâtiment 1 ».
 *
 * La règle est `ChoixDePerimetres` ; cette vue ne fait que la dessiner. `changer` reçoit le
 * nouvel état (l'écran le garde). `repere` : le préfixe des repères d'accessibilité, ceux
 * de l'iOS (`nouvelle-annonce-hall.perimetre`) — `"$repere.<code>"` par pastille,
 * `"$repere.lieu"` pour le lieu servi. `service` : le nom du lieu retenu
 * (`LieuDeLaSaisie`) ; sans lui, pas de ligne de lieu (un aperçu, un test).
 */
@Composable
fun ChoixPerimetres(
    arbre: ArbreDesPerimetres,
    choix: ChoixDePerimetres,
    changer: (ChoixDePerimetres) -> Unit,
    repere: String,
    modifier: Modifier = Modifier,
    service: ServicePerimetres? = null,
) {
    val couleurs = LocalCouleurs.current
    val haptique = LocalHapticFeedback.current
    val reduit = animationsCoupees()
    // Le retour de sélection de l'iOS (`.sensoryFeedback(.selection, trigger: choix)`).
    val geste = { nouveau: ChoixDePerimetres ->
        if (nouveau != choix) haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
        changer(nouveau)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowCapsules(espace = ESPACE_PASTILLES) {
            arbre.defaut?.let { defaut ->
                Pastille(defaut.libelle, choix.estDefaut(arbre), Modifier.testTag("$repere.${defaut.code}")) {
                    geste(choix.choisirDefaut(arbre))
                }
            }
            arbre.tetes.forEach { tete ->
                Pastille(
                    choix.libelleDeTete(tete, arbre),
                    choix.teteRetenue(tete.code, arbre),
                    Modifier.testTag("$repere.${tete.code}"),
                ) { geste(choix.basculer(tete.code, arbre)) }
            }
        }
        AnimatedContent(
            targetState = choix.ouverte(arbre)?.let(arbre::noeud),
            transitionSpec = {
                val apparition = if (reduit) {
                    fadeIn(tween(DUREE_MS))
                } else {
                    fadeIn(tween(DUREE_MS, easing = Mouvement.sortie)) +
                        slideInVertically(tween(DUREE_MS, easing = Mouvement.sortie)) { -it / 6 }
                }
                (apparition togetherWith fadeOut(tween(DUREE_MS / 2)))
                    .using(SizeTransform(clip = false) { _, _ -> if (reduit) snap() else tween(DUREE_MS, easing = Mouvement.sortie) })
            },
            contentKey = { it?.code },
            label = "espaces",
        ) { tete ->
            if (tete != null) RangeeDesEspaces(tete, arbre, choix, repere, geste)
        }
        choix.aide(arbre)?.let { aide ->
            Text(aide, style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
        }
        // Le lieu retenu, nommé par le serveur — sous les pastilles : rien ne bouge sous le
        // doigt quand il arrive.
        if (service != null) LieuDeLaSaisie(choix.codes, service, "$repere.lieu")
    }
}

/** « Préciser dans <tête> — facultatif » et ses espaces, dans une surface bordée. */
@Composable
private fun RangeeDesEspaces(
    tete: Perimetre,
    arbre: ArbreDesPerimetres,
    choix: ChoixDePerimetres,
    repere: String,
    geste: (ChoixDePerimetres) -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val forme = RoundedCornerShape(Mise.rayon)
    val titre = buildAnnotatedString {
        append(stringResource(R.string.perimetres_preciser, tete.libelle))
        withStyle(SpanStyle(color = couleurs.textMuted)) { append(stringResource(R.string.perimetres_facultatif)) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(couleurs.surface, forme)
            .border(1.dp, couleurs.border, forme)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(titre, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = couleurs.text)
        FlowCapsules(espace = ESPACE_PASTILLES) {
            arbre.espaces(tete.code).forEach { espace ->
                Pastille(espace.libelle, espace.code in choix.codes, Modifier.testTag("$repere.${espace.code}")) {
                    geste(choix.basculer(espace.code, arbre))
                }
            }
        }
    }
}

/** L'espace entre deux pastilles touchables (`FlowEtiquettes` de l'iOS : 8). */
private val ESPACE_PASTILLES = 8.dp

/** L'apparition de la rangée : courte, en sortie douce (sous 300 ms). */
private const val DUREE_MS = 220
