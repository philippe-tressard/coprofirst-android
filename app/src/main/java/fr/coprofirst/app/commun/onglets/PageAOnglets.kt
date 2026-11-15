// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.onglets

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletDuSite
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.navigation.PageDeNavigation
import fr.coprofirst.app.navigation.PagesOuvertes
import fr.coprofirst.app.navigation.ouverts
import fr.coprofirst.app.session.SessionOuverte
import java.net.URI

/**
 * Ce qu'un onglet d'une page à onglets reçoit du cadre (`CadreAOnglets`), comme
 * `ContexteOnglet` de l'iOS. L'onglet rend son CONTENU (une `Column`, jamais une liste
 * paresseuse : le cadre porte le défilement, la barre d'onglets et le descriptif) et peut
 * poser ses actions dans la barre du haut (`ActionsDeBarre(contexte.barre) { … }`).
 */
data class ContexteOnglet(
    /** La page du menu (titre, route…). */
    val page: PageDeNavigation,
    /** L'onglet affiché. */
    val onglet: OngletDuSite,
    /** La session de la résidence, pour les services de l'onglet. */
    val session: SessionOuverte,
    val droits: DroitsOnglet,
    /** La phrase brève qui suit un geste : `avis.dire(…)` — le cadre la montre. */
    val avis: AvisDuGeste,
    /** Augmente à chaque « tirer pour actualiser » : l'onglet relit (`Chargement(cle = …)`). */
    val actualisation: Int,
    /** La barre du haut, où l'onglet pose ses actions. */
    val barre: BarreDuHaut,
    /** La porte (#85), celle du menu (`ContexteDeMenu.ouvrir`) : `false` si rien ne la porte. */
    val ouvrir: (Destination) -> Boolean = { false },
)

/**
 * Ce qu'une page à onglets déclare au cadre, comme `PageAOnglets` de l'iOS : son
 * identifiant du site, la TABLE de ses onglets (l'identifiant d'un onglet du site → son
 * écran, une ligne par onglet) et la phrase qui dit à qui elle est réservée.
 *
 * `renvoieAuSite` : la page dont l'app ne porte qu'une partie (le Paramétrage) — sa barre
 * n'offre que les onglets inscrits, puis « Sur le site », qui renvoie à chacun des autres
 * par son adresse du site. Sinon, un onglet non inscrit dit « Bientôt ».
 *
 * Les descriptifs et les adresses des onglets sont ceux du site (`OngletsDuSite`, générés).
 */
class PageAOnglets(
    val id: String,
    val ecrans: Map<String, @Composable (ContexteOnglet) -> Unit>,
    @StringRes val reserve: Int,
    val renvoieAuSite: Boolean = false,
    val descriptifs: Map<String, String> = OngletsDuSite.descriptifs[id].orEmpty(),
    val routes: Map<String, String> = OngletsDuSite.routes[id].orEmpty(),
)

/**
 * La barre d'une page à onglets pour un compte, comme `CadreAOnglets` de l'iOS l'établit :
 * les onglets que ce compte ouvre, dans l'ordre du site (`OngletsDuSite.ouverts`) — aucun
 * si la page lui est réservée (`PagesOuvertes`) ; sur une page qui renvoie au site, ceux
 * que l'app porte, puis « Sur le site » s'il en reste ailleurs.
 */
class OngletsDeLaPage(
    page: String,
    portes: Set<String>,
    renvoieAuSite: Boolean,
    droits: DroitsOnglet,
    surLeSite: OngletDuSite,
) {
    private val ouverts: List<OngletDuSite> =
        if (PagesOuvertes.ouverte(page, droits)) OngletsDuSite.ouverts(page, droits) else emptyList()

    /** Les onglets ouverts que l'app ne porte pas, sur une page qui renvoie au site. */
    val ailleurs: List<OngletDuSite> = if (renvoieAuSite) ouverts.filter { it.id !in portes } else emptyList()

    val barre: List<OngletDuSite> = when {
        !renvoieAuSite -> ouverts
        ailleurs.isEmpty() -> ouverts.filter { it.id in portes }
        else -> ouverts.filter { it.id in portes } + surLeSite
    }

    /** L'onglet affiché : celui choisi s'il est dans la barre, sinon le premier ; `null` : la page est réservée. */
    fun actif(choisi: String?): OngletDuSite? = barre.firstOrNull { it.id == choisi } ?: barre.firstOrNull()
}

/** Les repères des tests d'interface, ceux de l'iOS. */
object ReperesOnglets {
    fun onglet(id: String) = "onglet.$id"
    fun surLeSite(id: String) = "site.onglet.$id"
    const val RESERVEE = "onglets.reservee"
}

/** « Sur le site » : ce n'est pas un onglet du site — ni réserve ni descriptif servi. */
object OngletsSurLeSite {
    const val ID = "sur-le-site"

    /**
     * L'adresse d'un onglet sur le site de la résidence : sa route (`/admin?onglet=…`,
     * générée), sous l'adresse de l'installation (qui peut porter un chemin). `null` si
     * l'une ou l'autre ne se lit pas, ou si la route n'est pas un chemin.
     */
    fun lien(route: String, adresse: String): String? {
        val base = runCatching { URI(adresse) }.getOrNull() ?: return null
        val cible = runCatching { URI(route) }.getOrNull() ?: return null
        if (base.scheme != "https" || base.rawAuthority == null) return null
        if (cible.isAbsolute || cible.rawAuthority != null || !cible.rawPath.orEmpty().startsWith("/")) return null
        val chemin = base.rawPath.orEmpty().removeSuffix("/") + cible.rawPath
        val requete = cible.rawQuery?.let { "?$it" }.orEmpty()
        return "${base.scheme}://${base.rawAuthority}$chemin$requete"
    }
}
