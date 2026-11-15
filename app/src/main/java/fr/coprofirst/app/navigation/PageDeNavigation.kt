// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Une page de navigation du site (`PagesParDefaut`, générée), comme l'iOS. */
data class PageDeNavigation(
    val id: String,
    val route: String,
    val libelle: String,
    val titre: String,
    /** Le nom Lucide de son icône (`Pictogrammes`). */
    val icone: String,
)

/** Ce que l'administration a écrit pour une page (`page_config_<id>` de GET /config). */
@Serializable
data class SurchargePage(val navLabel: String? = null, val titre: String? = null) {
    companion object {
        /** Lue depuis le JSON servi ; `null` s'il est illisible. */
        fun lire(json: String?): SurchargePage? {
            val objet = runCatching { Json.parseToJsonElement(json ?: return null) as? JsonObject }.getOrNull() ?: return null
            fun texte(cle: String) = (objet[cle] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
            return SurchargePage(texte("navLabel"), texte("titre"))
        }
    }
}

/** Les libellés courts de la barre du bas, comme l'iOS (`LibellesBarre`). */
object LibellesBarre {
    const val ID_FIL = "tableau-de-bord"
    private val courts = mapOf(
        ID_FIL to PagesParDefaut.TITRE_DU_FIL,
        "residence" to "Résidence",
        "mes-demandes" to "Affaires",
        "mon-lot" to "Accès",
    )

    /** Le libellé écrit par l'administration s'il diffère du défaut, sinon le court, sinon le défaut. */
    fun libelle(defaut: PageDeNavigation, surcharge: SurchargePage?): String {
        surcharge?.navLabel?.takeIf { it != defaut.libelle }?.let { return it }
        surcharge?.titre?.takeIf { it != defaut.titre }?.let { return it }
        return courts[defaut.id] ?: defaut.libelle
    }
}

/** L'ordre de la barre, réglé par l'administration (`pages_order`), comme l'iOS (`OrdrePages`). */
object OrdrePages {
    /** Les pages dans l'ordre servi ; celles qu'il ne nomme pas suivent, dans l'ordre par défaut. */
    fun ordonner(
        pages: List<PageDeNavigation>,
        ordre: List<String>?,
        surcharges: Map<String, SurchargePage>,
    ): List<PageDeNavigation> {
        val restantes = pages.toMutableList()
        val rangees = (ordre ?: emptyList()).mapNotNull { id ->
            restantes.firstOrNull { it.id == id }?.also { restantes.remove(it) }
        }
        return (rangees + restantes).map { defaut ->
            val surcharge = surcharges[defaut.id]
            defaut.copy(libelle = LibellesBarre.libelle(defaut, surcharge), titre = surcharge?.titre ?: defaut.titre)
        }
    }

    /** `pages_order` servi : un tableau JSON, ou des identifiants séparés par des virgules. */
    fun lire(valeur: String?): List<String>? {
        if (valeur.isNullOrEmpty()) return null
        val json = runCatching { Json.parseToJsonElement(valeur) as? JsonArray }.getOrNull()
        if (json != null) return json.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        return valeur.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
