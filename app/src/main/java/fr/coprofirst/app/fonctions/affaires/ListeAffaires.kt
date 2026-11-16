// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.NaturesAffaire
import fr.coprofirst.app.charte.Pictogrammes
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.ChampRecherche
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.gesteEnLigne
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * L'onglet Liste, comme l'iOS (`ListeAffaires`) : les actualités et les affaires en cours —
 * recherche au serveur, filtre « Suivi », nature et portée (le filtre de la barre du haut) ;
 * un toucher ouvre l'affaire, un appui long ses gestes.
 *
 * ⚠️ Divergence déclarée : « Archiver » est dans les gestes de l'appui long — l'iOS l'offre
 * aussi au balayage de la carte, geste qu'une liste d'Android ne porte pas d'usage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListeAffaires(ctx: ContexteAffaires) {
    val etat = ctx.etat
    val portee = rememberCoroutineScope()
    var rafraichit by remember { mutableStateOf(false) }
    LaunchedEffect(etat.recherche) { etat.chercher() }

    PullToRefreshBox(
        isRefreshing = rafraichit,
        onRefresh = { portee.launch { rafraichit = true; etat.charger(); rafraichit = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Mise.marge, end = Mise.marge, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Mise.ecart),
        ) {
            item(key = "recherche") {
                ChampRecherche(
                    etat.recherche, { etat.recherche = it }, stringResource(R.string.affaires_rechercher), ReperesAffaires.RECHERCHE,
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item(key = "suivi") { PastillesDeSuivi(etat) }
            if (etat.recherche.trim().length >= ReglesAffaires.RECHERCHE_MINIMALE && etat.archiveesTrouvees > 0) {
                item(key = "archives") { InclureArchives(etat) }
            }
            when (val phase = etat.phase) {
                EtatAffaires.Phase.Chargement -> items(4, key = { "squelette-$it" }) { SqueletteCarte() }
                is EtatAffaires.Phase.Erreur -> item(key = "erreur") {
                    ErreurOnglet(R.string.affaires_erreur_liste, phase.erreur) { portee.launch { etat.charger() } }
                }
                EtatAffaires.Phase.Pret -> {
                    val liste = etat.liste
                    items(liste, key = { it.id }) { affaire -> LigneAffaire(affaire, ctx) }
                    if (liste.isEmpty()) item(key = "vide") { ListeVide(etat, ctx.nouvelle) }
                }
            }
        }
    }
}

/** Une carte et ses gestes (appui long) : copier le numéro, partager le lien, archiver si permis. */
@Composable
private fun LigneAffaire(affaire: Affaire, ctx: ContexteAffaires) {
    var gestes by remember { mutableStateOf(false) }
    val presse = LocalClipboard.current
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    Box {
        CarteAffaire(
            affaire, archive = affaire.archivee, ouvrir = { ctx.ouvrir(affaire) },
            modifier = Modifier.testTag(ReperesAffaires.carte(affaire.numero)), gestes = { gestes = true },
        )
        DropdownMenu(gestes, { gestes = false }, Modifier.testTag(ReperesAffaires.gestes(affaire.numero))) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.affaires_copier_numero)) },
                leadingIcon = { Icon(Icons.Outlined.Numbers, contentDescription = null) },
                onClick = {
                    gestes = false
                    portee.launch { presse.setClipEntry(ClipEntry(ClipData.newPlainText(affaire.numero, affaire.numero))) }
                },
            )
            ctx.lienSite(affaire.id)?.let { lien ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.affaires_partager_lien)) },
                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    onClick = {
                        gestes = false
                        val envoi = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lien)
                        contexte.startActivity(Intent.createChooser(envoi, null))
                    },
                )
            }
            // Ce que le serveur permet à CE lecteur (`peut_archiver`) ; archiver, jamais supprimer.
            if (affaire.peutArchiver && !affaire.archivee) {
                HorizontalDivider()
                val geste = gesteEnLigne()
                DropdownMenuItem(
                    enabled = geste.actif,
                    text = { Text(stringResource(R.string.affaires_archiver)) },
                    leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                    onClick = {
                        gestes = false
                        portee.launch { ctx.etat.archiver(affaire) }
                    },
                    modifier = geste.modifier.testTag(ReperesAffaires.archiver(affaire.numero)),
                )
            }
        }
    }
}

