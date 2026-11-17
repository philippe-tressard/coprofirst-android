// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #274 : ce que la visionneuse montre dans l'app, par l'extension du nom ; le reste part dans l'app de l'appareil. */
class VisionnableTest {
    @Test
    fun unPdfEtUneImageSeMontrentDansLApp() {
        assertEquals(Visionnable.Pdf, Visionnable.de("reglement.pdf"))
        assertEquals(Visionnable.Pdf, Visionnable.de("PV-AG-2026.PDF"))
        assertEquals(Visionnable.Image, Visionnable.de("plan.jpeg"))
        assertEquals(Visionnable.Image, Visionnable.de("facade.webp"))
    }

    @Test
    fun leResteEtUnNomSansExtensionPartentAilleurs() {
        assertNull(Visionnable.de("contrat.docx"))
        assertNull(Visionnable.de("releve.xlsx"))
        assertNull("le nom par défaut d'un rapport : décidé au fichier lu", Visionnable.de("rapport-12"))
        assertNull(Visionnable.de(""))
    }
}
