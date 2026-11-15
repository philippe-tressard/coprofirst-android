// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.fonctions.monlot.LibellesMonLot
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « Comptes & accès » (onglet `validations`), comme l'iOS (`OngletComptesCS`) : la FILE DE
 * TRAVAIL du conseil — deux compteurs, les comptes à valider, puis les demandes de badges et
 * de télécommandes. Chaque ligne s'approuve ou se rejette ; l'approbation d'un compte
 * demande s'il s'agit d'un nouvel arrivant (feuille), celle d'une commande les codes remis.
 */
@Composable
fun OngletValidations(contexte: ContexteOnglet) {
    val service = remember(contexte.session) { ServiceValidationsApi(contexte.session) }
    OngletValidations(service, contexte.avis, contexte.actualisation)
}

/** La même file, sur un service donné : « À traiter » du Paramétrage (#26) l'ouvre aussi. */
@Composable
fun OngletValidations(service: ServiceValidations, avis: AvisDuGeste, actualisation: Int) {
    Chargement(charger = { service.file() }, cle = actualisation, squelette = { SqueletteFile() }) { file, _ ->
        val etat = remember(file) { EtatValidations(service, file) }
        FileValidations(etat, avis)
    }
}

/** Ce qui attend un geste : le compte à approuver (feuille), à rejeter, la commande à accepter, à refuser. */
private sealed interface Demande {
    data class Approuver(val compte: CompteEnAttente) : Demande
    data class RejeterCompte(val compte: CompteEnAttente) : Demande
    data class Accepter(val commande: CommandeEnAttente) : Demande
    data class RejeterCommande(val commande: CommandeEnAttente) : Demande
}

@Composable
private fun FileValidations(etat: EtatValidations, avis: AvisDuGeste) {
    val portee = rememberCoroutineScope()
    val ressources = LocalResources.current
    var demande by remember { mutableStateOf<Demande?>(null) }
    var echec by remember { mutableStateOf<ErreurService?>(null) }

    /** Un geste : son annonce en cas de succès, la phrase du serveur sinon. */
    fun geste(faire: suspend () -> String) {
        portee.launch {
            try {
                val annonce = faire()
                echec = null
                avis.dire(annonce)
            } catch (e: ErreurService) {
                echec = e
            }
        }
    }

    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Compteurs(etat.comptes.size, etat.commandes.size)
        echec?.let { MessageErreur(it) }
        Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            TitreFile(R.string.espacecs_titre_comptes)
            if (etat.comptes.isEmpty()) Vide(R.string.espacecs_aucun_compte)
            etat.comptes.forEach { compte ->
                CarteCompte(compte, etat.enCours == compte.id, { demande = Demande.Approuver(compte) }, { demande = Demande.RejeterCompte(compte) })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            TitreFile(R.string.espacecs_titre_commandes)
            if (etat.commandes.isEmpty()) Vide(R.string.espacecs_aucune_commande)
            etat.commandes.forEach { commande ->
                CarteCommande(commande, etat.enCours == commande.id, { demande = Demande.Accepter(commande) }, { demande = Demande.RejeterCommande(commande) })
            }
        }
    }

    when (val d = demande) {
        is Demande.Approuver -> FeuilleValidationCompte(
            d.compte,
            valider = { accueil -> etat.valider(d.compte, accueil, ressources.getString(R.string.espacecs_accueil_envoye)) },
            fermer = {
                demande = null
                avis.feuilleFermee()
            },
            fait = { annonce -> annonce?.let(avis::direApresFermeture) },
        )
        is Demande.RejeterCompte -> ConfirmationRejet(stringResource(R.string.espacecs_rejeter_compte, d.compte.nomAffiche), { demande = null }) {
            geste {
                etat.rejeter(d.compte)
                ressources.getString(R.string.espacecs_compte_rejete)
            }
        }
        is Demande.RejeterCommande -> ConfirmationRejet(stringResource(R.string.espacecs_rejeter_commande, d.commande.demandeurNom), { demande = null }) {
            geste {
                etat.traiter(d.commande, codes = null)
                ressources.getString(R.string.espacecs_commande_rejetee)
            }
        }
        is Demande.Accepter -> AccepterCommande({ demande = null }) { saisie ->
            geste {
                etat.traiter(d.commande, codesSaisis(saisie))
                ressources.getString(R.string.espacecs_commande_approuvee)
            }
        }
        null -> Unit
    }
}

