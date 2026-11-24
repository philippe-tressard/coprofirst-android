// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.ConstantesDuServeur
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.Note
import fr.coprofirst.app.commun.apercu.NomDeLaSaisie
import fr.coprofirst.app.commun.rememberEnvoi

/** Un champ d'un formulaire du Profil : son libellé, son repère, son clavier. */
@Composable
fun ChampProfil(
    valeur: String,
    libelle: String,
    repere: String,
    modifier: Modifier = Modifier,
    exemple: String? = null,
    clavier: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    lignes: Int = 1,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(libelle) },
        placeholder = exemple?.let { { Text(it) } },
        singleLine = lignes == 1,
        minLines = lignes,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else clavier,
            capitalization = if (clavier == KeyboardType.Text && !secret) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            autoCorrectEnabled = clavier == KeyboardType.Text && !secret,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = Mise.marge).testTag(repere),
    )
}

/**
 * « Informations personnelles », comme l'iOS (`FormulaireIdentite`) : prénom, nom,
 * adresse, téléphone, société, fonction. Une nouvelle adresse demande le mot de passe
 * actuel et ne vaut qu'une fois confirmée. `enregistre` reçoit la phrase à montrer.
 */
@Composable
fun FormulaireIdentite(compte: UserRead, service: ServiceProfil, fermer: () -> Unit, enregistre: (Phrase) -> Unit) {
    var saisie by remember(compte) { mutableStateOf(SaisieIdentite.depuis(compte)) }
    val envoi = rememberEnvoi()
    val nouvelle = saisie.changeAdresse(compte.email)
    FeuilleProfil(
        titre = stringResource(R.string.profil_informations),
        fermer = fermer,
        geste = GesteDeFeuille(stringResource(R.string.profil_enregistrer), "profil.identite.enregistrer", saisie.complete(compte.email), envoi.enCours) {
            envoi.lancer {
                val lien = service.modifierIdentite(saisie, compte.email)
                fermer()
                enregistre(
                    if (lien) Phrase.Texte(R.string.profil_lien_envoye, listOf(saisie.email.trim()))
                    else Phrase.Texte(R.string.profil_mis_a_jour),
                )
            }
        },
    ) {
        envoi.erreur?.let { MessageErreur(it, Modifier.padding(horizontal = Mise.marge)) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampProfil(saisie.prenom, stringResource(R.string.profil_prenom), "profil.identite.prenom") { saisie = saisie.copy(prenom = it) }
            ChampProfil(saisie.nom, stringResource(R.string.profil_nom), "profil.identite.nom") { saisie = saisie.copy(nom = it) }
            // Le nom tel qu'il sera affiché, rendu par le serveur (`NomDeLaSaisie` de l'iOS) : celui du compte d'emblée.
            NomDeLaSaisie(
                saisie.prenom, saisie.nom, service, "profil.identite.nom-affiche",
                Modifier.padding(horizontal = Mise.marge + 4.dp), connu = compte.nomAffiche,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampProfil(saisie.email, stringResource(R.string.profil_email), "profil.identite.email", clavier = KeyboardType.Email) {
                saisie = saisie.copy(email = it)
            }
            AnimatedVisibility(nouvelle) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChampProfil(saisie.motDePasseActuel, stringResource(R.string.profil_mot_de_passe_actuel), "profil.identite.motDePasse", secret = true) {
                        saisie = saisie.copy(motDePasseActuel = it)
                    }
                    Note(stringResource(R.string.profil_changement_adresse), Modifier.padding(horizontal = Mise.marge + 4.dp))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampProfil(saisie.telephone, stringResource(R.string.profil_telephone), "profil.identite.telephone", exemple = stringResource(R.string.profil_exemple_telephone), clavier = KeyboardType.Phone) {
                saisie = saisie.copy(telephone = it)
            }
            ChampProfil(saisie.societe, stringResource(R.string.profil_societe), "profil.identite.societe", exemple = stringResource(R.string.profil_exemple_societe)) {
                saisie = saisie.copy(societe = it)
            }
            ChampProfil(saisie.fonction, stringResource(R.string.profil_fonction), "profil.identite.fonction", exemple = stringResource(R.string.profil_exemple_fonction)) {
                saisie = saisie.copy(fonction = it)
            }
        }
    }
}

/**
 * « Modifier le mot de passe », comme l'iOS (`MotDePasseView`) : l'actuel, puis le
 * nouveau saisi deux fois. Le bouton dit pourquoi il attend, dans l'ordre du site ; un
 * refus (actuel incorrect, nouveau trop faible) porte la phrase du serveur.
 */
@Composable
fun FormulaireMotDePasse(service: ServiceProfil, fermer: () -> Unit) {
    var actuel by rememberSaveable { mutableStateOf("") }
    var nouveau by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val envoi = rememberEnvoi()
    var reussi by remember { mutableStateOf(false) }
    val couleurs = LocalCouleurs.current
    val attente = attenteMotDePasse(actuel, nouveau, confirmation)
    FeuilleProfil(titre = stringResource(R.string.profil_titre_mot_de_passe), fermer = fermer) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampProfil(actuel, stringResource(R.string.profil_mot_de_passe_actuel), "profil.motDePasse.actuel", secret = true) { actuel = it }
            Note(stringResource(R.string.profil_mot_de_passe_aide), Modifier.padding(horizontal = Mise.marge + 4.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampProfil(nouveau, stringResource(R.string.profil_nouveau_mot_de_passe), "profil.motDePasse.nouveau", secret = true) { nouveau = it }
            ChampProfil(confirmation, stringResource(R.string.profil_confirmation), "profil.motDePasse.confirmation", secret = true) { confirmation = it }
            val marge = Modifier.padding(horizontal = Mise.marge + 4.dp)
            when {
                confirmation.isNotEmpty() && confirmation != nouveau ->
                    Note(stringResource(R.string.profil_confirmation_differe), marge, couleurs.danger)
                attente != null -> Note(attente.phrase(), marge)
            }
        }
        envoi.erreur?.let { MessageErreur(it, Modifier.padding(horizontal = Mise.marge)) }
        BoutonPlein(
            R.string.profil_modifier_mot_de_passe,
            Modifier.padding(horizontal = Mise.marge).testTag("profil.motDePasse.modifier"),
            actif = attente == null,
            enCours = envoi.enCours,
        ) {
            envoi.lancer {
                service.changerMotDePasse(actuel, nouveau)
                reussi = true
            }
        }
    }
    if (reussi) {
        AlertDialog(
            onDismissRequest = fermer,
            confirmButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.profil_ok)) } },
            title = { Text(stringResource(R.string.profil_mot_de_passe_modifie)) },
        )
    }
}

/** Ce qui manque, dit comme l'iOS ; la longueur minimale est celle du serveur. */
@Composable
private fun AttenteMotDePasse.phrase(): String = when (this) {
    AttenteMotDePasse.Actuel -> stringResource(R.string.profil_attente_actuel)
    AttenteMotDePasse.Nouveau -> ConstantesDuServeur.LONGUEUR_MIN_MOT_DE_PASSE.let {
        pluralStringResource(R.plurals.profil_attente_nouveau, it, it)
    }
    AttenteMotDePasse.Confirmation -> stringResource(R.string.profil_attente_confirmation)
}
