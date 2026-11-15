// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.formats.FormatsDuSite
import kotlinx.coroutines.launch

/** Une clôture qui pourrait absorber des affaires liées : on demande d'abord. */
private data class Cloture(val affaire: Affaire, val statut: ReferentielsAffaires.Statut, val candidates: Int)

/**
 * L'onglet Kanban, comme l'iOS (`KanbanAffaires`) : les affaires suivies, non archivées,
 * une colonne par étape du workflow, qui défilent de côté et s'alignent. Les colonnes, la
 * colonne de chaque carte et l'état qu'une colonne pose sont SERVIS : l'app ne range rien.
 *
 * Une carte se déplace si le serveur le permet à CE lecteur (`peut_deplacer_au_kanban`) :
 * « Déplacer vers… » à l'appui long, ou par les actions du lecteur d'écran. Clore demande
 * d'abord si des affaires liées pourraient y être fusionnées : l'app ne fusionne pas.
 *
 * ⚠️ Divergence déclarée : pas de glisser-déposer d'une colonne à l'autre (l'iOS l'offre
 * en plus du menu) — dans un défilement de côté, le geste se confond avec celui de la page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KanbanAffaires(ctx: ContexteAffaires) {
    val etat = ctx.etat
    val portee = rememberCoroutineScope()
    val haptique = LocalHapticFeedback.current
    val referentiels = etat.referentiels
    // L'échec de la liste, ou celui des référentiels tant qu'aucun n'est lu : sans colonnes, pas de tableau.
    val erreur = (etat.phase as? EtatAffaires.Phase.Erreur)?.erreur ?: if (referentiels == null) etat.erreurReferentiels else null
    if (erreur != null) {
        ErreurOnglet(R.string.kanban_erreur, erreur, Modifier.padding(Mise.marge)) { portee.launch { etat.charger() } }
        return
    }
    val colonnes = referentiels?.colonnes ?: List(2) { ColonneKanban("attente.$it", " ", " ") }
    val pages = rememberPagerState { colonnes.size }
    var aClore by remember { mutableStateOf<Cloture?>(null) }
    var rafraichit by remember { mutableStateOf(false) }
    val pret = etat.phase == EtatAffaires.Phase.Pret && referentiels != null

    suspend fun deplacer(affaire: Affaire, statut: ReferentielsAffaires.Statut) {
        if (etat.deplacer(affaire, statut)) {
            haptique.performHapticFeedback(HapticFeedbackType.Confirm)
            colonnes.indexOfFirst { it.id == statut.colonne }.takeIf { it >= 0 }?.let { pages.animateScrollToPage(it) }
        }
    }

    fun demander(affaire: Affaire, colonne: ColonneKanban) {
        val statut = referentiels?.statutPourColonne(colonne.id)
        if (!affaire.peutDeplacer || statut == null || statut.code == affaire.statut) return
        portee.launch {
            val candidates = if (statut.clos && !affaire.close) etat.candidatesALaFusion(affaire) else 0
            if (candidates > 0) aClore = Cloture(affaire, statut, candidates) else deplacer(affaire, statut)
        }
    }

    Column(Modifier.fillMaxSize()) {
        val compte = etat.auKanban.size
        Text(
            if (pret) pluralStringResource(R.plurals.kanban_compte, compte, compte) else " ",
            Modifier.padding(horizontal = Mise.marge).testTag(ReperesAffaires.KANBAN_COMPTE),
            style = MaterialTheme.typography.bodySmall, color = LocalCouleurs.current.textMuted,
        )
        Sauts(referentiels?.colonnes.orEmpty(), colonnes.getOrNull(pages.currentPage)?.id, etat) { i ->
            portee.launch { pages.animateScrollToPage(i) }
        }
        PullToRefreshBox(
            isRefreshing = rafraichit,
            onRefresh = { portee.launch { rafraichit = true; etat.charger(); rafraichit = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(
                pages, Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Mise.marge),
                pageSize = LARGEUR_COLONNE, pageSpacing = 12.dp, verticalAlignment = Alignment.Top,
            ) { i ->
                val colonne = colonnes[i]
                ColonneDuKanban(
                    colonne, if (pret) etat.cartes(colonne) else null, ctx.ouvrir,
                    emoji = { referentiels?.categorie(it.categorie)?.emoji },
                    destinations = { ReglesAffaires.destinations(it, referentiels) },
                    deplacer = ::demander,
                )
            }
        }
    }

    aClore?.let { cloture ->
        AlertDialog(
            onDismissRequest = { aClore = null },
            title = { Text(stringResource(R.string.kanban_clore_titre)) },
            text = { Text(pluralStringResource(R.plurals.kanban_clore_message, cloture.candidates, cloture.candidates)) },
            confirmButton = {
                TextButton(
                    onClick = { aClore = null; portee.launch { deplacer(cloture.affaire, cloture.statut) } },
                    modifier = Modifier.testTag(ReperesAffaires.CLORE_SANS_FUSION),
                ) { Text(stringResource(R.string.kanban_clore_sans_fusion)) }
            },
            dismissButton = { TextButton(onClick = { aClore = null }) { Text(stringResource(R.string.annuler)) } },
        )
    }
}

/** Une pastille par colonne servie, teintée, avec son compte : un toucher y mène. */
@Composable
private fun Sauts(colonnes: List<ColonneKanban>, visible: String?, etat: EtatAffaires, aller: (Int) -> Unit) {
    val c = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Mise.marge, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colonnes.forEachIndexed { i, colonne ->
            val nombre = etat.cartes(colonne).size
            val vocal = pluralStringResource(R.plurals.kanban_colonne_vocal, nombre, colonne.libelle, nombre)
            Row(
                Modifier
                    .heightIn(min = Mise.cible)
                    .clip(CircleShape)
                    .background(if (visible == colonne.id) c.primaryLight else c.surface)
                    .border(1.dp, c.border, CircleShape)
                    .selectable(visible == colonne.id, role = Role.Tab) { aller(i) }
                    .clearAndSetSemantics { contentDescription = vocal }
                    .padding(horizontal = 12.dp)
                    .testTag(ReperesAffaires.saut(colonne.id)),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(teinte(colonne.id)))
                Text(colonne.libelleCourt, style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(FormatsDuSite.nombre(nombre), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = c.textMuted)
            }
        }
    }
}

