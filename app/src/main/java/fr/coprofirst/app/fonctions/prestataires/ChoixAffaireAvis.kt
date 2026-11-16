// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.ChampRecherche
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.texte.Separateur
import fr.coprofirst.app.fonctions.affaires.ReglesAffaires
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Les repères de la saisie d'un avis : ceux de l'iOS. */
object ReperesSaisieAvis {
    const val ENREGISTRER = "avis.enregistrer"
    const val CONFIRMER_AJOUT = "avis.confirmerAjout"
    const val LIBRE = "avis.libre"
    const val REFUS = "avis.refus"
    const val RECHERCHE = "avis.affaire.recherche"
    const val RETENUE = "avis.affaire.retenue"
    const val CHANGER = "avis.affaire.changer"
    const val CONFIRMER_RETRAIT = "avis.confirmerRetrait"
    fun etat(etat: String) = "avis.etat.$etat"
    fun affaire(id: Int) = "avis.affaire.$id"
    fun noter(prestataire: Int) = "prestataire.$prestataire.noter"
    fun noterContrat(contrat: Int) = "contrat.$contrat.noter"
    fun modifier(avis: Int) = "avis.$avis.modifier"
    fun retirer(avis: Int) = "avis.$avis.retirer"
}

/**
 * « Affaire concernée » (`ChoixAffaireAvis.svelte`), comme l'iOS : l'affaire retenue et
 * « Changer », ou la recherche et les deux groupes (`ChoixAffaire`). Une frappe filtre
 * aussitôt sur le numéro et le titre, puis, la frappe finie, demande la recherche détaillée
 * de la page Affaires ; une frappe neuve annule la précédente.
 *
 * ⚠️ Divergence déclarée : le seuil et le délai de frappe sont ceux du menu Affaires
 * (`ReglesAffaires`), importés de son paquet en attendant leur montée dans `commun/`.
 */
@Composable
internal fun ChoixAffaireAvis(
    affaires: List<AffaireANoter>,
    retenue: Int?,
    /** Le titre servi de l'affaire d'un avis qu'on corrige, tant que la liste ne la nomme pas. */
    titreServi: String?,
    prestataire: String,
    service: ServicePrestataires,
    choisir: (Int?) -> Unit,
) {
    var recherche by rememberSaveable { mutableStateOf("") }
    var trouvees by remember { mutableStateOf<List<Int>>(emptyList()) }
    var enCours by remember { mutableStateOf(false) }
    val terme = ChoixAffaire.terme(recherche)
    val active = terme.length >= ReglesAffaires.RECHERCHE_MINIMALE
    LaunchedEffect(terme) {
        trouvees = emptyList()
        if (terme.length < ReglesAffaires.RECHERCHE_MINIMALE) {
            enCours = false
            return@LaunchedEffect
        }
        enCours = true
        try {
            delay(ReglesAffaires.DELAI_DE_FRAPPE_MS)
            trouvees = service.rechercherAffaires(terme)
        } catch (e: CancellationException) {
            throw e
        } catch (_: ErreurService) {
            // La recherche détaillée échoue : le filtre sur le numéro et le titre reste.
        }
        enCours = false
    }
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Libelle(stringResource(R.string.notation_affaire_concernee), Modifier.semantics { heading() })
        if (retenue != null) {
            val choisie = affaires.firstOrNull { it.id == retenue }
            Carte {
                Row(Modifier.fillMaxWidth().testTag(ReperesSaisieAvis.RETENUE), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        choisie?.let { "${it.numero}${Separateur.tiret}${it.titre}" } ?: titreServi.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = couleurs.text,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { choisir(null) }, modifier = Modifier.testTag(ReperesSaisieAvis.CHANGER)) {
                        Text(stringResource(R.string.notation_changer))
                    }
                }
            }
            return@Column
        }
        ChampRecherche(recherche, { recherche = it }, stringResource(R.string.notation_recherche_affaire), ReperesSaisieAvis.RECHERCHE)
        val (siennes, autres) = ChoixAffaire.groupes(affaires, recherche, trouvees, ReglesAffaires.RECHERCHE_MINIMALE)
        val aide = when {
            enCours -> R.string.notation_recherche_en_cours
            active && siennes.isEmpty() && autres.isEmpty() -> R.string.notation_aucune_affaire
            !active -> R.string.notation_sans_affaire
            else -> null
        }
        aide?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted) }
        val avec = stringResource(R.string.notation_avec, prestataire.ifBlank { stringResource(R.string.notation_ce_prestataire) })
        val retenir = { id: Int ->
            recherche = ""
            choisir(id)
        }
        GroupeAffaires(avec, siennes, retenir)
        GroupeAffaires(stringResource(R.string.notation_autres_affaires), autres, retenir)
    }
}

/** Un groupe d'affaires à choisir : son titre, puis une ligne touchable par affaire. */
@Composable
private fun GroupeAffaires(titre: String, affaires: List<AffaireANoter>, choisir: (Int) -> Unit) {
    if (affaires.isEmpty()) return
    val couleurs = LocalCouleurs.current
    Column {
        Text(
            titre,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = couleurs.textMuted,
            modifier = Modifier.semantics { heading() },
        )
        affaires.forEach { affaire ->
            val ligne = "${affaire.numero}${Separateur.tiret}${affaire.titre}"
            Text(
                ligne,
                style = MaterialTheme.typography.bodyMedium,
                color = couleurs.primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(ReperesSaisieAvis.affaire(affaire.id))
                    .cliquable(ligne) { choisir(affaire.id) }
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}