/** Les deux compteurs du haut : en alerte quand la file n'est pas vide ; empilés aux grandes tailles. */
@Composable
private fun Compteurs(comptes: Int, commandes: Int) {
    val grand = LocalDensity.current.fontScale >= 1.5f
    if (grand) {
        Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            Compteur(comptes, R.string.espacecs_comptes_a_valider, ReperesEspaceCS.COMPTEUR_COMPTES, Modifier.fillMaxWidth())
            Compteur(commandes, R.string.espacecs_demandes_d_acces, ReperesEspaceCS.COMPTEUR_COMMANDES, Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            Compteur(comptes, R.string.espacecs_comptes_a_valider, ReperesEspaceCS.COMPTEUR_COMPTES, Modifier.weight(1f))
            Compteur(commandes, R.string.espacecs_demandes_d_acces, ReperesEspaceCS.COMPTEUR_COMMANDES, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Compteur(valeur: Int, libelle: Int, repere: String, modifier: Modifier) {
    val c = LocalCouleurs.current
    val alerte = valeur > 0
    val forme = RoundedCornerShape(Mise.rayon)
    Column(
        modifier
            .background(if (alerte) c.warningFond else c.surface, forme)
            .border(1.dp, if (alerte) c.warningBordure else c.border, forme)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(repere),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(FormatsDuSite.nombre(valeur), style = Typo.titre(MaterialTheme.typography.displaySmall), color = if (alerte) c.warningTexte else c.text)
        Text(stringResource(libelle), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}

@Composable
private fun TitreFile(texte: Int) {
    Text(
        stringResource(texte),
        style = Typo.titre(MaterialTheme.typography.titleLarge),
        color = LocalCouleurs.current.text,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
private fun Vide(texte: Int) {
    Text(stringResource(texte), style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
}

/** Un compte en attente : qui, quel statut, quel bâtiment, depuis quand — et ses deux gestes. */
@Composable
private fun CarteCompte(compte: CompteEnAttente, enCours: Boolean, approuver: () -> Unit, rejeter: () -> Unit) {
    val c = LocalCouleurs.current
    CarteDeFile(ReperesEspaceCS.compte(compte.id), enCours, approuver, rejeter) {
        Text(compte.nomAffiche, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(
            listOfNotNull(Roles.statut(compte.statut).libelle, compte.batimentNom).joinToString(" — "),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
        )
        compte.nomAideAffiche?.let { aide ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = c.textMuted)
                Text(stringResource(R.string.espacecs_aide, aide), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
        }
        Text(FormatsDuSite.dateCourte(compte.creeLe), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

/** Une demande de badge ou de télécommande : le demandeur, le lot, le type, la quantité. */
@Composable
private fun CarteCommande(commande: CommandeEnAttente, enCours: Boolean, approuver: () -> Unit, rejeter: () -> Unit) {
    val c = LocalCouleurs.current
    CarteDeFile(ReperesEspaceCS.commande(commande.id), enCours, approuver, rejeter) {
        Text(commande.demandeurNom, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(
            listOfNotNull(commande.batiment, commande.lot, LibellesMonLot.typeAcces(commande.type), FormatsDuSite.nombre(commande.quantite))
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
        )
        Text(FormatsDuSite.dateCourte(commande.creeLe), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

/** Le rendu factice de la file, le temps du chargement. */
@Composable
private fun SqueletteFile() {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        repeat(3) {
            Carte {
                Text("Prénom Nom du demandeur", style = MaterialTheme.typography.titleMedium)
                Text("Statut — Bâtiment", style = MaterialTheme.typography.bodyMedium)
                Text("01/01/2026", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
