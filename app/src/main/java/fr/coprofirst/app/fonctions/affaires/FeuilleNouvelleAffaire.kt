// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.LocalSaisieAffaire
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.apercu.QuiLaLira
import fr.coprofirst.app.commun.apercu.SaisieLecture
import fr.coprofirst.app.session.ErreurService

/** Les repères de « Nouvelle affaire » et de « Modifier », ceux de l'iOS ; ceux des champs : `ReperesChamps`. */
object ReperesNouvelle {
    const val NOUVELLE = "affaires.nouvelle"
    const val ENVOYER = "nouvelle.envoyer"
    const val QUI_LA_LIRA = "nouvelle.quiLaLira"
    const val DEPUIS_LE_SITE = "nouvelle.depuisLeSite"
    const val ABANDONNER = "nouvelle.abandonner"
    const val MODIFIER_ENREGISTRER = "modifier.enregistrer"
}

/**
 * « Nouvelle affaire », comme l'iOS (`NouvelleAffaireView`, `ChampsAffaire`) : catégorie,
 * titre, description, lieu, urgence. Ce que l'app ne pose pas est dit, jamais caché : les
 * pièces jointes et les sections que le conseil remplit restent au site.
 *
 * En saisie Essentielle, ce qui n'est pas essentiel pour la catégorie se range dans « Plus de
 * détails » (`FormeDeSaisie`, #178), ici comme dans « Modifier ».
 * « Qui la lira » se dit dès la catégorie choisie (`QuiLaLira`, #179).
 */
@Composable
internal fun FeuilleNouvelleAffaire(
    referentiels: ReferentielsAffaires?,
    erreurReferentiels: ErreurService?,
    service: ServiceAffaires,
    fermer: () -> Unit,
    creee: (Affaire) -> Unit,
) {
    var saisie by remember { mutableStateOf(SaisieAffaire()) }
    var abandon by remember { mutableStateOf(false) }
    val categories = referentiels?.permises
    FeuilleDeSaisie(
        R.string.nouvelle_titre, R.string.nouvelle_envoyer, ReperesNouvelle.ENVOYER,
        prete = categories != null && saisie.complete, fermer = fermer,
        envoyer = { creee(service.creer(saisie)) },
        retenue = saisie.entamee, demanderAbandon = { abandon = true },
    ) {
        when {
            categories != null -> {
                ChampsAffaire(
                    saisie, categories, service, ReperesChamps.NOUVELLE, poserLeDefaut = true,
                    forme = FormeDeSaisie.de(LocalSaisieAffaire.current, referentiels.categories),
                ) { saisie = it }
                // Ce que la saisie dit de ses lecteurs : sa catégorie et son lieu (un bogue n'en a pas).
                val aLire = saisie.categorie.takeIf { it.isNotEmpty() }?.let { SaisieLecture(it, listOfNotNull(saisie.perimetre.takeUnless { saisie.estBogue })) }
                QuiLaLira(aLire, service, ReperesNouvelle.QUI_LA_LIRA)
            }
            erreurReferentiels != null -> MessageErreur(erreurReferentiels)
            else -> {
                val annonce = stringResource(R.string.nouvelle_chargement_categories)
                Box(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = annonce }, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        DepuisLeSite(referentiels?.poseLesReservees ?: false)
    }
    if (abandon) {
        AlertDialog(
            onDismissRequest = { abandon = false },
            title = { Text(stringResource(R.string.nouvelle_abandonner_titre)) },
            confirmButton = {
                TextButton(onClick = { abandon = false; fermer() }, modifier = Modifier.testTag(ReperesNouvelle.ABANDONNER)) {
                    Text(stringResource(R.string.nouvelle_abandonner), color = LocalCouleurs.current.danger)
                }
            },
            dismissButton = { TextButton(onClick = { abandon = false }) { Text(stringResource(R.string.nouvelle_continuer)) } },
        )
    }
}

/** Ce que cette feuille ne pose pas, selon ce que le serveur permet à qui la remplit. */
@Composable
private fun DepuisLeSite(poseLesReservees: Boolean) {
    val c = LocalCouleurs.current
    Libelle(stringResource(R.string.nouvelle_depuis_le_site), Modifier.semantics { heading() })
    val texte = stringResource(R.string.nouvelle_pieces_au_site) + " " +
        stringResource(if (poseLesReservees) R.string.nouvelle_reserve_au_site else R.string.nouvelle_conseil_au_site)
    Row(
        Modifier.fillMaxWidth().testTag(ReperesNouvelle.DEPUIS_LE_SITE).clearAndSetSemantics { contentDescription = texte },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Language, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        Text(texte, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

/**
 * « Modifier l'affaire » (✏️ du bandeau), comme l'iOS (`ModifierAffaireFeuille`) : les mêmes
 * champs que la Création, partis de l'affaire servie ; n'envoie que ce qui a changé
 * (`EditionAffaire`). Les catégories offertes sont celles que le serveur PERMET
 * (`permises(enModifiant)`).
 * Ses prestataires, selon le droit servi (`peut_changer_prestataires`) : ajouter, retirer,
 * réordonner (`SectionPrestataires`, #182).
 */
@Composable
internal fun FeuilleModifierAffaire(affaire: DetailAffaire, service: ServiceAffaires, fermer: () -> Unit, enregistree: () -> Unit) {
    var saisie by remember(affaire.id) { mutableStateOf(SaisieAffaire.modifiant(affaire)) }
    val edition = saisie.edition(affaire)
    FeuilleDeSaisie(
        R.string.historique_titre_correction, R.string.feuille_enregistrer, ReperesNouvelle.MODIFIER_ENREGISTRER,
        prete = saisie.prete(affaire), fermer = fermer,
        envoyer = {
            service.enregistrer(affaire.id, edition)
            enregistree()
        },
    ) {
        ChampsAffaire(
            saisie, affaire.referentiels.permises(enModifiant = affaire.categorie), service, ReperesChamps.MODIFIER,
            descriptionFigee = if (affaire.descriptionSaisie == null) affaire.description else null,
            lieuModifiable = affaire.lieuModifiable, contenuFige = affaire.contenuFige,
            forme = FormeDeSaisie.de(LocalSaisieAffaire.current, affaire.referentiels.categories),
        ) { saisie = it }
        if (affaire.droits.changerPrestataires) {
            SectionPrestataires(saisie.prestataires, service) { saisie = saisie.copy(prestataires = it) }
        }
    }
}
