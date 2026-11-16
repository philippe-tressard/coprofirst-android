// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.ChampRecherche
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.commun.TablesCommunes
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet

/**
 * « Utilisateurs » (onglet `utilisateurs`), comme l'iOS (`OngletUtilisateursAdmin`) : tous
 * les comptes — recherche, type, état du compte, compteurs —, et chacun s'ouvre sur sa
 * fiche, qui porte les gestes de l'app. Modifier la fiche et l'accueil d'un arrivant
 * restent au site.
 */
@Composable
fun OngletUtilisateurs(contexte: ContexteOnglet, service: ServiceAdmin) {
    val etat = remember(service) { EtatUtilisateurs(service) }
    var ouvert by rememberSaveable { mutableStateOf<Int?>(null) }
    var essai by remember { mutableIntStateOf(0) }
    LaunchedEffect(etat, contexte.actualisation, essai) { etat.charger() }
    val comptes = etat.comptes
    val erreur = etat.erreur
    when {
        erreur != null -> MessageErreur(erreur) { essai++ }
        comptes == null -> Squelette()
        else -> Liste(etat, contexte) { ouvert = it }
    }
    ouvert?.let { id ->
        FicheUtilisateur(id, etat, contexte) {
            ouvert = null
            contexte.avis.feuilleFermee()
        }
    }
}

@Composable
private fun Liste(etat: EtatUtilisateurs, contexte: ContexteOnglet, ouvrir: (Int) -> Unit) {
    val affiches = etat.affiches
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Filtres(etat, affiches.size)
        if (affiches.isEmpty()) {
            EtatVide(stringResource(R.string.admin_utilisateurs_aucun_resultat), icone = Icons.Outlined.SearchOff)
        }
        affiches.forEach { compte -> LigneUtilisateur(compte) { ouvrir(compte.id) } }
        RenvoiAuSite(stringResource(R.string.admin_utilisateurs_sur_le_site), "utilisateurs", contexte.session.installation.url)
    }
}

/** La recherche, le type (menu, comme la pastille déroulante du site), l'état du compte et le compteur. */
@Composable
private fun Filtres(etat: EtatUtilisateurs, affiches: Int) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChampRecherche(
            etat.recherche,
            { etat.recherche = it },
            stringResource(R.string.admin_utilisateurs_recherche),
            ReperesAdmin.RECHERCHE,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FiltreType(etat)
            FiltreCompte.entries.forEach { filtre ->
                val retenue = etat.filtre == filtre
                Pastille(
                    stringResource(libelle(filtre)),
                    retenue,
                    Modifier.testTag(ReperesAdmin.filtre(filtre)),
                    compte = if (retenue) affiches else null,
                ) { etat.filtre = filtre }
            }
        }
        val total = etat.comptes.orEmpty().size
        val conseil = FormatsDuSite.nombre(ListeUtilisateurs.membresDuConseil(etat.comptes.orEmpty()))
        Text(
            if (affiches == total) {
                stringResource(R.string.admin_utilisateurs_compteur_tous, FormatsDuSite.nombre(total), conseil)
            } else {
                stringResource(R.string.admin_utilisateurs_compteur_filtre, FormatsDuSite.nombre(affiches), FormatsDuSite.nombre(total), conseil)
            },
            style = MaterialTheme.typography.bodySmall,
            color = couleurs.textMuted,
        )
    }
}

/** Le filtre « Type » : une pastille qui déroule les statuts de compte, dans l'ordre du site. */
@Composable
private fun FiltreType(etat: EtatUtilisateurs) {
    var deplie by remember { mutableStateOf(false) }
    val tous = stringResource(R.string.admin_utilisateurs_tous_types)
    Box {
        Pastille(
            etat.statut?.let(Roles::statutAbrege) ?: tous,
            etat.statut != null,
            Modifier.testTag(ReperesAdmin.TYPE),
        ) { deplie = true }
        DropdownMenu(expanded = deplie, onDismissRequest = { deplie = false }) {
            DropdownMenuItem(text = { Text(tous) }, onClick = { etat.statut = null; deplie = false })
            TablesCommunes.statuts.keys.forEach { statut ->
                DropdownMenuItem(text = { Text(Roles.statut(statut).libelle) }, onClick = { etat.statut = statut; deplie = false })
            }
        }
    }
}

