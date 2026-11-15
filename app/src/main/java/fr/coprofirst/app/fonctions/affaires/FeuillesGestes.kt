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
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.session.ErreurService
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
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    val annuler: () -> Unit = { if (retenue) demanderAbandon() else portee.launch { etat.hide(); fermer() } }
    ModalBottomSheet(onDismissRequest = annuler, sheetState = etat, containerColor = LocalCouleurs.current.bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = Mise.marge).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(titre), Modifier.semantics { heading() }, style = Typo.titre(), color = LocalCouleurs.current.text)
            erreur?.let { MessageErreur(it) }
            champs()
            BoutonsFeuille(envoi, repere, annuler, libelle = verbe, actif = prete) {
                if (!envoi) {
                    portee.launch {
                        envoi = true
                        try {
                            envoyer()
                            etat.hide()
                            fermer()
                        } catch (e: ErreurService) {
                            erreur = e
                        } finally {
                            envoi = false
                        }
                    }
                }
            }
        }
    }
}

/** Le titre d'une section de feuille (`Section` de l'iOS). */
@Composable
internal fun TitreSection(@StringRes texte: Int) {
    Text(stringResource(texte), Modifier.semantics { heading() }, style = Typo.libelle(), color = LocalCouleurs.current.textMuted)
}

/** « Répondre », comme l'iOS (`RepondreFeuille`) : un message du fil de l'affaire. */
@Composable
internal fun FeuilleRepondre(affaire: DetailAffaire, service: ServiceAffaires, fermer: () -> Unit, envoyee: () -> Unit) {
    var texte by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    FeuilleDeSaisie(
        R.string.geste_repondre, R.string.nouvelle_envoyer, ReperesFeuilles.REPONDRE_ENVOYER, texte.isNotBlank(), fermer,
        envoyer = {
            service.repondre(affaire.id, texte)
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
 */
@Composable
internal fun FeuilleSuite(affaire: DetailAffaire, service: ServiceAffaires, fermer: () -> Unit, envoyee: (changeEtat: Boolean) -> Unit) {
    var saisie by remember(affaire.id) { mutableStateOf(SaisieSuite(affaire.statut)) }
    val etats = remember(affaire) { SaisieSuite.etats(affaire) }
    FeuilleDeSaisie(
        R.string.suite_titre, R.string.feuille_enregistrer, ReperesFeuilles.SUITE_ENREGISTRER, saisie.prete, fermer,
        envoyer = {
            service.ajouterSuite(affaire.id, saisie)
            envoyee(saisie.changeEtat)
        },
    ) {
        if (etats.isNotEmpty()) {
            TitreSection(R.string.suite_suivi)
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
            Text(
                stringResource(if (saisie.changeEtat) R.string.suite_changera else R.string.suite_garder),
                style = MaterialTheme.typography.bodySmall, color = LocalCouleurs.current.textMuted,
            )
        }
        TitreSection(R.string.suite_commentaire)
        OutlinedTextField(
            saisie.contenu, { saisie = saisie.copy(contenu = it) }, Modifier.fillMaxWidth().testTag(ReperesFeuilles.SUITE_TEXTE),
            placeholder = { Text(stringResource(if (saisie.changeEtat) R.string.suite_mot_facultatif else R.string.suite_commentaire_indication)) },
            minLines = 4,
        )
    }
}
