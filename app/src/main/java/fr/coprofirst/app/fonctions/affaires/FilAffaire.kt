// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.pieces.FormatPieces
import fr.coprofirst.app.commun.pieces.PiecesJointes
import fr.coprofirst.app.commun.pieces.PiecesJointesVue
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.commun.texte.TexteRiche
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val JALON = 16.dp
private val JALON_SECONDAIRE = 10.dp
/** La largeur du rail : la plus grande pastille et son halo. */
private val RAIL = 28.dp
private val HALO = 10.dp
private val FILET = 2.dp

/**
 * Ce qui est ouvert dans le fil, comme l'iOS (`DepliageDuFil`) : les sections pliées,
 * l'élément ouvert le temps d'un regard (4 s, puis il se replie seul), et ceux qu'on garde
 * ouverts (appui long). Les comptes rendus partent pliés : la suite se lit d'abord.
 */
@Stable
class DepliageDuFil(private val portee: CoroutineScope, val dureeMs: Long = REGARD_MS) {
    var ouvertUnInstant by mutableStateOf<String?>(null)
        private set
    var gardes by mutableStateOf(setOf<String>())
        private set
    var plies by mutableStateOf(setOf(EtapeFil.Cle.ComptesRendus))
        private set
    private var minuterie: Job? = null

    fun estOuvert(id: String) = id in gardes || ouvertUnInstant == id
    fun estGarde(id: String) = id in gardes
    fun estPliee(cle: EtapeFil.Cle) = cle in plies

    /** Un toucher : l'élément s'ouvre un instant, ou se replie s'il l'était déjà. Un élément gardé ne bouge pas. */
    fun toucher(id: String) {
        if (id in gardes) return
        minuterie?.cancel()
        if (ouvertUnInstant == id) {
            ouvertUnInstant = null
            return
        }
        ouvertUnInstant = id
        minuterie = portee.launch {
            delay(dureeMs)
            if (ouvertUnInstant == id) ouvertUnInstant = null
        }
    }

    /** Garder ouvert, ou relâcher. */
    fun basculerGarde(id: String) {
        if (ouvertUnInstant == id) {
            minuterie?.cancel()
            ouvertUnInstant = null
        }
        gardes = if (id in gardes) gardes - id else gardes + id
    }

    fun basculerPli(cle: EtapeFil.Cle) {
        plies = if (cle in plies) plies - cle else plies + cle
    }

    companion object {
        /** La durée d'un regard : 4 s, arrêtées avec l'utilisateur le 12/11/2026 (iOS). */
        const val REGARD_MS = 4_000L
    }
}

/**
 * Le fil d'une affaire, comme l'iOS (`FilAffaire`, maquette A du 12/11/2026) : La demande,
 * La suite, Comptes rendus, La synthèse — sur un rail, chacune avec ses éléments en
 * retrait. La suite et les comptes rendus se plient ; pliés, ils gardent leur résumé.
 *
 * ⚠️ Divergence déclarée : avec le lecteur d'écran, un toucher ouvre un élément un instant
 * comme à l'œil ; « Garder ouvert » est l'action de l'appui long (l'iOS garde ouvert au
 * toucher sous VoiceOver).
 */
@Composable
internal fun FilAffaire(affaire: DetailAffaire, session: SessionOuverte, gestes: ((GesteSuite, SuiteAffaire) -> Unit)? = null) {
    val portee = rememberCoroutineScope()
    val depliage = remember { DepliageDuFil(portee) }
    val textes = rememberTextesFil()
    val etapes = remember(affaire, textes) { EtapeFil.du(affaire, textes) }
    val etats = etapes.map { it.etat }
    Column {
        etapes.forEachIndexed { i, etape ->
            RangeeDuRail(etape.etat, JALON, TraitDuRail.sous(i, etats)) {
                Section(etape, affaire, depliage, session, gestes)
            }
        }
    }
}

