// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Note
import fr.coprofirst.app.commun.Section
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.commun.rememberEnvoi
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import kotlinx.coroutines.launch

/**
 * « Vos droits (RGPD) », comme l'iOS (`SectionDroits`) : la politique de confidentialité,
 * le refus des statistiques de navigation, leur export et leur effacement. Le refus
 * s'applique sur-le-champ ; un échec remet la case où elle était.
 *
 * ⚠️ Divergence déclarée : l'iOS partage l'export en fichier ; ici il se partage en texte
 * (le JSON servi, mis en forme) — sans fichier écrit ni fournisseur de fichiers à déclarer.
 */
@Composable
fun SectionDroits(
    compte: UserRead,
    installation: Installation,
    service: ServiceProfil,
    /** Après un refus (ou un accord) enregistré : la mesure d'audience le voit aussitôt (#144). */
    relireDroits: suspend () -> Unit = {},
    modifie: (UserRead) -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val contexte = LocalContext.current
    val liens = LocalUriHandler.current
    val portee = rememberCoroutineScope()
    var phrase by remember { mutableStateOf<Phrase?>(null) }
    var export by remember { mutableStateOf<String?>(null) }
    val exportation = rememberEnvoi()
    var effacer by remember { mutableStateOf(false) }
    val refus = compte.optOutTelemetrie == true
    val libelleRefus = stringResource(R.string.profil_refuser_statistiques)

    fun avec(echec: (ErreurService) -> Unit = { phrase = Phrase.Erreur(it) }, geste: suspend () -> Unit) = portee.launch {
        try {
            geste()
        } catch (e: ErreurService) {
            echec(e)
        }
    }

    Section(stringResource(R.string.profil_droits)) {
        Note(stringResource(R.string.profil_droits_texte))
        LigneAction(stringResource(R.string.profil_politique), "profil.droits.politique") {
            liens.openUri(installation.url.trimEnd('/') + "/" + CHEMIN_POLITIQUE)
        }
        val enLigne = gesteEnLigne()
        Row(
            enLigne.modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag("profil.droits.refus")
                .toggleable(value = refus, enabled = enLigne.actif, role = Role.Switch) { valeur ->
                    modifie(compte.copy(optOutTelemetrie = valeur))
                    avec(echec = { e ->
                        modifie(compte)
                        phrase = Phrase.Erreur(e)
                    }) {
                        service.refuserTelemetrie(valeur)
                        relireDroits()
                        phrase = Phrase.Texte(if (valeur) R.string.profil_statistiques_desactivees else R.string.profil_statistiques_reactivees)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(libelleRefus, color = couleurs.text, modifier = Modifier.weight(1f))
            Switch(checked = refus, onCheckedChange = null, enabled = enLigne.actif)
        }
        val pret = export
        if (pret != null) {
            LigneAction(stringResource(R.string.profil_partager_export), "profil.droits.partager") {
                val envoi = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, pret)
                contexte.startActivity(Intent.createChooser(envoi, null))
            }
        } else {
            val geste = gesteEnLigne(!exportation.enCours)
            LigneAction(stringResource(R.string.profil_exporter), "profil.droits.exporter", actif = geste.actif, modifier = geste.modifier) {
                // La phrase se partage avec les autres gestes de la section : l'échec y va aussi.
                exportation.lancer(echec = { phrase = Phrase.Erreur(it) }) {
                    export = service.exporterTelemetrie()
                    phrase = Phrase.Texte(R.string.profil_export_pret)
                }
            }
        }
        val effacement = gesteEnLigne()
        LigneAction(
            stringResource(R.string.profil_effacer), "profil.droits.effacer", detruit = true, actif = effacement.actif, modifier = effacement.modifier,
        ) { effacer = true }
        phrase?.let { Note(it.texte()) }
    }
    if (effacer) {
        AlertDialog(
            onDismissRequest = { effacer = false },
            title = { Text(stringResource(R.string.profil_effacer)) },
            confirmButton = {
                TextButton(onClick = {
                    effacer = false
                    avec {
                        service.effacerTelemetrie()
                        phrase = Phrase.Texte(R.string.profil_donnees_effacees)
                    }
                }) { Text(stringResource(R.string.profil_oui_effacer), color = couleurs.danger) }
            },
            dismissButton = { TextButton(onClick = { effacer = false }) { Text(stringResource(R.string.annuler)) } },
        )
    }
}

/** La page du site où se lit la politique de confidentialité (`/politique-de-confidentialite`). */
private const val CHEMIN_POLITIQUE = "politique-de-confidentialite"
