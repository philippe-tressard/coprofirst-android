// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « Ce que j'affiche », comme l'iOS (`PreferencesView`) : la restriction aux bâtiments, puis
 * les canaux de notification (`TablesProfil.canauxNotifications`), deux cases chacun. Une
 * case jamais réglée prend le défaut du SERVEUR et le dit (« réglage par défaut ») ;
 * « Enregistrer » envoie toutes les clés, qui deviennent un choix. La feuille reste ouverte
 * et dit le résultat sous ses cases, comme l'iOS.
 *
 * ⚠️ Divergence déclarée (celle de `FeuilleProfil`) : l'iOS pousse la vue dans sa pile, avec
 * son bouton plein en bas ; ici elle s'ouvre en feuille, « Enregistrer » en haut à droite.
 */
@Composable
fun FeuillePreferences(compte: UserRead, service: ServiceProfil, fermer: () -> Unit, enregistre: (UserRead) -> Unit) {
    val couleurs = LocalCouleurs.current
    val retour = LocalHapticFeedback.current
    var lecture by remember { mutableStateOf(LecturePreferences.depuis(compte.preferencesNotifications)) }
    var restreindre by remember { mutableStateOf(compte.restreindreAMesBatiments ?: false) }
    var envoi by remember { mutableStateOf(false) }
    var resultat by remember { mutableStateOf<Phrase?>(null) }
    val portee = rememberCoroutineScope()
    FeuilleProfil(
        titre = stringResource(R.string.profil_preferences),
        fermer = fermer,
        geste = GesteDeFeuille(stringResource(R.string.profil_enregistrer), ReperesPreferences.ENREGISTRER, true, envoi) {
            portee.launch {
                envoi = true
                val json = lecture.json
                resultat = try {
                    service.enregistrerPreferences(json, restreindre)
                    enregistre(compte.copy(preferencesNotifications = json, restreindreAMesBatiments = restreindre))
                    lecture = lecture.copy(heritees = emptySet())
                    Phrase.Texte(R.string.profil_preferences_enregistrees)
                } catch (e: ErreurService) {
                    Phrase.Erreur(e)
                } finally {
                    envoi = false
                }
                retour.performHapticFeedback(if (resultat is Phrase.Erreur) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
            }
        },
    ) {
        SectionProfil(null, note = stringResource(R.string.profil_preferences_restreindre_note)) {
            Interrupteur(stringResource(R.string.profil_preferences_restreindre), restreindre, ReperesPreferences.RESTREINDRE) {
                restreindre = it
            }
        }
        TablesProfil.canauxNotifications.forEach { canal ->
            SectionProfil(canal.titre) {
                listOf(canal.mon to R.string.profil_preferences_mon, canal.autres to R.string.profil_preferences_autres).forEach { (cle, libelle) ->
                    Interrupteur(
                        stringResource(libelle),
                        lecture.valeurs[cle] ?: false,
                        ReperesPreferences.cle(cle),
                        parDefaut = cle in lecture.heritees,
                    ) { lecture = lecture.avec(cle, it) }
                }
            }
        }
        Column(Modifier.padding(horizontal = Mise.marge + 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Note(stringResource(R.string.profil_preferences_toujours))
            AnimatedVisibility(lecture.heritees.isNotEmpty()) { Note(stringResource(R.string.profil_preferences_heritees)) }
            resultat?.let { phrase ->
                Note(phrase.texte(), couleur = if (phrase is Phrase.Erreur) couleurs.danger else couleurs.successTexte)
            }
        }
    }
}

/** Une case de « Ce que j'affiche » : toute la ligne se touche ; « réglage par défaut » dessous si elle n'a jamais été réglée. */
@Composable
private fun Interrupteur(libelle: String, valeur: Boolean, repere: String, parDefaut: Boolean = false, change: (Boolean) -> Unit) {
    val couleurs = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(repere).toggleable(valeur, role = Role.Switch, onValueChange = change),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(libelle, color = couleurs.text)
            if (parDefaut) {
                Text(stringResource(R.string.profil_preferences_par_defaut), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
            }
        }
        Switch(checked = valeur, onCheckedChange = null)
    }
}

/** Les repères des tests d'interface (l'iOS n'en pose pas sur cette vue). */
object ReperesPreferences {
    const val OUVRIR = "profil.preferences"
    const val RESTREINDRE = "profil.preferences.restreindre"
    const val ENREGISTRER = "profil.preferences.enregistrer"
    fun cle(cle: String) = "profil.preferences.$cle"
}
