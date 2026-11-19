// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.apropos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.charte.largeurDeLecture
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.commun.texte.TexteRiche
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.TitreDeBarre

/** Le repère du texte d'une page légale, et celui du paragraphe « Logiciel libre ». */
object ReperesPageLegale {
    const val TEXTE = "a-propos.texte-legal"
    const val LOGICIEL_LIBRE = "a-propos.logiciel-libre"
}

/** Une page qui défile, centrée et bornée en largeur sur grand écran. */
@Composable
private fun Page(contenu: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(LocalCouleurs.current.bg).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.largeurDeLecture().fillMaxWidth().padding(Mise.marge),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = contenu,
        )
    }
}

/**
 * Un texte légal de la résidence, comme l'iOS (`PageLegaleView`, `PageLegale.svelte`) : le
 * HTML servi, en texte riche. Les mentions légales finissent, comme sur le site, par
 * « Logiciel libre ».
 */
@Composable
fun FicheTexteLegal(contexte: ContexteDeMenu, id: String) {
    val service = remember(contexte.session) { ServiceAProposApi(contexte.session) }
    FicheTexteLegal(service, id) { TitreDeBarre(contexte.barre, it) }
}

@Composable
internal fun FicheTexteLegal(service: ServiceAPropos, id: String, titrer: @Composable (String) -> Unit = {}) {
    val mentions = id == FichesAPropos.MENTIONS
    titrer((if (mentions) TextesDuPied.mentions else TextesDuPied.confidentialite).libelle)
    Page {
        Chargement(charger = { service.aPropos() }, cle = id, squelette = {}) { lu, _ ->
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                TexteLegal(if (mentions) lu.mentionsLegales else lu.confidentialite)
                if (mentions) LogicielLibre()
            }
        }
    }
}

@Composable
private fun TexteLegal(html: String?) {
    val couleurs = LocalCouleurs.current
    val modifier = Modifier.testTag(ReperesPageLegale.TEXTE)
    if (html != null && TexteDuSite.blocs(html).isNotEmpty()) {
        SelectionContainer { TexteRiche(html, modifier) }
    } else {
        Text(stringResource(R.string.apropos_non_publie), color = couleurs.textMuted, modifier = modifier)
    }
}

/** Ce que le site écrit sous les mentions légales : la licence du logiciel et son code. */
@Composable
private fun LogicielLibre() {
    val couleurs = LocalCouleurs.current
    val html = stringResource(
        R.string.apropos_logiciel_libre,
        echapper(TextesDuPied.nomPlateforme), echapper(TextesDuPied.licenceUrl), echapper(TextesDuPied.licenceNom),
        echapper(TextesDuPied.licenceSpdx), echapper(TextesDuPied.depotSource),
    )
    Column(Modifier.testTag(ReperesPageLegale.LOGICIEL_LIBRE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            TextesDuPied.source.libelle,
            style = Typo.titre(MaterialTheme.typography.titleLarge),
            color = couleurs.text,
            modifier = Modifier.semantics { heading() },
        )
        TexteRiche(html)
    }
}

/** Une valeur publiée, glissée dans le HTML du paragraphe : ses caractères spéciaux échappés. */
internal fun echapper(texte: String) =
    texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * Un composant tiers et ses textes entiers, comme l'iOS (`LicencesTiercesView`) : l'avis
 * doit accompagner chaque copie. Les textes se sélectionnent, à chasse fixe.
 */
@Composable
fun FicheComposantTiers(contexte: ContexteDeMenu, nom: String) {
    val ressources = LocalContext.current.assets
    val composant = remember(nom) { ComposantsTiers.tous(ressources).firstOrNull { it.nom == nom } } ?: return
    val textes = remember(composant) { ComposantsTiers.textes(ressources, composant) }
    TitreDeBarre(contexte.barre, composant.nom)
    val couleurs = LocalCouleurs.current
    Page {
        Text(composant.role, style = MaterialTheme.typography.bodyLarge, color = couleurs.text)
        textes.forEach { texte ->
            SelectionContainer {
                Text(texte, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = couleurs.textMuted)
            }
        }
    }
}