private fun libelle(filtre: FiltreCompte): Int = when (filtre) {
    FiltreCompte.Tous -> R.string.admin_utilisateurs_tous_comptes
    FiltreCompte.Actifs -> R.string.admin_utilisateurs_actifs
    FiltreCompte.EnAttente -> R.string.admin_utilisateurs_en_attente
}

/** Un compte dans la liste : nom, e-mail, type, état, rôles, bâtiment — grisé s'il est inactif, comme le site. */
@Composable
private fun LigneUtilisateur(compte: UtilisateurAdmin, ouvrir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Carte(
        Modifier.alpha(if (compte.actif) 1f else 0.6f).testTag(ReperesAdmin.utilisateur(compte.id)),
        ouvrir = ouvrir,
        action = stringResource(R.string.admin_utilisateurs_ouvrir),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(compte.nomAffiche, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
                    if (compte.ban.banni) {
                        val dit = stringResource(R.string.admin_utilisateurs_banni)
                        Icon(
                            if (compte.ban.interdit) Icons.Outlined.Block else Icons.Outlined.HourglassTop,
                            contentDescription = null,
                            tint = couleurs.danger,
                            modifier = Modifier.semantics { contentDescription = dit },
                        )
                    }
                }
                Text(compte.email, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted, maxLines = 1)
                PastillesCompte(compte)
                if (compte.statut == UtilisateurAdmin.STATUT_LOCATAIRE) {
                    compte.nomProprietaire?.let {
                        Text(stringResource(R.string.admin_utilisateurs_bailleur, it), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
                    }
                }
                compte.purgeAvertieLe?.let { PurgeAvertie(it) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = couleurs.textMuted)
        }
    }
}

/** « Inactif, averti le … » : le compte sera supprimé s'il ne se reconnecte pas. */
@Composable
fun PurgeAvertie(le: java.time.Instant) {
    val couleurs = LocalCouleurs.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = couleurs.warningTexte)
        Text(
            stringResource(R.string.admin_utilisateurs_purge_avertie, FormatsDuSite.date(le)),
            style = MaterialTheme.typography.bodySmall,
            color = couleurs.warningTexte,
        )
    }
}

/** Le type, l'état du compte, les rôles et le bâtiment, en pastilles teintées du site. */
@Composable
fun PastillesCompte(compte: UtilisateurAdmin) {
    val etat = when (compte.etat) {
        EtatCompte.Actif -> R.string.admin_utilisateurs_actif
        EtatCompte.EmailNonVerifie -> R.string.admin_utilisateurs_email_non_verifie
        EtatCompte.EnAttente -> R.string.admin_utilisateurs_en_attente
    }
    FlowCapsules {
        PastilleTeinte(Roles.statutAbrege(compte.statut), Roles.statut(compte.statut).badge, compacte = true)
        PastilleTeinte(stringResource(etat), compte.etat.badge, compacte = true)
        compte.roles.forEach { PastilleTeinte(Roles.role(it), compacte = true) }
        compte.batimentNom?.let { PastilleTeinte(it, "badge-gray", compacte = true) }
    }
}

/** Le squelette : quatre comptes grisés. */
@Composable
private fun Squelette() {
    val annonce = stringResource(R.string.chargement)
    Column(Modifier.alpha(0.35f).semantics(mergeDescendants = true) { contentDescription = annonce }, verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        repeat(4) {
            Carte {
                Text(stringResource(R.string.admin_utilisateurs_squelette_nom), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.admin_utilisateurs_squelette_email), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
