// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.annuaire.Annuaire
import fr.coprofirst.app.commun.annuaire.CarteContact
import fr.coprofirst.app.commun.annuaire.Coordonnees
import fr.coprofirst.app.commun.annuaire.Medaille
import fr.coprofirst.app.commun.annuaire.medaillonsDuConseil
import fr.coprofirst.app.commun.annuaire.medaillonsDuSyndic
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.Separateur
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.launch

/** Ce que les deux sections partagent : le service, la session (photos), et comment dire un geste. */
internal class GestesAnnuaire(
    val service: ServiceAnnuaireCS,
    val session: SessionOuverte,
    val echouer: (ErreurService) -> Unit,
    /** Une phrase d'avis ; `apresFeuille` : dite par une feuille, elle attend sa fermeture. */
    val dire: (String, apresFeuille: Boolean) -> Unit,
    val feuilleFermee: () -> Unit,
)

/** Ce qu'une section ouvre : son en-tête, une fiche (son rang ; `null` pour une neuve). */
private sealed interface Ouverte {
    data object Entete : Ouverte
    data class Fiche(val rang: Int?) : Ouverte
}

/** « Conseil Syndical » : l'AG et la communauté WhatsApp, puis une carte par membre. */
@Composable
internal fun SectionConseilCS(gestes: GestesAnnuaire, actualisation: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        TitreSection(R.string.annuaire_conseil)
        Chargement(charger = { gestes.service.conseil() }, cle = actualisation, squelette = { SqueletteSection() }) { lu, _ ->
            var conseil by remember(lu) { mutableStateOf(lu) }
            var ouverte by remember { mutableStateOf<Ouverte?>(null) }
            var aRetirer by remember { mutableStateOf<Int?>(null) }
            val portee = rememberCoroutineScope()
            val ressources = LocalResources.current
            val c = LocalCouleurs.current
            val site = gestes.session.installation.nom
            Column(Modifier.testTag(ReperesEspaceCS.ANNUAIRE_CONSEIL), verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
                EnteteFiche(ReperesEspaceCS.ANNUAIRE_CONSEIL_MODIFIER, { ouverte = Ouverte.Entete }) {
                    Text(texteAg(conseil.agAnnee, conseil.agDate), style = MaterialTheme.typography.bodyMedium, color = c.text)
                    LienSortant(stringResource(R.string.annuaire_whatsapp), conseil.whatsappUrl)
                }
                if (conseil.membres.isEmpty()) {
                    Text(stringResource(R.string.annuaire_aucun_membre), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
                conseil.membres.forEachIndexed { rang, membre ->
                    val logement = listOfNotNull(membre.batimentNom?.let { stringResource(R.string.annuaire_bat, it) }, membre.etageLibelle)
                    FicheAnnuaireCS(
                        membre.enCarte, medaillonsDuConseil(membre.enCarte, site), false, gestes.session, ReperesEspaceCS.annuaireConseil(rang),
                        listOf(
                            GesteFiche(R.string.espacecs_modifier, Icons.Outlined.Edit) { ouverte = Ouverte.Fiche(rang) },
                            GesteFiche(R.string.espacecs_supprimer, Icons.Outlined.Delete, destructif = true) { aRetirer = rang },
                        ),
                    ) {
                        if (logement.isNotEmpty()) {
                            Text(logement.joinToString(Separateur.point), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        }
                        if (membre.userId != null) InscritLie()
                    }
                }
                BoutonAjout(R.string.espacecs_annuaire_nouveau_membre_cs, ReperesEspaceCS.ANNUAIRE_CONSEIL_AJOUTER) { ouverte = Ouverte.Fiche(null) }
            }

            /** Enregistrer la composition ENTIÈRE ; l'écran montre ce que le serveur a retenu. */
            suspend fun enregistrer(nouveau: ConseilCS): ConseilCS = gestes.service.enregistrer(nouveau).also { conseil = it }
            val fermer = {
                ouverte = null
                gestes.feuilleFermee()
            }
            when (val o = ouverte) {
                Ouverte.Entete -> FeuilleEnteteConseil(conseil, fermer) { nouveau ->
                    enregistrer(nouveau)
                    gestes.dire(ressources.getString(R.string.espacecs_annuaire_conseil_enregistre), true)
                }
                is Ouverte.Fiche -> FeuilleMembreConseil(
                    o.rang?.let { conseil.membres[it] } ?: MembreConseil(),
                    rapprocher = { gestes.service.rapprochement(it, localiser = true) },
                    session = gestes.session,
                    fermer = fermer,
                ) { membre ->
                    val membres = conseil.membres.toMutableList().apply { if (o.rang != null) set(o.rang, membre) else add(membre) }
                    val retenu = retenue(enregistrer(conseil.copy(membres = membres)).membres, o.rang) ?: membre
                    gestes.dire(ressources.getString(R.string.espacecs_annuaire_enregistre, retenu.nomAffiche), true)
                }
                null -> Unit
            }
            aRetirer?.let { rang ->
                ConfirmationRetrait({ aRetirer = null }) {
                    portee.launch {
                        try {
                            enregistrer(conseil.copy(membres = conseil.membres.filterIndexed { i, _ -> i != rang }))
                            gestes.dire(ressources.getString(R.string.espacecs_annuaire_conseil_enregistre), false)
                        } catch (e: ErreurService) {
                            gestes.echouer(e)
                        }
                    }
                }
            }
        }
    }
}

/** « Syndic » : le cabinet, son adresse et son espace client, puis ses interlocuteurs dans l'ordre choisi. */
@Composable
internal fun SectionSyndicCS(gestes: GestesAnnuaire, actualisation: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        TitreSection(R.string.annuaire_syndic)
        Chargement(charger = { gestes.service.syndic() }, cle = actualisation, squelette = { SqueletteSection() }) { lu, _ ->
            var syndic by remember(lu) { mutableStateOf(lu) }
            var ouverte by remember { mutableStateOf<Ouverte?>(null) }
            var aRetirer by remember { mutableStateOf<Int?>(null) }
            val portee = rememberCoroutineScope()
            val ressources = LocalResources.current
            val c = LocalCouleurs.current

            suspend fun enregistrer(nouveau: SyndicCS): SyndicCS = gestes.service.enregistrer(nouveau).also { syndic = it }
            fun geste(nouveau: SyndicCS) = portee.launch {
                try {
                    enregistrer(nouveau)
                    gestes.dire(ressources.getString(R.string.espacecs_annuaire_syndic_enregistre), false)
                } catch (e: ErreurService) {
                    gestes.echouer(e)
                }
            }
            Column(Modifier.testTag(ReperesEspaceCS.ANNUAIRE_SYNDIC), verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
                EnteteFiche(ReperesEspaceCS.ANNUAIRE_SYNDIC_MODIFIER, { ouverte = Ouverte.Entete }) {
                    Text(
                        syndic.nomSyndic ?: stringResource(R.string.espacecs_annuaire_nom_syndic_absent),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text,
                    )
                    syndic.adresse?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textMuted) }
                    LienSortant(stringResource(R.string.annuaire_espace_client), syndic.siteWeb)
                }
                if (syndic.membres.isEmpty()) {
                    Text(stringResource(R.string.annuaire_aucun_contact), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
                val total = syndic.membres.size
                syndic.membres.forEachIndexed { rang, membre ->
                    val gestesFiche = buildList {
                        add(GesteFiche(R.string.espacecs_modifier, Icons.Outlined.Edit) { ouverte = Ouverte.Fiche(rang) })
                        if (rang > 0) add(GesteFiche(R.string.espacecs_annuaire_monter, Icons.Outlined.ArrowUpward) { geste(syndic.deplace(rang, -1)) })
                        if (rang < total - 1) add(GesteFiche(R.string.espacecs_annuaire_descendre, Icons.Outlined.ArrowDownward) { geste(syndic.deplace(rang, 1)) })
                        add(GesteFiche(R.string.espacecs_supprimer, Icons.Outlined.Delete, destructif = true) { aRetirer = rang })
                    }
                    FicheAnnuaireCS(
                        membre.enCarte, medaillonsDuSyndic(membre.enCarte), membre.estPrincipal, gestes.session,
                        ReperesEspaceCS.annuaireSyndic(rang), gestesFiche,
                    ) {
                        membre.fonction?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textMuted) }
                        Coordonnees(membre.enCarte)
                    }
                }
                BoutonAjout(R.string.espacecs_annuaire_nouveau_membre_syndic, ReperesEspaceCS.ANNUAIRE_SYNDIC_AJOUTER) { ouverte = Ouverte.Fiche(null) }
            }
            val fermer = {
                ouverte = null
                gestes.feuilleFermee()
            }
            when (val o = ouverte) {
                Ouverte.Entete -> FeuilleEnteteSyndic(syndic, fermer) { nouveau ->
                    enregistrer(nouveau)
                    gestes.dire(ressources.getString(R.string.espacecs_annuaire_syndic_enregistre), true)
                }
                is Ouverte.Fiche -> FeuilleMembreSyndic(
                    o.rang?.let { syndic.membres[it] } ?: MembreSyndic(),
                    rapprocher = { gestes.service.rapprochement(it, localiser = false) },
                    session = gestes.session,
                    fermer = fermer,
                ) { membre ->
                    val membres = syndic.membres.toMutableList().apply { if (o.rang != null) set(o.rang, membre) else add(membre) }
                    val retenu = retenue(enregistrer(syndic.copy(membres = membres)).membres, o.rang) ?: membre
                    gestes.dire(ressources.getString(R.string.espacecs_annuaire_enregistre, retenu.nomAffiche), true)
                }
                null -> Unit
            }
            aRetirer?.let { rang ->
                ConfirmationRetrait({ aRetirer = null }) { geste(syndic.copy(membres = syndic.membres.filterIndexed { i, _ -> i != rang })) }
            }
        }
    }
}

