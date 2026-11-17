// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Pictogrammes
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.BandeauInstallationSuspendue
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.LocalSaisieAffaire
import fr.coprofirst.app.commun.audience.CheminsDeLApp
import fr.coprofirst.app.commun.audience.LocalMesureAudience
import fr.coprofirst.app.commun.audience.PageVue
import fr.coprofirst.app.commun.miseenpage.ColonneDeFiche
import fr.coprofirst.app.commun.miseenpage.LocalColonneDeFiche
import fr.coprofirst.app.commun.miseenpage.LocalJourDuCarnet
import fr.coprofirst.app.commun.miseenpage.PageEtColonneDeFiche
import fr.coprofirst.app.commun.miseenpage.ficheLocale
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.audience.CheminDuSite
import fr.coprofirst.app.session.audience.ResidenceMesuree

/**
 * La résidence ouverte, comme `OngletsView` de l'iOS : la barre du bas (les 4 premières
 * pages de l'ordre servi, puis « Plus »), et la page choisie, ouverte par le registre ; en
 * haut, le titre et le sous-titre que l'écran déclare (`EnTeteDeBarre`), la cloche, qui ouvre
 * les notifications en feuille (`Cloche`), avec son point « non lues » (`CompteurNonLues`).
 * La porte (`ContexteDeMenu.ouvrir`, #85) y mène d'un écran à une page, à la fiche d'un
 * élément — poussée par-dessus la page ouverte — ou à la cloche, par `Aiguillage`.
 */
