// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

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
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.onglets.BarreOnglets
import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.navigation.ActionsDeBarre
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.navigation.ouverts
import fr.coprofirst.app.session.ErreurService

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesAffaires {
    const val RECHERCHE = "affaires.recherche"
    const val FILTRES = "affaires.filtres"
    const val INCLURE_ARCHIVES = "affaires.inclureArchives"
    const val ME_CONCERNE = "affaires.meConcerne"
    const val KANBAN_COMPTE = "kanban.compte"
    const val CLORE_SANS_FUSION = "kanban.clore"
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
 * Une carte ouvre la fiche de son affaire par la porte (`Destination.Element("affaire", id)`) :
 * la barre du haut en prend le titre et la flèche de retour.
 */
@Composable
fun EcranAffaires(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceAffairesApi(contexte.session) }
    val adresse = contexte.session.installation.url
    EcranAffaires(service, contexte.barre, contexte.droits, { id -> OngletsSurLeSite.lien("/tickets/$id", adresse) }) { id ->
        contexte.ouvrir(Destination.Element(NATURE_AFFAIRE, id.toString()))
    }
}

/**
 * La fiche d'une affaire, déclarée à la porte (`RegistreDesMenus.fiches`, nature `affaire`) :
 * ouverte depuis une carte, le fil d'activité ou une notification.
 */
@Composable
fun EcranFicheAffaire(contexte: ContexteDeMenu, id: String) {
    val service = remember(contexte.session) { ServiceAffairesApi(contexte.session) }
    val avis = remember { AvisDuGeste() }
    AvisEphemere(avis, Modifier.fillMaxSize().background(LocalCouleurs.current.bg)) {
        val numero = id.toIntOrNull()
        val adresse = contexte.session.installation.url
        val fiche = remember(service, contexte.droits, avis) {
            ContexteFiche(service, contexte.session, contexte.droits.cs, avis) { OngletsSurLeSite.lien("/tickets/$it", adresse) }
        }
        if (numero == null) MessageErreur(ErreurService.Introuvable, Modifier.padding(Mise.marge)) else FicheAffaire(numero, fiche)
    }
}

@Composable
internal fun EcranAffaires(
    service: ServiceAffaires,
    barre: BarreDuHaut,
    droits: DroitsOnglet,
    lienSite: (Int) -> String?,
    ouvrir: (Int) -> Unit,
) {
    val etat = remember(service) { EtatAffaires(service) }
    val avis = remember { AvisDuGeste() }
    var onglet by rememberSaveable { mutableStateOf(ONGLET_LISTE) }
    val onglets = remember(droits) { OngletsDuSite.ouverts(PAGE, droits) }
    // Un onglet que la réserve retire ne s'atteint pas (la redirection du site).
    val actif = onglet.takeIf { id -> onglets.any { it.id == id } } ?: ONGLET_LISTE
    var nouvelle by rememberSaveable { mutableStateOf(false) }
    val contexteOnglet = remember(etat) { ContexteAffaires(etat, lienSite, { nouvelle = true }) { ouvrir(it.id) } }

    LaunchedEffect(etat) { etat.charger() }
    // Un geste qui échoue se dit dans l'avis, puis s'efface.
    val echec = etat.avis?.phrase()
    LaunchedEffect(echec) {
        if (echec != null) {
            avis.dire(echec, TonAvis.Erreur)
            etat.avis = null
        }
    }
    // La barre du haut : les filtres de la Liste, puis « Nouvelle affaire ».
    ActionsDeBarre(barre) {
        if (actif == ONGLET_LISTE) MenuFiltres(etat)
        IconButton({ nouvelle = true }, Modifier.testTag(ReperesNouvelle.NOUVELLE)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.nouvelle_titre), tint = LocalCouleurs.current.primary)
        }
    }
    if (nouvelle) {
        FeuilleNouvelleAffaire(etat.referentiels, etat.erreurReferentiels, service, fermer = { nouvelle = false }) { creee ->
            etat.ajouter(creee)
            onglet = ONGLET_LISTE
        }
    }

    AvisEphemere(avis, Modifier.fillMaxSize().background(LocalCouleurs.current.bg)) {
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

/** La nature d'une affaire à la porte (celle du fil). */
const val NATURE_AFFAIRE = "affaire"

/** La page du site que porte ce menu (`pages.ts`) et ses onglets. */
internal const val PAGE = "mes-demandes"
internal const val ONGLET_LISTE = "liste"
internal const val ONGLET_KANBAN = "kanban"
internal const val ONGLET_ARCHIVES = "archives"
