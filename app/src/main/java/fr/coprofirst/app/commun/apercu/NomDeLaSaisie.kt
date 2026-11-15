// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.apercu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import fr.coprofirst.api.modeles.NomSaisi
import fr.coprofirst.api.routes.NomsApi
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.SessionOuverte

/** Le « Prénom NOM » d'une saisie, rendu par le serveur (POST /noms/affiche) — jamais composé ici. */
interface ServiceNoms {
    suspend fun nomAffiche(prenom: String?, nom: String?): String
}

class ServiceNomsApi(private val session: SessionOuverte) : ServiceNoms {
    override suspend fun nomAffiche(prenom: String?, nom: String?): String =
        session.appeler { base, client -> NomsApi(base, client).postNomsAffiche(NomSaisi(prenom = prenom, nom = nom)).nomAffiche }
}

/**
 * La ligne « Nom affiché » d'une feuille d'édition, comme l'iOS (`NomDeLaSaisie`) : ce que le
 * serveur rendra de la saisie (« jean-baptiste », « fort » → « Jean-Baptiste FORT »), par
 * `ApercuServi` (une rafale = une demande ; la dernière valeur tient tant qu'il n'a pas
 * répondu). `connu` : le nom déjà servi pour la saisie d'origine — il ne se redemande pas.
 * Repère `repere` sur la ligne entière, lue d'un bloc.
 */
@Composable
fun NomDeLaSaisie(
    prenom: String?,
    nom: String?,
    service: ServiceNoms,
    repere: String,
    modifier: Modifier = Modifier,
    connu: String? = null,
) {
    val saisie = (prenom.orEmpty() to nom.orEmpty())
    ApercuServi(saisie, demander = { (p, n) -> service.nomAffiche(p, n) }, modifier, connue = connu?.let { saisie to it }) { servi ->
        val couleurs = LocalCouleurs.current
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(repere),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.apercu_nom_affiche), style = MaterialTheme.typography.bodyMedium, color = couleurs.text)
            Text(servi?.takeIf { it.isNotBlank() } ?: FormatsDuSite.TIRET, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
        }
    }
}
