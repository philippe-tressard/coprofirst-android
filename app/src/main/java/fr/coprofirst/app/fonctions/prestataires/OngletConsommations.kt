// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.pieces.AvisOuverture
import fr.coprofirst.app.commun.pieces.Ouverture
import fr.coprofirst.app.commun.pieces.PhotoProtegee
import fr.coprofirst.app.commun.pieces.rememberOuverture
import fr.coprofirst.app.fonctions.monlot.Intertitre
import fr.coprofirst.app.session.SessionOuverte

/**
 * L'onglet « Consommations » (`OngletConsommations.svelte`), comme l'iOS : un compteur à
 * la fois (pastilles, sans « Tous »), ses relevés groupés par année, les plus récents
 * d'abord. Au conseil, le fournisseur du compteur choisi.
 *
 * ⚠️ Divergence déclarée : la photo d'un relevé s'ouvre dans l'app de l'appareil
 * (`Ouverture`) — l'iOS la montre dans une feuille.
 */
@Composable
internal fun OngletConsommations(donnees: DonneesPrestataires, service: ServicePrestataires, onglet: ContexteOnglet, conseil: Boolean) {
    val ouverture = rememberOuverture(onglet.session)
    Chargement(
        charger = { service.compteurs().sortedBy { it.ordre } },
        cle = onglet.actualisation,
        squelette = { Text(stringResource(R.string.prestataires_compteur)) },
    ) { compteurs, _ ->
        if (compteurs.isEmpty()) {
            EtatVide(stringResource(R.string.prestataires_aucun_releve), icone = Icons.Outlined.Speed)
            return@Chargement
        }
        var choisi by rememberSaveable { mutableStateOf(compteurs.first().typeCompteur) }
        val compteur = compteurs.firstOrNull { it.typeCompteur == choisi } ?: compteurs.first()
        Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            Libelle(stringResource(R.string.prestataires_compteur))
            ChoixCompteur(compteurs, compteur.typeCompteur) { choisi = it }
            if (conseil) {
                val fournisseur = donnees.prestataire(compteur.prestataireId)
                PastilleTeinte(
                    fournisseur?.let { "🔧 ${it.nom}" } ?: stringResource(R.string.prestataires_aucun_fournisseur),
                    if (fournisseur == null) "badge-gray" else "badge-blue",
                )
            }
            Chargement(
                charger = { service.releves(compteur.typeCompteur) },
                cle = compteur.typeCompteur to onglet.actualisation,
                squelette = { CarteReleve(Releve(0, "2026-01-01", 1), onglet.session, ouverture) },
            ) { releves, _ ->
                Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
                    if (releves.isEmpty()) EtatVide(stringResource(R.string.prestataires_aucun_releve), icone = Icons.Outlined.Speed)
                    ReglesPrestataires.parAnnee(releves).forEach { (annee, groupe) ->
                        Intertitre(annee?.toString() ?: FormatsDuSite.TIRET)
                        groupe.forEach { CarteReleve(it, onglet.session, ouverture) }
                    }
                }
            }
            AvisOuverture(ouverture)
        }
    }
}

@Composable
private fun ChoixCompteur(compteurs: List<Compteur>, choisi: String, choisir: (String) -> Unit) {
    val haptique = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        compteurs.forEach { c ->
            Pastille(c.libelle, retenue = c.typeCompteur == choisi, modifier = Modifier.testTag(ReperesPrestataires.compteur(c.typeCompteur))) {
                if (c.typeCompteur != choisi) {
                    haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    choisir(c.typeCompteur)
                }
            }
        }
    }
}

@Composable
private fun CarteReleve(releve: Releve, session: SessionOuverte, ouverture: Ouverture) {
    val couleurs = LocalCouleurs.current
    Carte(Modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(FormatsDuSite.date(releve.dateReleve), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
                Text(
                    releve.index?.let { stringResource(R.string.prestataires_index, FormatsDuSite.nombre(it)) }
                        ?: stringResource(R.string.prestataires_releve_sans_index),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = couleurs.text,
                )
                releve.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
                }
            }
            releve.photoUrl?.let { chemin ->
                val action = stringResource(R.string.prestataires_photo_releve)
                PhotoProtegee(
                    chemin,
                    session,
                    Modifier.size(PHOTO).clip(RoundedCornerShape(PHOTO_RAYON)).cliquable(action) { ouverture.piece(chemin) },
                )
            }
        }
    }
}

/** La vignette d'un relevé : 64 dp, comme l'iOS. */
private val PHOTO = 64.dp
private val PHOTO_RAYON = 10.dp