/** La teinte d'une colonne (un jeton `kanban*` de la palette) ; une colonne que le site ne teinte pas garde un trait neutre. */
@Composable
private fun teinte(colonne: String) = LocalCouleurs.current.let { c -> TablesAffaires.teintesKanban[colonne]?.invoke(c) ?: c.border }

/** Une colonne : son en-tête teinté, son compte, ses cartes — ou « Aucune affaire ». `cartes` nul : le squelette. */
@Composable
private fun ColonneDuKanban(
    colonne: ColonneKanban,
    cartes: List<Affaire>?,
    ouvrir: (Affaire) -> Unit,
    emoji: (Affaire) -> String?,
    destinations: (Affaire) -> List<ColonneKanban>,
    deplacer: (Affaire, ColonneKanban) -> Unit,
) {
    val c = LocalCouleurs.current
    val forme = RoundedCornerShape(Mise.rayon)
    Column(
        Modifier.fillMaxSize().padding(bottom = 8.dp).clip(forme).background(c.surface.copy(alpha = 0.55f))
            .border(1.dp, c.border, forme).testTag(ReperesAffaires.colonne(colonne.id)),
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(teinte(colonne.id)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).semantics(mergeDescendants = true) { heading() }) {
            Text(colonne.libelle, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.text)
            cartes?.let {
                Text(FormatsDuSite.nombre(it.size), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = c.textMuted)
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                cartes == null -> items(2) {
                    Box(Modifier.alpha(0.35f).clearAndSetSemantics { }) { CarteKanban(SQUELETTE_KANBAN, null, {}) }
                }
                cartes.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp), contentAlignment = Alignment.Center) {
                        Text(TablesAffaires.COLONNE_VIDE, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
                else -> items(cartes, key = { it.id }) { affaire ->
                    CarteDeplacable(affaire, emoji(affaire), { ouvrir(affaire) }, if (affaire.peutDeplacer) destinations(affaire) else emptyList()) {
                        deplacer(affaire, it)
                    }
                }
            }
        }
    }
}