@Composable
private fun Section(
    etape: EtapeFil,
    affaire: DetailAffaire,
    depliage: DepliageDuFil,
    session: SessionOuverte,
    gestes: ((GesteSuite, SuiteAffaire) -> Unit)?,
) {
    val pliee = etape.pliable && depliage.estPliee(etape.cle)
    Column(Modifier.padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EnTeteEtape(etape, pliee) { depliage.basculerPli(etape.cle) }
        if (etape.cle == EtapeFil.Cle.Demande) {
            TexteRiche(affaire.description, couleur = LocalCouleurs.current.text)
            PiecesJointesVue(remember(affaire.pieces) { PiecesJointes(affaire.pieces) }, session)
        } else {
            AnimatedVisibility(!pliee && etape.elements.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                val etats = etape.elements.map { if (it.realise) EtapeFil.Etat.Realise else EtapeFil.Etat.EnCours }
                Column {
                    etape.elements.forEachIndexed { i, element ->
                        RangeeDuRail(
                            etats[i], JALON_SECONDAIRE, TraitDuRail.sous(i, etats, etape.ouverteALaSuite),
                            enAvant = depliage.estOuvert(element.id),
                        ) { ElementDuFil(element, depliage, session, gestes) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnTeteEtape(etape: EtapeFil, pliee: Boolean, basculer: () -> Unit) {
    val c = LocalCouleurs.current
    val angle by animateFloatAsState(if (pliee) 0f else 180f, label = "pli")
    val etatDit = if (etape.pliable) stringResource(if (pliee) R.string.affaires_fil_repliee else R.string.affaires_fil_depliee) else ""
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (etape.pliable) Modifier.clickable(role = Role.Button, onClick = basculer) else Modifier)
            .semantics(mergeDescendants = true) {
                heading()
                if (etatDit.isNotEmpty()) stateDescription = etatDit
            }
            .testTag(ReperesAffaires.etape(etape.cle)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                etape.titre, Modifier.weight(1f), style = Typo.titre(MaterialTheme.typography.titleLarge),
                color = if (etape.etat == EtapeFil.Etat.AVenir) c.textMuted else c.text,
            )
            if (etape.pliable) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.textMuted, modifier = Modifier.rotate(angle))
        }
        Text(etape.meta, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        if (pliee) etape.resumePlie?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 2) }
    }
}

/** Un élément sous une section : plié, un titre, une ligne, un aperçu ; ouvert, son texte et ses pièces. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ElementDuFil(element: ElementFil, depliage: DepliageDuFil, session: SessionOuverte, gestes: ((GesteSuite, SuiteAffaire) -> Unit)?) {
    val c = LocalCouleurs.current
    val ouvert = depliage.estOuvert(element.id)
    val garde = depliage.estGarde(element.id)
    var menu by remember { mutableStateOf(false) }
    val presse = LocalClipboard.current
    val portee = rememberCoroutineScope()
    val libelleGarde = stringResource(if (garde) R.string.affaires_fil_ne_plus_garder else R.string.affaires_fil_garder_ouvert)
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClickLabel = stringResource(R.string.affaires_fil_ouvrir),
                        onLongClickLabel = libelleGarde,
                        onLongClick = { menu = true },
                        onDoubleClick = { depliage.basculerGarde(element.id) },
                        onClick = { depliage.toucher(element.id) },
                    )
                    .semantics(mergeDescendants = true) { if (garde) stateDescription = libelleGarde }
                    .testTag(ReperesAffaires.element(element.id)),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(element.titre, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text)
                    if (garde) Icon(Icons.Filled.PushPin, contentDescription = null, tint = c.primary, modifier = Modifier.size(14.dp))
                    if (element.pieces.isNotEmpty()) Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(14.dp))
                    GestesDeLEntree(element.suite, gestes)
                }
                if (element.ligne.isNotEmpty()) Text(element.ligne, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                if (!ouvert) element.apercu?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 1) }
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(libelleGarde) }, onClick = { menu = false; depliage.basculerGarde(element.id) })
                element.html?.let { html ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.affaires_fil_copier)) },
                        onClick = {
                            menu = false
                            val texte = TexteDuSite.apercu(html)
                            portee.launch { presse.setClipEntry(ClipEntry(ClipData.newPlainText(element.titre, texte))) }
                        },
                    )
                }
            }
        }
        AnimatedVisibility(ouvert, enter = fadeIn(), exit = fadeOut()) {
            val forme = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(forme)
                    .background(if (garde) c.surface else c.bg)
                    .then(if (garde) Modifier.border(1.dp, c.primary.copy(alpha = 0.5f), forme) else Modifier)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                element.precise?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textMuted) }
                element.html?.takeIf { TexteDuSite.blocs(it).isNotEmpty() }?.let { TexteRiche(it, couleur = c.text) }
                PiecesJointesVue(remember(element.pieces) { PiecesJointes(element.pieces) }, session, format = FormatPieces.Compact)
                if (!garde) Regard(depliage.dureeMs)
            }
        }
    }
}

/**
 * Les gestes d'UNE entrée de la suite, comme l'iOS (`GestesSuite`) : un « ⋯ » visible à sa
 * droite — un appui long seul serait un geste caché —, et les mêmes en actions du lecteur
 * d'écran. Rien quand le serveur n'en sert aucun, ni hors d'un écran qui les porte.
 */
@Composable
private fun GestesDeLEntree(suite: SuiteAffaire?, choisir: ((GesteSuite, SuiteAffaire) -> Unit)?) {
    if (suite == null || choisir == null) return
    val offerts = GesteSuite.offerts(suite)
    if (offerts.isEmpty()) return
    var menu by remember { mutableStateOf(false) }
    val ressources = LocalResources.current
    val actions = offerts.map { g -> CustomAccessibilityAction(ressources.getString(libelleGesteSuite(g))) { choisir(g, suite); true } }
    Box {
        IconButton(
            { menu = true },
            Modifier.size(32.dp).semantics { customActions = actions }.testTag(ReperesAffaires.gestesSuite(suite.id)),
        ) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(R.string.historique_gestes), tint = LocalCouleurs.current.primary)
        }
        DropdownMenu(menu, { menu = false }) {
            offerts.forEach { g ->
                DropdownMenuItem(
                    text = { Text(stringResource(libelleGesteSuite(g)), color = if (g == GesteSuite.Supprimer) LocalCouleurs.current.danger else LocalCouleurs.current.text) },
                    leadingIcon = { Icon(if (g == GesteSuite.Supprimer) Icons.Outlined.Delete else Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { menu = false; choisir(g, suite) },
                    modifier = Modifier.testTag(ReperesAffaires.gesteSuite(g)),
                )
            }
        }
    }
}

private fun libelleGesteSuite(g: GesteSuite) = if (g == GesteSuite.Modifier) R.string.historique_modifier else R.string.historique_supprimer

/** Le temps qui reste avant que l'élément se replie : un filet qui se vide. */
@Composable
private fun Regard(dureeMs: Long) {
    var parti by remember { mutableStateOf(false) }
    val reste by animateFloatAsState(if (parti) 0f else 1f, tween(dureeMs.toInt(), easing = LinearEasing), label = "regard")
    LaunchedEffect(Unit) { parti = true }
    val teinte = LocalCouleurs.current.primary
    Box(
        Modifier.fillMaxWidth().height(FILET).graphicsLayer {
            scaleX = reste
            transformOrigin = TransformOrigin(0f, 0.5f)
        }.clip(CircleShape).background(teinte),
    )
}

/**
 * Une rangée du rail : la pastille à gauche, puis le trait jusqu'à la rangée suivante ; le
 * contenu à droite. Pleine pour le réalisé, un anneau sinon ; un halo sur l'élément ouvert.
 */
@Composable
private fun RangeeDuRail(etat: EtapeFil.Etat, taille: Dp, trait: TraitDuRail?, enAvant: Boolean = false, contenu: @Composable () -> Unit) {
    val c = LocalCouleurs.current
    val teinte = if (etat == EtapeFil.Etat.AVenir) c.textMuted else c.primary
    val couleurTrait = if (trait == TraitDuRail.Engage) c.primary else c.border
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(RAIL).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 2.dp).size(taille + HALO), contentAlignment = Alignment.Center) {
                if (enAvant) Box(Modifier.size(taille + HALO).clip(CircleShape).background(c.primaryLight))
                Box(
                    Modifier.size(taille).clip(CircleShape).background(if (etat == EtapeFil.Etat.Realise) teinte else c.surface)
                        .border(FILET, teinte, CircleShape),
                )
            }
            if (trait != null) {
                Box(
                    Modifier.weight(1f).width(FILET).drawBehind {
                        val effet = if (trait == TraitDuRail.Pointille) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())) else null
                        drawLine(couleurTrait, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), FILET.toPx(), pathEffect = effet)
                    },
                )
            }
        }
        Box(Modifier.weight(1f)) { contenu() }
    }
}
