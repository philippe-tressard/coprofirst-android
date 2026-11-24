// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.BoutonsFeuille
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.rememberEnvoi
import fr.coprofirst.app.commun.texte.TexteDuSite
import kotlinx.coroutines.launch

/** Les repères des feuilles, ceux de l'iOS. */
object ReperesFeuilles {
    const val REPONDRE_TEXTE = "repondre.texte"
    const val REPONDRE_ENVOYER = "repondre.envoyer"
    const val SUITE_TEXTE = "suite.texte"
    const val SUITE_ENREGISTRER = "suite.enregistrer"
    fun suiteEtat(code: String) = "suite.etat.$code"
}

/**
 * Une feuille de saisie d'une affaire : son titre, l'erreur d'un envoi (la phrase du
 * serveur), ses champs, puis `BoutonsFeuille`. `envoyer` lève une `ErreurService` pour dire
 * l'échec ; sur un succès, la feuille se ferme. `retenue` : la fermer se confirme ailleurs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeuilleDeSaisie(
    @StringRes titre: Int,
    @StringRes verbe: Int,
    repere: String,
    prete: Boolean,
    fermer: () -> Unit,
    envoyer: suspend () -> Unit,
    retenue: Boolean = false,
    demanderAbandon: () -> Unit = fermer,
    champs: @Composable ColumnScope.() -> Unit,
) {
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !retenue })
    val portee = rememberCoroutineScope()
    val envoi = rememberEnvoi()
    val annuler: () -> Unit = { if (retenue) demanderAbandon() else portee.launch { etat.hide(); fermer() } }
    ModalBottomSheet(onDismissRequest = annuler, sheetState = etat, containerColor = LocalCouleurs.current.bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = Mise.marge).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(titre), Modifier.semantics { heading() }, style = Typo.titre(), color = LocalCouleurs.current.text)
            envoi.erreur?.let { MessageErreur(it) }
            champs()
            BoutonsFeuille(envoi.enCours, repere, annuler, libelle = verbe, actif = prete) {
                envoi.lancer {
                    envoyer()
                    etat.hide()
                    fermer()
                }
            }
        }
    }
}

/**
 * « Répondre », comme l'iOS (`RepondreFeuille`) : un message du fil de l'affaire.
 *
 * Et sa CORRECTION, « Modifier mon message » (`corrige`, #281) : la même feuille, partie du
 * texte servi ; elle n'enregistre qu'un changement. Elle ne s'ouvre que sur un texte que
 * l'app réécrit sans rien perdre (`MessageAffaire.contenuSaisi`).
 */
