// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.PhraseAvis
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * La fiche d'un compte, en feuille, comme l'iOS (`FicheUtilisateurAdmin`) : ce que le
 * serveur en sert, puis les gestes que l'app porte — chacun offert aux seuls rôles que sa
 * route accepte : la communauté et la suppression à l'administration, l'auto-match au
 * conseil et à l'administration pour un compte actif sans lot, comme le site ; les rôles,
 * tels que le serveur les sert (`gestes_roles`).
 *
 * Un geste réussi ferme la fiche, et son annonce suit la fermeture ; refusé, la phrase du
 * serveur reste dans la fiche.
 *
 * ⚠️ Divergence déclarée : l'iOS relit le profil du lecteur après un geste de rôle sur son
 * propre compte (ses droits changent aussitôt) ; l'app Android n'a pas encore de quoi
 * relire les droits du compte depuis un écran — ils le sont à la prochaine ouverture de
 * la résidence (#87).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheUtilisateur(id: Int, etat: EtatUtilisateurs, contexte: ContexteOnglet, fermer: () -> Unit) {
    val feuille = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val portee = rememberCoroutineScope()
    var echec by remember(id) { mutableStateOf<ErreurService?>(null) }
    val compte = etat.compte(id)

    /** Un geste : son annonce après la fermeture, ou la phrase du serveur ici. */
    fun geste(faire: suspend () -> PhraseAvis) {
        portee.launch {
            try {
                val annonce = faire()
                contexte.avis.direApresFermeture(annonce)
                fermer()
            } catch (e: ErreurService) {
                echec = e
            }
        }
    }

    ModalBottomSheet(onDismissRequest = fermer, sheetState = feuille, containerColor = LocalCouleurs.current.bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EnTete(compte?.nomAffiche, fermer)
            echec?.let { MessageErreur(it) }
            if (compte == null) {
                EtatVide(stringResource(R.string.admin_utilisateurs_aucun_resultat))
                return@Column
            }
            val actif = etat.enCours != compte.id
            Column(Modifier.alpha(if (actif) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Identite(compte)
                Rattachements(compte)
                GestesDuCompte(compte, etat, contexte, actif, ::geste)
            }
            RenvoiAuSite(stringResource(R.string.admin_utilisateurs_sur_le_site_sans_roles), "utilisateurs", contexte.session.installation.url)
        }
    }
}

@Composable
private fun EnTete(nom: String?, fermer: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            nom.orEmpty(),
            style = Typo.titre(MaterialTheme.typography.titleLarge),
            color = LocalCouleurs.current.text,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = fermer, modifier = Modifier.testTag(ReperesAdmin.FICHE_FERMER)) {
            Text(stringResource(R.string.admin_fiche_fermer), color = LocalCouleurs.current.primary)
        }
    }
}

