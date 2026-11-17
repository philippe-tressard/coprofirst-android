// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import okhttp3.Call

/** Pourquoi une pièce ne s'est pas ouverte. */
sealed interface EchecOuverture {
    /** La résidence ne l'a pas servie (session expirée, introuvable…) : sa phrase. */
    data class Service(val erreur: ErreurService) : EchecOuverture

    /** Aucune app de l'appareil ne sait montrer ce type de fichier. */
    data object AucuneApp : EchecOuverture

    /** Le fichier n'a pas pu s'écrire dans le cache. */
    data object Illisible : EchecOuverture
}

/**
 * Ouvrir une pièce « en grand », l'équivalent Android de Coup d'œil : la pièce est lue
 * (une fois) dans le cache de l'app (`PiecesLocales`), puis confiée à l'app de l'appareil
 * qui sait la montrer (`Intent.ACTION_VIEW`), avec le droit de LIRE ce fichier-là seulement.
 * Une seule ouverture à la fois ; `enCours` dit laquelle, `echec` pourquoi elle a échoué.
 *
 * Un écran qui pose une `Visionneuse` montre DANS L'APP ce qu'elle sait montrer (PDF, image),
 * comme `DocumentView` de l'iOS (#274) : le document s'y ouvre dès le toucher (« Le document se
 * télécharge. »), à côté de la liste en fenêtre large. Le reste part dans l'app de l'appareil.
 *
 * ⚠️ Divergences déclarées : (1) un écran sans `Visionneuse` (les photos d'une affaire, d'un
 * relevé) confie la pièce à l'app de l'appareil — zoom, partage et impression y sont ceux de
 * cette app ; un type que la visionneuse ne sait pas montrer aussi. (2) L'iOS feuillette toutes
 * les photos de l'élément ; ici, la photo touchée s'ouvre seule.
 */
