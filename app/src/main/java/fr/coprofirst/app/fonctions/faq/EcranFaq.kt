// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.faq

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import fr.coprofirst.app.commun.texte.TexteRiche
import fr.coprofirst.app.navigation.ContexteDeMenu

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesFaq {
    fun question(id: Int) = "faq.$id"
    const val REDACTION = "faq.redaction"
    const val MANUEL = "faq.manuel"
}

/** Les adresses du site que la FAQ ouvre, sous celle de la résidence. */
data class LiensFaq(val faq: String?, val manuel: String?) {
    /** Le lien du site vers une question (`#faq-<id>`), comme l'iOS. */
    fun question(id: Int): String? = faq?.let { "$it#faq-$id" }

    companion object {
        /** Le manuel, servi à la racine du site (`manuel-utilisateur.html`), comme l'iOS. */
        private const val MANUEL = "/manuel-utilisateur.html"

        fun depuis(route: String, adresse: String) =
            LiensFaq(OngletsSurLeSite.lien(route, adresse), OngletsSurLeSite.lien(MANUEL, adresse))
    }
}

/**
 * La FAQ de la résidence (page `faq`, #20), comme l'iOS (`FAQView`) : une section par
 * catégorie (le libellé du serveur), une question dépliée à la fois, la réponse en texte
 * riche. Lecture seule : « Rédiger, masquer ou réorganiser les questions se fait sur le
 * site » ne se dit qu'à qui le serveur l'ouvre (GET /faq/gestes → `peut_rediger`, #37) ;
 * un droit illisible vaut « non », et la FAQ se lit quand même.
 *
 * ⚠️ Divergences déclarées : « Créer une affaire » (qui mène à l'onglet Affaires) attend
 * qu'un écran puisse ouvrir une autre page (#91) ; « Copier le lien » et « Partager » de
 * l'iOS sont un seul « Partager le lien » (la feuille de partage d'Android sait copier).
 */
@Composable
fun EcranFaq(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceFaqApi(contexte.session) }
    EcranFaq(service, remember(contexte.page.route) { LiensFaq.depuis(contexte.page.route, contexte.session.installation.url) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EcranFaq(service: ServiceFaq, liens: LiensFaq) {
    val general = stringResource(R.string.faq_general)
    var relecture by rememberSaveable { mutableIntStateOf(0) }
    PullToRefreshBox(isRefreshing = false, onRefresh = { relecture++ }, modifier = Modifier.fillMaxSize().background(LocalCouleurs.current.bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = Mise.marge, vertical = 16.dp)) {
                Chargement(
                    charger = { ReglesFaq.lire(service, general) },
                    cle = relecture,
                    squelette = { ListeFaq(SQUELETTE, liens) },
                ) { lue, _ -> ListeFaq(lue, liens) }
            }
        }
    }
}

@Composable
private fun ListeFaq(lue: FaqLue, liens: LiensFaq) {
    var ouverte by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (lue.categories.isEmpty()) EtatVide(stringResource(R.string.faq_vide), icone = Icons.Outlined.HelpOutline)
        lue.categories.forEach { categorie ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(categorie.nom.uppercase(), style = Typo.libelle(), color = LocalCouleurs.current.textMuted)
                categorie.questions.forEach { question ->
                    LigneQuestion(question, ouverte == question.id, liens.question(question.id)) {
                        ouverte = if (ouverte == question.id) null else question.id
                    }
                }
            }
        }
        Aide(lue.peutRediger, liens)
    }
}

/** Une question, dépliable ; une seule ouverte à la fois, comme sur le site. */
@Composable
private fun LigneQuestion(question: QuestionFaq, ouverte: Boolean, lien: String?, basculer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val contexte = LocalContext.current
    Carte(Modifier.testTag(ReperesFaq.question(question.id))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible)
                .cliquable(stringResource(if (ouverte) R.string.faq_replier else R.string.faq_deplier), basculer),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                question.question,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = couleurs.text,
                modifier = Modifier.weight(1f),
            )
            Icon(if (ouverte) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = couleurs.textMuted)
        }
        AnimatedVisibility(ouverte) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TexteRiche(question.reponse)
                if (lien != null) {
                    TextButton(onClick = {
                        val envoi = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lien)
                        contexte.startActivity(Intent.createChooser(envoi, null))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Text(stringResource(R.string.faq_partager_lien), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** L'encart du bas de la page du site, puis la rédaction réservée au site. */
@Composable
private fun Aide(peutRediger: Boolean, liens: LiensFaq) {
    val couleurs = LocalCouleurs.current
    val navigateur = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Carte {
            Text(stringResource(R.string.faq_pas_trouve), style = MaterialTheme.typography.titleMedium, color = couleurs.text)
            Text(stringResource(R.string.faq_pas_trouve_texte), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
            liens.manuel?.let { manuel ->
                TextButton(onClick = { navigateur.openUri(manuel) }, modifier = Modifier.testTag(ReperesFaq.MANUEL)) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                    Text(stringResource(R.string.faq_ouvrir_manuel), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (peutRediger) {
            Column(Modifier.testTag(ReperesFaq.REDACTION), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.faq_redaction_au_site), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
                liens.faq?.let { faq ->
                    TextButton(onClick = { navigateur.openUri(faq) }) {
                        Text(stringResource(R.string.faq_ouvrir_site), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private val SQUELETTE = FaqLue(
    listOf(CategorieFaq("Une catégorie", (1..4).map { QuestionFaq(it, "", "Une question fréquente de la résidence", "") })),
    peutRediger = false,
)
