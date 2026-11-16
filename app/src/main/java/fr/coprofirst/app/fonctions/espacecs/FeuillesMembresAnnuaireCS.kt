// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.apercu.NomDeLaSaisie
import fr.coprofirst.app.commun.apercu.ServiceNomsApi
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.delay

/** Le délai avant de demander au serveur ce qu'un nom saisi désigne (celui de l'iOS). */
private const val PAUSE_RAPPROCHEMENT_MS = 350L

/**
 * Un membre du conseil, comme l'iOS (`FeuilleMembreConseil`) : sa civilité, son prénom, son
 * nom — et, dès la frappe du nom, l'inscrit et le logement que le SERVEUR y rapproche, comme
 * le site les montre. Le logement trouvé remplace l'ancien ; un lien déjà posé reste.
 */
@Composable
internal fun FeuilleMembreConseil(
    membre: MembreConseil,
    rapprocher: suspend (String) -> Rapprochement,
    session: SessionOuverte,
    fermer: () -> Unit,
    enregistrer: suspend (MembreConseil) -> Unit,
) {
    var fiche by remember { mutableStateOf(membre) }
    FeuilleAnnuaire(
        stringResource(if (membre.id == null) R.string.espacecs_annuaire_nouveau_membre_cs else R.string.espacecs_annuaire_fiche),
        ReperesEspaceCS.FEUILLE_MEMBRE_CONSEIL,
        fermer,
        envoyer = { enregistrer(fiche) },
        complete = AnnuaireCS.nomme(fiche.prenom, fiche.nom),
    ) {
        ChampsIdentite(fiche.genre, fiche.prenom, fiche.nom, Origine(membre.prenom, membre.nom, membre.nomServi), session, { fiche = fiche.copy(genre = it) }) { prenom, nom ->
            fiche = fiche.copy(prenom = prenom, nom = nom, nomServi = null)
        }
        RapprochementServi(fiche.userId != null, fiche.batimentNom)
    }
    Rapprocher(fiche.nom, membre.nom, rapprocher, saisi = { fiche = fiche.copy(userId = null) }) { trouve ->
        val loge = trouve.batimentId != null || trouve.etage != null
        val logee = if (loge) {
            fiche.copy(batimentId = trouve.batimentId, batimentNom = trouve.batimentNom, etage = trouve.etage, etageLibelle = trouve.etageLibelle)
        } else {
            fiche
        }
        fiche = logee.copy(userId = fiche.userId ?: trouve.userId)
    }
}

/**
 * Un interlocuteur du syndic, comme l'iOS (`FeuilleMembreSyndic`) : civilité, prénom, nom,
 * fonction, courriel et numéros — un champ par numéro. Il se lie à un inscrit, sans logement.
 */
@Composable
internal fun FeuilleMembreSyndic(
    membre: MembreSyndic,
    rapprocher: suspend (String) -> Rapprochement,
    session: SessionOuverte,
    fermer: () -> Unit,
    enregistrer: suspend (MembreSyndic) -> Unit,
) {
    var fiche by remember { mutableStateOf(membre) }
    val telephones = remember { mutableStateListOf(*AnnuaireCS.champsTelephone(membre.telephones).toTypedArray()) }
    FeuilleAnnuaire(
        stringResource(if (membre.id == null) R.string.espacecs_annuaire_nouveau_membre_syndic else R.string.espacecs_annuaire_fiche),
        ReperesEspaceCS.FEUILLE_MEMBRE_SYNDIC,
        fermer,
        envoyer = { enregistrer(fiche.copy(telephones = telephones.map { it.trim() }.filter { it.isNotEmpty() })) },
        complete = AnnuaireCS.nomme(fiche.prenom, fiche.nom),
    ) {
        ChampsIdentite(fiche.genre, fiche.prenom, fiche.nom, Origine(membre.prenom, membre.nom, membre.nomServi), session, { fiche = fiche.copy(genre = it) }) { prenom, nom ->
            fiche = fiche.copy(prenom = prenom, nom = nom, nomServi = null)
        }
        RapprochementServi(fiche.userId != null, null)
        Champ(fiche.fonction.orEmpty(), { fiche = fiche.copy(fonction = it) }, R.string.espacecs_annuaire_fonction, R.string.espacecs_annuaire_fonction_indication)
        Champ(fiche.email.orEmpty(), { fiche = fiche.copy(email = it.trim()) }, R.string.espacecs_annuaire_email, R.string.espacecs_annuaire_email, KeyboardType.Email)
        LibelleCS(stringResource(if (telephones.size > 1) R.string.espacecs_annuaire_telephones else R.string.espacecs_annuaire_telephone))
        telephones.forEachIndexed { rang, numero ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = numero,
                    onValueChange = { telephones[rang] = it },
                    placeholder = { Text(stringResource(R.string.espacecs_annuaire_telephone_indication)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
                if (telephones.size > 1) {
                    IconButton(onClick = { telephones.removeAt(rang) }) {
                        Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = stringResource(R.string.espacecs_annuaire_retirer_numero), tint = LocalCouleurs.current.danger)
                    }
                }
            }
        }
        TextButton(onClick = { telephones.add("") }, modifier = Modifier.heightIn(min = Mise.cible)) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.espacecs_annuaire_ajouter_telephone))
        }
    }
    Rapprocher(fiche.nom, membre.nom, rapprocher, saisi = { fiche = fiche.copy(userId = null) }) { trouve ->
        fiche = fiche.copy(userId = fiche.userId ?: trouve.userId)
    }
}