/** Le filtre « Suivi » en pastilles : « Toutes », puis les états présents — le compte sur la retenue. */
@Composable
private fun PastillesDeSuivi(etat: EtatAffaires) {
    val libelle = stringResource(R.string.affaires_suivi)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = libelle },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val options = listOf<Pair<String?, String>>(null to stringResource(R.string.affaires_toutes)) + etat.optionsDeSuivi.map { it.code to it.libelle }
        options.forEach { (code, texte) ->
            val retenue = etat.filtres.suivi == code
            Pastille(
                texte, retenue, Modifier.testTag(ReperesAffaires.suivi(code)),
                compte = if (retenue && etat.phase == EtatAffaires.Phase.Pret) etat.liste.size else null,
            ) { etat.filtres = etat.filtres.copy(suivi = code) }
        }
    }
}

/** La recherche a trouvé des affaires archivées : on les propose, sans les imposer. */
@Composable
private fun InclureArchives(etat: EtatAffaires) {
    val c = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().testTag(ReperesAffaires.INCLURE_ARCHIVES),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Archive, contentDescription = null, tint = c.text)
        Text(
            stringResource(R.string.affaires_inclure_archives, FormatsDuSite.nombre(etat.archiveesTrouvees)),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text,
        )
        Switch(etat.avecArchives, { etat.avecArchives = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.primaryPlein))
    }
}

@Composable
private fun ListeVide(etat: EtatAffaires, nouvelle: () -> Unit) {
    when {
        etat.trouvees != null -> EtatVide(
            stringResource(R.string.affaires_aucune_trouvee), icone = Icons.Outlined.Search,
            explication = stringResource(R.string.affaires_aucune_trouvee_aide),
        )
        !etat.rechercheEnCours -> Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            EtatVide(stringResource(R.string.affaires_aucune), icone = Icons.Outlined.Inbox, explication = stringResource(R.string.affaires_aucune_aide))
            val geste = gesteEnLigne()
            BoutonPlein(R.string.nouvelle_titre, geste.modifier, actif = geste.actif, onClick = nouvelle)
        }
    }
}

/**
 * Nature et portée, dans un menu de la barre du haut, comme l'iOS (`MenuFiltresAffaires`) :
 * le pictogramme se remplit quand un filtre est retenu, et le lecteur d'écran dit combien.
 */
@Composable
internal fun MenuFiltres(etat: EtatAffaires) {
    var ouvert by remember { mutableStateOf(false) }
    val actifs = etat.filtres.actifs
    val valeur = if (actifs > 0) pluralStringResource(R.plurals.affaires_filtres_retenus, actifs, actifs) else stringResource(R.string.affaires_aucun_filtre)
    val libelle = stringResource(R.string.affaires_filtrer)
    Box {
        IconButton(
            onClick = { ouvert = true },
            modifier = Modifier.testTag(ReperesAffaires.FILTRES).semantics { stateDescription = valeur },
        ) {
            Icon(if (actifs > 0) Icons.Filled.FilterList else Icons.Outlined.FilterList, contentDescription = libelle, tint = LocalCouleurs.current.primary)
        }
        DropdownMenu(ouvert, { ouvert = false }) {
            Text(
                stringResource(R.string.affaires_nature), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = LocalCouleurs.current.textMuted,
            )
            val natures = listOf<Triple<String?, String, String?>>(Triple(null, stringResource(R.string.affaires_toutes_natures), null)) +
                NaturesAffaire.toutes.map { Triple(it.code, it.libelle, it.icone) }
            natures.forEach { (code, texte, icone) ->
                DropdownMenuItem(
                    text = { Text(texte) },
                    leadingIcon = { RadioButton(etat.filtres.nature == code, onClick = null) },
                    trailingIcon = icone?.let { { Icon(Pictogrammes.icone(it), contentDescription = null) } },
                    onClick = { etat.filtres = etat.filtres.copy(nature = code) },
                    modifier = Modifier.testTag(ReperesAffaires.nature(code)),
                )
            }
            if (etat.offrirMeConcerne) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.affaires_me_concerne)) },
                    leadingIcon = { Checkbox(etat.filtres.meConcerne, onCheckedChange = null) },
                    onClick = { etat.filtres = etat.filtres.copy(meConcerne = !etat.filtres.meConcerne) },
                    modifier = Modifier.testTag(ReperesAffaires.ME_CONCERNE),
                )
            }
        }
    }
}

/** La carte en squelette, pendant le chargement : estompée, muette au toucher, dite « Chargement ». */
@Composable
internal fun SqueletteCarte() {
    val annonce = stringResource(R.string.chargement)
    Box(Modifier.alpha(0.35f).clearAndSetSemantics { contentDescription = annonce }) {
        CarteAffaire(SQUELETTE, archive = false, ouvrir = {})
    }
}

private val SQUELETTE = Affaire(
    id = 0, numero = "A-0000", titre = "Une affaire de la résidence", apercu = "Le détail de l'affaire arrive.",
    creeLe = Instant.EPOCH, statut = "ouvert", statutLibelle = "Ouvert", categorie = "", categorieLibelle = "",
)
