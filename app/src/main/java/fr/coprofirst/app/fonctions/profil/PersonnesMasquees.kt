// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.FeuillePleine
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.commun.masquages.Masquage
import fr.coprofirst.app.commun.masquages.ReperesMasquage
import fr.coprofirst.app.commun.masquages.ServiceMasquages
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.commun.rememberEnvoi
import java.time.Instant

/**
 * Profil › Personnes masquées (#282), comme l'iOS (`PersonnesMasqueesView`) et le site
 * (`PersonnesMasquees.svelte`) : les résidents dont on masque ce qu'ils publient, et « Ne plus
 * masquer ». Le masque se POSE depuis une publication ; il se défait ici, et seulement ici —
 * l'endroit qu'annonce la confirmation. Une liste vide, en erreur ou en cours : `Chargement`,
 * jamais un « personne » qui prendrait un échec pour une absence. Celui qu'on ne masque plus
 * quitte la liste sans la relire (le site fait de même).
 */
@Composable
internal fun FeuillePersonnesMasquees(service: ServiceMasquages, fermer: () -> Unit) {
    val avis = remember { AvisDuGeste() }
    FeuillePleine(stringResource(R.string.masquage_titre_liste), "masquees.ok", fermer) {
        AvisEphemere(avis) {
            Chargement(
                charger = { service.masques() },
                squelette = { ListeDesMasques(listOf(Masquage(0, "Prénom NOM", Instant.EPOCH)), service, avis) },
            ) { masques, _ -> ListeDesMasques(masques, service, avis) }
        }
    }
}

@Composable
private fun ListeDesMasques(masques: List<Masquage>, service: ServiceMasquages, avis: AvisDuGeste) {
    val couleurs = LocalCouleurs.current
    var retires by remember { mutableStateOf(emptySet<Int>()) }
    val restants = masques.filter { it.auteurId !in retires }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(Mise.ecart),
    ) {
        if (restants.isEmpty()) {
            EtatVide(
                stringResource(R.string.masquage_personne),
                icone = Icons.Outlined.VisibilityOff,
                explication = stringResource(R.string.masquage_ou_se_trouve),
            )
        }
        restants.forEach { masque -> LigneMasque(masque, service, avis) { retires = retires + masque.auteurId } }
        Text(stringResource(R.string.masquage_aide), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
    }
}

/** Le nom, depuis quand, puis « Ne plus masquer ». */
@Composable
private fun LigneMasque(masque: Masquage, service: ServiceMasquages, avis: AvisDuGeste, retire: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val ressources = LocalResources.current
    val envoi = rememberEnvoi()
    Row(Modifier.fillMaxWidth().testTag(ReperesMasquage.masque(masque.auteurId)), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(masque.auteur, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = couleurs.text)
            Text(
                stringResource(R.string.masquage_depuis, FormatsDuSite.date(masque.creeLe)),
                style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted,
            )
        }
        val geste = gesteEnLigne(!envoi.enCours)
        val libelle = stringResource(R.string.masquage_ne_plus_masquer_qui, masque.auteur)
        OutlinedButton(
            onClick = {
                envoi.lancer(echec = { avis.dire(it.phrase(ressources), TonAvis.Erreur) }) {
                    service.demasquer(masque.auteurId)
                    retire()
                    avis.dire(ressources.getString(R.string.masquage_demasque, masque.auteur))
                }
            },
            enabled = geste.actif,
            modifier = geste.modifier.heightIn(min = Mise.cible).semantics { contentDescription = libelle }
                .testTag(ReperesMasquage.demasquer(masque.auteurId)),
        ) { Text(stringResource(R.string.masquage_ne_plus_masquer)) }
    }
}