/** Le prénom et le nom d'une fiche à son ouverture, et le nom affiché que le serveur en a servi. */
private data class Origine(val prenom: String?, val nom: String?, val nomServi: String?)

/**
 * Civilité, prénom et nom : les trois champs de toute fiche — puis le nom affiché que le
 * serveur en rend. Le nom servi avec la fiche d'origine ne se redemande pas tant que le
 * prénom et le nom restent les siens.
 */
@Composable
private fun ChampsIdentite(
    genre: String?,
    prenom: String?,
    nom: String?,
    origine: Origine,
    session: SessionOuverte,
    changerGenre: (String) -> Unit,
    changerNom: (prenom: String?, nom: String?) -> Unit,
) {
    val noms = remember(session) { ServiceNomsApi(session) }
    val retenue = genre ?: AnnuaireCS.civiliteParDefaut
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LibelleCS(stringResource(R.string.espacecs_annuaire_civilite))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TablesEspaceCS.civilites.forEach { c -> Pastille(c, retenue == c) { changerGenre(c) } }
        }
    }
    Champ(prenom.orEmpty(), { changerNom(it, nom) }, R.string.espacecs_annuaire_prenom, R.string.espacecs_annuaire_prenom, repere = ReperesEspaceCS.FEUILLE_PRENOM)
    Champ(nom.orEmpty(), { changerNom(prenom, it) }, R.string.espacecs_annuaire_nom, R.string.espacecs_annuaire_nom, repere = ReperesEspaceCS.FEUILLE_NOM)
    NomDeLaSaisie(
        prenom, nom, noms, ReperesEspaceCS.FEUILLE_NOM_AFFICHE,
        connu = origine.nomServi.takeIf { prenom == origine.prenom && nom == origine.nom },
    )
}

/** Ce que le serveur a rapproché du nom : l'inscrit lié, et le logement (conseil). */
@Composable
private fun RapprochementServi(lie: Boolean, batiment: String?) {
    if (lie) InscritLie()
    batiment?.takeIf { it.isNotBlank() }?.let {
        val c = LocalCouleurs.current
        Row(Modifier.testTag(ReperesEspaceCS.FEUILLE_LOGEMENT), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Apartment, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.annuaire_bat, it), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

/**
 * Le rapprochement d'un nom SAISI (pas celui de l'ouverture), comme l'iOS : le lien d'avant
 * se défait (`saisi`), puis, après une courte pause, la question au serveur — seule la
 * DERNIÈRE réponse compte (la question d'avant est annulée). Un échec se tait, comme le
 * site : le PUT refait le rapprochement, rien ne se perd.
 */
@Composable
private fun Rapprocher(
    nom: String?,
    nomOuvert: String?,
    rapprocher: suspend (String) -> Rapprochement,
    saisi: () -> Unit,
    appliquer: (Rapprochement) -> Unit,
) {
    LaunchedEffect(nom) {
        if (nom == nomOuvert) return@LaunchedEffect
        saisi()
        val question = nom?.trim().orEmpty()
        if (question.isEmpty()) return@LaunchedEffect
        delay(PAUSE_RAPPROCHEMENT_MS)
        val trouve = try {
            rapprocher(question)
        } catch (_: ErreurService) {
            return@LaunchedEffect
        }
        appliquer(trouve)
    }
}
