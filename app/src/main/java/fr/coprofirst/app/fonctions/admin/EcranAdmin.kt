// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.onglets.CadreAOnglets
import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import fr.coprofirst.app.commun.onglets.PageAOnglets
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.OngletsDuSite

/**
 * Le Paramétrage (page `admin` du site, #26), EN PARTIE, comme l'iOS (`AdminView`) : les
 * onglets que l'app porte, puis « Sur le site » pour les autres, chacun avec son lien
 * (`renvoieAuSite`). Le cadre est celui des pages à onglets (`CadreAOnglets`).
 *
 * Réservé à l'administrateur et à l'opérateur de la plateforme (`PagesOuvertes`) ;
 * l'opérateur qui n'est pas administrateur n'y ouvre que l'Exploitation
 * (`OngletsDuSite.ouverts`), donc ici la seule Maintenance. Le serveur refuse chaque
 * route à qui n'a pas le rôle.
 *
 * ⚠️ Étape intermédiaire déclarée (#26) : « À traiter » n'est pas encore inscrit ici — il
 * paraît dans « Sur le site », avec son lien, jusqu'à ce que la file des comptes de l'Espace
 * CS (#24), qu'il réemploie, soit sur main.
 */
@Composable
fun EcranAdmin(contexte: ContexteDeMenu) {
    val page = remember(contexte.session) { OngletsAdmin.page(ServiceAdminApi(contexte.session)) }
    CadreAOnglets(contexte, page)
}

/**
 * La table des onglets que l'app porte : l'identifiant d'un onglet du site
 * (`pages-roles.ts`) → son écran. Un onglet absent d'ici paraît dans « Sur le site ».
 *
 * ⚠️ UNE LIGNE PAR ONGLET, séparée des autres par une ligne vide. L'ordre et
 * l'ouverture se décident au site (`OngletsDuSite.ouverts`, généré).
 */
object OngletsAdmin {
    const val PAGE = "admin"

    fun page(service: ServiceAdmin) = PageAOnglets(
        PAGE,
        mapOf(
            "utilisateurs" to { contexte -> OngletUtilisateurs(contexte, service) },

            "telemetry" to { contexte -> OngletTelemetrie(contexte, service) },

            "maintenance" to { contexte -> OngletMaintenance(contexte, service) },
        ),
        reserve = R.string.admin_reserve,
        renvoieAuSite = true,
    )
}

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesAdmin {
    fun surLeSite(onglet: String) = "admin.$onglet.sur-le-site"
    fun tache(code: String) = "admin.tache.$code"
    const val ANOMALIES = "admin.maintenance.anomalies"
    const val EXPORT = "admin.maintenance.export"
    fun vue(portee: PorteeTelemetrie) = "admin.telemetrie.vue.${portee.code}"
    const val FILTRE = "admin.telemetrie.filtre"
    const val INDICATEURS = "admin.telemetrie.indicateurs"
    const val GRAPHE = "admin.telemetrie.graphe"
    const val RECHERCHE = "admin.utilisateurs.recherche"
    const val TYPE = "admin.utilisateurs.type"
    fun filtre(filtre: FiltreCompte) = "admin.utilisateurs.compte." + when (filtre) {
        FiltreCompte.Tous -> "tous"
        FiltreCompte.Actifs -> "actifs"
        FiltreCompte.EnAttente -> "enAttente"
    }
    fun utilisateur(id: Int) = "admin.utilisateur.$id"
    const val FICHE_FERMER = "admin.fiche.fermer"
    const val FICHE_COMMUNAUTE = "admin.fiche.communaute"
    const val FICHE_RAPPROCHEMENT = "admin.fiche.rapprochement"
    const val FICHE_SUPPRIMER = "admin.fiche.supprimer"
    const val FICHE_CONFIRMER = "admin.fiche.confirmer"
    const val FICHE_EXPLOITANT = "admin.fiche.operateur-exploitant"
    fun role(role: String) = "admin.fiche.role.$role"
}

/**
 * Ce qu'un onglet du Paramétrage laisse au site, comme l'iOS (`RenvoiAuSite`) : une
 * phrase, et le lien vers l'onglet du site qui le porte en entier — ouvert par le
 * navigateur, à l'adresse de la résidence.
 */
@Composable
fun RenvoiAuSite(texte: String, onglet: String, adresse: String) {
    val couleurs = LocalCouleurs.current
    val site = LocalUriHandler.current
    val lien = OngletsDuSite.routes[OngletsAdmin.PAGE]?.get(onglet)?.let { OngletsSurLeSite.lien(it, adresse) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Public, contentDescription = null, tint = couleurs.textMuted)
            Text(texte, style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
        }
        if (lien != null) {
            val action = stringResource(R.string.onglets_ouvrir_le_site)
            Row(
                Modifier
                    .heightIn(min = Mise.cible)
                    .cliquable(action) { site.openUri(lien) }
                    .testTag(ReperesAdmin.surLeSite(onglet)),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = couleurs.primary)
                Text(
                    stringResource(R.string.admin_sur_le_site),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = couleurs.primary,
                )
            }
        }
    }
}

/** Le titre d'une section d'un onglet, en serif, annoncé comme titre. */
@Composable
fun TitreSection(texte: String) {
    Text(
        texte,
        style = Typo.titre(MaterialTheme.typography.titleLarge),
        color = LocalCouleurs.current.text,
        modifier = Modifier.semantics { heading() },
    )
}
