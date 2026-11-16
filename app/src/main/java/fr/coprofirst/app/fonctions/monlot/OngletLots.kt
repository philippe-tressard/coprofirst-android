// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Intertitre
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.LigneFiche
import fr.coprofirst.app.commun.NoteEnCarte
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.annuaire.Coordonnees
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.Separateur

/**
 * L'onglet « Mes lots » : la vue dépend de ce que l'on est, comme le site
 * (`mon-lot/+page.svelte`) et l'iOS — locataire, bailleur, ou la vue de tous les autres.
 */
@Composable
fun OngletLots(donnees: DonneesMonLot) {
    val droits = donnees.droits
    when {
        donnees.lots.isEmpty() && !droits.locataire -> NoteEnCarte(
            stringResource(R.string.monlot_aucun_lot_detail),
            stringResource(R.string.monlot_aucun_lot),
            Icons.Outlined.Home,
        )
        droits.locataire -> LotsDuLocataire(donnees.bail, donnees.lots)
        droits.bailleur -> LotsDuBailleur(donnees.lots, donnees.bauxActifs)
        else -> LotsStandard(donnees.lots)
    }
}

/** Vue standard : un sélecteur de lot s'il y en a plusieurs, puis sa carte. */
@Composable
private fun LotsStandard(lots: List<LotDuCompte>) {
    var choisi by rememberSaveable { mutableStateOf<Int?>(null) }
    val selection = lots.firstOrNull { it.id == choisi } ?: lots.firstOrNull()
    if (lots.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Libelle(stringResource(R.string.monlot_lot_affiche))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lots.forEach { lot ->
                    // « Bât. A / appartement - 15 » (`optionsLots` du site).
                    Pastille(
                        libelle = "${lot.batimentNom ?: FormatsDuSite.TIRET} / ${lot.type ?: FormatsDuSite.TIRET} - ${lot.numero}",
                        retenue = lot.id == selection?.id,
                        modifier = Modifier.testTag(ReperesMonLot.lot(lot.id)),
                    ) { choisi = lot.id }
                }
            }
        }
    }
    ChargesSurLeSite()
    if (selection != null) {
        AnimatedContent(selection, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "lot") { lot ->
            Carte {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.monlot_caracteristiques),
                        style = Typo.titre(MaterialTheme.typography.titleLarge),
                        color = LocalCouleurs.current.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    CaracteristiquesDuLot(
                        lot,
                        avant = listOf(
                            stringResource(R.string.monlot_lot) to lot.numero,
                            stringResource(R.string.monlot_batiment) to (lot.batimentNom ?: FormatsDuSite.TIRET),
                        ),
                    )
                }
            }
        }
    }
}

/** Vue locataire (`LotsLocataire.svelte`) : le lot loué par bail, puis ses lots. */
@Composable
private fun LotsDuLocataire(bail: BailDuLocataire?, lots: List<LotDuCompte>) {
    val loues = lots.filter { it.loue }
    val possedes = lots.filterNot { it.loue }
    if (bail != null) {
        Intertitre(stringResource(R.string.monlot_lot_loue))
        CarteDuBail(bail)
    } else if (loues.isEmpty()) {
        NoteEnCarte(stringResource(R.string.monlot_aucun_bail_actif_aide), stringResource(R.string.monlot_aucun_bail_actif), Icons.Outlined.Key)
    }
    if (bail == null && loues.isNotEmpty()) {
        Intertitre(stringResource(R.string.monlot_lots_loues, loues.size))
        loues.forEach { CarteLot(it) }
    }
    if (possedes.isNotEmpty()) {
        Intertitre(stringResource(R.string.monlot_lots_en_propriete, possedes.size))
        ChargesSurLeSite()
        possedes.forEach { CarteLot(it) }
    }
}

@Composable
private fun CarteDuBail(bail: BailDuLocataire) {
    val apres = buildList {
        add(stringResource(R.string.monlot_entree) to FormatsDuSite.dateCourte(bail.dateEntree))
        if (bail.dateSortiePrevue != null) add(stringResource(R.string.monlot_sortie_prevue) to FormatsDuSite.dateCourte(bail.dateSortiePrevue))
    }
    CarteLot(
        bail.lot,
        avant = listOf(stringResource(R.string.monlot_batiment) to (bail.lot.batimentNom ?: FormatsDuSite.TIRET)),
        complement = { PastilleTeinte(LibellesMonLot.statutBail(bail.statut), compacte = true) },
    ) {
        apres.forEach { (libelle, valeur) -> LigneFiche(libelle, valeur) }
        if (bail.nomBailleur != FormatsDuSite.TIRET) {
            HorizontalDivider(color = LocalCouleurs.current.border)
            Coordonnees(bail.bailleurEmail, listOfNotNull(bail.bailleurTelephone), stringResource(R.string.monlot_proprietaire, bail.nomBailleur))
        }
    }
}

/** Vue bailleur (`LotsBailleur.svelte`) : ses lots, occupés ou vacants, et ses locataires. */
@Composable
private fun LotsDuBailleur(lots: List<LotDuCompte>, bauxActifs: List<Bail>) {
    fun bailDu(lot: LotDuCompte) = bauxActifs.firstOrNull { it.lotId == lot.id }
    val vacants = lots.filter { bailDu(it) == null }
    val couleurs = LocalCouleurs.current
    ChargesSurLeSite()
    Intertitre(stringResource(R.string.monlot_lots_possedes, lots.size))
    lots.forEach { lot ->
        val bail = bailDu(lot)
        CarteLot(
            lot,
            complement = {
                PastilleTeinte(
                    stringResource(if (bail == null) R.string.monlot_vacant else R.string.monlot_occupe),
                    if (bail == null) "badge-gray" else "badge-green",
                    compacte = true,
                )
            },
        ) {
            if (bail != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = couleurs.textMuted)
                    Text(bail.nomLocataire, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
                }
            }
        }
    }
    val groupes = GroupesDeBaux.parLocataire(bauxActifs)
    if (groupes.isNotEmpty()) {
        Intertitre(stringResource(R.string.monlot_locataires, groupes.size))
        groupes.forEach { CarteLocataire(it, lots) }
    }
    if (vacants.isNotEmpty()) {
        Intertitre(stringResource(R.string.monlot_lots_vacants, vacants.size))
        Text(stringResource(R.string.monlot_lots_vacants_aide), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
    }
}

/** Un locataire et ses baux, dans la vue du bailleur. */
@Composable
private fun CarteLocataire(groupe: GroupesDeBaux.Groupe, lots: List<LotDuCompte>) {
    val premier = groupe.baux.first()
    val couleurs = LocalCouleurs.current
    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "👤 ${premier.nomLocataire}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = couleurs.text,
                    modifier = Modifier.weight(1f),
                )
                PastilleTeinte(LibellesMonLot.statutBail(premier.statut), compacte = true)
            }
            Coordonnees(premier.locataireEmail, listOfNotNull(premier.locataireTelephone))
            groupe.baux.forEach { bail ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    lots.firstOrNull { it.id == bail.lotId }?.let { lot ->
                        Text("${lot.intitule}${Separateur.point}${lot.typeLu}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    }
                    val entree = FormatsDuSite.dateCourte(bail.dateEntree)
                    Text(
                        if (bail.dateSortiePrevue == null) {
                            stringResource(R.string.monlot_depuis, entree)
                        } else {
                            stringResource(R.string.monlot_depuis_sortie, entree, FormatsDuSite.dateCourte(bail.dateSortiePrevue))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = couleurs.textMuted,
                    )
                }
            }
        }
    }
}
