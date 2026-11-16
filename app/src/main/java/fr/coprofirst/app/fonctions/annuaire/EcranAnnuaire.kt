// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.annuaire

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.annuaire.Annuaire
import fr.coprofirst.app.commun.annuaire.CarteContact
import fr.coprofirst.app.commun.annuaire.Coordonnees
import fr.coprofirst.app.commun.annuaire.medaillonsDuConseil
import fr.coprofirst.app.commun.annuaire.medaillonsDuSyndic
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.session.SessionOuverte


/** Où l'annuaire s'ouvre : en tête, ou sur le syndic (« Syndic » de la Fiche). */
enum class AncreAnnuaire { Syndic }

/** L'Annuaire (page `annuaire`, #17) : l'écran du registre. */
@Composable
fun EcranAnnuaire(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceAnnuaireApi(contexte.session) }
    EcranAnnuaire(service, contexte.session)
}

/**
 * L'annuaire, comme l'iOS (`AnnuaireView`) : les membres du conseil syndical, puis le
 * syndic et ses contacts — un toucher appelle ou écrit. Ouvert depuis la Fiche, il se
 * place sur le syndic.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EcranAnnuaire(service: ServiceAnnuaire, session: SessionOuverte?, ancre: AncreAnnuaire? = null) {
    var relecture by rememberSaveable { mutableIntStateOf(0) }
    val syndic = remember { BringIntoViewRequester() }
    val site = session?.installation?.nom.orEmpty()
    PullToRefreshBox(isRefreshing = false, onRefresh = { relecture++ }, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Chargement(
                charger = { service.annuaire() },
                cle = relecture,
                squelette = { Contenu(APERCU, site, null, null) },
            ) { annuaire, _ ->
                Contenu(annuaire, site, session, syndic)
                LaunchedEffect(annuaire, ancre) { if (ancre == AncreAnnuaire.Syndic) syndic.bringIntoView() }
            }
        }
    }
}

@Composable
private fun Contenu(annuaire: Annuaire, site: String, session: SessionOuverte?, ancreSyndic: BringIntoViewRequester?) {
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = Mise.marge, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(stringResource(R.string.annuaire_descriptif), style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
        SectionConseil(annuaire, site, session)
        SectionSyndic(annuaire.syndic, session, ancreSyndic)
    }
}

/** « Conseil Syndical » : l'AG qui l'a élu, le groupe WhatsApp, puis les membres, par bâtiment s'il y en a plusieurs. */
@Composable
private fun SectionConseil(annuaire: Annuaire, site: String, session: SessionOuverte?) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EnTete(stringResource(R.string.annuaire_conseil))
        annuaire.cs.agAnnee?.let { annee ->
            val date = FormatsDuSite.lire(annuaire.cs.agDate)
            Text(
                if (date != null) stringResource(R.string.annuaire_vote_en_ag_le, annee.toString(), FormatsDuSite.dateLongue(date))
                else stringResource(R.string.annuaire_vote_en_ag, annee.toString()),
                style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted,
            )
        }
        annuaire.whatsappUrl?.let { LienExterne(stringResource(R.string.annuaire_whatsapp), Icons.AutoMirrored.Outlined.Chat, it) }
        val groupes = annuaire.groupesCS
        when {
            annuaire.cs.membres.isEmpty() -> Vide(stringResource(R.string.annuaire_aucun_membre))
            groupes.size > 1 -> groupes.forEach { (batiment, membres) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Apartment, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(14.dp))
                        Text(
                            (batiment?.let { stringResource(R.string.annuaire_batiment, it) } ?: stringResource(R.string.annuaire_sans_batiment)).uppercase(),
                            style = Typo.libelle(), color = couleurs.textMuted,
                        )
                    }
                    // Le bâtiment titre déjà le groupe : la carte ne le répète pas.
                    membres.forEach { CarteContact(it, medaillonsDuConseil(it, site), session) }
                }
            }
            else -> annuaire.cs.membres.forEach { membre ->
                CarteContact(membre, medaillonsDuConseil(membre, site), session) {
                    membre.batimentNom?.let { Text(stringResource(R.string.annuaire_bat, it), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted) }
                }
            }
        }
    }
}

/** « Syndic » : le cabinet, son espace client, puis ses contacts ; le gestionnaire principal est mis en avant. */
@Composable
private fun SectionSyndic(syndic: Annuaire.Syndic, session: SessionOuverte?, ancre: BringIntoViewRequester?) {
    val couleurs = LocalCouleurs.current
    Column(
        if (ancre != null) Modifier.bringIntoViewRequester(ancre) else Modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EnTete(stringResource(R.string.annuaire_syndic))
        syndic.nomSyndic?.let { nom ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(nom, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
                syndic.adresse?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted) }
            }
        }
        syndic.siteWeb?.let { LienExterne(stringResource(R.string.annuaire_espace_client), Icons.Outlined.Language, it) }
        if (syndic.membres.isEmpty()) Vide(stringResource(R.string.annuaire_aucun_contact))
        syndic.membres.forEach { personne ->
            CarteContact(personne, medaillonsDuSyndic(personne), session, principal = personne.estPrincipal) {
                personne.fonction?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted) }
                Coordonnees(personne)
            }
        }
    }
}

@Composable
private fun EnTete(titre: String) {
    Text(titre, style = Typo.titre(), color = LocalCouleurs.current.text, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Vide(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
}

/** Un lien qui quitte l'app (groupe WhatsApp, espace client du syndic) — une adresse sûre seulement. */
@Composable
private fun LienExterne(titre: String, icone: ImageVector, adresse: String) {
    val lien = TexteDuSite.lienSur(adresse) ?: return
    val couleurs = LocalCouleurs.current
    val liens = LocalUriHandler.current
    val forme = RoundedCornerShape(Mise.rayon)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).background(couleurs.surface, forme).border(1.dp, couleurs.border, forme)
            .cliquable(stringResource(R.string.annuaire_lien_externe)) { liens.openUri(lien) }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(18.dp))
        Text(titre, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = couleurs.primary)
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(16.dp))
    }
}

/** La place de l'annuaire pendant le chargement : des mots qui ont la forme du contenu. */
private val APERCU = Annuaire(
    cs = Annuaire.ConseilSyndical(membres = listOf(Annuaire.Membre(1, prenom = "Prénom", nom = "Nom"))),
    syndic = Annuaire.Syndic(nomSyndic = "Cabinet", membres = listOf(Annuaire.Membre(2, prenom = "Prénom", nom = "Nom"))),
)
