// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.AnnonceRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.LibelleTeinte
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.texte.TexteDuSite

/**
 * Les petites annonces, comme l'iOS (`OngletAnnonces`) : un filtre par type, une catégorie
 * et un tri en menu, puis les annonces ; un toucher ouvre l'annonce (photos, contact,
 * réponses) ; « + » en dépose une.
 */
@Composable
fun OngletAnnonces(contexte: ContexteOnglet, service: ServiceCommunaute) {
    var nouvelle by rememberSaveable { mutableStateOf(false) }
    var relecture by rememberSaveable { mutableIntStateOf(0) }
    BoutonCreer(contexte.barre, R.string.annonces_deposer) { nouvelle = true }
    Chargement(
        charger = { service.annonces() },
        cle = contexte.actualisation to relecture,
        squelette = { LigneAnnonce(SQUELETTE) {} },
    ) { annonces, recharger ->
        Annonces(annonces, contexte, service, recharger)
    }
    if (nouvelle) {
        FormulaireAnnonce(contexte.avis, service, fermer = { nouvelle = false }) { relecture++ }
    }
}

@Composable
private fun Annonces(annonces: List<AnnonceRead>, contexte: ContexteOnglet, service: ServiceCommunaute, recharger: () -> Unit) {
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var categorie by rememberSaveable { mutableStateOf<String?>(null) }
    var tri by rememberSaveable { mutableStateOf(ReglesCommunaute.triParDefaut) }
    var ouverte by rememberSaveable { mutableStateOf<Int?>(null) }
    val retenues = ReglesCommunaute.annonces(annonces, type, categorie, tri)
    val textes = TextesListe(
        R.string.annonces_vide, R.string.annonces_vide_detail,
        R.string.annonces_en_cours_vide, R.string.annonces_en_cours_vide_detail, Icons.Outlined.Sell,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        RangeeFiltres {
            Pastille(stringResource(R.string.annonces_tous), type == null) { type = null }
            TablesCommunaute.typesAnnonce.forEach { (code, libelle) ->
                Pastille(libelle, type == code) { type = code }
            }
        }
        // Côte à côte quand ils tiennent, l'un sous l'autre aux grandes tailles.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val toutes = stringResource(R.string.annonces_toutes_categories)
            ChoixDansUnMenu(
                categorie?.let { ReglesCommunaute.libelle(it, TablesCommunaute.categoriesAnnonce) } ?: toutes,
                TablesCommunaute.categoriesAnnonce,
                aucun = toutes,
                icone = Icons.Outlined.FilterList,
            ) { categorie = it }
            ChoixDansUnMenu(
                ReglesCommunaute.libelle(tri, TablesCommunaute.trisAnnonce),
                TablesCommunaute.trisAnnonce,
                aucun = null,
                icone = Icons.AutoMirrored.Filled.Sort,
            ) { code -> if (code != null) tri = code }
        }
        ListeCommunaute(retenues, { it.archivee == true }, textes, videAuServeur = annonces.isEmpty()) { annonce ->
            LigneAnnonce(annonce) { ouverte = annonce.id }
        }
    }
    ouverte?.let { id ->
        DetailAnnonce(annonces.firstOrNull { it.id == id }, contexte, service, recharger) { ouverte = null }
    }
}

/** Une annonce : le type, le titre, son statut, sa catégorie, le prix, l'auteur. */
@Composable
private fun LigneAnnonce(annonce: AnnonceRead, ouvrir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    Carte(Modifier.testTag(ReperesCommunaute.annonce(annonce.id)), ouvrir = ouvrir, action = annonce.titre) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TypeAnnonce(annonce)
            Text(annonce.titre, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
            EtiquettesAnnonce(annonce)
            Text(
                TexteDuSite.apercu(annonce.description),
                style = MaterialTheme.typography.bodyMedium,
                color = couleurs.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TypeAnnonce(annonce: AnnonceRead) {
    Text(
        ReglesCommunaute.libelle(annonce.typeAnnonce.value, TablesCommunaute.typesAnnonce),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = LocalCouleurs.current.textMuted,
    )
}

/** Le statut, la catégorie, le prix (« Négociable ») et l'auteur — dans l'ordre du site. */
@Composable
fun EtiquettesAnnonce(annonce: AnnonceRead) {
    val couleurs = LocalCouleurs.current
    FlowCapsules {
        PastilleTeinte(ReglesCommunaute.teinte(annonce.statut.value, TablesCommunaute.statutsAnnonce), compacte = true)
        PastilleTeinte(LibelleTeinte.brut(ReglesCommunaute.libelle(annonce.categorie.value, TablesCommunaute.categoriesAnnonce)), compacte = true)
        annonce.prix?.let { prix ->
            Text(
                FormatsDuSite.montant(prix.toDouble()),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = couleurs.text,
            )
            if (annonce.negotiable == true) Etiquette(stringResource(R.string.annonces_negociable))
        }
        auteurAnnonce(annonce)?.let { Etiquette(it) }
    }
}

/** L'auteur tel que le serveur l'affiche ; rien s'il n'est pas servi. */
fun auteurAnnonce(annonce: AnnonceRead): String? =
    FormatsDuSite.nom(annonce.auteurAffiche, annonce.auteurPrenom, annonce.auteurNom)
        .takeIf { annonce.auteurAffiche.orEmpty().isNotBlank() || annonce.auteurPrenom.orEmpty().isNotBlank() || annonce.auteurNom.orEmpty().isNotBlank() }

private val SQUELETTE = AnnonceRead(
    id = 0,
    titre = "Une annonce de la résidence",
    description = "<p>Elle se charge.</p>",
    typeAnnonce = fr.coprofirst.api.modeles.TypeAnnonce.VENTE,
    categorie = fr.coprofirst.api.modeles.CategorieAnnonce.DIVERS,
    statut = fr.coprofirst.api.modeles.StatutAnnonce.EN_COURS,
    auteurId = 0,
    creeLe = java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    perimetreLibelle = "",
)