@Stable
class Ouverture internal constructor(
    private val contexte: Context,
    private val session: SessionOuverte,
    private val portee: CoroutineScope,
    private val confirmer: () -> Unit,
) {
    /** La pièce (son chemin, ou `document-<id>`) en train de s'ouvrir. */
    var enCours by mutableStateOf<String?>(null)
        private set
    var echec by mutableStateOf<EchecOuverture?>(null)
        private set

    /** Le document montré DANS l'app (`Visionneuse`) ; `null` : aucun. */
    var montre by mutableStateOf<DocumentMontre?>(null)
        internal set

    /** Les visionneuses posées par l'écran : sans elle, tout part dans l'app de l'appareil. */
    internal var visionneuses by mutableIntStateOf(0)

    /** Ferme le document montré. */
    fun fermer() {
        montre = null
    }

    private val locales = PiecesLocales.de(contexte)

    /** Une pièce jointe servie (« /uploads/… »), photo ou document. */
    fun piece(chemin: String) = lancer(chemin, chemin.substringAfterLast('/')) { locales.piece(chemin, session) }

    /** Un document, par son identifiant (`DocumentView` de l'iOS) ; `titre` : celui montré, son nom sinon. */
    fun document(id: Int, nom: String?, titre: String? = null) = lancer(cleDocument(id), nom, titre) { locales.document(id, nom, session) }

    /**
     * Un fichier servi par une ROUTE de l'API (un rapport, un export…), que le client généré
     * télécharge — comme `fichierServi` de l'iOS. `sous` et `cle` rangent la copie
     * (`<sous>/<cle>/<nom sûr>`) ; `nom` est le nom montré, `defaut` celui d'un nom absent.
     * ```
     * ouverture.fichier("rapports", id.toString(), nom, "rapport-$id") { base, client ->
     *     DiagnosticsApi(base, client).getDiagnosticsRapportsRapportIdTlcharger(id)
     * }
     * ```
     */
    fun fichier(
        sous: String,
        cle: String,
        nom: String?,
        defaut: String,
        titre: String? = null,
        appel: (base: String, client: Call.Factory) -> File,
    ) = lancer(cleFichier(sous, cle), nom ?: defaut, titre) { locales.fichierServi(session, sous, cle, nom, defaut, appel) }

    /**
     * `nom` : celui montré, et ce qui dit d'avance si la visionneuse sait montrer le fichier ;
     * inconnu, on le sait au fichier lu.
     */
    private fun lancer(cle: String, nom: String?, titre: String? = null, lire: suspend () -> File) {
        if (enCours != null) return
        val avant = nom?.takeIf { visionneuses > 0 && Visionnable.de(it) != null }?.let { titre ?: it }
        if (avant != null) montre = DocumentMontre(cle, avant, null)
        enCours = cle
        echec = null
        portee.launch {
            val resultat = try {
                val fichier = lire()
                when {
                    avant != null -> null.also { if (montre?.cle == cle) montre = DocumentMontre(cle, avant, fichier) }
                    visionneuses > 0 && Visionnable.de(fichier.name) != null ->
                        null.also { montre = DocumentMontre(cle, titre ?: nom ?: fichier.name, fichier) }
                    ouvrirDansUneApp(contexte, fichier) -> null
                    else -> EchecOuverture.AucuneApp
                }
            } catch (erreur: ErreurService) {
                EchecOuverture.Service(erreur)
            } catch (_: IOException) {
                EchecOuverture.Illisible
            }
            // Un document qui n'a pas pu se lire ne reste pas ouvert sur « se télécharge ».
            if (resultat != null && montre?.cle == cle) montre = null
            if (resultat == null) confirmer()
            echec = resultat
            enCours = null
        }
    }

    companion object {
        /** La clé d'un document par son identifiant : celle de `enCours`, de `montre` et de la rangée choisie. */
        fun cleDocument(id: Int) = "document-$id"

        /** La clé d'un fichier servi par une route (`fichier`). */
        fun cleFichier(sous: String, cle: String) = "$sous-$cle"

        /** Le suffixe de l'autorité du `FileProvider` des pièces (`<applicationId>.pieces`, manifeste). */
        const val SUFFIXE_AUTORITE = ".pieces"

        /** Confie le fichier à l'app qui sait le montrer ; `false` s'il n'y en a aucune. */
        fun ouvrirDansUneApp(contexte: Context, fichier: File): Boolean {
            val adresse = FileProvider.getUriForFile(contexte, contexte.packageName + SUFFIXE_AUTORITE, fichier)
            val ext = Fichiers.extensionDe(fichier.name).lowercase()
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            val intention = Intent(Intent.ACTION_VIEW)
                .setDataAndType(adresse, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (contexte !is Activity) intention.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                contexte.startActivity(intention)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}

/** L'ouverture des pièces d'un écran, sur la session de sa résidence. */
@Composable
fun rememberOuverture(session: SessionOuverte): Ouverture {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val retour = LocalHapticFeedback.current
    return remember(session, contexte) {
        Ouverture(contexte, session, portee) { retour.performHapticFeedback(HapticFeedbackType.ContextClick) }
    }
}

/** Ce qui a empêché une pièce de s'ouvrir, en une phrase, annoncée au lecteur d'écran. */
@Composable
fun AvisOuverture(ouverture: Ouverture, modifier: Modifier = Modifier) {
    val echec = ouverture.echec ?: return
    val couleurs = LocalCouleurs.current
    val phrase = when (echec) {
        is EchecOuverture.Service -> echec.erreur.phrase()
        EchecOuverture.AucuneApp -> stringResource(R.string.pieces_aucune_app)
        EchecOuverture.Illisible -> stringResource(R.string.pieces_echec)
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = couleurs.danger, modifier = Modifier.size(16.dp))
        Text(phrase, style = MaterialTheme.typography.bodySmall, color = couleurs.danger)
    }
}
