// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.perimetres.ServicePerimetres
import fr.coprofirst.app.commun.perimetres.ServicePerimetresApi
import fr.coprofirst.app.commun.texte.Separateur
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * « Écrans du hall » (onglet `ecrans`, `OngletEcrans.svelte`, #283), comme l'iOS
 * (`OngletEcransCS`, coprofirst-ios#295) : appairer un écran avec le code qu'il affiche, voir
 * quand il a lu les annonces pour la dernière fois, le retirer. La forme est celle du site :
 * l'action primaire en tête, la liste et ses trois états, le retrait confirmé en rouge.
 */
@Composable
fun OngletEcransCS(contexte: ContexteOnglet) {
    val service = remember(contexte.session) { ServiceEcransCSApi(contexte.session) }
    val perimetres = remember(contexte.session) { ServicePerimetresApi(contexte.session) }
    OngletEcransCS(service, perimetres, contexte.avis, contexte.actualisation)
}

@Composable
fun OngletEcransCS(service: ServiceEcransCS, perimetres: ServicePerimetres?, avis: AvisDuGeste, actualisation: Int) {
    val ressources = LocalResources.current
    var feuille by remember { mutableStateOf(false) }
    // La liste se relit quand l'écran a eu le temps de venir chercher son accès (il interroge
    // toutes les 5 s, `RELECTURE_APRES_APPAIRAGE_MS` du site).
    var appairages by remember { mutableIntStateOf(0) }
    var relectures by remember { mutableIntStateOf(0) }
    LaunchedEffect(appairages) {
        if (appairages > 0) {
            delay(RELECTURE_APRES_APPAIRAGE)
            relectures++
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val geste = gesteEnLigne()
        BoutonPlein(R.string.ecrans_appairer, geste.modifier.testTag(ReperesEcrans.APPAIRER), actif = geste.actif) { feuille = true }
        ListeDesEcrans(service, avis, actualisation to relectures)
    }
    if (feuille) {
        FeuilleAppairage(
            service, perimetres,
            fermer = {
                feuille = false
                // La phrase laissée par la feuille se dit une fois la feuille fermée.
                avis.feuilleFermee()
            },
        ) { appaire ->
            avis.direApresFermeture(ressources.getString(R.string.ecrans_appaire, appaire.nom))
            appairages++
        }
    }
}

@Composable
private fun ListeDesEcrans(service: ServiceEcransCS, avis: AvisDuGeste, cle: Any) {
    val portee = rememberCoroutineScope()
    val ressources = LocalResources.current
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    var aRetirer by remember { mutableStateOf<EcranDuHall?>(null) }
    Chargement(charger = { service.ecrans() }, cle = cle, squelette = { SqueletteEcrans() }) { ecrans, recharger ->
        Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            erreur?.let { MessageErreur(it) }
            if (ecrans.isEmpty()) {
                EtatVide(
                    stringResource(R.string.ecrans_vide_titre),
                    Modifier.testTag(ReperesEcrans.VIDE),
                    icone = Icons.Outlined.Tv,
                    explication = stringResource(R.string.ecrans_vide_message),
                )
            }
            ecrans.forEach { ecran -> CarteEcran(ecran) { aRetirer = ecran } }
        }
        aRetirer?.let { ecran ->
            AlertDialog(
                onDismissRequest = { aRetirer = null },
                title = { Text(stringResource(R.string.ecrans_retirer_titre)) },
                text = { Text(stringResource(R.string.ecrans_retirer_message, ecran.nom)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            aRetirer = null
                            erreur = null
                            portee.launch {
                                try {
                                    service.retirer(ecran.id)
                                    avis.dire(ressources.getString(R.string.ecrans_retire))
                                    recharger()
                                } catch (e: ErreurService) {
                                    erreur = e
                                }
                            }
                        },
                        modifier = Modifier.testTag(ReperesEcrans.RETIRER_CONFIRMER),
                    ) { Text(stringResource(R.string.ecrans_retirer), color = LocalCouleurs.current.danger) }
                },
                dismissButton = { TextButton(onClick = { aRetirer = null }) { Text(stringResource(R.string.espacecs_annuler)) } },
            )
        }
    }
}

/** Un écran : son nom, son lieu, « Échu » s'il l'est, sa dernière lecture — et « Retirer ». */
@Composable
private fun CarteEcran(ecran: EcranDuHall, retirer: () -> Unit) {
    val c = LocalCouleurs.current
    val lu = ecran.vuLe?.let { stringResource(R.string.ecrans_derniere_lecture, FormatsDuSite.dateHeure(it)) }
        ?: stringResource(R.string.ecrans_aucune_lecture)
    Carte(Modifier.testTag(ReperesEcrans.ecran(ecran.id))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📺 ${ecran.nom}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = c.text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ecran.lieu?.let { PastilleTeinte(it, "badge-gray", compacte = true) }
                    if (ecran.echu) {
                        PastilleTeinte(stringResource(R.string.ecrans_echu), "badge-yellow", Modifier.testTag(ReperesEcrans.echu(ecran.id)), compacte = true)
                    }
                }
                Text(
                    lu + Separateur.point + stringResource(R.string.ecrans_appaire_le, FormatsDuSite.dateCourte(ecran.creeLe)),
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
            val geste = gesteEnLigne()
            val libelle = stringResource(R.string.ecrans_retirer_libelle, ecran.nom)
            TextButton(
                onClick = retirer,
                enabled = geste.actif,
                modifier = geste.modifier.heightIn(min = Mise.cible).semantics { contentDescription = libelle }.testTag(ReperesEcrans.retirer(ecran.id)),
            ) { Text(stringResource(R.string.ecrans_retirer), color = if (geste.actif) c.danger else c.textMuted) }
        }
    }
}

@Composable
private fun SqueletteEcrans() {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        repeat(2) {
            Carte {
                Text("📺 Hall du bâtiment", style = MaterialTheme.typography.titleMedium)
                Text("Dernière lecture : 18 nov. 2026, 08:42", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Le temps qu'un écran met à venir chercher son accès, puis la liste se relit une fois. */
private val RELECTURE_APRES_APPAIRAGE = 8.seconds

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesEcrans {
    const val APPAIRER = "ecrans.appairer"
    const val VIDE = "ecrans.vide"
    const val RETIRER_CONFIRMER = "ecrans.retirer.confirmer"
    const val FEUILLE = "ecrans.feuille"
    const val FEUILLE_CODE = "ecrans.feuille.code"
    const val FEUILLE_NOM = "ecrans.feuille.nom"
    const val FEUILLE_PERIMETRE = "ecrans.feuille.perimetre"
    const val FEUILLE_ENREGISTRER = "ecrans.feuille.enregistrer"
    fun ecran(id: Int) = "ecran.$id"
    fun echu(id: Int) = "ecran.$id.echu"
    fun retirer(id: Int) = "ecran.$id.retirer"
}
