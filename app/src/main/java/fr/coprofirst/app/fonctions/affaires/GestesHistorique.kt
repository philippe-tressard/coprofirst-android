// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.commun.SignalementAuConseil
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.commun.masquages.AuteurAMasquer
import fr.coprofirst.app.commun.masquages.ConfirmationMasquage
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Un geste sans feuille, comme l'iOS (`geste` du bandeau) : envoyé, annoncé par l'avis, puis
 * l'affaire relue ; un échec dit sa phrase (celle du serveur s'il en donne une). `enCours`
 * éteint les boutons le temps de l'envoi.
 */
@Stable
internal class GesteAnnonce(private val portee: CoroutineScope, private val avis: AvisDuGeste, private val recharger: () -> Unit) {
    var enCours by mutableStateOf(false)
        private set
    internal var echec by mutableStateOf<ErreurService?>(null)

    fun lancer(phrase: String, envoi: suspend () -> Unit) {
        if (enCours) return
        portee.launch {
            enCours = true
            try {
                envoi()
                avis.dire(phrase)
                recharger()
            } catch (e: ErreurService) {
                echec = e
            } finally {
                enCours = false
            }
        }
    }
}

@Composable
internal fun rememberGesteAnnonce(avis: AvisDuGeste, recharger: () -> Unit): GesteAnnonce {
    val portee = rememberCoroutineScope()
    val actuel by rememberUpdatedState(recharger)
    val annonce = remember(avis) { GesteAnnonce(portee, avis) { actuel() } }
    val phrase = annonce.echec?.phrase()
    LaunchedEffect(phrase) {
        if (phrase != null) {
            avis.dire(phrase, TonAvis.Erreur)
            annonce.echec = null
        }
    }
    return annonce
}

/**
 * L'entrée de la suite qu'un geste vise, tenue par la fiche, comme l'iOS (`ChoixDeSuite`) :
 * seule la fiche présente la feuille et les questions — posées sur une entrée, elles se
 * multiplieraient avec le fil.
 */
@Stable
internal class ChoixDeSuite(
    /** Le compte (`DroitsOnglet.compte`) : on ne signale ni ne masque ce qu'on a écrit. */
    private val moi: Int?,
    /** L'installation sert-elle la modération (`Installation.sertLePlancher`, #311) ? */
    private val moderation: Boolean,
) {
    var aCorriger by mutableStateOf<SuiteAffaire?>(null)
    var aSupprimer by mutableStateOf<SuiteAffaire?>(null)
    var aSignaler by mutableStateOf<SuiteAffaire?>(null)
    var aMasquer by mutableStateOf<AuteurAMasquer?>(null)

    /** Les gestes qu'offre CETTE entrée à ce compte. */
    fun offerts(suite: SuiteAffaire): List<GesteSuite> = GesteSuite.offerts(suite, moi, moderation)

    /** Une correction que l'affaire n'a pas suivie : remise par la feuille, dite une fois la feuille fermée. */
    var nonSuivie by mutableStateOf<IssueCorrection?>(null)
    var aDire by mutableStateOf<IssueCorrection?>(null)

    fun choisir(geste: GesteSuite, suite: SuiteAffaire) {
        when (geste) {
            GesteSuite.Modifier -> aCorriger = suite
            GesteSuite.Signaler -> aSignaler = suite
            GesteSuite.Masquer -> aMasquer = AuteurAMasquer.de(suite.auteurId, suite.auteur, moi)
            GesteSuite.Supprimer -> aSupprimer = suite
        }
    }

    fun feuilleFermee() {
        aDire = nonSuivie
        nonSuivie = null
    }
}

