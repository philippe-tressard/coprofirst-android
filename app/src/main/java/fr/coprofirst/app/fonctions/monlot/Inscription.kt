// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import fr.coprofirst.app.navigation.EcranDeMenu
import fr.coprofirst.app.navigation.InscriptionDeMenu

/** Ce que ce menu inscrit au registre des menus, depuis SON paquet (#109). */
object InscriptionMonLot : InscriptionDeMenu {
    override val ecrans: Map<String, EcranDeMenu> = mapOf(
        "mon-lot" to { contexte -> EcranMonLot(contexte) },
    )
}
