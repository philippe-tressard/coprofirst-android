// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.miseenpage.rangeeChoisie
import fr.coprofirst.app.navigation.OngletDuSite

/**
 * Les rubriques de la Fiche, dans l'ordre du site, comme `RubriqueFiche` de l'iOS : l'onglet les
 * montre à la suite, le sommaire du carnet une par page (#287). La réserve du locataire s'écrit
 * ICI, une fois : ni comptes-rendus d'AG ni diagnostics, comme sur le site.
 */
enum class RubriqueFiche(val id: String, @param:StringRes val titre: Int) {
    Identite("identite", R.string.residence_copropriete),
    Regles("regles", R.string.residence_regles),
    Plans("plans", R.string.residence_plans),
    Reglement("reglement", R.string.residence_reglement),
    ComptesRendus("comptesRendus", R.string.residence_comptes_rendus),
    Diagnostics("diagnostics", R.string.residence_diagnostics),
    ;

    companion object {
        /** Celles que ce compte lit. */
        fun ouvertes(locataire: Boolean): List<RubriqueFiche> =
            entries.filter { !locataire || (it != ComptesRendus && it != Diagnostics) }
    }
}

/**
 * Une page du sommaire de « Ma résidence » (#287, `PageDuSommaire` de l'iOS) : une rubrique de la
 * Fiche, ou un autre onglet ouvert au compte (Carnet d'entretien, Règlement). Sa CLÉ la nomme
 * (`rubrique.plans`, `onglet.carnet`) : le marque-page la garde ainsi, et c'est son repère.
 */
sealed interface PageDuSommaire {
    val cle: String

    data class Rubrique(val rubrique: RubriqueFiche) : PageDuSommaire {
        override val cle: String get() = "rubrique.${rubrique.id}"
    }

    data class Onglet(val onglet: OngletDuSite) : PageDuSommaire {
        override val cle: String get() = "onglet.${onglet.id}"
    }

    companion object {
        /**
         * Le sommaire de ce compte : les rubriques de la Fiche, puis les autres onglets ouverts
         * (`OngletsResidence.ouverts`) — rien n'y est réservé ni ouvert autrement que sur le site.
         */
        fun sommaire(locataire: Boolean, ouverts: List<OngletDuSite>): List<PageDuSommaire> =
            RubriqueFiche.ouvertes(locataire).map(::Rubrique) + ouverts.filter { it.id != OngletsResidence.FICHE }.map(::Onglet)

        /** La page ouverte : celle que nomme `cle`, sinon la première — jamais « Aucune sélection » en face du sommaire. */
        fun choisie(sommaire: List<PageDuSommaire>, cle: String?): PageDuSommaire? =
            sommaire.firstOrNull { it.cle == cle } ?: sommaire.firstOrNull()
    }
}

/** La nature de la page ouverte en face du sommaire (`rangeeChoisie`). */
internal const val NATURE_PAGE_DU_SOMMAIRE = "residence-page"

/** Le repère d'une rangée du sommaire (`residence.sommaire.rubrique.<id>` de l'iOS). */
fun repereDuSommaire(page: PageDuSommaire): String = "residence.sommaire.${page.cle}"

/**
 * Le sommaire, à gauche du pli : une rangée par page, soulignée quand sa page est ouverte en
 * face ; un filet sépare les rubriques de la Fiche des autres onglets.
 */
@Composable
internal fun Sommaire(pages: List<PageDuSommaire>, choisir: (PageDuSommaire) -> Unit) {
    val couleurs = LocalCouleurs.current
    val ouvrir = stringResource(R.string.residence_sommaire_ouvrir)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
        pages.forEachIndexed { rang, page ->
            if (page is PageDuSommaire.Onglet && pages.getOrNull(rang - 1) is PageDuSommaire.Rubrique) {
                HorizontalDivider(Modifier.padding(horizontal = Mise.marge, vertical = 8.dp), color = couleurs.border)
            }
            Text(
                titre(page),
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .heightIn(min = Mise.cible)
                    .rangeeChoisie(NATURE_PAGE_DU_SOMMAIRE, page.cle)
                    .cliquable(ouvrir) { choisir(page) }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag(repereDuSommaire(page)),
                style = MaterialTheme.typography.bodyLarge,
                color = couleurs.text,
            )
        }
    }
}

/** Le titre d'une page : celui de la rubrique ou de l'onglet sur le site. */
@Composable
internal fun titre(page: PageDuSommaire): String = when (page) {
    is PageDuSommaire.Rubrique -> stringResource(page.rubrique.titre)
    is PageDuSommaire.Onglet -> page.onglet.libelle
}
