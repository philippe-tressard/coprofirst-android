// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le registre COMPOSÉ des inscriptions des menus (#109). */
class RegistreTest {
    @Test
    fun chaqueMenuInscritSesPagesUneFois() {
        // L'initialisation refuse une page ou une fiche inscrite deux fois : la lire suffit.
        val ecrans = RegistreDesMenus.ecrans
        assertTrue("le fil est inscrit", "tableau-de-bord" in ecrans)
        assertEquals(RegistreGenere.inscriptions.sumOf { it.ecrans.size }, ecrans.size)
    }

    @Test
    fun uneInscriptionNePorteQueDesPagesDuSite() {
        // Une faute de frappe dans un identifiant ne s'ouvrirait jamais : « Bientôt » à jamais.
        val connues = (PagesParDefaut.toutes + PagesParDefaut.horsMenu).map { it.id }.toSet() + PagesDePlus.profil.id + PagesDePlus.aPropos.id
        assertEquals(emptySet<String>(), RegistreDesMenus.ecrans.keys - connues)
    }
}
