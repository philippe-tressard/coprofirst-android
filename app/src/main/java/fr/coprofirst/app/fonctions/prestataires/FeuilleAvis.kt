// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.BoutonsFeuille
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * Ce que les gestes des avis demandent à l'écran qui les porte (#209), comme l'iOS
 * (`GestesAvis`) : le droit SERVI du conseil syndical (`est_moderateur` : noter, retirer),
 * l'avis qui dit la suite d'un geste, et la relecture de la liste (le nombre d'avis de la
 * carte est servi avec elle).
 */
class GestesAvis(val conseil: Boolean, val avis: AvisDuGeste, val apres: () -> Unit)

/** Noter (depuis la fiche, ou la carte d'un contrat), ou corriger son avis. */
sealed interface ModeAvis {
    data class Noter(val contrat: Int? = null) : ModeAvis
    data class Corriger(val avis: AvisPrestataire) : ModeAvis
}

/**
 * La saisie d'un avis en feuille (`GesteNoter.svelte`), comme la `FeuilleAvis` de l'iOS :
 * l'affaire jugée, la confirmation de l'ajout s'il le faut, l'état, les motifs à cocher et
 * la phrase qu'ils composent, le texte libre (`ChampsAvis`). « Enregistrer » attend ce que le
 * site exige, et la phrase du site dit ce qui manque, sous le dernier champ. L'état choisi
 * prend la teinte pleine de son état, comme l'iOS (#259).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeuilleAvis(
    mode: ModeAvis,
    prestataire: Prestataire,
    service: ServicePrestataires,
    gestes: GestesAvis,
    fermer: () -> Unit,
) {
    val initiale = remember(mode) { (mode as? ModeAvis.Corriger)?.let { SaisieAvis.corrigeant(it.avis) } ?: SaisieAvis() }
    var saisie by remember(mode) { mutableStateOf(initiale) }
    var affaires by remember { mutableStateOf<List<AffaireANoter>>(emptyList()) }
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    var depart by remember { mutableStateOf(false) }
    // Une saisie commencée ne se perd pas d'un glissé (l'iOS : `interactiveDismissDisabled`) ;
    // « Annuler » et l'envoi réussi, eux, ferment.
    val etat = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || depart || (!envoi && saisie == initiale) },
    )
    val portee = rememberCoroutineScope()
    val couleurs = LocalCouleurs.current
    val partir: () -> Unit = {
        depart = true
        portee.launch { etat.hide() }.invokeOnCompletion { fermer() }
    }
    LaunchedEffect(prestataire.id) {
        affaires = try {
            service.affairesANoter(prestataire.id)
        } catch (_: ErreurService) {
            emptyList()
        }
    }
    val choisie = affaires.firstOrNull { it.id == saisie.affaire }
    val ajoutRequis = choisie?.intervient == false
    val refus = saisie.refus(ajoutRequis)
    val corrige = (mode as? ModeAvis.Corriger)?.avis
    val dit = stringResource(if (corrige == null) R.string.notation_enregistree else R.string.notation_modifie)

    ModalBottomSheet(onDismissRequest = { if (!envoi) fermer() }, sheetState = etat, containerColor = couleurs.bg) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge),
            verticalArrangement = Arrangement.spacedBy(Mise.ecart),
        ) {
            Text(
                stringResource(if (corrige == null) R.string.notation_titre_noter else R.string.notation_modifier),
                style = Typo.titre(),
                color = couleurs.text,
                modifier = Modifier.semantics { heading() },
            )
            AnimatedVisibility(visible = erreur != null) { erreur?.let { MessageErreur(it) } }
            ChoixAffaireAvis(affaires, saisie.affaire, corrige?.ticketTitre, prestataire.nom, service) { id ->
                saisie = saisie.copy(affaire = id, confirmeAjout = false)
            }
            if (choisie != null && ajoutRequis) {
                CaseACocher(
                    stringResource(R.string.notation_ajouter, prestataire.nom, choisie.titre),
                    saisie.confirmeAjout,
                    ReperesSaisieAvis.CONFIRMER_AJOUT,
                ) { saisie = saisie.copy(confirmeAjout = it) }
            }
            ChampsAvis(saisie, refus, prestataire.nom) { saisie = it }
            BoutonsFeuille(
                envoi = envoi,
                repere = ReperesSaisieAvis.ENREGISTRER,
                annuler = partir,
                modifier = Modifier.padding(bottom = 16.dp),
                actif = refus == null,
            ) {
                envoi = true
                erreur = null
                portee.launch {
                    try {
                        when (mode) {
                            is ModeAvis.Noter -> service.noter(saisie.creation(prestataire.id, mode.contrat, ajoutRequis))
                            is ModeAvis.Corriger -> service.corriger(mode.avis.id, saisie.correction(mode.avis, ajoutRequis))
                        }
                        gestes.avis.direApresFermeture(dit)
                        gestes.apres()
                        envoi = false
                        partir()
                    } catch (e: ErreurService) {
                        erreur = e
                        envoi = false
                    }
                }
            }
        }
    }
}
