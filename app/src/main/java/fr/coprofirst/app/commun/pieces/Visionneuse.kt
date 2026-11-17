// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.FeuillePleine
import fr.coprofirst.app.commun.miseenpage.FicheOuFeuille
import fr.coprofirst.app.commun.miseenpage.rangeeChoisie
import java.io.File

/** Un document montré dans l'app : sa clé (celle de la rangée), son titre, son fichier — `null` tant qu'il se télécharge. */
data class DocumentMontre(val cle: String, val titre: String, val fichier: File?)

/** Ce que la visionneuse sait montrer, par l'extension du nom. */
enum class Visionnable {
    Pdf, Image;

    companion object {
        private val IMAGES = setOf("png", "jpg", "jpeg", "webp")

        /** `null` : un type que la visionneuse ne montre pas (il part dans l'app de l'appareil). */
        fun de(nom: String): Visionnable? = when (Fichiers.extensionDe(nom).lowercase()) {
            "pdf" -> Pdf
            in IMAGES -> Image
            else -> null
        }
    }
}

/**
 * La VISIONNEUSE d'un écran (#274), comme `DocumentView` de l'iOS : le document s'ouvre dans
 * l'app — « Le document se télécharge. », puis ses pages (PDF) ou son image —, avec « Partager »
 * et « Ouvrir dans une autre app » (le zoom et l'impression de l'app de l'appareil). En fenêtre
 * large, à côté de la liste qui l'a ouvert (`FicheOuFeuille`) ; sinon dans une feuille pleine.
 * Un écran la pose une fois, à côté de son `rememberOuverture` ; sans elle, rien ne change.
 */
@Composable
fun Visionneuse(ouverture: Ouverture) {
    DisposableEffect(ouverture) {
        ouverture.visionneuses++
        onDispose { ouverture.visionneuses-- }
    }
    val document = ouverture.montre ?: return
    FicheOuFeuille(NATURE_DOCUMENT, document.cle, fermer = ouverture::fermer) {
        FeuillePleine(
            document.titre,
            REPERE_FERMER,
            ouverture::fermer,
            actions = { document.fichier?.let { ActionsDuDocument(it) } },
        ) {
            val fichier = document.fichier
            when {
                fichier == null -> Telechargement(document.titre)
                Visionnable.de(fichier.name) == Visionnable.Pdf -> PagesPdf(fichier)
                else -> ImageDuDocument(fichier, document.titre)
            }
        }
    }
}

/** La rangée du document `cle` (`Ouverture.cleDocument`), soulignée quand il est montré à côté de la liste. */
@Composable
fun Modifier.documentChoisi(cle: String): Modifier = rangeeChoisie(NATURE_DOCUMENT, cle)

/** Le temps du téléchargement : l'icône, le titre, la phrase de l'iOS. */
@Composable
private fun Telechargement(titre: String) {
    val couleurs = LocalCouleurs.current
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag(REPERE_TELECHARGEMENT),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(56.dp))
        Text(titre, style = MaterialTheme.typography.titleMedium, color = couleurs.textMuted, textAlign = TextAlign.Center)
        Text(stringResource(R.string.visionneuse_telechargement), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
    }
}

/** « Partager » et « Ouvrir dans une autre app », pour CE fichier seulement (droit de lecture). */
@Composable
private fun ActionsDuDocument(fichier: File) {
    val contexte = LocalContext.current
    IconButton(onClick = { confier(contexte, fichier, Intent.ACTION_SEND) }, modifier = Modifier.testTag(REPERE_PARTAGER)) {
        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.visionneuse_partager), tint = LocalCouleurs.current.primary)
    }
    IconButton(onClick = { confier(contexte, fichier, Intent.ACTION_VIEW) }, modifier = Modifier.testTag(REPERE_AUTRE_APP)) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.visionneuse_autre_app), tint = LocalCouleurs.current.primary)
    }
}

/** Partager (le choix du système) ou ouvrir ailleurs ; aucune app : rien ne se passe, comme un partage annulé. */
private fun confier(contexte: Context, fichier: File, action: String) {
    if (action == Intent.ACTION_VIEW) {
        Ouverture.ouvrirDansUneApp(contexte, fichier)
        return
    }
    val adresse = FileProvider.getUriForFile(contexte, contexte.packageName + Ouverture.SUFFIXE_AUTORITE, fichier)
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Fichiers.extensionDe(fichier.name).lowercase()) ?: "application/octet-stream"
    val envoi = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, adresse).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val choix = Intent.createChooser(envoi, null)
    if (contexte !is Activity) choix.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        contexte.startActivity(choix)
    } catch (_: ActivityNotFoundException) {
        Unit
    }
}

/** La nature d'un document dans la colonne de fiche. */
private const val NATURE_DOCUMENT = "document"

/** Repères (tests d'interface). */
const val REPERE_FERMER = "visionneuse.fermer"
const val REPERE_TELECHARGEMENT = "visionneuse.telechargement"
const val REPERE_PARTAGER = "visionneuse.partager"
const val REPERE_AUTRE_APP = "visionneuse.autreApp"
const val REPERE_PAGE = "visionneuse.page"
