// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.onglets.CadreAOnglets
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.onglets.PageAOnglets
import fr.coprofirst.app.commun.texte.Separateur
import fr.coprofirst.app.navigation.ContexteDeMenu

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesPrestataires {
    const val RECHERCHE = "prestataires.recherche"
    const val ARCHIVES = "prestataires.archives"
    fun prestataire(id: Int) = "prestataire.$id"
    fun moyenne(id: Int) = "prestataire.$id.moyenne"
    fun contrat(id: Int) = "contrat.$id"
    fun remplacement(contrat: Int, rang: Int) = "contrat.$contrat.remplacement.$rang"
    fun compteur(type: String) = "compteur.$type"
}

/**
 * « Prestataires » (page `prestataires` du site, #19), comme l'iOS (`PrestatairesView`) :
 * trois onglets — Prestataires, Contrats, Consommations —, réservés au conseil
 * syndical. Les fiches, les contrats et les équipements se lisent une fois pour les trois
 * onglets (`DepotPrestataires`), et se relisent en tirant pour actualiser.
 *
 * ⚠️ Divergence déclarée : le cadre commun (`CadreAOnglets`) pose la barre d'onglets, là où
 * l'iOS garde sa propre mise en page (`BarreOnglets`) — même ordre, mêmes repères.
 */
@Composable
fun EcranPrestataires(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServicePrestatairesApi(contexte.session) }
    EcranPrestataires(contexte, service)
}

@Composable
internal fun EcranPrestataires(contexte: ContexteDeMenu, service: ServicePrestataires) {
    val depot = remember(service) { DepotPrestataires(service) }
    val page = remember(depot) {
        PageAOnglets(
            "prestataires",
            mapOf(
                "prestataires" to { onglet -> AvecDonnees(onglet, depot) { d, gestes -> OngletPrestataires(d, service, gestes) } },
                "contrats" to { onglet -> AvecDonnees(onglet, depot) { d, gestes -> OngletContrats(d, service, onglet, gestes) } },
                "consommations" to { onglet ->
                    AvecDonnees(onglet, depot) { d, _ -> OngletConsommations(d, service, onglet, conseil = onglet.droits.cs) }
                },
            ),
            reserve = R.string.prestataires_reserve,
        )
    }
    CadreAOnglets(contexte, page)
}

/**
 * Les données partagées, puis l'onglet ; et, pour le conseil, ce qui reste sur le site. Les
 * gestes des avis (#209) tiennent leur droit du compte SERVI (`est_moderateur`, `droits.cs`)
 * et relisent la liste après coup, sans repasser par le squelette.
 */
@Composable
private fun AvecDonnees(
    onglet: ContexteOnglet,
    depot: DepotPrestataires,
    contenu: @Composable (DonneesPrestataires, GestesAvis) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        Chargement(
            charger = { depot.donnees(onglet.actualisation) },
            cle = onglet.actualisation,
            squelette = { SquelettePrestataires() },
        ) { donnees, recharger ->
            val gestes = remember(onglet.droits, onglet.avis, recharger) {
                GestesAvis(onglet.droits.cs, onglet.avis) {
                    depot.oublier()
                    recharger()
                }
            }
            contenu(donnees, gestes)
        }
        if (onglet.droits.cs) {
            Text(
                stringResource(R.string.prestataires_gestes_sur_le_site),
                style = MaterialTheme.typography.bodySmall,
                color = LocalCouleurs.current.textMuted,
            )
        }
    }
}

@Composable
private fun SquelettePrestataires() {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        repeat(LIGNES_SQUELETTE) {
            Carte {
                Text("Un prestataire de la résidence", style = MaterialTheme.typography.titleMedium)
                Text("Maintenance${Separateur.point}Ascenseurs", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private const val LIGNES_SQUELETTE = 4
