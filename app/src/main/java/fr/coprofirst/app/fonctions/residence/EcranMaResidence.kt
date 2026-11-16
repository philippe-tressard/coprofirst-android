// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.perimetres.Perimetre
import fr.coprofirst.app.commun.pieces.AvisOuverture
import fr.coprofirst.app.commun.pieces.rememberOuverture
import fr.coprofirst.app.commun.onglets.BarreOnglets
import fr.coprofirst.app.fonctions.annuaire.AncreAnnuaire
import fr.coprofirst.app.fonctions.annuaire.EcranAnnuaire
import fr.coprofirst.app.fonctions.annuaire.ServiceAnnuaire
import fr.coprofirst.app.fonctions.annuaire.ServiceAnnuaireApi
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletDuSite
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.navigation.ouverts
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesResidence {
    const val SYNDIC = "residence.syndic"
    const val PERIMETRE = "carnet.perimetre"
    const val QUESTION = "reglement.question"
    const val POSER = "reglement.poser"
    const val FONDEMENTS = "avis.fondements"
    const val FERMER = "residence.feuille.ok"
    fun question(id: Int) = "reglement.question.$id"
    fun fondement(renvoi: String) = "avis.fondement.$renvoi"
    fun section(section: SectionAvis) = "avis.section.${section.name.lowercase()}"
    fun extrait(renvoi: String?) = renvoi?.let { "avis.extrait.$it" } ?: "avis.extrait"
}

/** Les onglets que l'écran sait rendre ; leurs libellés, leur ordre et leurs réserves viennent du site. */
object OngletsResidence {
    /** L'identifiant de la page dans `pages.ts`. */
    const val PAGE = "residence"
    const val FICHE = "fiche"
    const val CARNET = "carnet"
    const val REGLEMENT = "reglement"
    private val rendus = setOf(FICHE, CARNET, REGLEMENT)

    /**
     * Les onglets que ce compte ouvre (`OngletsDuSite.ouverts`), moins le Règlement quand
     * l'administration n'a pas ouvert l'assistant (`accesReglement == false`) ; tant qu'elle
     * n'a pas répondu (`null`), l'onglet reste, comme sur le site.
     */
    fun ouverts(droits: DroitsOnglet, accesReglement: Boolean?): List<OngletDuSite> =
        OngletsDuSite.ouverts(PAGE, droits).filter { it.id in rendus && (it.id != REGLEMENT || accesReglement != false) }
}

/** Où se rangent les copies des rapports ouverts (`Ouverture.fichier`). */
private const val RAPPORTS = "rapports"

/** Ce que l'écran ouvre par-dessus lui : un avis, ou l'annuaire placé sur le syndic. */
sealed interface PageOuverte {
    data class Avis(val question: QuestionReglement) : PageOuverte
    data object Syndic : PageOuverte
}

/** « Ma résidence » (page `residence`, #17) : l'écran du registre. */
@Composable
fun EcranMaResidence(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceResidenceApi(contexte.session) }
    val annuaire = remember(contexte.session) { ServiceAnnuaireApi(contexte.session) }
    EcranMaResidence(service, annuaire, contexte.droits, contexte.session, contexte.ouvrir)
}

