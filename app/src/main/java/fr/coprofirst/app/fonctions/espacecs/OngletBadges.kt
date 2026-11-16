// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VpnKeyOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.ChampRecherche
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.acces.LibellesAcces
import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet

/**
 * « Badges & télécommandes » (onglet `badges`, `BadgesCopropriete.svelte`), comme l'iOS
 * (`OngletBadgesCS`) : qui détient quoi. La recherche en tête (code, porteur, lot), le type
 * dessous, puis le parc, rangé par porteur.
 *
 * Consultation seule, comme l'iOS : enregistrer, corriger, exporter et retirer un accès se
 * font sur le site (le descriptif de l'onglet le dit).
 */
@Composable
fun OngletBadges(contexte: ContexteOnglet) {
    val service = remember(contexte.session) { ServiceBadgesApi(contexte.session) }
    OngletBadges(service, contexte.actualisation)
}

@Composable
fun OngletBadges(service: ServiceBadges, actualisation: Int) {
    Chargement(charger = { service.parc() }, cle = actualisation, squelette = { SqueletteParc() }) { parc, _ ->
        if (parc.isEmpty()) {
            EtatVide(
                stringResource(R.string.espacecs_badges_vide_titre),
                icone = Icons.Outlined.VpnKeyOff,
                explication = stringResource(R.string.espacecs_badges_vide_message),
            )
        } else {
            Parc(parc)
        }
    }
}

@Composable
private fun Parc(parc: List<AccesDuParc>) {
    val c = LocalCouleurs.current
    var recherche by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<TypeAcces?>(null) }
    val filtres = remember(parc, type, recherche) { parc.filtrer(type, recherche) }
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        ChampRecherche(recherche, { recherche = it }, stringResource(R.string.espacecs_badges_recherche), ReperesEspaceCS.BADGES_RECHERCHE)
        FiltreTypes(type) { type = it }
        if (filtres.isEmpty()) {
            Text(stringResource(R.string.espacecs_badges_aucun_resultat), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        filtres.forEach { CarteAccesDuParc(it) }
        val n = filtres.size
        Text(
            if (n == parc.size) {
                pluralStringResource(R.plurals.espacecs_badges_compte, n, FormatsDuSite.nombre(n))
            } else {
                pluralStringResource(R.plurals.espacecs_badges_compte_sur, n, FormatsDuSite.nombre(n), FormatsDuSite.nombre(parc.size))
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            modifier = Modifier.testTag(ReperesEspaceCS.BADGES_COMPTE),
        )
    }
}

/** Le filtre « Type » : Tous, puis les familles du site (`TYPES_ACCES`, généré). */
@Composable
private fun FiltreTypes(type: TypeAcces?, choisir: (TypeAcces?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.espacecs_badges_type).uppercase(), style = Typo.libelle(), color = LocalCouleurs.current.textMuted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pastille(stringResource(R.string.espacecs_badges_tous), retenue = type == null) { choisir(null) }
            TypeAcces.entries.forEach { option ->
                Pastille(option.libelle, retenue = type == option, Modifier.testTag(ReperesEspaceCS.badgesType(option.code))) {
                    choisir(if (type == option) null else option)
                }
            }
        }
    }
}

/** Un accès du parc : son code, son type et son état, puis qui le porte, pour quel lot, et ce qu'il ouvre. */
@Composable
private fun CarteAccesDuParc(acces: AccesDuParc) {
    val c = LocalCouleurs.current
    Carte(Modifier.testTag(ReperesEspaceCS.badge(acces.id))) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(acces.code, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = c.text)
                Text(acces.type.libelle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                PastilleTeinte(LibellesAcces.statutAcces(acces.statut), compacte = true)
            }
            if (acces.porteurNom.isNotEmpty()) Ligne(Icons.Outlined.Person, acces.porteurNom, c.text)
            acces.lotLibelle?.let { Ligne(Icons.Outlined.Home, it, c.textMuted) }
            acces.lieu?.let { Ligne(Icons.Outlined.MeetingRoom, it, c.textMuted) }
            if (acces.chezLocataire) {
                Text(stringResource(R.string.espacecs_badges_chez_locataire), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun Ligne(icone: ImageVector, texte: String, couleur: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icone, contentDescription = null, tint = LocalCouleurs.current.textMuted, modifier = Modifier.size(18.dp))
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = couleur)
    }
}

@Composable
private fun SqueletteParc() {
    val exemple = AccesDuParc(TypeAcces.Vigik, 0, "0000", "Prénom Nom", 0, lotLibelle = "Lot 00 — appartement", statut = "actif")
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) { repeat(3) { CarteAccesDuParc(exemple) } }
}
