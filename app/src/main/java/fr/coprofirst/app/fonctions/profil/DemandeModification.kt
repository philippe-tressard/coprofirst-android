// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.BatimentPublic
import fr.coprofirst.api.modeles.DemandeModifRead
import fr.coprofirst.api.modeles.StatutDemandeProfil
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.Note
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.commun.Section
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * « Demander une modification (profil / bâtiment) », comme l'iOS
 * (`DemandeModificationView`) : la demande en attente, sinon le formulaire ; l'historique
 * en dessous. Le conseil syndical valide.
 */
@Composable
fun FeuilleDemandeModification(service: ServiceProfil, fermer: () -> Unit) {
    var demandes by remember { mutableStateOf<List<DemandeModifRead>>(emptyList()) }
    var batiments by remember { mutableStateOf<List<BatimentPublic>>(emptyList()) }
    var charge by remember { mutableStateOf(false) }
    var lecture by remember { mutableIntStateOf(0) }
    LaunchedEffect(lecture) {
        // Comme l'iOS : une lecture qui échoue laisse la liste vide, le formulaire reste ouvert.
        coroutineScope {
            val lues = async { ouVide { service.demandesModification() } }
            val lus = async { ouVide { service.batiments() } }
            demandes = lues.await()
            batiments = lus.await()
        }
        charge = true
    }
    FeuilleProfil(titre = stringResource(R.string.profil_titre_demande_modification), fermer = fermer) {
        val attente = demandes.firstOrNull { it.statutDemande == StatutDemandeProfil.EN_ATTENTE }
        when {
            attente != null -> Section(stringResource(R.string.profil_demande_en_attente)) { Text(resume(attente)) }
            charge -> FormulaireDemande(service, batiments) { lecture++ }
        }
        if (demandes.isNotEmpty()) Historique(demandes)
    }
}

@Composable
private fun FormulaireDemande(service: ServiceProfil, batiments: List<BatimentPublic>, envoyee: () -> Unit) {
    var statut by remember { mutableStateOf<String?>(null) }
    var batiment by remember { mutableStateOf<Int?>(null) }
    var motif by remember { mutableStateOf("") }
    var envoi by remember { mutableStateOf(false) }
    var reussi by remember { mutableStateOf(false) }
    var refus by remember { mutableStateOf<ErreurService?>(null) }
    val portee = rememberCoroutineScope()
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Choix(
            libelle = stringResource(R.string.profil_nouveau_profil),
            choix = listOf(null to stringResource(R.string.profil_inchange)) + Roles.demandables.map { (v, l) -> v to l.libelle },
            choisi = statut,
            repere = "profil.demande.statut",
        ) { statut = it }
        Choix(
            libelle = stringResource(R.string.profil_batiment_souhaite),
            choix = listOf(null to stringResource(R.string.profil_inchange_tirets)) +
                batiments.map { it.id to stringResource(R.string.profil_batiment_numero, it.numero) },
            choisi = batiment,
            repere = "profil.demande.batiment",
        ) { batiment = it }
        ChampProfil(motif, stringResource(R.string.profil_motif), "profil.demande.motif", exemple = stringResource(R.string.profil_motif_exemple), lignes = 3) {
            motif = it
        }
        Note(stringResource(R.string.profil_validation_cs), Modifier.padding(horizontal = Mise.marge + 4.dp))
        val geste = gesteEnLigne(statut != null || batiment != null)
        BoutonPlein(
            R.string.profil_envoyer_demande,
            geste.modifier.padding(horizontal = Mise.marge).testTag("profil.demande.envoyer"),
            actif = geste.actif,
            enCours = envoi,
        ) {
            portee.launch {
                envoi = true
                try {
                    service.demanderModification(statut, batiment, motif)
                    refus = null
                    reussi = true
                    envoyee()
                } catch (e: ErreurService) {
                    refus = e
                    reussi = false
                } finally {
                    envoi = false
                }
            }
        }
        val marge = Modifier.padding(horizontal = Mise.marge + 4.dp)
        if (reussi) Note(stringResource(R.string.profil_demande_envoyee_cs), marge, couleurs.successTexte)
        refus?.let { Note(it.phrase(), marge, couleurs.danger) }
    }
}

@Composable
private fun Historique(demandes: List<DemandeModifRead>) {
    val couleurs = LocalCouleurs.current
    Section(stringResource(R.string.profil_historique, demandes.size)) {
        demandes.forEachIndexed { i, demande ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = couleurs.border)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(FormatsDuSite.dateCourte(demande.creeLe.toInstant()), style = MaterialTheme.typography.labelMedium, color = couleurs.textMuted)
                    Text(
                        stringResource(demande.statutDemande.libelle()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = couleurs.textMuted,
                    )
                }
                Text(resume(demande), color = couleurs.text)
                demande.motifRefus?.let { Note(it, couleur = couleurs.danger) }
            }
        }
    }
}

/** Une lecture, ou rien si elle échoue. */
private suspend fun <T> ouVide(lire: suspend () -> List<T>): List<T> = try {
    lire()
} catch (_: ErreurService) {
    emptyList()
}

private fun StatutDemandeProfil.libelle(): Int = when (this) {
    StatutDemandeProfil.EN_ATTENTE -> R.string.profil_etat_en_attente
    StatutDemandeProfil.APPROUVEE -> R.string.profil_etat_approuvee
    StatutDemandeProfil.REJETEE -> R.string.profil_etat_rejetee
}

/** « Changement de type vers « Locataire » + déménagement vers Bât. B », comme le site. */
@Composable
private fun resume(demande: DemandeModifRead): String {
    val parties = listOfNotNull(
        demande.statutSouhaite?.let { stringResource(R.string.profil_resume_statut, Roles.statut(it).libelle) },
        demande.batimentNomSouhaite?.let { stringResource(R.string.profil_resume_batiment, it) },
    )
    return parties.joinToString(" + ").replaceFirstChar { it.uppercase() }
}

/** Un choix dans une liste déroulante (le `Picker` de l'iOS). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choix(libelle: String, choix: List<Pair<T, String>>, choisi: T, repere: String, choisir: (T) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = ouvert,
        onExpandedChange = { ouvert = it },
        modifier = Modifier.padding(horizontal = Mise.marge),
    ) {
        OutlinedTextField(
            value = choix.firstOrNull { it.first == choisi }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(libelle) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ouvert) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).testTag(repere),
        )
        ExposedDropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            choix.forEach { (valeur, texte) ->
                DropdownMenuItem(text = { Text(texte) }, onClick = {
                    choisir(valeur)
                    ouvert = false
                })
            }
        }
    }
}