/**
 * « Ma résidence », comme l'iOS (`ResidenceView`) : la Fiche, le Carnet d'entretien et le
 * Règlement — ces deux derniers réservés aux propriétaires et au conseil syndical.
 *
 * ⚠️ Divergence déclarée : l'iOS POUSSE l'avis d'une question et l'annuaire du syndic dans
 * la pile de l'onglet ; ici ils s'ouvrent dans une feuille pleine hauteur (« OK », geste
 * de retour), la barre du haut n'offrant pas de retour à une sous-page d'un écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EcranMaResidence(
    service: ServiceResidence,
    annuaire: ServiceAnnuaire,
    droits: DroitsOnglet,
    session: SessionOuverte?,
    porte: (Destination) -> Boolean = { false },
) {
    var onglet by rememberSaveable { mutableStateOf(OngletsResidence.FICHE) }
    var accesReglement by remember { mutableStateOf<Boolean?>(null) }
    var relecture by rememberSaveable { mutableIntStateOf(0) }
    var destination by remember { mutableStateOf<PageOuverte?>(null) }
    LaunchedEffect(droits.proprioOuCS) {
        if (!droits.proprioOuCS) return@LaunchedEffect
        // Une réponse en échec ne masque rien : l'onglet ouvert dira l'erreur.
        accesReglement = try {
            service.accesReglement()
        } catch (_: ErreurService) {
            true
        }
    }
    val ouverts = OngletsResidence.ouverts(droits, accesReglement)
    // Un onglet qui se ferme (rôle, accès retiré) ramène sur la Fiche.
    val actif = onglet.takeIf { id -> ouverts.any { it.id == id } } ?: OngletsResidence.FICHE
    val ouverture = session?.let { rememberOuverture(it) }
    val ouvrir = OuvrirFiche(
        document = { ouverture?.document(it.id, it.fichier) },
        rapport = { ouverture?.fichier(RAPPORTS, it.id.toString(), it.fichier, "rapport-${it.id}", ServiceResidenceApi.rapport(it.id)) },
        rapportEnCours = ouverture?.enCours?.removePrefix("$RAPPORTS-")?.toIntOrNull(),
        syndic = { destination = PageOuverte.Syndic },
    )
    PullToRefreshBox(isRefreshing = false, onRefresh = { relecture++ }, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ouverts.size > 1) {
                BarreOnglets(ouverts, actif, marges = PaddingValues(horizontal = Mise.marge)) { onglet = it }
            }
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = Mise.marge, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(Mise.ecart),
            ) {
                ouverture?.let { AvisOuverture(it) }
                when (actif) {
                    OngletsResidence.CARNET -> Carnet(service, droits.cs, session?.installation?.url.orEmpty(), relecture, porte)
                    OngletsResidence.REGLEMENT -> Reglement(service, droits.cs, relecture, { onglet = OngletsResidence.FICHE }) {
                        destination = PageOuverte.Avis(it)
                    }
                    else -> Chargement(
                        charger = { service.fiche() },
                        cle = relecture,
                        squelette = { OngletFiche(ApercuResidence.fiche, null, droits.locataire, OuvrirFiche()) },
                    ) { contenu, _ ->
                        OngletFiche(contenu, session, droits.locataire, ouvrir)
                    }
                }
            }
        }
    }
    when (val ouverte = destination) {
        is PageOuverte.Avis -> FeuilleResidence(stringResource(R.string.avis_titre), { destination = null }) {
            AvisReglement(ouverte.question)
        }
        PageOuverte.Syndic -> FeuilleResidence(stringResource(R.string.annuaire_titre), { destination = null }, defile = false) {
            EcranAnnuaire(annuaire, session, AncreAnnuaire.Syndic)
        }
        null -> Unit
    }
}

@Composable
private fun Carnet(service: ServiceResidence, cs: Boolean, adresse: String, relecture: Int, porte: (Destination) -> Boolean) {
    var perimetre by rememberSaveable { mutableStateOf<String?>(null) }
    var perimetres by remember { mutableStateOf<List<Perimetre>>(emptyList()) }
    LaunchedEffect(Unit) {
        perimetres = try {
            service.perimetres()
        } catch (_: ErreurService) {
            emptyList()
        }
    }
    Chargement(
        charger = { service.carnet(perimetre) },
        cle = relecture to perimetre,
        squelette = { OngletCarnet(ApercuResidence.carnet, emptyList(), null, false, "", { false }, {}) },
    ) { carnet, _ ->
        OngletCarnet(carnet, perimetres, perimetre, cs, adresse, porte) { perimetre = it }
    }
}

@Composable
private fun Reglement(
    service: ServiceResidence,
    cs: Boolean,
    relecture: Int,
    ouvrirFiche: () -> Unit,
    ouvrir: (QuestionReglement) -> Unit,
) {
    // Une question posée ici passe en tête ; le serveur la rendra au prochain chargement.
    val nouvelles = remember { mutableStateListOf<QuestionReglement>() }
    Chargement(
        charger = { service.reglement() },
        cle = relecture,
        squelette = {
            OngletReglement(EtatReglement(true, true), listOf(ApercuResidence.question), cs, service, {}, {}, {})
        },
    ) { (etat, questions), _ ->
        val anciennes = questions.filter { q -> nouvelles.none { it.id == q.id } }
        OngletReglement(etat, nouvelles + anciennes, cs, service, ouvrirFiche, { nouvelles.add(0, it) }, ouvrir)
    }
}
