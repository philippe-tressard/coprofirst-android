// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.charte

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Les mesures de l'app (`Mise`) face à celles du site (#2), comme l'iOS (`MesuresTests`).
 *
 * Une mesure dont le site a le jeton se GÉNÈRE (`MesuresDuSite`, scripts/generer_mesures.py) ;
 * les autres sont PROPRES À L'APP et se déclarent ici, avec leur raison et leur date. Refusé :
 * une mesure de `Mise` non déclarée, un écart que le site a rendu caduc (même valeur : il faut
 * prendre la sienne), un jeton du site qui a bougé depuis la décision (à redécider).
 */
class MesuresTest {
    /** Une mesure propre à l'app ; `ecart` : le jeton du site qu'elle écarte, et sa valeur à la décision. */
    private data class MesurePropre(val valeur: Dp, val ecart: Ecart?, val raison: String, val depuis: String)

    private data class Ecart(val jeton: String, val auSite: Dp, val aLaDecision: Dp)

    private val propres: Map<String, MesurePropre> = mapOf(
        "rayon" to MesurePropre(
            Mise.rayon, Ecart("--radius", MesuresDuSite.rayon, 8.dp),
            "14 dp comme l'iOS (Mise.rayon, coins continus à l'échelle des cartes du système) — le site arrondit à 0,5 rem",
            "16/11/2026",
        ),
        "marge" to MesurePropre(Mise.marge, null, "la marge latérale des écrans, comme l'iOS ; le site n'a pas de jeton (1,25 rem dans `.card`)", "16/11/2026"),
        "cible" to MesurePropre(Mise.cible, null, "48 dp, la règle d'Android (44 pt sur iOS) ; le site écrit 44 px en dur, sans jeton", "16/11/2026"),
        "ecart" to MesurePropre(Mise.ecart, null, "l'espace entre deux cartes d'une liste, comme l'iOS ; sans jeton au site", "16/11/2026"),
        "mosaique" to MesurePropre(Mise.mosaique, null, "la mosaïque des photos d'un élément ouvert, comme l'iOS ; sans jeton au site", "16/11/2026"),
        "mosaiqueCompacte" to MesurePropre(Mise.mosaiqueCompacte, null, "la mosaïque sous une suite ou un message, comme l'iOS ; sans jeton au site", "16/11/2026"),
        "vignette" to MesurePropre(Mise.vignette, null, "la vignette d'une carte du fil, comme l'iOS ; sans jeton au site", "16/11/2026"),
    )

    /** Les mesures de `Mise`, par réflexion (hors `$stable` du compilateur Compose) : une mesure ajoutée sans déclaration fait échouer le test. */
    private val mesuresDeMise: Set<String> =
        Mise::class.java.declaredFields.filter { !it.isSynthetic && it.name != "INSTANCE" && "$" !in it.name && Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()

    @Test
    fun chaqueMesureDeMiseEstDeclaree() {
        assertEquals("une mesure de Mise sans déclaration, ou une déclaration sans mesure", mesuresDeMise, propres.keys)
        propres.forEach { (nom, m) -> assertNotEquals("$nom : une raison et une date", "", m.raison + m.depuis) }
    }

    @Test
    fun unEcartAuSiteResteUnChoixEtSonJetonNAPasBouge() {
        propres.forEach { (nom, m) ->
            val ecart = m.ecart ?: return@forEach
            assertEquals("$nom : ${ecart.jeton} a bougé au site depuis la décision du ${m.depuis} — à redécider", ecart.aLaDecision, ecart.auSite)
            assertNotEquals("$nom : même valeur que ${ecart.jeton} — l'écart est caduc, prendre celle du site", ecart.auSite, m.valeur)
        }
    }
}
