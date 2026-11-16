// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.LigneFiche
import fr.coprofirst.app.commun.NoteEnCarte
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.annuaire.Coordonnees
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.Separateur
import fr.coprofirst.app.commun.texte.TexteRiche

/**
 * L'onglet « Gestion locative » (`OngletGestionLocative.svelte`), en LECTURE comme l'iOS :
 * baux actifs ou archives, une carte par locataire, l'inventaire de chaque bail. Les
 * gestes d'écriture restent sur le site, et l'écran le dit.
 */
@Composable
fun OngletLocation(donnees: DonneesMonLot) {
    var archives by rememberSaveable { mutableStateOf(false) }
    val groupes = GroupesDeBaux.parLocataire(if (archives) donnees.bauxTermines else donnees.bauxActifs)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag(ReperesMonLot.SOUS_ONGLET_LOCATION)) {
        listOf(
            false to stringResource(R.string.monlot_baux_actifs, donnees.bauxActifs.size),
            true to stringResource(R.string.monlot_archives, donnees.bauxTermines.size),
        ).forEachIndexed { rang, (valeur, libelle) ->
            SegmentedButton(
                selected = archives == valeur,
                onClick = { archives = valeur },
                shape = SegmentedButtonDefaults.itemShape(rang, 2),
            ) { Text(libelle) }
        }
    }
    if (groupes.isEmpty()) {
        NoteEnCarte(stringResource(if (archives) R.string.monlot_aucun_bail_termine else R.string.monlot_aucun_bail_actif_point))
    } else {
        groupes.forEach { CarteBaux(it, donnees.lots) }
    }
    Text(
        stringResource(R.string.monlot_gestes_location_sur_le_site),
        style = MaterialTheme.typography.bodySmall,
        color = LocalCouleurs.current.textMuted,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Un locataire et ses baux : coordonnées, statut, dates, notes, inventaire. */
@Composable
private fun CarteBaux(groupe: GroupesDeBaux.Groupe, lots: List<LotDuCompte>) {
    val premier = groupe.baux.first()
    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    premier.nomLocataire,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = LocalCouleurs.current.text,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                PastilleTeinte(LibellesMonLot.statutBail(premier.statut))
            }
            Coordonnees(premier.locataireEmail, listOfNotNull(premier.locataireTelephone))
            groupe.baux.forEach { bail ->
                HorizontalDivider(color = LocalCouleurs.current.border)
                UnBail(bail, lots.firstOrNull { it.id == bail.lotId }, plusieurs = groupe.baux.size > 1)
            }
        }
    }
}

@Composable
private fun UnBail(bail: Bail, lot: LotDuCompte?, plusieurs: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            if (lot != null) {
                Text(lot.intitule, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                PastilleTeinte(lot.typeLu, "badge-gray", compacte = true)
            }
            if (plusieurs) PastilleTeinte(LibellesMonLot.statutBail(bail.statut), compacte = true)
        }
        LigneFiche(stringResource(R.string.monlot_entree_le), FormatsDuSite.dateCourte(bail.dateEntree))
        LigneFiche(stringResource(R.string.monlot_sortie_prevue_le), FormatsDuSite.dateCourte(bail.dateSortiePrevue))
        if (bail.dateSortieReelle != null) {
            LigneFiche(stringResource(R.string.monlot_sortie_reelle_le), FormatsDuSite.dateCourte(bail.dateSortieReelle))
        }
        bail.notes?.takeIf { it.isNotEmpty() }?.let { TexteRiche(it, style = MaterialTheme.typography.bodyMedium) }
        Inventaire(bail.objets)
    }
}

/** L'inventaire d'un bail (`InventaireBail.svelte`), en lecture, replié d'abord. */
@Composable
private fun Inventaire(objets: List<ObjetRemis>) {
    var ouvert by rememberSaveable { mutableStateOf(false) }
    val couleurs = LocalCouleurs.current
    val titre = pluralStringResource(R.plurals.monlot_inventaire, objets.size, objets.size)
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible).cliquable(titre) { ouvert = !ouvert },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titre, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = couleurs.primary, modifier = Modifier.weight(1f))
            Icon(if (ouvert) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = couleurs.primary)
        }
        AnimatedVisibility(ouvert) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (objets.isEmpty()) {
                    Text(stringResource(R.string.monlot_aucun_objet), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
                }
                objets.forEach { LigneObjet(it) }
            }
        }
    }
}

@Composable
private fun LigneObjet(objet: ObjetRemis) {
    val couleurs = LocalCouleurs.current
    val details = buildList {
        add(stringResource(R.string.monlot_objet_quantite, FormatsDuSite.nombre(objet.quantite)))
        objet.reference?.takeIf { it.isNotEmpty() }?.let { add(it) }
        add(stringResource(R.string.monlot_objet_remis, FormatsDuSite.date(objet.remisLe)))
        if (objet.renduLe != null) add(stringResource(R.string.monlot_objet_rendu, FormatsDuSite.date(objet.renduLe)))
    }
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text(LibellesMonLot.typeObjet(objet.type), style = MaterialTheme.typography.bodyMedium)
            Text(objet.libelle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = couleurs.text)
            PastilleTeinte(LibellesMonLot.statutObjet(objet.statut), compacte = true)
        }
        Text(details.joinToString(Separateur.point), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
    }
}
