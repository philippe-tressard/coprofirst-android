// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.pieces.AvisOuverture
import fr.coprofirst.app.commun.pieces.LigneDocument
import fr.coprofirst.app.commun.pieces.Ouverture
import fr.coprofirst.app.commun.pieces.rememberOuverture
import fr.coprofirst.app.commun.texte.TexteRiche
import fr.coprofirst.app.fonctions.monlot.Intertitre
import fr.coprofirst.app.fonctions.monlot.LigneFiche
import fr.coprofirst.app.fonctions.monlot.NoteMonLot
import fr.coprofirst.app.session.ErreurService

/**
 * L'onglet « Contrats » (`OngletContrats.svelte`), comme l'iOS : un résumé, les contrats
 * groupés par équipement et rangés par échéance, la résiliation et le remplacement servis,
 * les archives repliées en bas.
 */
@Composable
internal fun OngletContrats(donnees: DonneesPrestataires, service: ServicePrestataires, onglet: ContexteOnglet) {
    var deplie by rememberSaveable { mutableStateOf<Int?>(null) }
    var archives by remember(donnees) { mutableStateOf<List<Contrat>>(emptyList()) }
    LaunchedEffect(donnees) {
        archives = try {
            service.contrats(archives = true)
        } catch (_: ErreurService) {
            emptyList()
        }
    }
    val ouverture = rememberOuverture(onglet.session)
    val courants = donnees.contrats
    val carte: @Composable (Contrat) -> Unit = { c ->
        CarteContrat(c, donnees.prestataire(c.prestataireId), service, ouverture, deplie == c.id) {
            deplie = if (deplie == c.id) null else c.id
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        if (courants.isEmpty() && archives.isEmpty()) {
            EtatVide(stringResource(R.string.prestataires_aucun_contrat), icone = Icons.Outlined.Description)
            return@Column
        }
        Text(
            resume(courants),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = LocalCouleurs.current.text,
        )
        if (courants.isEmpty()) {
            NoteMonLot(stringResource(R.string.prestataires_contrats_archives_ranges), stringResource(R.string.prestataires_aucun_contrat_actif))
        }
        ReglesPrestataires.groupes(donnees).forEach { (code, contrats) ->
            Intertitre(donnees.equipement(code))
            contrats.forEach { carte(it) }
        }
        GroupeArchives(archives.size) { archives.forEach { carte(it) } }
        AvisOuverture(ouverture)
    }
}

/**
 * Le résumé en tête : actifs, sans échéance. Le site compte aussi les visites en retard et
 * à venir : une règle de son front, que l'app ne recopie pas (comme l'iOS).
 */
@Composable
private fun resume(contrats: List<Contrat>): String {
    val n = FormatsDuSite.nombre(contrats.size)
    val actifs = stringResource(if (contrats.size > 1) R.string.prestataires_contrats_actifs else R.string.prestataires_contrats_actifs_un, n)
    val sans = ReglesPrestataires.sansEcheance(contrats)
    return if (sans == 0) actifs else actifs + " · " + stringResource(R.string.prestataires_sans_echeance_compte, FormatsDuSite.nombre(sans))
}

/** La carte d'un contrat (`CarteContrat.svelte`) : ses capsules, puis, dépliée, son détail et ses documents. */
@Composable
private fun CarteContrat(
    contrat: Contrat,
    prestataire: Prestataire?,
    service: ServicePrestataires,
    ouverture: Ouverture,
    deplie: Boolean,
    basculer: () -> Unit,
) {
    var documents by remember(contrat.id) { mutableStateOf<List<DocumentContrat>?>(null) }
    LaunchedEffect(contrat.id) {
        documents = try {
            service.documents(contrat.id)
        } catch (_: ErreurService) {
            null
        }
    }
    val frequence = contrat.frequenceLibelle?.takeIf { it.isNotBlank() }
    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EnteteDepliable(contrat.libelle, deplie, ReperesPrestataires.contrat(contrat.id), basculer)
            FlowCapsules {
                if (prestataire != null) {
                    PastilleTeinte(prestataire.nom, "badge-gray", compacte = true)
                } else {
                    PastilleTeinte(stringResource(R.string.prestataires_sans_intervenant), "badge-orange", compacte = true)
                }
                contrat.numeroContrat?.takeIf { it.isNotBlank() }?.let { PastilleTeinte("🔖 $it", "badge-gray", compacte = true) }
                // 🔁 La résiliation et le remplacement servis, sur les DEUX cartes : l'ancien dit
                // par qui, le nouveau qui il remplace.
                pastillesRemplacement(contrat.remplacement).forEachIndexed { rang, libelle ->
                    PastilleTeinte(libelle, "badge-gray", Modifier.testTag(ReperesPrestataires.remplacement(contrat.id, rang)), compacte = true)
                }
                if (contrat.prochaineVisite == null && !contrat.remplacement.estDatee) {
                    PastilleTeinte(stringResource(R.string.prestataires_sans_echeance), "badge-gray", compacte = true)
                }
                frequence?.let { PastilleTeinte(it, "badge-gray", compacte = true) }
                documents?.takeIf { it.isNotEmpty() }?.let { PastilleTeinte("📄 ${FormatsDuSite.nombre(it.size)}", "badge-gray", compacte = true) }
            }
            Depliage(deplie) { DetailContrat(contrat, frequence, documents.orEmpty(), ouverture) }
        }
    }
}

