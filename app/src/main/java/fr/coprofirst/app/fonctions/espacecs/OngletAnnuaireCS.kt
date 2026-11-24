// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.texte.Separateur
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.session.ErreurService

/**
 * « Annuaire CS & Syndic » (onglet `annuaire`, `OngletAnnuaireCS.svelte`), comme l'iOS
 * (`OngletAnnuaireCS`) : le conseil syndical puis le syndic, chacun avec son en-tête et ses
 * fiches — à corriger, ajouter, retirer, et pour le syndic à ordonner. Les cartes sont celles
 * de l'Annuaire des résidents (`CarteContact`, commun) : un même objet, un même rendu.
 *
 * Chaque geste réécrit la fiche ENTIÈRE (PUT), qui rend la composition ENREGISTRÉE : l'écran
 * montre ce que le serveur a retenu, pas ce qu'on lui a envoyé.
 */
@Composable
fun OngletAnnuaireCS(contexte: ContexteOnglet) {
    val service = remember(contexte.session) { ServiceAnnuaireCSApi(contexte.session) }
    var echec by remember { mutableStateOf<ErreurService?>(null) }
    val gestes = GestesAnnuaire(
        service = service,
        session = contexte.session,
        echouer = { echec = it },
        dire = { texte, apresFeuille ->
            echec = null
            if (apresFeuille) contexte.avis.direApresFermeture(texte) else contexte.avis.dire(texte)
        },
        feuilleFermee = contexte.avis::feuilleFermee,
    )
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        echec?.let { MessageErreur(it) }
        SectionConseilCS(gestes, contexte.actualisation)
        SectionSyndicCS(gestes, contexte.actualisation)
    }
}


/** Le rendu factice d'une section, le temps de la lire. */
@Composable
internal fun SqueletteSection() {
    Carte { Text("AG 2026${Separateur.point}12/06/2026${Separateur.point}Groupe WhatsApp", style = MaterialTheme.typography.bodyMedium) }
}

/** L'en-tête d'une fiche (l'AG, le cabinet) et son crayon. */
@Composable
internal fun EnteteFiche(repere: String, modifier: () -> Unit, contenu: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { contenu() }
        IconButton(onClick = modifier, modifier = Modifier.testTag(repere)) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.espacecs_modifier), tint = LocalCouleurs.current.primary)
        }
    }
}

/** Un lien hors de l'app (WhatsApp, espace client) : ouvert par le système, seulement s'il est sûr. */
@Composable
internal fun LienSortant(libelle: String, adresse: String?) {
    val lien = adresse?.let { TexteDuSite.lienSur(it) } ?: return
    val uri = LocalUriHandler.current
    val c = LocalCouleurs.current
    Row(
        Modifier.heightIn(min = Mise.cible).cliquable(stringResource(R.string.annuaire_lien_externe)) { uri.openUri(lien) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
        Text(libelle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = c.primary)
    }
}

/** « Inscrit lié » : la fiche est rattachée à un compte de la résidence. */
@Composable
internal fun InscritLie() {
    val c = LocalCouleurs.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Link, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.espacecs_annuaire_inscrit_lie), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

/** « Nouveau membre… » : le bouton d'ajout d'une section. */
@Composable
internal fun BoutonAjout(libelle: Int, repere: String, ajouter: () -> Unit) {
    OutlinedButton(onClick = ajouter, modifier = Modifier.heightIn(min = Mise.cible).testTag(repere)) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(libelle), Modifier.padding(start = 6.dp))
    }
}

/** Retirer une fiche : la question du site, puis « Supprimer » (en rouge) ou « Annuler ». */
@Composable
internal fun ConfirmationRetrait(fermer: () -> Unit, retirer: () -> Unit) {
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(stringResource(R.string.espacecs_annuaire_retirer_titre)) },
        text = { Text(stringResource(R.string.espacecs_annuaire_retirer_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    fermer()
                    retirer()
                },
                modifier = Modifier.testTag(ReperesEspaceCS.ANNUAIRE_RETIRER_CONFIRMER),
            ) { Text(stringResource(R.string.espacecs_supprimer), color = LocalCouleurs.current.danger) }
        },
        dismissButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.espacecs_annuler)) } },
    )
}
