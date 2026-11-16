// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.ChampRecherche
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Intertitre
import fr.coprofirst.app.commun.NoteEnCarte
import fr.coprofirst.app.session.ErreurService

/**
 * L'onglet « Prestataires » (`OngletPrestataires.svelte`), comme l'iOS : recherche,
 * catégorie, contrat ; les fiches groupées par catégorie, une seule dépliée à la fois ;
 * les archives repliées en bas.
 */
@Composable
internal fun OngletPrestataires(donnees: DonneesPrestataires, service: ServicePrestataires, gestes: GestesAvis) {
    var recherche by rememberSaveable { mutableStateOf("") }
    var categorie by rememberSaveable { mutableStateOf<String?>(null) }
    var contrat by rememberSaveable { mutableStateOf<String?>(null) }
    var deplie by rememberSaveable { mutableStateOf<Int?>(null) }
    var archives by remember(donnees) { mutableStateOf<List<Prestataire>>(emptyList()) }
    // Les archives ne bloquent pas : illisibles, elles ne s'affichent pas (comme l'iOS).
    LaunchedEffect(donnees) {
        archives = try {
            service.prestataires(archives = true)
        } catch (_: ErreurService) {
            emptyList()
        }
    }
    val courants = ReglesPrestataires.filtrer(donnees.prestataires, donnees, categorie, contrat, recherche)
    val archivees = ReglesPrestataires.filtrer(archives, donnees, categorie, contrat, recherche)
    val filtre = recherche.isNotBlank() || categorie != null || contrat != null
    val compacte = courants.size > SEUIL_COMPACT
    val carte: @Composable (Prestataire, Boolean, Boolean) -> Unit = { p, compact, archive ->
        CartePrestataire(p, donnees, service, compact, deplie == p.id, archive, gestes) { deplie = if (deplie == p.id) null else p.id }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        ChampRecherche(recherche, { recherche = it }, stringResource(R.string.prestataires_recherche), ReperesPrestataires.RECHERCHE)
        FiltrePastilles(
            stringResource(R.string.prestataires_categorie),
            TablesPrestataires.types.map { it.code to it.libelle },
            categorie,
        ) { categorie = it }
        FiltrePastilles(stringResource(R.string.prestataires_contrat), TablesPrestataires.filtresContrat, contrat) { contrat = it }
        if (courants.isEmpty() && archivees.isEmpty()) {
            EtatVide(
                stringResource(if (filtre) R.string.prestataires_aucun_criteres else R.string.prestataires_aucun),
                icone = Icons.Outlined.Build,
            )
            return@Column
        }
        if (courants.isEmpty()) {
            NoteEnCarte(
                stringResource(R.string.prestataires_archives_rangees),
                stringResource(if (filtre) R.string.prestataires_aucun_actif_criteres else R.string.prestataires_aucun_actif),
            )
        }
        ReglesPrestataires.parCategorie(courants).forEach { (type, groupe) ->
            // L'en-tête de catégorie, sauf quand le filtre n'en retient qu'une.
            if (type != null && categorie == null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Intertitre(type.libelle)
                    Text(type.description, style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
                }
            }
            groupe.forEach { carte(it, compacte, false) }
        }
        GroupeArchives(archivees.size) { archivees.forEach { carte(it, true, true) } }
    }
}

/** Au-delà de sept fiches, les contacts ne s'affichent que dépliés (le site). */
private const val SEUIL_COMPACT = 7
