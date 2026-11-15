// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.pieces.FormatPieces
import fr.coprofirst.app.commun.pieces.PiecesJointes
import fr.coprofirst.app.commun.pieces.PiecesJointesVue
import fr.coprofirst.app.commun.texte.TexteRiche
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant

/**
 * La fiche d'une affaire — et d'une actualité, qui en est une —, comme l'iOS
 * (`DetailAffaireView`) : l'en-tête, le fil de l'affaire (la demande, la suite, les comptes
 * rendus, la synthèse) et, sauf sur une actualité, le fil des messages.
 *
 * ⚠️ Pour l'heure en lecture : les gestes du bandeau de l'iOS (Répondre, Suite, Modifier,
 * options, archiver, lien) arrivent avec la PR suivante de #16 ; « Qui la lit » se tait
 * jusqu'au composant commun (#70).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FicheAffaire(id: Int, service: ServiceAffaires, session: SessionOuverte, titrePage: String, retour: () -> Unit) {
    Chargement(
        charger = { service.affaire(id) },
        cle = id,
        squelette = { ContenuAffaire(SQUELETTE, session, titrePage, retour) },
    ) { affaire, recharger ->
        PullToRefreshBox(isRefreshing = false, onRefresh = recharger, modifier = Modifier.fillMaxSize()) {
            ContenuAffaire(affaire, session, titrePage, retour)
        }
    }
}

@Composable
private fun ContenuAffaire(affaire: DetailAffaire, session: SessionOuverte, titrePage: String, retour: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = Mise.marge), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            TextButton(onClick = retour, modifier = Modifier.testTag(ReperesAffaires.RETOUR)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.affaire_retour, titrePage))
            }
            EnTeteAffaire(affaire)
            FilAffaire(affaire, session)
            // Pas de messages sur une actualité : elle informe, on n'y répond pas.
            if (!affaire.estActualite) FilMessages(affaire.messages, session)
        }
    }
}

@Composable
private fun EnTeteAffaire(affaire: DetailAffaire) {
    val c = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (affaire.estActualite) R.string.affaire_actualite else R.string.affaire_affaire),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = if (affaire.estActualite) c.primary else c.filRouge,
            )
            if (affaire.urgente) Marque(Icons.Filled.Warning, stringResource(R.string.affaires_urgente), c.danger)
            if (affaire.epingle) Marque(Icons.Filled.PushPin, null, c.primary, stringResource(R.string.affaire_epinglee))
            if (affaire.archivee) Marque(Icons.Outlined.Archive, stringResource(R.string.affaire_archivee), c.textMuted)
            Spacer(Modifier.weight(1f))
            affaire.etat?.let { PastilleStatut(it.code, it.libelle) }
        }
        // Le numéro : la barre du haut garde le titre de la page (divergence déclarée dans EcranAffaires).
        Text(affaire.numero, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = c.textMuted)
        Text(affaire.titre, Modifier.semantics { heading() }, style = Typo.titre(MaterialTheme.typography.headlineSmall), color = c.text)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            affaire.lieu?.let { Lieu(it) }
            Meta(Icons.Outlined.CalendarMonth, FormatsDuSite.dateHeure(affaire.creeLe))
            affaire.auteur?.let { Meta(Icons.Outlined.Person, it) }
            // Une ligne par prestataire, le rythme après le dernier.
            affaire.intervenants.takeIf { it.isNotEmpty() }?.let { Meta(Icons.Outlined.Engineering, it.joinToString("\n")) }
        }
    }
}

@Composable
private fun Marque(icone: ImageVector, texte: String?, teinte: Color, vocal: String? = null) {
    Row(
        Modifier.then(if (vocal != null) Modifier.semantics { contentDescription = vocal } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = teinte, modifier = Modifier.size(14.dp))
        texte?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = teinte) }
    }
}

@Composable
private fun Meta(icone: ImageVector, texte: String) {
    val c = LocalCouleurs.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icone, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp))
        Text(texte, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

/**
 * Le fil des MESSAGES, comme l'iOS (`FilMessagesAffaire`) — ce n'est pas la suite : deux
 * tables, deux fils. Une bulle à droite et teintée pour le compte (`est_auteur` servi), à
 * gauche pour les autres ; une note interne, en jaune, porte sa pastille.
 */
@Composable
private fun FilMessages(messages: List<MessageAffaire>, session: SessionOuverte) {
    if (messages.isEmpty()) return
    val c = LocalCouleurs.current
    val vous = stringResource(R.string.affaire_vous)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.affaire_messages), Modifier.semantics { heading() }, style = Typo.libelle(), color = c.textMuted)
        messages.forEach { m ->
            val forme = RoundedCornerShape(Mise.rayon)
            val fond = when {
                m.interne -> c.jauneFond
                m.deMoi -> c.infoFond
                else -> c.surface
            }
            val bord = when {
                m.interne -> c.warningBordure
                m.deMoi -> c.infoBordure
                else -> c.border
            }
            Row(Modifier.fillMaxWidth()) {
                if (m.deMoi) Spacer(Modifier.size(32.dp))
                Column(
                    Modifier.weight(1f).background(fond, forme).border(1.dp, bord, forme).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.semantics(mergeDescendants = true) {}.testTag(ReperesAffaires.message(m.id)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            (if (m.deMoi) vous else m.auteurServi)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text)
                            }
                            if (m.interne) PastilleTeinte(stringResource(R.string.affaire_interne), "badge-yellow", compacte = true)
                            Spacer(Modifier.weight(1f))
                            Text(FormatsDuSite.dateHeure(m.date), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                        TexteRiche(m.contenu, couleur = c.text)
                    }
                    PiecesJointesVue(remember(m.pieces) { PiecesJointes(m.pieces) }, session, format = FormatPieces.Compact)
                }
                if (!m.deMoi) Spacer(Modifier.size(32.dp))
            }
        }
    }
}

/** La forme d'une affaire, le temps qu'elle arrive. */
private val SQUELETTE = DetailAffaire(
    id = 0, numero = "A-0000", titre = "Une affaire de la résidence qui se charge",
    description = "Le détail de l'affaire arrive. ".repeat(5), categorie = "", statut = "ouvert", statutLibelle = "Ouvert",
    creeLe = Instant.EPOCH, auteur = "Le conseil syndical",
)