/**
 * Les gestes des entrées de la suite (`RubriqueHistorique.svelte`), comme l'iOS
 * (`GestesHistorique`) : « Modifier la suite » ouvre la feuille de la Suite partie de
 * l'entrée, « Supprimer cette entrée » se confirme ; chacun s'annonce, puis l'affaire se
 * relit. Une correction que l'affaire n'a pas suivie s'annonce par une ALERTE — la phrase
 * du serveur et, si le compte peut changer l'état, l'état qu'il propose d'appliquer.
 *
 * Signaler une suite au conseil, masquer ce que publie son auteur (#282), comme l'iOS : le
 * motif, puis l'avis ; la confirmation, puis l'affaire relue — le serveur ne sert plus ses
 * suites ni ses messages.
 */
@Composable
internal fun GestesHistorique(affaire: DetailAffaire, choix: ChoixDeSuite, service: ServiceAffaires, avis: AvisDuGeste, recharger: () -> Unit) {
    val ressources = LocalResources.current
    val annonce = rememberGesteAnnonce(avis, recharger)

    choix.aCorriger?.let { suite ->
        FeuilleSuite(
            affaire, service,
            fermer = {
                choix.aCorriger = null
                avis.feuilleFermee()
                choix.feuilleFermee()
            },
            corrigee = suite,
            nonSuivie = { choix.nonSuivie = it },
        ) {
            // L'alerte dit déjà « Entrée corrigée » quand l'affaire n'a pas suivi : pas d'avis en plus.
            if (choix.nonSuivie == null) avis.direApresFermeture(ressources.getString(R.string.historique_corrigee))
            recharger()
        }
    }

    choix.aSupprimer?.let { suite ->
        AlertDialog(
            onDismissRequest = { choix.aSupprimer = null },
            title = { Text(stringResource(R.string.historique_supprimer)) },
            text = { Text(stringResource(R.string.historique_question, FormatsDuSite.date(suite.date))) },
            confirmButton = {
                val geste = gesteEnLigne()
                TextButton(
                    onClick = {
                        choix.aSupprimer = null
                        annonce.lancer(ressources.getString(R.string.historique_supprimee)) { service.supprimerSuite(affaire.id, suite.id) }
                    },
                    enabled = geste.actif,
                    modifier = geste.modifier.testTag(ReperesAffaires.CONFIRMER_SUPPRESSION),
                ) { Text(stringResource(R.string.historique_confirmer), color = LocalCouleurs.current.danger) }
            },
            dismissButton = { TextButton(onClick = { choix.aSupprimer = null }) { Text(stringResource(R.string.annuler)) } },
        )
    }

    SignalementAuConseil(
        choix.aSignaler,
        envoyer = { suite, motif ->
            annonce.lancer(ressources.getString(R.string.signalement_transmis)) {
                service.signalerAuConseil(CibleSignalement.suite(suite.id), motif)
            }
        },
        fermer = { choix.aSignaler = null },
    )

    ConfirmationMasquage(
        choix.aMasquer,
        masquer = { auteur -> annonce.lancer(ressources.getString(R.string.masquage_masque, auteur.nom)) { service.masquer(auteur) } },
        fermer = { choix.aMasquer = null },
    )

    choix.aDire?.let { issue ->
        val proposition = issue.proposition(affaire)
        val libelle = issue.libelleProposition(affaire)
        AlertDialog(
            onDismissRequest = { choix.aDire = null },
            title = { Text(stringResource(R.string.historique_corrigee)) },
            text = { Text(issue.phrase) },
            confirmButton = {
                if (proposition != null && libelle != null) {
                    TextButton(
                        onClick = {
                            choix.aDire = null
                            annonce.lancer(ressources.getString(R.string.geste_statut_mis_a_jour)) { service.ajouterSuite(affaire.id, proposition) }
                        },
                        modifier = Modifier.testTag(ReperesAffaires.APPLIQUER),
                    ) { Text(stringResource(R.string.historique_appliquer, libelle)) }
                } else {
                    TextButton(onClick = { choix.aDire = null }) { Text(stringResource(R.string.historique_compris)) }
                }
            },
            dismissButton = if (proposition != null && libelle != null) {
                { TextButton(onClick = { choix.aDire = null }) { Text(stringResource(R.string.historique_laisser)) } }
            } else {
                null
            },
        )
    }
}
