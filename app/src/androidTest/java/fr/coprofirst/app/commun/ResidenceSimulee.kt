// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import fr.coprofirst.api.VERSION_CONTRAT_PLANCHER
import fr.coprofirst.app.session.Installation

/**
 * La résidence simulée des tests d'interface : elle SERT LE PLANCHER, comme toute installation
 * qui s'ouvre, et comme la résidence simulée de l'iOS (#311). Sans contrat, ce que le plancher
 * offre par défaut — signaler, masquer — se cacherait, et un test affirmerait l'absence d'un
 * geste que la vraie app montre (la leçon de coprofirst-ios#333). `verifier-depot.sh` refuse
 * toute autre `Installation(` dans les tests d'interface.
 */
fun residenceSimulee(
    url: String = "https://residence.exemple.test",
    nom: String = "Les Glycines",
    suspendue: Boolean = false,
): Installation = Installation(url, nom, versionContrat = VERSION_CONTRAT_PLANCHER, suspendue = suspendue)