@Composable
private fun Identite(compte: UtilisateurAdmin) {
    val couleurs = LocalCouleurs.current
    Section(stringResource(R.string.admin_fiche_compte)) {
        PastillesCompte(compte)
        Ligne(stringResource(R.string.admin_fiche_email), compte.email)
        Ligne(stringResource(R.string.admin_fiche_type), Roles.statut(compte.statut).libelle)
        Ligne(stringResource(R.string.admin_fiche_batiment), compte.batimentNom ?: FormatsDuSite.TIRET)
        if (compte.statut == UtilisateurAdmin.STATUT_LOCATAIRE) {
            compte.nomProprietaire?.let {
                Text(stringResource(R.string.admin_utilisateurs_bailleur, it), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
            }
        }
        Ligne(stringResource(R.string.admin_fiche_inscription), FormatsDuSite.date(compte.creeLe))
        Ligne(stringResource(R.string.admin_fiche_derniere_connexion), FormatsDuSite.dateHeure(compte.derniereConnexion))
        compte.purgeAvertieLe?.let {
            PurgeAvertie(it)
            Text(stringResource(R.string.admin_utilisateurs_purge_aide), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
        }
    }
}

/** Loti, TC, Vigik, Bail : ce que le serveur sert ; un manque en rouge. */
@Composable
private fun Rattachements(compte: UtilisateurAdmin) {
    Section(stringResource(R.string.admin_fiche_rattachements)) {
        EtiquetteCompte.toutes.forEach { etiquette ->
            val etat = compte.etiquettes[etiquette.cle]
            val texte = etiquette.texte(etat)?.let { stringResource(it) }.orEmpty()
            val manque = (etat ?: EtiquetteCompte.MANQUE) == EtiquetteCompte.MANQUE
            Ligne(stringResource(etiquette.libelle), texte, if (manque) LocalCouleurs.current.danger else null)
        }
    }
}

/** La communauté, les rôles servis, l'auto-match et la suppression — chacun à qui sa route l'accepte. */
@Composable
private fun GestesDuCompte(
    compte: UtilisateurAdmin,
    etat: EtatUtilisateurs,
    contexte: ContexteOnglet,
    actif: Boolean,
    geste: (suspend () -> PhraseAvis) -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val ressources = LocalResources.current
    val droits = contexte.droits
    val nom = compte.nomAffiche
    var roleAConfirmer by remember { mutableStateOf<GesteDeRole?>(null) }
    var aSupprimer by remember { mutableStateOf(false) }
    Section(stringResource(R.string.admin_fiche_communaute)) {
        Ligne(stringResource(R.string.admin_fiche_communaute), etatCommunaute(compte.ban))
        if (droits.administrateur) {
            val banni = compte.ban.banni
            LigneGeste(
                stringResource(if (banni) R.string.admin_fiche_autoriser else R.string.admin_fiche_interdire),
                if (banni) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                if (banni) couleurs.successTexte else couleurs.warningTexte,
                ReperesAdmin.FICHE_COMMUNAUTE,
                actif,
            ) {
                geste { PhraseAvis(ressources.getString(etat.basculerCommunaute(compte).texte, nom)) }
            }
        }
    }
    val parExploitant = compte.operateurDesigneParLExploitant(etat.lecteur.instanceCommune)
    if (compte.gestesRoles.isNotEmpty() || parExploitant) {
        Section(stringResource(R.string.admin_fiche_roles)) {
            compte.gestesRoles.forEach { offert ->
                val dit = stringResource(TextesRoles.action(offert), TextesRoles.libelle(offert.role))
                LigneGeste(
                    TextesRoles.bouton(offert),
                    if (offert.geste == GesteRole.Ajouter) Icons.Outlined.AddCircleOutline else Icons.Outlined.RemoveCircleOutline,
                    teinte(offert),
                    ReperesAdmin.role(offert.role),
                    actif,
                    dit,
                ) { roleAConfirmer = offert }
            }
            if (parExploitant) {
                Text(
                    stringResource(R.string.admin_fiche_operateur_par_exploitant),
                    style = MaterialTheme.typography.bodyMedium,
                    color = couleurs.textMuted,
                    modifier = Modifier.testTag(ReperesAdmin.FICHE_EXPLOITANT),
                )
            }
        }
    }
    if ((droits.cs || droits.administrateur) && compte.actif && !compte.aDesLots) {
        Carte {
            LigneGeste(stringResource(R.string.admin_fiche_rapprochement), Icons.Outlined.Sync, couleurs.primary, ReperesAdmin.FICHE_RAPPROCHEMENT, actif) {
                geste { etat.relancerRapprochement(compte) }
            }
        }
    }
    if (droits.administrateur) {
        Carte {
            LigneGeste(stringResource(R.string.admin_fiche_supprimer), Icons.Outlined.Delete, couleurs.danger, ReperesAdmin.FICHE_SUPPRIMER, actif) {
                aSupprimer = true
            }
        }
    }
    roleAConfirmer?.let { offert ->
        ConfirmationDeRole(offert, compte, etat.lecteur.id, fermer = { roleAConfirmer = null }) {
            roleAConfirmer = null
            geste {
                etat.changerRole(compte, offert)
                PhraseAvis(ressources.getString(TextesRoles.annonce(offert), TextesRoles.libelle(offert.role), nom))
            }
        }
    }
    if (aSupprimer) {
        Confirmation(
            titre = stringResource(R.string.admin_fiche_confirmer_suppression, nom, compte.email),
            texte = null,
            action = stringResource(R.string.admin_fiche_supprimer),
            teinte = couleurs.danger,
            fermer = { aSupprimer = false },
        ) {
            aSupprimer = false
            geste {
                etat.supprimer(compte)
                PhraseAvis(ressources.getString(R.string.admin_annonce_supprime, nom))
            }
        }
    }
}

/** La question d'un geste de rôle (`ConfirmationRole`) : « votre compte » sur la fiche du lecteur, puis sa suite. */
@Composable
private fun ConfirmationDeRole(offert: GesteDeRole, compte: UtilisateurAdmin, lecteur: Int?, fermer: () -> Unit, confirmer: () -> Unit) {
    val confirmation = ConfirmationRole.de(offert, compte, lecteur)
    val destinataire = if (confirmation.pourSoi) stringResource(R.string.admin_fiche_votre_compte) else compte.nomAffiche
    Confirmation(
        titre = stringResource(TextesRoles.question(offert), TextesRoles.libelle(offert.role), destinataire),
        texte = confirmation.suite?.let { stringResource(TextesRoles.suite(it)) },
        action = stringResource(R.string.admin_fiche_confirmer),
        teinte = if (offert.geste == GesteRole.Retirer) LocalCouleurs.current.danger else LocalCouleurs.current.primary,
        fermer = fermer,
        confirmer = confirmer,
    )
}

@Composable
private fun Confirmation(titre: String, texte: String?, action: String, teinte: Color, fermer: () -> Unit, confirmer: () -> Unit) {
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(titre, style = MaterialTheme.typography.titleMedium) },
        text = texte?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = confirmer, modifier = Modifier.testTag(ReperesAdmin.FICHE_CONFIRMER)) {
                Text(action, color = teinte, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.admin_fiche_annuler)) } },
        containerColor = LocalCouleurs.current.surface,
    )
}

