// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.app.commun.pieces.Ouverture
import fr.coprofirst.app.commun.pieces.documentChoisi
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.commun.texte.TexteRiche

/** « Règles & Recommandations » : une carte par règle ; un toucher la déplie — une seule ouverte à la fois. */
@Composable
fun RubriqueRegles(contenu: ContenuFiche, ouvrir: (DocumentResidence) -> Unit) {
    val couleurs = LocalCouleurs.current
    val haptique = LocalHapticFeedback.current
    var ouverte by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EnTeteRubrique(stringResource(R.string.residence_regles))
        if (contenu.regles.isEmpty()) RubriqueVide(stringResource(R.string.residence_regles_vide))
        contenu.regles.forEach { regle ->
            val annexes = contenu.annexesDeRegle(regle.id)
            val depliable = regle.contenu != null || annexes.isNotEmpty()
            val depliee = ouverte == regle.id
            val basculer = {
                haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
                ouverte = if (depliee) null else regle.id
            }
            CarteAGouttiere(
                Nature.Regle,
                modifier = Modifier.animateContentSize(),
                ouvrir = if (depliable) basculer else null,
                action = stringResource(if (depliee) R.string.residence_replier else R.string.residence_deplier),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(regle.titre, style = MaterialTheme.typography.titleMedium, color = couleurs.text, modifier = Modifier.weight(1f))
                    if (depliable) {
                        Icon(
                            Icons.Outlined.ExpandMore, contentDescription = null, tint = couleurs.textMuted,
                            modifier = Modifier.size(20.dp).rotate(if (depliee) 180f else 0f),
                        )
                    }
                }
                regle.contenu?.let { html ->
                    if (depliee) {
                        TexteRiche(html)
                    } else {
                        Text(TexteDuSite.apercu(html), maxLines = 3, overflow = TextOverflow.Ellipsis, color = couleurs.textMuted)
                    }
                }
                if (depliee) ListeAnnexes(annexes, ouvrir)
            }
        }
    }
}

/** Une rubrique de documents (plans, règlement, comptes-rendus d'AG) ; un toucher ouvre le document. */
@Composable
fun RubriqueDocuments(
    @StringRes titre: Int,
    @StringRes vide: Int,
    documents: List<DocumentResidence>,
    contenu: ContenuFiche,
    nature: Nature,
    ouvrir: (DocumentResidence) -> Unit,
) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EnTeteRubrique(stringResource(titre))
        if (documents.isEmpty()) RubriqueVide(stringResource(vide))
        documents.forEach { document ->
            // La date montrée est celle de l'acte, jamais celle du dépôt ; un plan n'en a pas.
            CarteAGouttiere(nature, Modifier.documentChoisi(Ouverture.cleDocument(document.id)), date = if (nature == Nature.Plan) null else document.date) {
                Column(
                    Modifier.fillMaxWidth().cliquable(stringResource(R.string.residence_ouvrir_document)) { ouvrir(document) },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(document.titre, style = MaterialTheme.typography.titleMedium, color = couleurs.text, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.DocumentScanner, contentDescription = null, tint = couleurs.primary)
                    }
                    document.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = couleurs.textMuted)
                    }
                    when (nature) {
                        Nature.Plan -> PastilleNeutre(libelleBatiment(contenu.numeroBatiment(document.batimentId)))
                        // Le périmètre d'un compte-rendu se compose dans le front du site : l'app
                        // attend que l'API le serve (coprofirst#2505), comme l'iOS.
                        Nature.Assemblee -> document.annee?.let { PastilleNeutre(it.toString()) }
                        else -> Unit
                    }
                }
                ListeAnnexes(contenu.annexesDeDocument(document.id), ouvrir)
            }
        }
    }
}

@Composable
private fun libelleBatiment(numero: NumeroBatiment): String = when (numero) {
    NumeroBatiment.Copropriete -> stringResource(R.string.residence_copropriete)
    NumeroBatiment.Inconnu -> stringResource(R.string.residence_bat_inconnu)
    is NumeroBatiment.Connu -> stringResource(R.string.residence_bat, numero.numero)
}

/**
 * « Diagnostics et Contrôles Réglementaires » : les types applicables et, sous chacun, ses
 * rapports — un toucher ouvre le rapport, « Synthèse » la déplie.
 */
@Composable
fun RubriqueDiagnostics(contenu: ContenuFiche, ouvrir: OuvrirFiche) {
    val couleurs = LocalCouleurs.current
    var synthese by rememberSaveable { mutableStateOf<Int?>(null) }
    val applicables = contenu.diagnosticsApplicables
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EnTeteRubrique(stringResource(R.string.residence_diagnostics))
        if (applicables.isEmpty()) RubriqueVide(stringResource(R.string.residence_diagnostics_vide))
        applicables.forEach { type ->
            CarteAGouttiere(Nature.Diagnostic, Modifier.animateContentSize(), date = type.rapports.mapNotNull { it.date }.maxOrNull()) {
                Text(type.nom, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
                Text(type.texteLegislatif, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
                type.frequence?.let { PastilleNeutre(it) }
                type.rapports.forEach { rapport ->
                    LigneRapport(rapport, ouvrir, synthese == rapport.id) { synthese = if (synthese == rapport.id) null else rapport.id }
                    ListeAnnexes(contenu.annexesDeRapport(rapport.id), ouvrir.document)
                }
            }
        }
    }
}

@Composable
private fun LigneRapport(rapport: RapportDiagnostic, ouvrir: OuvrirFiche, ouverte: Boolean, basculer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = couleurs.border)
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible)
                .documentChoisi(Ouverture.cleFichier(RAPPORTS, rapport.id.toString()))
                .cliquable(stringResource(R.string.residence_ouvrir_rapport)) { ouvrir.rapport(rapport) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(rapport.titre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = couleurs.text)
                rapport.date?.let { Text(FormatsDuSite.dateCourte(it), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted) }
            }
            if (ouvrir.rapportEnCours == rapport.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        val html = rapport.synthese ?: return@Column
        Row(
            Modifier.heightIn(min = Mise.cible).cliquable(stringResource(R.string.residence_synthese), basculer),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.residence_synthese), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = couleurs.primary)
            Icon(if (ouverte) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(ouverte) {
            Column(Modifier.fillMaxWidth().filetGauche(couleurs.primary).padding(start = 12.dp)) {
                TexteRiche(html)
            }
        }
    }
}

/** Le filet de 3 dp à gauche d'un texte cité (synthèse, extrait), comme le site. */
fun Modifier.filetGauche(couleur: Color): Modifier = drawBehind {
    drawRect(couleur, size = Size(3.dp.toPx(), size.height))
}
