// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.app.commun.miseenpage.rangeeChoisie
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.pieces.PhotoProtegee
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant

/**
 * L'onglet « Fiche », comme l'iOS (`FicheView`) : la résidence, ses règles, ses plans, son
 * règlement, ses comptes-rendus d'AG et ses diagnostics — dans l'ordre du site. En lecture :
 * la fiche, les documents et les rapports se déposent et se corrigent sur le site.
 */
@Composable
fun OngletFiche(
    contenu: ContenuFiche,
    session: SessionOuverte?,
    locataire: Boolean,
    ouvrir: OuvrirFiche,
) {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        RubriqueFiche.ouvertes(locataire).forEach { RubriqueDeLaFiche(it, contenu, session, ouvrir) }
    }
}

/**
 * Une rubrique de la Fiche : dans l'onglet, toutes à la suite ; dans le carnet du pliable, celle
 * que le sommaire ouvre en face de lui (#287). Écrite une fois pour les deux.
 */
@Composable
fun RubriqueDeLaFiche(rubrique: RubriqueFiche, contenu: ContenuFiche, session: SessionOuverte?, ouvrir: OuvrirFiche) {
    when (rubrique) {
        RubriqueFiche.Identite -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BanniereResidence(contenu.fiche, session)
            CarteFiche(contenu, ouvrir.syndic)
        }
        RubriqueFiche.Regles -> RubriqueRegles(contenu, ouvrir.document)
        RubriqueFiche.Plans ->
            RubriqueDocuments(R.string.residence_plans, R.string.residence_plans_vide, contenu.plans, contenu, Nature.Plan, ouvrir.document)
        RubriqueFiche.Reglement ->
            RubriqueDocuments(R.string.residence_reglement, R.string.residence_reglement_vide, contenu.reglements, contenu, Nature.Reglement, ouvrir.document)
        RubriqueFiche.ComptesRendus -> RubriqueDocuments(
            R.string.residence_comptes_rendus, R.string.residence_comptes_rendus_vide, contenu.comptesRendus, contenu,
            Nature.Assemblee, ouvrir.document,
        )
        RubriqueFiche.Diagnostics -> RubriqueDiagnostics(contenu, ouvrir)
    }
}

/** Ce que la Fiche ouvre : un document, un rapport (celui qui s'ouvre tourne), l'annuaire du syndic. */
class OuvrirFiche(
    val document: (DocumentResidence) -> Unit = {},
    val rapport: (RapportDiagnostic) -> Unit = {},
    val rapportEnCours: Int? = null,
    val syndic: () -> Unit = {},
)

/** La photo de la résidence, au format du site (16/5), et son nom en légende. */
@Composable
private fun BanniereResidence(fiche: FicheResidence, session: SessionOuverte?) {
    val couleurs = LocalCouleurs.current
    val dite = stringResource(R.string.residence_la_residence)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 5f).clip(RoundedCornerShape(Mise.rayon))
                .semantics { contentDescription = dite },
            contentAlignment = Alignment.Center,
        ) {
            val photo = fiche.photo
            if (photo != null && session != null) {
                PhotoProtegee(photo, session, Modifier.fillMaxSize())
            } else {
                Row(
                    Modifier.fillMaxSize().background(couleurs.neutreFond),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Apartment, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.residence_aucune_photo), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
                }
            }
        }
        Text(fiche.nom, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = couleurs.textMuted)
    }
}