/**
 * Les pastilles du remplacement, dans l'ordre du site (`CarteContrat.svelte`) : par qui et
 * dès quand il est remplacé — à défaut, sa résiliation —, puis ceux qu'il remplace.
 */
@Composable
private fun pastillesRemplacement(r: RemplacementContrat): List<String> {
    val lues = mutableListOf<String>()
    val par = r.remplacePar
    val resilieLe = r.resilieLe
    if (par != null) {
        lues += stringResource(R.string.prestataires_remplace_par_capsule, par.prestataireNom, FormatsDuSite.dateCourte(par.dateDebut))
    } else if (resilieLe != null) {
        val modele = if (r.resilie) R.string.prestataires_resilie_le_capsule else R.string.prestataires_resilie_au_capsule
        lues += stringResource(modele, FormatsDuSite.dateCourte(resilieLe))
    }
    r.remplace.forEach { ancien ->
        lues += ancien.resilieLe?.let {
            stringResource(R.string.prestataires_remplace_resilie_capsule, ancien.prestataireNom, FormatsDuSite.dateCourte(it))
        } ?: stringResource(R.string.prestataires_remplace_capsule, ancien.prestataireNom)
    }
    return lues
}

/** « Contrat — Prestataire ». */
@Composable
private fun lie(contrat: ContratLie): String = stringResource(R.string.prestataires_lie, contrat.libelle, contrat.prestataireNom)

@Composable
private fun DetailContrat(contrat: Contrat, frequence: String?, documents: List<DocumentContrat>, ouverture: Ouverture) {
    HorizontalDivider(color = LocalCouleurs.current.border)
    Libelle(stringResource(R.string.prestataires_le_contrat))
    LigneFiche(stringResource(R.string.prestataires_debut), "📅 ${FormatsDuSite.dateCourte(contrat.dateDebut)}")
    contrat.dureeValeur?.let {
        LigneFiche(stringResource(R.string.prestataires_duree_initiale), "${FormatsDuSite.nombre(it)} ${contrat.dureeUnite.orEmpty()}".trim())
    }
    frequence?.let { LigneFiche(stringResource(R.string.prestataires_frequence), it) }
    contrat.prochaineVisite?.let {
        LigneFiche(stringResource(R.string.prestataires_prochaine_visite), "🗓 ${FormatsDuSite.dateCourte(it)}")
    }
    contrat.remplacement.resilieLe?.let { LigneFiche(stringResource(R.string.prestataires_resilie_le), FormatsDuSite.dateCourte(it)) }
    contrat.remplacement.remplacePar?.let { LigneFiche(stringResource(R.string.prestataires_remplace_par), lie(it)) }
    contrat.remplacement.remplace.forEach { LigneFiche(stringResource(R.string.prestataires_remplace), lie(it)) }
    contrat.notes?.takeIf { it.isNotBlank() }?.let {
        Libelle(stringResource(R.string.prestataires_description), Modifier.padding(top = 4.dp))
        TexteRiche(it, style = MaterialTheme.typography.bodyMedium)
    }
    Libelle(stringResource(R.string.prestataires_documents, FormatsDuSite.nombre(documents.size)), Modifier.padding(top = 4.dp))
    if (documents.isEmpty()) {
        Text(stringResource(R.string.prestataires_aucun_document), style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
    }
    documents.forEach { d ->
        LigneDocument(d.fichierNom, ouverture.enCours == "document-${d.id}", taille = d.taille) { ouverture.document(d.id, d.fichierNom) }
    }
}
