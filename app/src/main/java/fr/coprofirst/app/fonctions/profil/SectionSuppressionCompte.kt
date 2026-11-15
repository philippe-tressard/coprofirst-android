// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « Supprimer mon compte », sous « Vos droits (RGPD) » du Profil — le même geste que le
 * site (`SuppressionCompte.svelte`, coprofirst#2699) et l'iOS (`SectionSuppressionCompte`),
 * ce qu'exige aussi Google Play (suppression demandée depuis l'app) : la personne
 * DEMANDE, l'administration de la résidence traite. Le délai et ce qui sera effacé sont
 * SERVIS ; l'app ne les écrit nulle part.
 */
@Composable
fun SectionSuppressionCompte(service: ServiceProfil) {
    val portee = rememberCoroutineScope()
    var etat by remember { mutableStateOf<EtatSuppressionCompte?>(null) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    var lecture by remember { mutableIntStateOf(0) }
    var formulaire by remember { mutableStateOf(false) }
    var envoi by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf<Phrase?>(null) }
    LaunchedEffect(lecture) {
        try {
            etat = service.etatSuppression()
            erreur = null
        } catch (e: ErreurService) {
            erreur = e
        }
    }
    SectionProfil(stringResource(R.string.profil_supprimer_compte)) {
        val lu = etat
        val demande = lu?.demande
        when {
            erreur != null -> Note(stringResource(R.string.profil_suppression_illisible, erreur!!.phrase()))
            demande != null -> {
                Note(demandeEnvoyee(demande))
                LigneAction(stringResource(R.string.profil_retirer_demande), "profil.suppression.retirer", actif = !envoi) {
                    portee.launch {
                        envoi = true
                        try {
                            service.retirerDemandeSuppression()
                            phrase = Phrase.Texte(R.string.profil_demande_retiree)
                            lecture++
                        } catch (e: ErreurService) {
                            phrase = Phrase.Erreur(e)
                        } finally {
                            envoi = false
                        }
                    }
                }
            }
            lu != null -> {
                Note(invitation(lu.delaiJours))
                LigneAction(stringResource(R.string.profil_demander_suppression), "profil.suppression.demander", detruit = true) {
                    formulaire = true
                }
            }
            else -> CircularProgressIndicator(Modifier.padding(8.dp).align(Alignment.CenterHorizontally))
        }
        phrase?.let { Note(it.texte()) }
    }
    val lu = etat
    if (formulaire && lu != null) {
        FeuilleSuppression(lu, fermer = { formulaire = false }) { motDePasse, motif ->
            service.demanderSuppression(motDePasse, motif)
            phrase = Phrase.Texte(R.string.profil_demande_suppression_envoyee)
            lecture++
        }
    }
}

@Composable
private fun demandeEnvoyee(demande: DemandeSuppressionCompte): String =
    stringResource(R.string.profil_suppression_envoyee_le, FormatsDuSite.date(demande.creeLe)) +
        (demande.delaiJours?.let { " " + pluralStringResource(R.plurals.profil_traitee_sous, it, it) } ?: "")

@Composable
private fun invitation(delai: Int?): String =
    stringResource(R.string.profil_suppression_invitation) +
        (delai?.let { pluralStringResource(R.plurals.profil_invitation_delai, it, it) } ?: ".")

/**
 * La confirmation, comme l'iOS (`DemandeSuppressionFeuille`) : ce qui sera effacé (les
 * phrases servies), le mot de passe, un motif facultatif. Un refus du serveur (« Mot de
 * passe incorrect. ») s'affiche dans la feuille, qui reste ouverte.
 */
@Composable
private fun FeuilleSuppression(etat: EtatSuppressionCompte, fermer: () -> Unit, demander: suspend (String, String) -> Unit) {
    var motDePasse by remember { mutableStateOf("") }
    var motif by remember { mutableStateOf("") }
    var envoi by remember { mutableStateOf(false) }
    var refus by remember { mutableStateOf<ErreurService?>(null) }
    val portee = rememberCoroutineScope()
    FeuilleProfil(
        titre = stringResource(R.string.profil_supprimer_compte),
        fermer = fermer,
        geste = GesteDeFeuille(stringResource(R.string.profil_confirmer_demande), "suppression.confirmer", motDePasse.isNotEmpty(), envoi) {
            portee.launch {
                envoi = true
                try {
                    demander(motDePasse, motif)
                    fermer()
                } catch (e: ErreurService) {
                    refus = e
                } finally {
                    envoi = false
                }
            }
        },
    ) {
        SectionProfil(titre = null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                etat.consequences.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        ChampProfil(motDePasse, stringResource(R.string.profil_mot_de_passe_confirmer), "suppression.motDePasse", secret = true) { motDePasse = it }
        ChampProfil(motif, stringResource(R.string.profil_motif_facultatif), "suppression.motif", lignes = 2) { motif = it }
        refus?.let { MessageErreur(it, Modifier.fillMaxWidth().padding(horizontal = Mise.marge)) }
    }
}
