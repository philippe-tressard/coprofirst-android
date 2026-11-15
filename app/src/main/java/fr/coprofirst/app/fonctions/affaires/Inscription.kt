// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.app.R
import fr.coprofirst.app.navigation.EcranDeMenu
import fr.coprofirst.app.navigation.FicheDeMenu
import fr.coprofirst.app.navigation.InscriptionDeMenu

/** Ce que ce menu inscrit au registre des menus, depuis SON paquet (#109) : la page et la fiche d'une affaire. */
object InscriptionAffaires : InscriptionDeMenu {
    override val ecrans: Map<String, EcranDeMenu> = mapOf(
        "mes-demandes" to { contexte -> EcranAffaires(contexte) },
    )

    override val fiches: Map<String, FicheDeMenu> = mapOf(
        "affaire" to FicheDeMenu(R.string.affaire_titre) { contexte, id -> EcranFicheAffaire(contexte, id) },
    )
}