@Composable
fun EcranResidence(
    session: SessionOuverte,
    sortie: GestesDeSortie,
    apparence: Apparence,
    /**
     * L'écran qu'un lien d'application vise (#147, `LienDuSite`), à l'ouverture de la
     * résidence ou dans la résidence ouverte ; `lienSuivi` le rend à l'entrée une fois posé.
     */
    lien: Destination? = null,
    lienSuivi: () -> Unit = {},
) {
    val parcours: ParcoursResidence = viewModel(
        key = session.installation.url,
        factory = viewModelFactory { initializer { ParcoursResidence(session, ServiceCompteApi(session)) } },
    )
    val etat by parcours.etat.collectAsStateWithLifecycle()
    val pages = parcours.pagesOuvertes(etat.droits)
    val barre = pages.take(ParcoursResidence.VISIBLES)
    val plus = pages.drop(ParcoursResidence.VISIBLES)
    // Une page que les droits retirent (relus) ne reste pas ouverte.
    val onglet = etat.onglet.takeIf { it == EtatResidence.PLUS || barre.any { p -> p.id == it } } ?: barre.firstOrNull()?.id
    var pageDePlus by rememberSaveable { mutableStateOf<String?>(null) }
    // La fiche ouverte par la porte : sa nature et son identifiant (`Destination.Element`).
    var ficheNature by rememberSaveable { mutableStateOf<String?>(null) }
    var ficheId by rememberSaveable { mutableStateOf<String?>(null) }
    // Le jour du Fil ouvert dans le carnet (#242) : gardé avec la page et dans le marque-page.
    val jourDuCarnet = rememberSaveable { mutableStateOf<String?>(null) }
    val barreDuHaut = remember { BarreDuHaut() }
    // La fiche ouverte dans la colonne d'une fenêtre large pose SA barre dans sa colonne (#69).
    val barreDeFiche = remember { BarreDuHaut() }
    // La colonne se sauve avec l'écran : rotation, pli, multi-fenêtre (#69, la mémoire des sélections).
    val colonne = rememberSaveable(session.installation.url, saver = ColonneDeFiche.Sauvegarde) { ColonneDeFiche() }
    var clocheOuverte by rememberSaveable { mutableStateOf(false) }
    var residencesOuvertes by rememberSaveable { mutableStateOf(false) }
    // Ce que le compte est ici, relu : retenu pour la feuille « Résidences » de l'appareil (#180).
    LaunchedEffect(etat.roles) { etat.roles?.let { sortie.noterRoles(session.installation, it.statut, it.roles) } }
    val notifications = Cloche.page
    val residence = session.installation.url
    // Le nombre de non lues de la cloche se relit à l'ouverture de la résidence.
    LaunchedEffect(parcours) { parcours.relireNonLues() }
    // La mesure d'audience (#144) suit la résidence ouverte et le choix relu du compte.
    val mesure = LocalMesureAudience.current
    DisposableEffect(mesure, parcours) {
        mesure?.courante = { ResidenceMesuree(session, parcours.etat.value.refusMesure) }
        onDispose { mesure?.courante = { null } }
    }

    val enPlus = if (onglet == EtatResidence.PLUS) pageDePlus else null
    val ouverte: PageDeNavigation? = when {
        onglet != EtatResidence.PLUS -> barre.firstOrNull { it.id == onglet }
        else -> enPlus?.let { id -> plus.firstOrNull { it.id == id } ?: PagesDePlus.fixe(id) }
    }
    val fiche = ficheNature?.let { RegistreDesMenus.fiches[it] }
    val titreFiche = fiche?.let { stringResource(it.titre) }

    fun fermerFiche() {
        ficheNature = null
        ficheId = null
    }

    fun aller(id: String) {
        parcours.choisir(id)
        pageDePlus = null
        fermerFiche()
    }
    val ouvrir: (Destination) -> Boolean = { destination ->
        val aiguillage = Aiguillage.aiguiller(destination, barre.map { it.id }, plus.map { it.id }, RegistreDesMenus.fiches.keys, notifications != null)
        // La cloche est une feuille : tout autre écran demandé la referme d'abord (l'iOS).
        if (aiguillage != Aiguillage.Aucun) clocheOuverte = aiguillage == Aiguillage.Cloche
        when (aiguillage) {
            is Aiguillage.Onglet -> aller(aiguillage.id)
            is Aiguillage.PageDePlus -> {
                aller(EtatResidence.PLUS)
                pageDePlus = aiguillage.id
            }
            is Aiguillage.Fiche -> {
                ficheNature = aiguillage.element.nature
                ficheId = aiguillage.element.id
            }
            Aiguillage.Cloche, Aiguillage.Aucun -> Unit
        }
        aiguillage != Aiguillage.Aucun
    }
    // Le marque-page d'un pliable (#242) : rouvrir où l'on avait refermé ; un lien, posé après, l'emporte.
    val ici = MarquePage(onglet ?: EtatResidence.PLUS, pageDePlus, ficheNature, ficheId, jourDuCarnet.value, colonne.occupee && ficheId != null)
    MarquePageDeLaResidence(residence, ici) { marque ->
        if (marque.onglet == EtatResidence.PLUS || barre.any { it.id == marque.onglet }) parcours.choisir(marque.onglet)
        pageDePlus = marque.pageDePlus
        ficheNature = marque.ficheNature
        ficheId = marque.ficheId
        jourDuCarnet.value = marque.jour
        if (marque.enColonne) colonne.retenir()
    }
    // Un lien d'application : l'écran visé ; une page que le compte ne voit pas, le fil (l'iOS).
    LaunchedEffect(lien) {
        val vise = lien ?: return@LaunchedEffect
        if (!ouvrir(vise)) ouvrir(LienDuSite.FIL)
        lienSuivi()
    }
    PageVue(
        when {
            fiche != null -> CheminsDeLApp.fiche(ficheNature, ficheId)
            ouverte?.id == PagesDePlus.profil.id -> CheminDuSite.PROFIL
            else -> ouverte?.let { CheminDuSite.page(it.id) }
        },
    )
    // La barre latérale ouvre toute page comme « Plus » l'ouvre : le même état (onglet, page de
    // « Plus », fiche) sert les deux présentations, et passer de l'une à l'autre garde la page.
    val choisirDansLaLaterale: (String) -> Unit = { id ->
        if (barre.any { it.id == id }) {
            aller(id)
        } else {
            aller(EtatResidence.PLUS)
            pageDePlus = id
        }
    }
    // La présentation suit la LARGEUR disponible et le PLI, jamais l'appareil (#69, #242, comme l'iOS).
    val posture = LocalPosture.current
    BoxWithConstraints {
        val presentation = PresentationNavigation.pour(maxWidth, posture)
        val laterale = presentation == PresentationNavigation.Laterale
        val carnet = presentation == PresentationNavigation.Carnet
        val large = laterale || carnet
        // Une liste occupe la colonne de fiche : sa page et la fiche qu'elle ouvre côte à côte (#69) ;
        // dans le carnet, la page de gauche et celle de droite, de part et d'autre du pli (#242).
        val enColonne = large && colonne.occupee && ouverte != null
        SideEffect { colonne.ouverte = if (enColonne && fiche != null) ficheNature.orEmpty() to ficheId.orEmpty() else null }
        // Une seule fiche dans la colonne : celle du registre (la porte) ou celle qu'une liste tient (#69).
        LaunchedEffect(ficheNature, ficheId) { if (ficheNature != null) colonne.locale?.fermer?.invoke() }
        LaunchedEffect(colonne.locale) { if (colonne.locale != null && ficheNature != null) fermerFiche() }
        val retour: (() -> Unit)? = when {
            // Dans la colonne, la fiche se remplace par une autre ; le geste « retour » du système la referme.
            fiche != null && enColonne -> null
            fiche != null -> ::fermerFiche
            // Dans la barre latérale, une page de « Plus » est une page comme une autre : pas de retour.
            enPlus != null && presentation == PresentationNavigation.Barre -> ({ pageDePlus = null })
            else -> null
        }
        BackHandler(enabled = retour != null || (enColonne && fiche != null)) { retour?.invoke() ?: fermerFiche() }
        // Le mode de saisie d'une affaire du compte, pour « Nouvelle affaire » et « Modifier » (#178).
        CompositionLocalProvider(LocalSaisieAffaire provides etat.saisieAffaire) {
            CadreResidence(
                titre = barreDuHaut.titre ?: titreFiche?.takeUnless { enColonne } ?: ouverte?.titre ?: stringResource(R.string.plus),
                sousTitre = barreDuHaut.sousTitre,
                retour = retour,
                barreDuHaut = barreDuHaut,
                cloche = {
                    PastilleResidence(session.installation) { residencesOuvertes = true }
                    if (notifications != null) BoutonCloche(notifications, CompteurNonLues.partage.nombre(residence)) { clocheOuverte = true }
                },
                bandeau = { BandeauInstallationSuspendue(session.installation) },
                laterale = if (laterale) ({ BarreLaterale(pages, ouverte?.id, choisirDansLaLaterale) }) else null,
                tranche = if (carnet) ({ Intercalaires(pages, ouverte?.id, choisirDansLaLaterale) }) else null,
                barre = {
                    if (presentation == PresentationNavigation.Barre) NavigationBar {
                        barre.forEach { page ->
                            NavigationBarItem(
                                selected = onglet == page.id,
                                onClick = { aller(page.id) },
                                icon = { Icon(Pictogrammes.icone(page.icone), contentDescription = null) },
                                label = { Text(page.libelle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                        NavigationBarItem(
                            selected = onglet == EtatResidence.PLUS,
                            onClick = { aller(EtatResidence.PLUS) },
                            icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                            label = { Text(stringResource(R.string.plus)) },
                        )
                    }
                },
            ) {
                val id = ficheId
                fun contexteDePage(page: PageDeNavigation) =
                    ContexteDeMenu(page, session, etat.droits, sortie, apparence, barreDuHaut, ouvrir, parcours::relireDroits, etat.logement)
                val contexteDeFiche: (BarreDuHaut) -> ContexteDeMenu? = { barreFiche ->
                    // La fiche reçoit une page à son titre : elle n'est pas une page de la barre.
                    titreFiche?.let {
                        val page = PageDeNavigation(ficheNature.orEmpty(), "", it, it, "")
                        ContexteDeMenu(page, session, etat.droits, sortie, apparence, barreFiche, ouvrir, parcours::relireDroits, etat.logement)
                    }
                }
                // Une fiche hors colonne (téléphone, ou page sans liste) : la barre de la page est la sienne.
                val ficheSeule = contexteDeFiche(barreDuHaut)
                CompositionLocalProvider(LocalColonneDeFiche provides colonne.takeIf { large }, LocalJourDuCarnet provides jourDuCarnet.takeIf { carnet }) {
                    when {
                        // Fenêtre large : la page garde sa place ; une liste qui l'occupe ouvre la colonne de fiche.
                        large && ouverte != null && (fiche == null || enColonne || colonne.retenueALaReconstruction) -> PageEtColonneDeFiche(
                            enColonne,
                            page = { RegistreDesMenus.Ecran(contexteDePage(ouverte)) },
                            fiche = if (fiche != null && id != null) ({
                                val contexte = contexteDeFiche(barreDeFiche)
                                if (contexte != null) FicheEnColonne(titreFiche.orEmpty(), barreDeFiche) { fiche.ecran(contexte, id) }
                            }) else colonne.ficheLocale(),
                            coupe = if (carnet) posture.coupe() else null,
                        )
                        fiche != null && id != null && ficheSeule != null -> fiche.ecran(ficheSeule, id)
                        ouverte != null -> RegistreDesMenus.Ecran(contexteDePage(ouverte))
                        else -> ListeDePlus(plus, ouvrir = { pageDePlus = it })
                    }
                }
            }
            if (residencesOuvertes) {
                FeuilleResidences(session.installation, etat.roles, sortie, parcours::relireDroits) { residencesOuvertes = false }
            }
            if (clocheOuverte && notifications != null) {
                FeuilleNotifications(
                    notifications,
                    contexte = { barre -> ContexteDeMenu(notifications, session, etat.droits, sortie, apparence, barre, ouvrir, parcours::relireDroits, etat.logement) },
                    fermer = { clocheOuverte = false },
                )
            }
        }
    }
}

/** Les entrées fixes de « Plus », comme l'iOS : Profil et À propos (leurs écrans : #21, #27). */
object PagesDePlus {
    val profil = PageDeNavigation("profil", "/profil", "Profil", "Profil", "user")
    val aPropos = PageDeNavigation("a-propos", "/a-propos", "À propos", "À propos", "info")
    private val fixes = listOf(profil, aPropos)

    fun fixe(id: String): PageDeNavigation? = fixes.firstOrNull { it.id == id }

    /** Des pages, puis Profil et À propos : « Plus », et la barre latérale (toutes les pages, #69). */
    fun suivies(pages: List<PageDeNavigation>): List<PageDeNavigation> = pages + fixes

    /** L'icône d'une entrée : celle de la page, ou celle de Profil et d'À propos. */
    fun icone(page: PageDeNavigation): ImageVector = when (page.id) {
        profil.id -> Icons.Filled.AccountCircle
        aPropos.id -> Icons.Filled.Info
        else -> Pictogrammes.icone(page.icone)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CadreResidence(
    titre: String,
    sousTitre: String?,
    retour: (() -> Unit)?,
    barreDuHaut: BarreDuHaut,
    cloche: @Composable () -> Unit,
    /** Un bandeau sous la barre, sur chaque page : l'installation suspendue (#233). */
    bandeau: @Composable () -> Unit,
    /** La barre latérale d'une fenêtre large, à gauche de la page (#69) ; `null` : la barre du bas. */
    laterale: (@Composable () -> Unit)?,
    /** Les intercalaires du carnet, sur la tranche droite d'un pliable déplié (#242). */
    tranche: (@Composable () -> Unit)?,
    barre: @Composable () -> Unit,
    contenu: @Composable () -> Unit,
) {
    Row {
        laterale?.invoke()
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                TopAppBar(
                    title = { EnTeteDeBarre(titre, sousTitre) },
                    navigationIcon = {
                        if (retour != null) {
                            IconButton(onClick = retour) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retour))
                            }
                        }
                    },
                    actions = {
                        ActionsDeclarees(barreDuHaut)
                        cloche()
                    },
                )
            },
            bottomBar = barre,
        ) { marges ->
            Column(Modifier.fillMaxSize().padding(marges)) {
                bandeau()
                Box(Modifier.weight(1f).fillMaxWidth()) { contenu() }
            }
        }
        tranche?.invoke()
    }
}