/** Une carte et son menu « Déplacer vers… » (appui long), offert aussi au lecteur d'écran. */
@Composable
private fun CarteDeplacable(
    affaire: Affaire,
    emoji: String?,
    ouvrir: () -> Unit,
    destinations: List<ColonneKanban>,
    deplacer: (ColonneKanban) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val ressources = LocalResources.current
    val actions = destinations.map { vers ->
        CustomAccessibilityAction(ressources.getString(R.string.kanban_deplacer_vers_colonne, vers.libelle)) { deplacer(vers); true }
    }
    Box {
        CarteKanban(
            affaire, emoji, ouvrir,
            Modifier.testTag(ReperesAffaires.carteKanban(affaire.numero)).semantics { customActions = actions },
            gestes = if (destinations.isEmpty()) null else ({ menu = true }),
        )
        DropdownMenu(menu, { menu = false }) {
            Text(
                stringResource(R.string.kanban_deplacer_vers), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = LocalCouleurs.current.textMuted,
            )
            destinations.forEach { vers ->
                DropdownMenuItem(
                    text = { Text(vers.libelle) },
                    onClick = { menu = false; deplacer(vers) },
                    modifier = Modifier.testTag(ReperesAffaires.deplacer(affaire.numero, vers.id)),
                )
            }
        }
    }
}

/**
 * Une affaire au Kanban, comme l'iOS (`CarteKanban`) : son prestataire, son titre, sa
 * catégorie et son numéro — sans l'aperçu : la colonne dit l'état.
 */
@Composable
private fun CarteKanban(affaire: Affaire, emoji: String?, ouvrir: () -> Unit, modifier: Modifier = Modifier, gestes: (() -> Unit)? = null) {
    val c = LocalCouleurs.current
    val pied = listOf(listOfNotNull(emoji, affaire.categorieLibelle).joinToString(" "), affaire.numero).joinToString(" · ")
    val prestataires = affaire.prestataires.firstOrNull()?.let { premier ->
        if (affaire.prestataires.size > 1) "$premier +${affaire.prestataires.size - 1}" else premier
    }
    val vocal = (listOfNotNull(if (affaire.urgente) stringResource(R.string.affaires_urgente) else null, affaire.titre) + affaire.prestataires + pied)
        .joinToString(", ")
    val rayon = Mise.rayon - 4.dp
    CarteTouchable(affaire.urgente, archive = false, vocal = vocal, ouvrir = ouvrir, modifier = modifier, rayon = rayon, gestes = gestes) {
        Column(
            Modifier.fillMaxWidth().background(c.surface).border(1.dp, c.border, RoundedCornerShape(rayon))
                .padding(start = if (affaire.urgente) 16.dp else 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            prestataires?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.textMuted, maxLines = 1) }
            Text(affaire.titre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(pied, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

/** Une colonne prend 86 % de la largeur : la suivante dépasse, et invite à défiler (comme l'iOS). */
private val LARGEUR_COLONNE = object : PageSize {
    override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int = (availableSpace * 0.86f).toInt()
}

private val SQUELETTE_KANBAN = Affaire(
    id = 0, numero = "A-0000", titre = "Une affaire de la résidence", apercu = "", creeLe = java.time.Instant.EPOCH,
    statut = "ouvert", statutLibelle = "Ouvert", categorie = "", categorieLibelle = "Catégorie",
)
