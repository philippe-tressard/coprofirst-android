// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.apropos

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.Section
import fr.coprofirst.app.commun.cliquable
// La section groupée du Profil, importée et non recopiée : elle monte dans commun/ par #138.
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.session.ErreurService
import java.time.Year

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesAPropos {
    const val MENTION = "a-propos.mention"
    const val MENTIONS = "a-propos.mentions"
    const val CONFIDENTIALITE = "a-propos.confidentialite"
    const val CODE_APP = "a-propos.code-app"
    fun composant(nom: String) = "apropos.composant.$nom"
}

/** Ce que « À propos » ouvre par la porte : les deux textes légaux et chaque composant tiers. */
object FichesAPropos {
    const val TEXTE_LEGAL = "texte-legal"
    const val COMPOSANT = "composant-tiers"
    const val MENTIONS = "mentions"
    const val CONFIDENTIALITE = "confidentialite"
}

/**
 * Le code de CETTE app (AGPLv3 §13 : offrir le source de ce qu'on distribue) — le dépôt du
 * serveur, lui, est celui du site (`TextesDuPied.depotSource`).
 */
const val DEPOT_APP = "https://github.com/philippe-tressard/coprofirst-android"

/**
 * « À propos » (#27), comme l'iOS (`AProposView`) : ce que le pied de page du site dit sur
 * chaque page — la résidence, le « © », la version, puis « À propos de CoproFirst »,
 * « Logiciel libre », « Mentions légales » et « Confidentialité ». Les deux derniers sont
 * verrouillés sur le site (LCEN art. 6, RGPD art. 13) : l'app les rend lisibles ici, sans
 * passer par le navigateur. Les liens du code source ne dépendent d'aucune lecture : ils
 * restent là quand la résidence ne répond pas.
 *
 * ⚠️ Divergence déclarée : l'iOS montre le logo de la RÉSIDENCE (`LogoResidence`), que
 * l'app Android ne lit pas encore — le logo de CoproFirst, son repli, en tient la place
 * (ticket #137).
 */
@Composable
fun EcranAPropos(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceAProposApi(contexte.session) }
    EcranAPropos(service, contexte.session.installation.nom) { contexte.ouvrir(it) }
}

@Composable
internal fun EcranAPropos(service: ServiceAPropos, nomResidence: String, ouvrir: (Destination) -> Boolean) {
    val ressources = LocalContext.current
    var lu by remember { mutableStateOf<AProposResidence?>(null) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    var essai by remember { mutableIntStateOf(0) }
    LaunchedEffect(essai) {
        try {
            lu = service.aPropos()
            erreur = null
        } catch (e: ErreurService) {
            erreur = e
        }
    }
    val composants = remember { ComposantsTiers.tous(ressources.assets) }
    Column(
        Modifier.fillMaxSize().background(LocalCouleurs.current.bg).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Section(titre = null) { Marque(nomResidence, AProposResidence.mention(lu, Year.now().value, version(ressources))) }
            AnimatedVisibility(erreur != null) {
                erreur?.let { MessageErreur(it, Modifier.padding(horizontal = Mise.marge)) { essai++ } }
            }
            Section(stringResource(R.string.apropos_informations_legales)) {
                val pret = lu != null
                Ligne(TextesDuPied.mentions.libelle, ReperesAPropos.MENTIONS, Icons.Outlined.AccountBalance, actif = pret) {
                    ouvrir(Destination.Element(FichesAPropos.TEXTE_LEGAL, FichesAPropos.MENTIONS))
                }
                Separateur()
                Ligne(TextesDuPied.confidentialite.libelle, ReperesAPropos.CONFIDENTIALITE, Icons.Outlined.PanTool, actif = pret) {
                    ouvrir(Destination.Element(FichesAPropos.TEXTE_LEGAL, FichesAPropos.CONFIDENTIALITE))
                }
            }
            Section(
                titre = null,
                note = stringResource(R.string.apropos_distribue, TextesDuPied.nomPlateforme, TextesDuPied.licenceNom),
            ) {
                LiensDuCode()
            }
            Section(stringResource(R.string.apropos_composants)) {
                composants.forEachIndexed { i, composant ->
                    if (i > 0) Separateur()
                    Ligne(composant.nom, ReperesAPropos.composant(composant.nom), valeur = composant.licences.joinToString(", ")) {
                        ouvrir(Destination.Element(FichesAPropos.COMPOSANT, composant.nom))
                    }
                }
            }
        }
    }
}

/** Le logo et le nom, puis la mention : « © 2025–2026 · texte libre · app 0.8.13 ». */
@Composable
private fun Marque(nom: String, mention: String) {
    val couleurs = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(ReperesAPropos.MENTION),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(44.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nom, style = Typo.titre(MaterialTheme.typography.titleMedium), color = couleurs.text)
            Text(mention, style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
        }
    }
}

/** Le site de CoproFirst, le code du serveur, puis celui de l'app : trois liens externes. */
@Composable
private fun LiensDuCode() {
    val navigateur = LocalUriHandler.current
    val liens = listOf(
        Triple(TextesDuPied.site.libelle, TextesDuPied.sitePlateforme, null),
        Triple(TextesDuPied.source.libelle, TextesDuPied.depotSource, null),
        Triple(stringResource(R.string.apropos_code_app), DEPOT_APP, ReperesAPropos.CODE_APP),
    )
    liens.forEachIndexed { i, (libelle, adresse, repere) ->
        if (i > 0) Separateur()
        Ligne(libelle, repere ?: "a-propos.lien.$i", if (repere == null) Icons.Outlined.Link else Icons.Outlined.Code, externe = true) {
            navigateur.openUri(adresse)
        }
    }
}

/** Une ligne touchable : pictogramme, libellé, valeur, puis un chevron (ou la flèche d'un lien externe). */
@Composable
private fun Ligne(
    libelle: String,
    repere: String,
    icone: ImageVector? = null,
    valeur: String? = null,
    externe: Boolean = false,
    actif: Boolean = true,
    ouvrir: () -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val teinte = if (actif) couleurs.text else couleurs.textMuted
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(repere)
            .then(if (actif) Modifier.cliquable(libelle, ouvrir) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icone?.let { Icon(it, contentDescription = null, tint = if (actif) couleurs.primary else couleurs.textMuted) }
        Text(libelle, color = teinte, modifier = Modifier.weight(1f))
        valeur?.let { Text(it, color = couleurs.textMuted, style = MaterialTheme.typography.bodyMedium) }
        Icon(
            if (externe) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = couleurs.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun Separateur() = HorizontalDivider(color = LocalCouleurs.current.border)

/** La version de l'app (« 0.8.13 »), déduite au build. */
private fun version(contexte: Context): String? =
    runCatching { contexte.packageManager.getPackageInfo(contexte.packageName, 0).versionName }.getOrNull()