/** L'identité : ce qu'elle EST (immatriculation, lots), ce qu'elle a CONTRACTÉ (assurance, syndic), sa composition. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarteFiche(contenu: ContenuFiche, ouvrirSyndic: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val fiche = contenu.fiche
    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (fiche.adresse.isNotEmpty()) Champ(stringResource(R.string.residence_adresse)) { Valeur(fiche.adresse) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val cellule = Modifier.widthIn(min = 140.dp)
                fiche.immatriculation?.let {
                    Champ(stringResource(R.string.residence_immatriculation), cellule) {
                        Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = couleurs.primary, maxLines = 1)
                    }
                }
                fiche.lotsPrincipaux?.let { Champ(stringResource(R.string.residence_lots_principaux), cellule) { Valeur(FormatsDuSite.nombre(it)) } }
                fiche.lotsTotal?.let { Champ(stringResource(R.string.residence_lots_total), cellule) { Valeur(FormatsDuSite.nombre(it)) } }
            }
            if (fiche.assurance != null || fiche.syndic != null) {
                HorizontalDivider(color = couleurs.border)
                Contrats(fiche, ouvrirSyndic)
            }
            if (contenu.batiments.isNotEmpty()) {
                HorizontalDivider(color = couleurs.border)
                CompositionBatiments(contenu)
            }
        }
    }
}

@Composable
private fun Contrats(fiche: FicheResidence, ouvrirSyndic: () -> Unit) {
    val couleurs = LocalCouleurs.current
    fiche.assurance?.let { assurance ->
        Champ(stringResource(R.string.residence_assurance)) {
            Valeur(assurance + echeance(fiche.assuranceEcheance, fiche.assuranceReconduit))
        }
    }
    fiche.syndic?.let { syndic ->
        // Le lien mène à l'annuaire, qui porte les PERSONNES : la fiche ne les recopie pas.
        val mandat = mandat(fiche.syndicEcheance, fiche.syndicEchu)
        val texte = buildAnnotatedString {
            withStyle(SpanStyle(color = couleurs.primary, textDecoration = TextDecoration.Underline)) { append(syndic) }
            withStyle(SpanStyle(color = if (fiche.syndicEchu) couleurs.danger else couleurs.text, fontWeight = if (fiche.syndicEchu) FontWeight.SemiBold else null)) {
                append(mandat)
            }
        }
        Champ(
            stringResource(R.string.residence_syndic),
            Modifier.heightIn(min = Mise.cible).rangeeChoisie(NATURE_SYNDIC, NATURE_SYNDIC)
                .cliquable(stringResource(R.string.residence_syndic_aide), ouvrirSyndic)
                .testTag(ReperesResidence.SYNDIC),
        ) {
            Text(texte, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

/** « — échéance 12/03/2027 (reconduit tacitement) » ; vide sans échéance. */
@Composable
private fun echeance(date: Instant?, reconduit: Boolean): String {
    date ?: return ""
    return stringResource(R.string.residence_echeance, FormatsDuSite.dateCourte(date)) +
        (if (reconduit) stringResource(R.string.residence_reconduit) else "")
}

/** « — mandat jusqu'au … » ou « — mandat échu depuis le … » : un mandat ne se reconduit pas. */
@Composable
private fun mandat(date: Instant?, echu: Boolean): String {
    date ?: return ""
    val jour = FormatsDuSite.dateCourte(date)
    return stringResource(if (echu) R.string.residence_mandat_echu else R.string.residence_mandat_jusqu_au, jour)
}

/** Un champ de la fiche : son libellé en capitales, sa valeur dessous, lus ensemble. */
@Composable
private fun Champ(libelle: String, modifier: Modifier = Modifier, valeur: @Composable () -> Unit) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Libelle(libelle)
        valeur()
    }
}

@Composable
private fun Valeur(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = LocalCouleurs.current.text)
}

/** La composition par bâtiment, en tableau ; « — » pour zéro, comme le tableau du site. */
@Composable
private fun CompositionBatiments(contenu: ContenuFiche) {
    val couleurs = LocalCouleurs.current
    val colonnes = listOf(
        R.string.residence_parkings, R.string.residence_caves, R.string.residence_appartements, R.string.residence_commerces,
    ).map { stringResource(it) }
    val communs = stringResource(R.string.residence_communs)
    val lignes = contenu.composition.map { (numero, valeurs) ->
        (numero?.let { stringResource(R.string.residence_bat, it) } ?: communs) to valeurs
    }
    fun valeur(n: Int) = if (n > 0) FormatsDuSite.nombre(n) else FormatsDuSite.TIRET
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Libelle(stringResource(R.string.residence_composition))
        LigneTableau(stringResource(R.string.residence_batiment), colonnes, entete = true)
        lignes.forEach { (nom, valeurs) -> LigneTableau(nom, valeurs.map(::valeur)) }
        HorizontalDivider(color = couleurs.border)
        LigneTableau(stringResource(R.string.residence_total), contenu.totaux.map(::valeur), total = true)
    }
}

@Composable
private fun LigneTableau(nom: String, valeurs: List<String>, entete: Boolean = false, total: Boolean = false) {
    val couleurs = LocalCouleurs.current
    val style = if (entete) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    val poids = when {
        entete || total -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    val couleur = if (entete) couleurs.textMuted else couleurs.text
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(nom, Modifier.weight(1.1f), style = style, fontWeight = if (entete) poids else FontWeight.Medium, color = couleur)
        valeurs.forEach { Text(it, Modifier.weight(1f), style = style, fontWeight = poids, color = couleur, textAlign = TextAlign.End) }
    }
}
