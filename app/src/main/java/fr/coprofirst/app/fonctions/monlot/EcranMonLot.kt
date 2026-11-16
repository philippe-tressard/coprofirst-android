// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.commun.onglets.BarreOnglets
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.navigation.ActionsDeBarre
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.DroitsOnglet

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesMonLot {
    fun onglet(id: String) = "onglet.$id"
    fun lot(id: Int) = "acces.lot.$id"
    fun perdu(id: Int) = "acces.perdu.$id"
    const val PERDU_CONFIRMER = "acces.perdu.confirmer"
    const val GESTES = "acces.gestes"
    const val DEMANDER = "acces.geste.demander"
    const val DECLARER = "acces.geste.declarer"
    const val DECLARER_CODE = "acces.declarer.code"
    const val DEMANDE_LOT = "acces.demande.lot"
    const val ENREGISTRER = "acces.feuille.enregistrer"
    const val SOUS_ONGLET_LOCATION = "acces.location.sousOnglet"
}

/** Les deux gestes d'accès, chacun dans sa feuille. */
enum class FeuilleAcces { Demander, Declarer }

/**
 * « Mes lots & accès » (page `mon-lot`, #18), comme l'iOS (`AccesView`) : quatre onglets —
 * Mes lots, Mes badges d'accès (Vigik), Télécommandes de parking, Gestion locative (à qui
 * la gère). Les deux gestes d'accès sont dans la barre du haut, sur les deux onglets d'accès.
 */
@Composable
fun EcranMonLot(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceMonLotApi(contexte.session) }
    EcranMonLot(service, contexte.barre, contexte.droits)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EcranMonLot(service: ServiceMonLot, barre: BarreDuHaut, droitsConnus: DroitsOnglet) {
    var onglet by rememberSaveable { mutableStateOf(DroitsMonLot.ONGLET_LOTS) }
    var relecture by rememberSaveable { mutableIntStateOf(0) }
    var versionAcces by rememberSaveable { mutableIntStateOf(0) }
    var feuille by rememberSaveable { mutableStateOf<FeuilleAcces?>(null) }
    // L'avis du geste (le « toast » du site) : une feuille le dit à sa fermeture.
    val avis = remember { AvisDuGeste() }
    val ressources = LocalResources.current
    val annoncer: (Int) -> Unit = { texte -> avis.dire(ressources.getString(texte)) }
    val annoncerApresFeuille: (Int) -> Unit = { texte -> avis.direApresFermeture(ressources.getString(texte)) }
    val fermerFeuille = {
        feuille = null
        avis.feuilleFermee()
    }

    AvisEphemere(avis, Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = false, onRefresh = { relecture++ }, modifier = Modifier.fillMaxSize()) {
            Chargement(
                charger = { DonneesMonLot.lire(service, droitsConnus) },
                cle = relecture,
                squelette = { SqueletteMonLot() },
            ) { donnees, _ ->
                // Un onglet masqué ne s'atteint pas (la redirection du site).
                val ouvert = onglet.takeIf { id -> donnees.onglets.any { it.id == id } } ?: DroitsMonLot.ONGLET_LOTS
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BarreOnglets(donnees.onglets, ouvert, marges = PaddingValues(horizontal = Mise.marge)) { onglet = it }
                    Column(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = Mise.marge, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(Mise.ecart),
                    ) {
                        Page(ouvert, donnees, service, versionAcces, annoncer, barre) { feuille = it }
                    }
                }
                when (feuille) {
                    FeuilleAcces.Demander -> FeuilleDemander(donnees.lots, service, fermer = fermerFeuille) {
                        annoncerApresFeuille(R.string.monlot_demande_envoyee)
                    }
                    FeuilleAcces.Declarer -> FeuilleDeclarer(service, fermer = fermerFeuille) { importResolu ->
                        annoncerApresFeuille(if (importResolu) R.string.monlot_acces_enregistre_import else R.string.monlot_acces_enregistre)
                        versionAcces++
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun Page(
    onglet: String,
    donnees: DonneesMonLot,
    service: ServiceMonLot,
    versionAcces: Int,
    annoncer: (Int) -> Unit,
    barre: BarreDuHaut,
    ouvrir: (FeuilleAcces) -> Unit,
) {
    val acces = onglet == DroitsMonLot.ONGLET_BADGES || onglet == DroitsMonLot.ONGLET_TELECOMMANDES
    // Les descriptifs des deux onglets d'accès décrivent la page web (« bouton … en haut à
    // droite », un lien de la FAQ) : ils égareraient dans l'app — comme l'iOS.
    val descriptif = TablesMonLot.descriptifs[onglet]
    if (!acces && descriptif != null) {
        Text(TexteDuSite.apercu(descriptif), style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
    }
    val erreur = donnees.erreurLots
    when {
        acces -> {
            GestesAcces(barre, donnees.droits.peutDemander(donnees.lots), ouvrir)
            val type = if (onglet == DroitsMonLot.ONGLET_BADGES) TypeAcces.Vigik else TypeAcces.Telecommande
            OngletAcces(type, donnees, service, versionAcces, annoncer)
        }
        erreur != null -> {
            Text(stringResource(R.string.monlot_erreur_lots), style = MaterialTheme.typography.titleMedium, color = LocalCouleurs.current.text)
            MessageErreur(erreur)
        }
        onglet == DroitsMonLot.ONGLET_LOCATION -> OngletLocation(donnees)
        else -> OngletLots(donnees)
    }
}

/**
 * « + » dans la barre du haut, comme l'iOS (`menuGestes`) : les deux gestes, qui ne disent
 * pas la même chose — « Nouvel accès » DEMANDE au syndic ce qu'on n'a pas, « Déclarer »
 * SIGNALE ce qu'on détient déjà. Posé tant que l'onglet d'accès est affiché.
 */
@Composable
private fun GestesAcces(barre: BarreDuHaut, peutDemander: Boolean, ouvrir: (FeuilleAcces) -> Unit) {
    ActionsDeBarre(barre) {
        var deplie by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { deplie = true }, modifier = Modifier.testTag(ReperesMonLot.GESTES)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.monlot_gestes))
            }
            DropdownMenu(expanded = deplie, onDismissRequest = { deplie = false }) {
                if (peutDemander) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.monlot_nouvel_acces)) },
                        leadingIcon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
                        onClick = { deplie = false; ouvrir(FeuilleAcces.Demander) },
                        modifier = Modifier.testTag(ReperesMonLot.DEMANDER),
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.monlot_declarer_un_acces)) },
                    leadingIcon = { Icon(Icons.Outlined.Verified, contentDescription = null) },
                    onClick = { deplie = false; ouvrir(FeuilleAcces.Declarer) },
                    modifier = Modifier.testTag(ReperesMonLot.DECLARER),
                )
            }
        }
    }
}

/** Le squelette : une fiche de lot grisée. */
@Composable
private fun SqueletteMonLot() {
    Column(Modifier.padding(Mise.marge)) {
        Carte {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.monlot_caracteristiques), style = MaterialTheme.typography.titleLarge)
                repeat(5) { LigneFiche("Libellé", "Une valeur") }
            }
        }
    }
}
