// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxWidth
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.texte.TexteDuSite

/** « Nouvelle idée » : un titre et une description, tous deux obligatoires. */
@Composable
fun FormulaireIdee(avis: AvisDuGeste, service: ServiceCommunaute, fermer: () -> Unit, fait: () -> Unit) {
    val ressources = LocalResources.current
    var titre by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    FeuilleSaisie(R.string.idees_nouvelle, avis, complete = titre.isNotBlank() && description.isNotBlank(), fermer = fermer, envoyer = {
        service.proposerIdee(titre.trim(), TexteDuSite.html(saisi = description.trim()))
        fait()
        ressources.getString(R.string.idees_soumise)
    }) {
        ChampCommunaute(R.string.communaute_titre, titre, { titre = it }, R.string.idees_titre_indication)
        ChampCommunaute(R.string.communaute_description, description, { description = it }, R.string.idees_description_indication, lignes = 5)
    }
}

/**
 * « Déposer une annonce » : le titre, l'objet (type, catégorie, prix pour une vente), la
 * description et le contact. Les photos s'ajoutent sur le site, et la feuille le dit.
 */
@Composable
fun FormulaireAnnonce(avis: AvisDuGeste, service: ServiceCommunaute, fermer: () -> Unit, fait: () -> Unit) {
    val ressources = LocalResources.current
    var saisie by remember { mutableStateOf(SaisieAnnonce()) }
    FeuilleSaisie(R.string.annonces_deposer, avis, complete = saisie.complete, fermer = fermer, envoyer = {
        service.deposerAnnonce(saisie)
        fait()
        ressources.getString(R.string.annonces_publiee)
    }) {
        ChampCommunaute(R.string.communaute_titre, saisie.titre, { saisie = saisie.copy(titre = it) }, R.string.annonces_titre_indication)
        LibelleSection(R.string.annonces_objet)
        RangeeFiltres {
            TablesCommunaute.typesAnnonce.forEach { (code, libelle) ->
                Pastille(libelle, saisie.type == code) { saisie = saisie.copy(type = code) }
            }
        }
        ChoixDansUnMenu(
            stringResource(R.string.annonces_categorie) + " : " + ReglesCommunaute.libelle(saisie.categorie, TablesCommunaute.categoriesAnnonce),
            TablesCommunaute.categoriesAnnonce,
            null,
        ) { code -> if (code != null) saisie = saisie.copy(categorie = code) }
        if (saisie.type == SaisieAnnonce.VENTE) {
            OutlinedTextField(
                saisie.prix,
                { saisie = saisie.copy(prix = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.annonces_prix)) },
                isError = !saisie.prixValide,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            InterrupteurCommunaute(R.string.annonces_prix_negociable, saisie.negociable) { saisie = saisie.copy(negociable = it) }
        }
        ChampCommunaute(
            R.string.communaute_description, saisie.description, { saisie = saisie.copy(description = it) },
            R.string.annonces_description_indication, lignes = 5,
        )
        InterrupteurCommunaute(R.string.annonces_coordonnees, saisie.coordonneesVisibles) { saisie = saisie.copy(coordonneesVisibles = it) }
        Etiquette(stringResource(R.string.annonces_photos_sur_le_site))
    }
}

/**
 * Un choix parmi les codes d'une table du site, en menu (le `Menu`/`Picker` de l'iOS) :
 * `libelle` montre le choix en cours ; `aucun` ajoute l'entrée « tous » (code `null`).
 */
@Composable
fun ChoixDansUnMenu(
    libelle: String,
    options: List<Pair<String, String>>,
    aucun: String?,
    icone: ImageVector = Icons.Filled.ArrowDropDown,
    choisir: (String?) -> Unit,
) {
    var deplie by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { deplie = true }, modifier = Modifier.heightIn(min = Mise.cible)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(libelle, style = MaterialTheme.typography.labelLarge, color = LocalCouleurs.current.primary)
                Icon(icone, contentDescription = null, tint = LocalCouleurs.current.primary)
            }
        }
        DropdownMenu(expanded = deplie, onDismissRequest = { deplie = false }) {
            if (aucun != null) DropdownMenuItem(text = { Text(aucun) }, onClick = { deplie = false; choisir(null) })
            options.forEach { (code, texte) ->
                DropdownMenuItem(text = { Text(texte) }, onClick = { deplie = false; choisir(code) })
            }
        }
    }
}
