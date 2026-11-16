// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.session.ErreurService

/** Une section du Profil, comme une `Section` de l'iOS : son libellé, sa carte, sa note dessous. */
@Composable
fun SectionProfil(titre: String?, note: String? = null, contenu: @Composable ColumnScope.() -> Unit) {
    val couleurs = LocalCouleurs.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Mise.marge), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (titre != null) {
            Libelle(titre, Modifier.padding(start = 4.dp).semantics { heading() })
        }
        Carte(contenu = contenu)
        if (note != null) Note(note, Modifier.padding(horizontal = 4.dp))
    }
}

/** Une phrase discrète, sous une section ou dans une carte. */
@Composable
fun Note(texte: String, modifier: Modifier = Modifier, couleur: Color = LocalCouleurs.current.textMuted) =
    Text(texte, style = MaterialTheme.typography.bodySmall, color = couleur, modifier = modifier)

/** Une valeur et son libellé, sur une ligne (`LabeledContent` de l'iOS). */
@Composable
fun LigneValeur(libelle: String, valeur: String) {
    val couleurs = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(libelle, color = couleurs.text, modifier = Modifier.weight(1f))
        Text(valeur, color = couleurs.textMuted)
    }
}

/**
 * Un geste en ligne : un texte touchable, en couleur primaire (en rouge s'il détruit) ;
 * `discret`, petit, gris et centré (« Oublier toutes les résidences »).
 */
@Composable
fun LigneAction(
    libelle: String,
    repere: String,
    modifier: Modifier = Modifier,
    detruit: Boolean = false,
    actif: Boolean = true,
    discret: Boolean = false,
    onClick: () -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val couleur = when {
        !actif || discret -> couleurs.textMuted
        detruit -> couleurs.danger
        else -> couleurs.primary
    }
    Box(
        modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(repere)
            .then(if (actif) Modifier.cliquable(libelle, onClick) else Modifier.semantics(mergeDescendants = true) { disabled() }),
        contentAlignment = if (discret) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            libelle,
            color = couleur,
            style = if (discret) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            fontWeight = if (discret) null else FontWeight.Medium,
        )
    }
}

/**
 * Une feuille plein écran, pour un formulaire du Profil — comme la feuille de l'iOS
 * (`NavigationStack` dans une `sheet`) : son titre, « Annuler », et son geste en haut à
 * droite. ⚠️ Divergence déclarée : l'iOS POUSSE « Mot de passe » et « Demande de
 * modification » dans sa pile ; ici la barre de la résidence porte le titre de la page,
 * et un formulaire s'ouvre en feuille, avec le sien.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleProfil(
    titre: String,
    fermer: () -> Unit,
    geste: GesteDeFeuille? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val enCours = geste?.enCours == true
    Dialog(
        onDismissRequest = { if (!enCours) fermer() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = couleurs.bg,
            topBar = {
                TopAppBar(
                    title = { Text(titre, style = Typo.titre(MaterialTheme.typography.titleLarge)) },
                    navigationIcon = {
                        IconButton(onClick = fermer, enabled = !enCours) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.annuler))
                        }
                    },
                    actions = {
                        if (geste != null) {
                            if (enCours) {
                                CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.dp)
                            } else {
                                // Le geste d'une feuille part au serveur : grisé en consultation hors ligne.
                                val enLigne = gesteEnLigne(geste.actif)
                                TextButton(onClick = geste.onClick, enabled = enLigne.actif, modifier = enLigne.modifier.testTag(geste.repere)) {
                                    Text(geste.libelle, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = couleurs.bg),
                )
            },
        ) { marges ->
            Column(
                Modifier.padding(marges).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                    .background(couleurs.bg).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = contenu,
            )
        }
    }
}

/** Le geste d'une feuille : « Enregistrer », « Confirmer la demande »… */
data class GesteDeFeuille(
    val libelle: String,
    val repere: String,
    val actif: Boolean,
    val enCours: Boolean,
    val onClick: () -> Unit,
)

/** Une phrase à lire, avec « OK » (l'`alert` de l'iOS). */
@Composable
fun AvisProfil(phrase: Phrase?, fermer: () -> Unit) {
    if (phrase == null) return
    AlertDialog(
        onDismissRequest = fermer,
        confirmButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.profil_ok)) } },
        text = { Text(phrase.texte()) },
    )
}

/**
 * Une phrase qu'un geste laisse à lire : un libellé de l'app, ou une erreur (celle du
 * serveur s'il en donne une). Gardée ainsi et rendue dans la composition, elle suit la
 * configuration (langue, taille) au lieu d'être figée au moment du geste.
 */
sealed interface Phrase {
    data class Texte(@param:StringRes val id: Int, val valeurs: List<String> = emptyList()) : Phrase
    data class Erreur(val erreur: ErreurService) : Phrase
}

@Composable
fun Phrase.texte(): String = when (this) {
    is Phrase.Texte -> stringResource(id, *valeurs.toTypedArray())
    is Phrase.Erreur -> erreur.phrase()
}
