// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.Section
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « Saisie d'une affaire », Essentielle ou Complète (#47), entre la sécurité et le thème,
 * comme l'iOS (`SectionSaisieAffaire`) et le Profil du site (`ChoixSaisieAffaire.svelte`).
 *
 * Le choix vit sur le COMPTE, pour tous les appareils — au contraire du thème. Il s'applique
 * sur-le-champ : la pastille change d'abord, puis PATCH /auth/me part ; un refus la remet où
 * elle était, avec la phrase du serveur. Ce qui est coché est ce que le serveur sert
 * (`saisie_affaire_effective`), jamais un défaut déduit ici ; les options sont générées
 * (`TablesProfil.optionsSaisie`).
 */
@Composable
fun SectionSaisieAffaire(compte: UserRead, service: ServiceProfil, relireDroits: suspend () -> Unit = {}, modifie: (UserRead) -> Unit) {
    val servi = compte.saisieAffaireEffective ?: return
    val portee = rememberCoroutineScope()
    var refus by remember { mutableStateOf<ErreurService?>(null) }
    val options = TablesProfil.optionsSaisie
    val detail = options.firstOrNull { it.code == servi }?.detail
    Section(stringResource(R.string.profil_saisie), note = detail?.let { stringResource(R.string.profil_saisie_note, it) }) {
        Column {
            val geste = gesteEnLigne()
            SingleChoiceSegmentedButtonRow(geste.modifier.fillMaxWidth().testTag(ReperesSaisie.CHOIX)) {
                options.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = option.code == servi,
                        enabled = geste.actif,
                        onClick = {
                            if (option.code == servi) return@SegmentedButton
                            refus = null
                            modifie(compte.copy(saisieAffaire = option.code, saisieAffaireEffective = option.code))
                            portee.launch {
                                try {
                                    service.choisirSaisie(option.code)
                                    // « Nouvelle affaire » et « Modifier » suivent la saisie du compte (#178).
                                    relireDroits()
                                } catch (e: ErreurService) {
                                    modifie(compte)
                                    refus = e
                                }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        modifier = Modifier.heightIn(min = Mise.cible).testTag(ReperesSaisie.option(option.code))
                            .semantics { contentDescription = "${option.libelle} : ${option.detail}" },
                    ) { Text(option.libelle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            refus?.let { MessageErreur(it) }
        }
    }
}

/** Les repères des tests d'interface : ceux de l'iOS (`profil.saisie`). */
object ReperesSaisie {
    const val CHOIX = "profil.saisie"
    fun option(code: String) = "profil.saisie.$code"
}
