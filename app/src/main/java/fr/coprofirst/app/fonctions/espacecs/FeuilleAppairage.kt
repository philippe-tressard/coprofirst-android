// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.perimetres.ArbreDesPerimetres
import fr.coprofirst.app.commun.perimetres.ChoixPerimetres
import fr.coprofirst.app.commun.perimetres.ServicePerimetres
import fr.coprofirst.app.session.ErreurService

/**
 * « Appairer un écran » (`FormulaireCreation` de `OngletEcrans.svelte`), comme l'iOS
 * (`FeuilleAppairage`) : le code que l'écran affiche, son nom, UN périmètre (le
 * `mode="single"` du site). « Enregistrer » reste grisé tant que le site refuserait ; un
 * code inconnu rend la phrase du serveur.
 */
@Composable
internal fun FeuilleAppairage(service: ServiceEcransCS, perimetres: ServicePerimetres?, fermer: () -> Unit, appaire: (EcranAppaire) -> Unit) {
    var saisie by remember { mutableStateOf(SaisieAppairage()) }
    var arbre by remember { mutableStateOf(ArbreDesPerimetres(emptyList())) }
    LaunchedEffect(perimetres) {
        // Le défaut d'abord (`perimetreDefautListe` du site) : un écran de toute la
        // copropriété se confirme sans toucher au choix.
        val lu = try {
            ArbreDesPerimetres(perimetres?.perimetres().orEmpty())
        } catch (_: ErreurService) {
            ArbreDesPerimetres(emptyList())
        }
        if (saisie.perimetres.codes.isEmpty()) saisie = saisie.copy(perimetres = saisie.perimetres.choisirDefaut(lu))
        arbre = lu
    }
    FeuilleAnnuaire(
        stringResource(R.string.ecrans_appairer), ReperesEcrans.FEUILLE, fermer,
        envoyer = { appaire(service.appairer(saisie)) },
        complete = saisie.complete,
    ) {
        Champ(R.string.ecrans_code, aide = R.string.ecrans_aide_code) {
            OutlinedTextField(
                saisie.code, { saisie = saisie.copy(code = it) },
                Modifier.fillMaxWidth().testTag(ReperesEcrans.FEUILLE_CODE),
                placeholder = { Text(stringResource(R.string.ecrans_code_exemple)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
            )
        }
        Champ(R.string.ecrans_nom) {
            OutlinedTextField(
                saisie.nom, { saisie = saisie.copy(nom = it) },
                Modifier.fillMaxWidth().testTag(ReperesEcrans.FEUILLE_NOM),
                placeholder = { Text(stringResource(R.string.ecrans_nom_exemple)) },
                singleLine = true,
            )
        }
        if (arbre.noeuds.isNotEmpty()) {
            Champ(R.string.ecrans_perimetre, aide = R.string.ecrans_aide_perimetre) {
                ChoixPerimetres(
                    arbre, saisie.perimetres, { saisie = saisie.copy(perimetres = it) },
                    repere = ReperesEcrans.FEUILLE_PERIMETRE, service = perimetres,
                )
            }
        }
    }
}