/** La teinte d'un geste de rôle, celle du site : le conseil en information, l'administration en orange, l'opérateur en violet, un retrait en danger. */
@Composable
private fun teinte(offert: GesteDeRole): Color {
    val couleurs = LocalCouleurs.current
    return when {
        offert.geste == GesteRole.Retirer -> couleurs.danger
        offert.role == UtilisateurAdmin.ROLE_ADMIN -> couleurs.orange
        offert.role == UtilisateurAdmin.ROLE_OPERATEUR -> couleurs.violet
        else -> couleurs.info
    }
}

@Composable
private fun etatCommunaute(ban: BanCommunaute): String = when {
    ban.interdit -> stringResource(R.string.admin_fiche_banni_pour_de_bon)
    ban.banni && ban.jusquAu != null -> stringResource(R.string.admin_fiche_banni_jusqu_au, FormatsDuSite.date(ban.jusquAu))
    else -> stringResource(R.string.admin_fiche_autorisee)
}

/** Une section de la fiche : son intitulé en petites capitales, puis sa carte. */
@Composable
private fun Section(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Libelle(titre)
        Carte { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = contenu) }
    }
}

/** Un libellé et sa valeur servie, sélectionnable (une adresse se recopie). */
@Composable
private fun Ligne(libelle: String, valeur: String, teinte: Color? = null) {
    val couleurs = LocalCouleurs.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(libelle, style = MaterialTheme.typography.bodyMedium, color = couleurs.text)
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                valeur,
                style = MaterialTheme.typography.bodyMedium,
                color = teinte ?: couleurs.textMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Un geste de la fiche : son pictogramme et son libellé, dans sa teinte ; `dit` : ce que le lecteur d'écran en lit. */
@Composable
private fun LigneGeste(
    libelle: String,
    icone: ImageVector,
    teinte: Color,
    repere: String,
    actif: Boolean,
    dit: String? = null,
    toucher: () -> Unit,
) {
    val lu = dit ?: libelle
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Mise.cible)
            .then(if (actif) Modifier.cliquable(lu, toucher) else Modifier)
            .semantics { if (dit != null) contentDescription = dit }
            .testTag(repere),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = teinte)
        Text(libelle, style = MaterialTheme.typography.bodyLarge, color = teinte)
    }
}