/**
 * La fiche telle que le serveur l'a enregistrée — avec son nom affiché SERVI : la même
 * place, ou la dernière pour une fiche ajoutée ; `null` si elle n'y est pas.
 */
internal fun <M> retenue(membres: List<M>, rang: Int?): M? = membres.getOrNull(rang ?: membres.lastIndex)

/** « AG 2026 · 12/06/2026 », ou ce qui manque. */
@Composable
private fun texteAg(annee: Int?, date: String?): String {
    val debut = annee?.let { stringResource(R.string.espacecs_annuaire_ag, it.toString()) }
        ?: stringResource(R.string.espacecs_annuaire_ag_absente)
    return FormatsDuSite.lire(date)?.let { "$debut${Separateur.point}${FormatsDuSite.dateCourte(it)}" } ?: debut
}

/** Un geste du menu d'une fiche. */
internal class GesteFiche(val libelle: Int, val icone: ImageVector, val destructif: Boolean = false, val faire: () -> Unit)

/** Une fiche : la carte de l'annuaire, et ses gestes dans un menu « … ». */
@Composable
private fun FicheAnnuaireCS(
    personne: Annuaire.Membre,
    medaillons: List<Medaille>,
    principal: Boolean,
    session: SessionOuverte,
    repere: String,
    gestes: List<GesteFiche>,
    detail: @Composable () -> Unit,
) {
    var deplie by remember { mutableStateOf(false) }
    val c = LocalCouleurs.current
    Box(Modifier.testTag(repere)) {
        CarteContact(personne, medaillons, session, principal) { detail() }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { deplie = true }, modifier = Modifier.testTag("$repere.gestes")) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(R.string.espacecs_annuaire_gestes, personne.nomComplet), tint = c.primary)
            }
            DropdownMenu(expanded = deplie, onDismissRequest = { deplie = false }) {
                gestes.forEach { g ->
                    val teinte = if (g.destructif) c.danger else c.text
                    DropdownMenuItem(
                        text = { Text(stringResource(g.libelle), color = teinte) },
                        leadingIcon = { Icon(g.icone, contentDescription = null, tint = teinte) },
                        onClick = {
                            deplie = false
                            g.faire()
                        },
                        modifier = Modifier.testTag("$repere.${g.libelle.repereGeste()}"),
                    )
                }
            }
        }
    }
}

/** Le repère d'un geste du menu, comme ceux de l'iOS. */
private fun Int.repereGeste(): String = when (this) {
    R.string.espacecs_modifier -> "modifier"
    R.string.espacecs_annuaire_monter -> "monter"
    R.string.espacecs_annuaire_descendre -> "descendre"
    else -> "supprimer"
}
