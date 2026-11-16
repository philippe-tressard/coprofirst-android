// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.apropos

import fr.coprofirst.app.R
import fr.coprofirst.app.navigation.EcranDeMenu
import fr.coprofirst.app.navigation.FicheDeMenu
import fr.coprofirst.app.navigation.InscriptionDeMenu

/** Ce que ce menu inscrit au registre des menus, depuis SON paquet (#109) : la page et ses deux fiches. */
object InscriptionAPropos : InscriptionDeMenu {
    override val ecrans: Map<String, EcranDeMenu> = mapOf(
        "a-propos" to { contexte -> EcranAPropos(contexte) },
    )

    override val fiches: Map<String, FicheDeMenu> = mapOf(
        FichesAPropos.TEXTE_LEGAL to FicheDeMenu(R.string.apropos_informations_legales) { contexte, id -> FicheTexteLegal(contexte, id) },
        FichesAPropos.COMPOSANT to FicheDeMenu(R.string.apropos_composant) { contexte, nom -> FicheComposantTiers(contexte, nom) },
    )
}
