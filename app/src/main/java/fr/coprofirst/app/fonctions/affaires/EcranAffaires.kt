// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.onglets.BarreOnglets
import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.navigation.ActionsDeBarre
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.navigation.ouverts
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.launch

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesAffaires {
    const val RECHERCHE = "affaires.recherche"
    const val FILTRES = "affaires.filtres"
    const val INCLURE_ARCHIVES = "affaires.inclureArchives"
    const val ME_CONCERNE = "affaires.meConcerne"
    const val KANBAN_COMPTE = "kanban.compte"
    const val CLORE_SANS_FUSION = "kanban.clore"
    const val RETOUR = "affaire.retour"
    fun suivi(code: String?) = "affaires.suivi.${code ?: "toutes"}"
    fun nature(code: String?) = "affaires.nature.${code ?: "toutes"}"
    fun carte(numero: String) = "affaires.carte.$numero"
    fun gestes(numero: String) = "affaires.gestes.$numero"
    fun archiver(numero: String) = "affaires.archiver.$numero"
    fun colonne(id: String) = "kanban.colonne.$id"
    fun saut(id: String) = "kanban.saut.$id"
    fun carteKanban(numero: String) = "kanban.carte.$numero"
    fun deplacer(numero: String, colonne: String) = "kanban.deplacer.$numero.$colonne"
    fun annee(annee: Int) = "archives.annee.$annee"
    fun archive(numero: String) = "archives.carte.$numero"
    fun etape(cle: EtapeFil.Cle) = "fil.etape.${cle.repere}"
    fun element(id: String) = "fil.element.$id"
    fun message(id: Int) = "affaire.message.$id"
    fun gestesSuite(id: Int) = "historique.gestes.$id"
    fun gesteSuite(geste: GesteSuite) = "historique.geste.${geste.name.lowercase()}"
    const val CONFIRMER_SUPPRESSION = "historique.confirmerSuppression"
    const val APPLIQUER = "historique.appliquer"
}

/** Les onglets de la page et le geste d'ouvrir une affaire, que chaque onglet reçoit. */
internal class ContexteAffaires(
    val etat: EtatAffaires,
    /** L'adresse de la page d'une affaire sur le site (« Partager le lien ») ; `null` si l'adresse n'est pas sûre. */
    val lienSite: (Int) -> String?,
    /** « Nouvelle affaire », offerte aussi par une liste vide. */
    val nouvelle: () -> Unit,
    val ouvrir: (Affaire) -> Unit,
)

/**
 * « Mes affaires » (page `mes-demandes`, #16), comme l'iOS (`AffairesView`) : Liste ·
 * Kanban · Archives, dans l'ordre et selon la réserve du site (`OngletsDuSite.ouverts`).
 * Une carte ouvre la fiche de son affaire, dans la page ; le retour y ramène.
 *
 * ⚠️ Divergence déclarée : la barre du haut garde le titre de la page, et le retour se dit
 * en tête de la fiche (« Retour à Mes affaires ») en plus du geste système — l'iOS pousse
 * la fiche dans sa pile, titre et chevron compris ; la barre commune ne porte pas encore
 * de retour (#89).
 */
@Composable
fun EcranAffaires(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceAffairesApi(contexte.session) }
    val adresse = contexte.session.installation.url
    EcranAffaires(service, contexte.session, contexte.barre, contexte.droits, contexte.page.titre) { id ->
        OngletsSurLeSite.lien("/tickets/$id", adresse)
    }
}

@Composable
internal fun EcranAffaires(
    service: ServiceAffaires,
    session: SessionOuverte,
    barre: BarreDuHaut,
    droits: DroitsOnglet,
    titre: String,
    lienSite: (Int) -> String?,
) {
    val etat = remember(service) { EtatAffaires(service) }
    val avis = remember { AvisDuGeste() }
    var onglet by rememberSaveable { mutableStateOf(ONGLET_LISTE) }
    var ouverte by rememberSaveable { mutableStateOf<Int?>(null) }
    val onglets = remember(droits) { OngletsDuSite.ouverts(PAGE, droits) }
    // Un onglet que la réserve retire ne s'atteint pas (la redirection du site).
    val actif = onglet.takeIf { id -> onglets.any { it.id == id } } ?: ONGLET_LISTE
    val portee = rememberCoroutineScope()
    var nouvelle by rememberSaveable { mutableStateOf(false) }
    val contexteOnglet = remember(etat) { ContexteAffaires(etat, lienSite, { nouvelle = true }) { ouverte = it.id } }
    val contexteFiche = remember(service, session, titre, droits, avis) { ContexteFiche(service, session, titre, droits.cs, avis, lienSite) }

    LaunchedEffect(etat) { etat.charger() }
    // Un geste qui échoue se dit dans l'avis, puis s'efface.
    val echec = etat.avis?.phrase()
    LaunchedEffect(echec) {
        if (echec != null) {
            avis.dire(echec, TonAvis.Erreur)
            etat.avis = null
        }
    }
    BackHandler(enabled = ouverte != null) { ouverte = null }
    // La barre du haut : les filtres de la Liste, puis « Nouvelle affaire » — pas sur la fiche.
    if (ouverte == null) {
        ActionsDeBarre(barre) {
            if (actif == ONGLET_LISTE) MenuFiltres(etat)
            IconButton({ nouvelle = true }, Modifier.testTag(ReperesNouvelle.NOUVELLE)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.nouvelle_titre), tint = LocalCouleurs.current.primary)
            }
        }
    }
    if (nouvelle) {
        FeuilleNouvelleAffaire(etat.referentiels, etat.erreurReferentiels, service, fermer = { nouvelle = false }) { creee ->
            etat.ajouter(creee)
            onglet = ONGLET_LISTE
        }
    }

    AvisEphemere(avis, Modifier.fillMaxSize().background(LocalCouleurs.current.bg)) {
        val id = ouverte
        if (id != null) {
            FicheAffaire(id, contexteFiche) {
                ouverte = null
                // La fiche a pu changer l'affaire (un geste) : la liste se relit.
                portee.launch { etat.charger() }
            }
            return@AvisEphemere
        }
        Column(Modifier.fillMaxSize()) {
            if (onglets.size > 1) {
                BarreOnglets(onglets, actif, Modifier.fillMaxWidth(), marges = PaddingValues(horizontal = Mise.marge)) { onglet = it }
            }
            Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
                when (actif) {
                    ONGLET_KANBAN -> KanbanAffaires(contexteOnglet)
                    ONGLET_ARCHIVES -> ArchivesAffaires(contexteOnglet)
                    else -> ListeAffaires(contexteOnglet)
                }
            }
        }
    }
}

/** La page du site que porte ce menu (`pages.ts`) et ses onglets. */
internal const val PAGE = "mes-demandes"
internal const val ONGLET_LISTE = "liste"
internal const val ONGLET_KANBAN = "kanban"
internal const val ONGLET_ARCHIVES = "archives"
