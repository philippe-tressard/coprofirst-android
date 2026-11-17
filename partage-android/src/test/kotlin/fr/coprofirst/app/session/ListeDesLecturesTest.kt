// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.app.session.ListeDesLectures.Companion.ID
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La liste blanche d'une app (#28, #206) : seules SES lectures se gardent, la session part encore. */
class ListeDesLecturesTest {
    private val liste = ListeDesLectures(
        routes = listOf("/notifications", "/tickets/$ID"),
        session = listOf("/auth/refresh"),
        deconnexion = "/auth/logout",
    )

    private fun get(chemin: String) = Request.Builder().url("https://r.exemple.test/api$chemin").build()

    @Test
    fun uneLectureDeLaListeSeGardeSousSonCheminServi() {
        assertEquals("/api/notifications?page=2", liste.cle(get("/notifications?page=2")))
        assertEquals("/api/tickets/42", liste.cle(get("/tickets/42")))
    }

    @Test
    fun horsDeLaListeRienNeSeGarde() {
        assertNull(liste.cle(get("/tickets/42/messages")))
        assertNull("les fichiers protégés, seulement si l'app les garde", liste.cle(get("/uploads/a.pdf")))
        assertEquals("/uploads/a.pdf", ListeDesLectures(emptyList(), fichiers = true).cle(Request.Builder().url("https://r.exemple.test/uploads/a.pdf").build()))
        val ecriture = Request.Builder().url("https://r.exemple.test/api/notifications").post(ByteArray(0).toRequestBody()).build()
        assertNull("une écriture ne se garde jamais", liste.cle(ecriture))
    }

    @Test
    fun laSessionEtLaDeconnexionSeReconnaissent() {
        assertTrue(liste.estLaSession(get("/auth/refresh")))
        assertFalse(liste.estLaSession(get("/notifications")))
        assertTrue(liste.estLaDeconnexion(get("/auth/logout")))
        assertFalse(ListeDesLectures(emptyList()).estLaDeconnexion(get("/auth/logout")))
    }
}
