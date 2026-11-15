// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.session.Installation

/** Plus › Profil : l'écran du registre (`RegistreDesMenus`). */
@Composable
fun EcranProfil(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceProfilApi(contexte.session) }
    EcranProfil(service, contexte.session.installation, contexte.sortie, contexte.apparence)
}

/** Les formulaires du Profil, chacun dans sa feuille. */
private enum class Feuille { Identite, MotDePasse, DemandeModification }

/**
 * Plus › Profil, comme l'iOS (`ProfilView`), dans l'ordre du site : la résidence et le
 * statut, les informations personnelles, la sécurité, l'apparence, les droits RGPD, la
 * demande de suppression du compte — puis tout en bas les gestes de sortie.
 *
 * ⚠️ Divergences déclarées. Si le compte ne se lit pas, l'iOS garde « Apparence » ; ici
 * elle se range avec les sections du compte, sous la phrase d'erreur — seule la sortie
 * reste. Et, résorbée par #47 : « Saisie d'une affaire » et « Ce que
 * j'affiche » restent au site, et l'écran le dit — leurs tables (options, défauts) ne
 * sont pas publiées (coprofirst#2755).
 */
@Composable
fun EcranProfil(service: ServiceProfil, installation: Installation, sortie: GestesDeSortie, apparence: Apparence) {
    var feuille by rememberSaveable { mutableStateOf<Feuille?>(null) }
    var phrase by remember { mutableStateOf<Phrase?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(vertical = Mise.marge),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "compte") {
            Chargement(charger = { service.moi() }, squelette = { SqueletteProfil(installation) }) { lu, recharger ->
                var compte by remember(lu) { mutableStateOf(lu) }
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SectionResidence(installation, compte) { feuille = Feuille.DemandeModification }
                    SectionIdentite(compte) { feuille = Feuille.Identite }
                    SectionSecurite { feuille = Feuille.MotDePasse }
                    SectionApparence(apparence)
                    Note(stringResource(R.string.profil_au_site), Modifier.padding(horizontal = Mise.marge + 4.dp))
                    SectionDroits(compte, installation, service) { compte = it }
                    SectionSuppressionCompte(service)
                }
                when (feuille) {
                    Feuille.Identite -> FormulaireIdentite(compte, service, fermer = { feuille = null }) { message ->
                        phrase = message
                        recharger()
                    }
                    Feuille.MotDePasse -> FormulaireMotDePasse(service) { feuille = null }
                    Feuille.DemandeModification -> FeuilleDemandeModification(service) { feuille = null }
                    null -> Unit
                }
            }
        }
        // Toujours là, même si le compte ne se lit pas : on doit pouvoir sortir (iOS : hors du `if let compte`).
        item(key = "sortie") { SectionSortie(installation.nom, sortie) }
    }
    AvisProfil(phrase) { phrase = null }
}

/** La place de l'écran pendant le chargement : la carte de la résidence, estompée par `Chargement`. */
@Composable
private fun SqueletteProfil(installation: Installation) {
    Column(Modifier.padding(vertical = Mise.marge)) {
        SectionProfil(stringResource(R.string.profil_residence)) {
            Text(installation.nom, style = MaterialTheme.typography.titleMedium)
            LigneValeur(stringResource(R.string.profil_profil_utilisateur), "—")
            LigneValeur(stringResource(R.string.profil_batiment), "—")
        }
    }
}

/** La résidence, puis « Résidence & statut » du site : profil d'utilisateur, bâtiment, état du compte, ancienneté. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionResidence(installation: Installation, compte: UserRead, demander: () -> Unit) {
    val couleurs = LocalCouleurs.current
    SectionProfil(stringResource(R.string.profil_residence)) {
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(installation.nom, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
            compte.libelleLogement?.takeIf { it.isNotBlank() }?.let { logement ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(16.dp))
                    Text(logement, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
                }
            }
            // Le statut, puis les rôles mis en avant, comme l'iOS (`rolesAffiches`).
            val roles = Roles.affiches(compte.statut.value, compte.roles.orEmpty() + compte.role.value)
            if (roles.isNotEmpty()) {
                val annonce = stringResource(R.string.profil_roles, roles.joinToString(", ") { it.libelle })
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = annonce },
                ) {
                    roles.forEach { PastilleTeinte(it) }
                }
            }
        }
        HorizontalDivider(color = couleurs.border)
        LigneValeur(stringResource(R.string.profil_profil_utilisateur), Roles.statut(compte.statut.value).libelle)
        LigneValeur(stringResource(R.string.profil_batiment), compte.batimentNom ?: "—")
        LigneValeur(
            stringResource(R.string.profil_statut_compte),
            stringResource(if (compte.actif) R.string.profil_actif else R.string.profil_inactif),
        )
        LigneValeur(stringResource(R.string.profil_membre_depuis), FormatsDuSite.date(compte.creeLe.toInstant()))
        compte.derniereConnexion?.let {
            LigneValeur(stringResource(R.string.profil_derniere_connexion), FormatsDuSite.dateHeure(it.toInstant()))
        }
        LigneAction(stringResource(R.string.profil_demander_modification), "profil.demandeModification", onClick = demander)
    }
}

@Composable
private fun SectionIdentite(compte: UserRead, modifier: () -> Unit) {
    val couleurs = LocalCouleurs.current
    SectionProfil(stringResource(R.string.profil_informations), note = stringResource(R.string.profil_photo_au_site)) {
        Column(Modifier.padding(vertical = 2.dp).semantics(mergeDescendants = true) {}) {
            Text(
                FormatsDuSite.nom(compte.nomAffiche, compte.prenom, compte.nom),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = couleurs.text,
            )
            Text(compte.email, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
        }
        compte.telephone?.let { LigneValeur(stringResource(R.string.profil_telephone), it) }
        compte.societe?.let { LigneValeur(stringResource(R.string.profil_societe), it) }
        compte.fonction?.let { LigneValeur(stringResource(R.string.profil_fonction), it) }
        LigneAction(stringResource(R.string.profil_modifier_informations), "profil.modifierIdentite", onClick = modifier)
    }
}

@Composable
private fun SectionSecurite(modifier: () -> Unit) {
    SectionProfil(stringResource(R.string.profil_securite)) {
        LigneAction(stringResource(R.string.profil_modifier_mot_de_passe), "profil.motDePasse", onClick = modifier)
    }
}