@Composable
internal fun FeuilleRepondre(
    affaire: DetailAffaire,
    service: ServiceAffaires,
    fermer: () -> Unit,
    corrige: MessageAffaire? = null,
    envoyee: () -> Unit,
) {
    var texte by rememberSaveable(corrige?.id) { mutableStateOf(corrige?.contenuSaisi.orEmpty()) }
    val focus = remember { FocusRequester() }
    val change = corrige == null || TexteDuSite.html(texte.trim()) != corrige.contenu
    FeuilleDeSaisie(
        if (corrige == null) R.string.geste_repondre else R.string.historique_titre_correction,
        if (corrige == null) R.string.nouvelle_envoyer else R.string.feuille_enregistrer,
        ReperesFeuilles.REPONDRE_ENVOYER, texte.isNotBlank() && change, fermer,
        envoyer = {
            if (corrige == null) service.repondre(affaire.id, texte) else service.corrigerMessage(affaire.id, corrige.id, texte)
            envoyee()
        },
    ) {
        Text(affaire.titre, style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        OutlinedTextField(
            texte, { texte = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag(ReperesFeuilles.REPONDRE_TEXTE),
            label = { Text(stringResource(R.string.repondre_votre_reponse)) }, minLines = 5,
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/**
 * « Ajouter une suite », comme l'iOS (`SuiteFeuille`) : le Suivi d'abord — l'état courant
 * choisi ; le garder fait de l'entrée un commentaire —, puis le commentaire, requis s'il est seul.
 *
 * Et sa CORRECTION, « Modifier la suite » (`corrigee`) : la même feuille, partie de l'entrée ;
 * seul ce qui a changé part (`CorrectionSuite`), et un texte mis en forme sur le site se
 * montre sans se modifier. `nonSuivie` reçoit ce que l'affaire n'a pas suivi, AVANT `envoyee`.
 */
@Composable
internal fun FeuilleSuite(
    affaire: DetailAffaire,
    service: ServiceAffaires,
    fermer: () -> Unit,
    corrigee: SuiteAffaire? = null,
    nonSuivie: (IssueCorrection) -> Unit = {},
    envoyee: (changeEtat: Boolean) -> Unit,
) {
    var saisie by remember(affaire.id, corrigee?.id) {
        mutableStateOf(corrigee?.let { SaisieSuite.corrigeant(it, affaire) } ?: SaisieSuite(affaire.statut))
    }
    val proposes = remember(affaire) { SaisieSuite.etats(affaire) }
    // En correction, le Suivi ne se montre que si la liste sait dire les deux états de l'entrée.
    val etats = if (corrigee == null || saisie.suiviCorrigeable(proposes.map { it.code })) proposes else emptyList()
    val prete = corrigee?.let { saisie.prete(it, affaire) } ?: saisie.prete
    FeuilleDeSaisie(
        if (corrigee == null) R.string.suite_titre else R.string.historique_titre_correction,
        R.string.feuille_enregistrer, ReperesFeuilles.SUITE_ENREGISTRER, prete, fermer,
        envoyer = {
            if (corrigee != null) {
                service.corrigerSuite(affaire.id, corrigee.id, saisie.correction(corrigee, affaire))?.let(nonSuivie)
            } else {
                service.ajouterSuite(affaire.id, saisie)
            }
            envoyee(saisie.changeEtat)
        },
    ) {
        if (etats.isNotEmpty()) {
            Libelle(stringResource(R.string.suite_suivi), Modifier.semantics { heading() })
            Column(Modifier.selectableGroup()) {
                etats.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().selectable(saisie.statut == e.code, role = Role.RadioButton) { saisie = saisie.copy(statut = e.code) }
                            .padding(vertical = 4.dp).testTag(ReperesFeuilles.suiteEtat(e.code)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(saisie.statut == e.code, onClick = null)
                        Text(e.libelle, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge, color = LocalCouleurs.current.text)
                    }
                }
            }
            val pied = when {
                corrigee != null && saisie.changeEtat -> R.string.historique_etat_donne
                corrigee != null -> R.string.historique_etat_d_avant
                saisie.changeEtat -> R.string.suite_changera
                else -> R.string.suite_garder
            }
            Text(stringResource(pied), style = MaterialTheme.typography.bodySmall, color = LocalCouleurs.current.textMuted)
        }
        Libelle(stringResource(R.string.suite_commentaire), Modifier.semantics { heading() })
        val fige = corrigee?.contenu?.takeIf { corrigee.contenuSaisi == null }
        if (fige != null) {
            // Un texte mis en forme sur le site : le réenregistrer le perdrait.
            Text(TexteDuSite.riche(fige), style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
            Text(TexteDuSite.MISE_EN_FORME_AU_SITE, style = MaterialTheme.typography.bodySmall, color = LocalCouleurs.current.textMuted)
        } else {
            OutlinedTextField(
                saisie.contenu, { saisie = saisie.copy(contenu = it) }, Modifier.fillMaxWidth().testTag(ReperesFeuilles.SUITE_TEXTE),
                placeholder = { Text(stringResource(if (saisie.changeEtat) R.string.suite_mot_facultatif else R.string.suite_commentaire_indication)) },
                minLines = 4,
            )
        }
    }
}
