// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Actualisable
import fr.coprofirst.app.commun.ConfirmationDeSortie
import fr.coprofirst.app.commun.GesteDeSortie
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.Note
import fr.coprofirst.app.fonctions.profil.ChampsMotDePasse
import fr.coprofirst.app.fonctions.profil.ServiceProfilApi
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.launch

/** Les repères de la porte, ceux de l'iOS (`residence.deconnecter`) ; les champs sont ceux du Profil. */
object ReperesPorteMotDePasse {
    const val DECONNECTER = "residence.deconnecter"
}

/**
 * La porte d'un mot de passe TEMPORAIRE (#371, comme `MotDePasseTemporaireView` de l'iOS) :
 * tant que GET /auth/me sert `mot_de_passe_a_changer`, la résidence n'ouvre que ce formulaire
 * — celui du Profil, à ses libellés — et la déconnexion, confirmée comme ailleurs. Le mot de
 * passe choisi, le compte se relit (`relire`) : le drapeau tombe et la résidence s'ouvre.
 * Si la relecture échoue, une phrase le dit, et tirer vers le bas relit le compte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PorteMotDePasseTemporaire(session: SessionOuverte, sortie: GestesDeSortie, relire: suspend () -> Boolean) {
    val couleurs = LocalCouleurs.current
    val service = remember(session) { ServiceProfilApi(session) }
    val portee = rememberCoroutineScope()
    var change by rememberSaveable { mutableStateOf(false) }
    var relecture by remember { mutableStateOf(false) }
    var sortir by remember { mutableStateOf<GesteDeSortie?>(null) }
    val relireLeCompte: suspend () -> Unit = {
        relecture = true
        relire()
        relecture = false
    }
    Scaffold(
        containerColor = couleurs.bg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profil_temporaire_titre), style = Typo.titre(MaterialTheme.typography.titleLarge)) },
                navigationIcon = {
                    TextButton(
                        onClick = { sortir = GesteDeSortie.Deconnecter(session.installation.nom) },
                        modifier = Modifier.testTag(ReperesPorteMotDePasse.DECONNECTER),
                    ) { Text(stringResource(R.string.se_deconnecter)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = couleurs.bg),
            )
        },
    ) { marges ->
        Actualisable(relecture, { portee.launch { relireLeCompte() } }, Modifier.padding(marges).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).background(couleurs.bg).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (change) {
                    // Le mot de passe temporaire ne vaut plus : rien à ressaisir, seulement la relecture.
                    Note(stringResource(R.string.profil_temporaire_relecture_echouee), Modifier.padding(horizontal = Mise.marge + 4.dp))
                } else {
                    // Relu d'abord : le formulaire quitté plus tôt annulerait la relecture. Le drapeau
                    // tombé, la porte se lève ; sinon la phrase remplace le formulaire.
                    ChampsMotDePasse(service, temporaire = true) {
                        relireLeCompte()
                        change = true
                    }
                }
            }
        }
    }
    ConfirmationDeSortie(sortir, sortie) { sortir = null }
}
