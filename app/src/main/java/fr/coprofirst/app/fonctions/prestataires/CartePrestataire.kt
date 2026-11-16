// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.LigneFiche
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.TexteRiche

/**
 * La carte d'un prestataire (`CartePrestataire.svelte`), comme l'iOS : repliée, son en-tête
 * l'ouvre ; dépliée, son adresse, ses contrats, sa description et les avis du conseil
 * syndical (`RecapitulatifAvis`), lus quand la fiche s'ouvre.
 */
@Composable
internal fun CartePrestataire(
    prestataire: Prestataire,
    donnees: DonneesPrestataires,
    service: ServicePrestataires,
    compacte: Boolean,
    deplie: Boolean,
    basculer: () -> Unit,
) {
    val contrats = donnees.contrats(prestataire)
    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EnteteDepliable(prestataire.nom, deplie, ReperesPrestataires.prestataire(prestataire.id), basculer)
            FlowCapsules {
                PastilleTeinte(ReglesPrestataires.type(prestataire.type), "badge-gray", compacte = true)
                PastilleTeinte(donnees.equipement(prestataire.specialite), "badge-blue", compacte = true)
                if (contrats.isNotEmpty()) {
                    PastilleTeinte(
                        stringResource(R.string.prestataires_sous_contrat, FormatsDuSite.nombre(contrats.size)),
                        "badge-green",
                        compacte = true,
                    )
                }
            }
            if (!compacte || deplie) ApercuContacts(prestataire)
            Depliage(deplie) {
                HorizontalDivider(color = LocalCouleurs.current.border)
                prestataire.adresse?.takeIf { it.isNotBlank() }?.let {
                    LigneFiche(stringResource(R.string.prestataires_adresse), "📮 $it")
                }
                LigneFiche(stringResource(R.string.prestataires_contrats), FormatsDuSite.nombre(contrats.size))
                prestataire.description?.takeIf { it.isNotBlank() }?.let {
                    TexteRiche(it, style = MaterialTheme.typography.bodyMedium)
                }
                RecapitulatifAvis(prestataire, service)
            }
        }
    }
}

/**
 * L'aperçu des contacts : les contacts nommés, sinon les numéros SERVIS (un lien d'appel
 * chacun) — puis le courriel.
 */
@Composable
private fun ApercuContacts(prestataire: Prestataire) {
    Column {
        if (prestataire.contacts.isNotEmpty()) {
            prestataire.contacts.forEach { contact ->
                val nom = contact.nomAffiche?.let { n -> " — $n" + (contact.fonction?.let { " ($it)" } ?: "") } ?: ""
                Lien("📞 ${contact.telephone ?: FormatsDuSite.TIRET}$nom", ReglesPrestataires.numeroAppel(contact.telephone)?.let { "tel:$it" })
            }
        } else {
            prestataire.telephones.forEach { Lien("📞 $it", ReglesPrestataires.numeroAppel(it)?.let { n -> "tel:$n" }) }
        }
        prestataire.email?.takeIf { it.isNotBlank() }?.let { Lien("✉️ $it", "mailto:$it") }
    }
}

/** Un numéro ou un courriel touchable (en `primary`), ou un texte inerte s'il n'a pas d'adresse. */
@Composable
private fun Lien(texte: String, adresse: String?) {
    val couleurs = LocalCouleurs.current
    val liens = LocalUriHandler.current
    val modele = Modifier.fillMaxWidth().heightIn(min = Mise.cible).wrapContentHeight(Alignment.CenterVertically)
    if (adresse == null) {
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted, modifier = modele)
    } else {
        Text(
            texte,
            style = MaterialTheme.typography.bodyMedium,
            color = couleurs.primary,
            modifier = Modifier.cliquable(texte) { liens.openUri(adresse) }.then(modele),
        )
    }
}
