// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.SondageRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.commun.lecture.EtiquetteLieu
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.lecture.LieuServi
import fr.coprofirst.app.commun.lecture.PastilleLecture
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.session.ErreurService

/** Les sondages, et ce que CE lecteur peut en faire (`peut_creer`, servi). */
data class ListeSondages(val sondages: List<SondageRead>, val peutCreer: Boolean)

/**
 * Les sondages, comme l'iOS (`OngletSondages`) : ceux en cours, puis les Archives repliées,
 * dans l'ordre du serveur. Anonymes, comme sur le site : aucun auteur. « Créer, modifier ou
 * stopper un sondage se fait sur le site » ne se dit qu'à qui le serveur l'ouvre
 * (GET /sondages/gestes → `peut_creer`, #37) ; un droit illisible vaut « non », et la liste
 * se lit quand même. Un toucher ouvre la fiche commune du sondage (vote, résultats,
 * commentaires) par la porte ; sans elle, le sondage s'ouvre sur le site.
 */
@Composable
fun OngletSondages(contexte: ContexteOnglet, service: ServiceCommunaute) {
    val adresse = contexte.session.installation.url
    val navigateur = LocalUriHandler.current
    Chargement(
        charger = { ListeSondages(service.sondages(), lirePeutCreer(service)) },
        cle = contexte.actualisation,
        squelette = { LigneSondage(SQUELETTE) {} },
    ) { liste, _ ->
        val textes = TextesListe(
            R.string.sondages_vide, R.string.sondages_vide_detail,
            R.string.sondages_en_cours_vide, R.string.sondages_en_cours_vide_detail, Icons.Outlined.BarChart,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            ListeCommunaute(liste.sondages, { it.archivee == true }, textes, videAuServeur = liste.sondages.isEmpty()) { sondage ->
                LigneSondage(sondage) {
                    if (!contexte.ouvrir(Destination.Element(FICHE_SONDAGE, sondage.id.toString()))) {
                        lienSondage(sondage.id, adresse)?.let(navigateur::openUri)
                    }
                }
            }
            if (liste.peutCreer) {
                Text(
                    stringResource(R.string.sondages_creation_sur_le_site),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalCouleurs.current.textMuted,
                    modifier = Modifier.testTag(ReperesCommunaute.SONDAGES_CREATION),
                )
            }
        }
    }
}

/** `peut_creer` servi ; illisible (route absente, refus, réseau), il vaut « non » — comme l'iOS. */
internal suspend fun lirePeutCreer(service: ServiceCommunaute): Boolean = try {
    service.peutCreerUnSondage()
} catch (_: ErreurService) {
    false
}

/** La page du sondage sur le site, sous l'adresse de la résidence (la route de l'onglet, générée). */
internal fun lienSondage(id: Int, adresse: String): String? {
    val route = OngletsDuSite.routes[AccesCommunaute.PAGE]?.get("sondages") ?: return null
    return OngletsSurLeSite.lien("$route/$id", adresse)
}

/** La nature de la fiche d'un sondage, déclarée par la couche commune (`RegistreDesMenus.fiches`). */
private const val FICHE_SONDAGE = "sondage"

/** Un sondage : la question, son état, sa clôture et ses votants, qui le lit et son lieu. Anonyme : aucun auteur. */
@Composable
private fun LigneSondage(sondage: SondageRead, ouvrir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val cloture = sondage.cloture == true
    Carte(Modifier.testTag(ReperesCommunaute.sondage(sondage.id)), ouvrir = ouvrir, action = sondage.question) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(sondage.question, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
            val description = sondage.description?.let(TexteDuSite::apercu).orEmpty()
            if (description.isNotEmpty()) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FlowCapsules {
                PastilleTeinte(
                    stringResource(if (cloture) R.string.sondages_cloture else R.string.sondages_ouvert),
                    if (cloture) "badge-gray" else "badge-green",
                    compacte = true,
                )
                val fin = sondage.clotureLe
                if (fin != null && !cloture) {
                    Etiquette(stringResource(R.string.sondages_cloture_le, FormatsDuSite.dateCourte(fin.toInstant())))
                }
                val votants = sondage.nbVotants ?: 0
                Etiquette(pluralStringResource(R.plurals.sondages_votants, votants, votants))
                PastilleLecture(sondage.lecture?.let(Lecture::depuis), masculin = true)
                EtiquetteLieu(LieuServi.lieu(sondage.perimetreLibelle))
            }
        }
    }
}

/** Une mention en petit, à côté des pastilles. */
@Composable
fun Etiquette(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodySmall, color = LocalCouleurs.current.textMuted)
}

private val SQUELETTE = SondageRead(
    id = 0,
    question = "Une question posée à la résidence",
    resultatsPublics = false,
    creeLe = java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    perimetreLibelle = "",
    description = "Le sondage se charge.",
)
