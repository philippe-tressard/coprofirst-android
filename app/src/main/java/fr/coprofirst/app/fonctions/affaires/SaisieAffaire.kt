// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.api.modeles.CategorieTicket
import fr.coprofirst.api.modeles.PerimetreRead

/** Ce que « Nouvelle affaire » envoie, comme l'iOS (`SaisieAffaire`). */
data class SaisieAffaire(
    val categorie: String = "",
    val titre: String = "",
    val description: String = "",
    val urgente: Boolean = false,
    val perimetre: String? = null,
) {
    /** Un bogue concerne le site : ni lieu ni urgence (`OPTIONS_TICKET` du site). */
    val estBogue: Boolean get() = categorie == CategorieTicket.BUG.value

    /** Prête à partir : une catégorie, un titre et une description (sections requises du site). */
    val complete: Boolean get() = categorie.isNotEmpty() && titre.isNotBlank() && description.isNotBlank()

    /** Commencée : l'abandonner se confirme. */
    val entamee: Boolean get() = categorie.isNotEmpty() || titre.isNotEmpty() || description.isNotEmpty()

    /** Ce qui part : le lieu d'un bogue ne part pas. */
    val aEnvoyer: SaisieAffaire get() = if (estBogue) copy(perimetre = null, urgente = false) else this
}

/** Une option de mise en avant (`OPTIONS_TICKET`, mots d'`OPTIONS_PUBLICATION`), générée dans `TablesAffaires`. */
data class OptionAffaire(val cle: String, val action: String, val etat: String, val aide: String) {
    fun active(affaire: DetailAffaire): Boolean = if (cle == EPINGLE) affaire.epingle else affaire.urgente

    companion object {
        const val EPINGLE = "epingle"
    }
}

/**
 * Ce que « Ajouter une suite » envoie : un commentaire, ou un nouvel état — garder l'état
 * courant fait de l'entrée un simple commentaire (`EvolForm` du site).
 */
data class SaisieSuite(val statutActuel: String, val statut: String = statutActuel, val contenu: String = "") {
    val changeEtat: Boolean get() = statut != statutActuel
    val texte: String get() = contenu.trim()

    /** Le texte est requis quand l'entrée n'apporte que lui. */
    val prete: Boolean get() = changeEtat || texte.isNotEmpty()
    val type: String get() = if (changeEtat) "etat" else "commentaire"

    companion object {
        /** Les états proposés : le cycle SERVI (`proposable`) ; une actualité n'en a pas ici, son suivi reste au site. */
        fun etats(affaire: DetailAffaire): List<ReferentielsAffaires.Statut> =
            if (affaire.estActualite) emptyList() else affaire.referentiels.statuts.filter { it.proposable }
    }
}

/**
 * Une icône du bandeau d'une affaire, dans l'ordre de la rangée du site
 * (`ActionsTicket.svelte`) : 🔗 · 💬 · 🔄 · options · 📦, comme l'iOS (`GesteAffaire`).
 */
enum class GesteAffaire {
    Lien, Repondre, Suite, Options, Archiver;

    companion object {
        /**
         * Les gestes offerts à CE lecteur. Chaque droit est servi avec l'affaire ; l'app n'en
         * ajoute aucun, elle en retire : 💬 jamais au conseil syndical (il suit par 🔄), ni sur
         * une actualité ou une affaire fusionnée ; 📦 tant que l'affaire n'est pas aux Archives.
         * `parPatch` : les gestes par `PATCH /tickets/{id}`, retenus jusqu'à #99.
         */
        fun offerts(affaire: DetailAffaire, conseil: Boolean, parPatch: Boolean = ReglesAffaires.GESTES_PAR_PATCH): List<GesteAffaire> =
            entries.filter { geste ->
                when (geste) {
                    Lien -> true
                    Repondre -> affaire.droits.repondre && !conseil && !affaire.estActualite && !affaire.fusionnee
                    Suite -> affaire.droits.changerEtat
                    // Options et Archiver passent par PATCH /tickets/{id} : retenus jusqu'à #99.
                    Options -> parPatch && affaire.droits.corriger
                    Archiver -> parPatch && affaire.droits.archiver && !affaire.archivee
                }
            }
    }
}

/**
 * Un périmètre de la copropriété (`PerimetreRead`, GET /perimetres) : le lieu d'une
 * affaire. Le défaut est celui que le serveur DÉSIGNE (`par_defaut`).
 *
 * ⚠️ Pour l'heure dans le paquet des affaires : la Résidence (#17) en aura l'usage pour le
 * filtre du carnet — il montera dans la couche commune par un ticket `commun`.
 */
data class Perimetre(
    val code: String,
    val libelle: String,
    val profondeur: Int = 0,
    val selectionnable: Boolean = true,
    val parDefaut: Boolean = false,
    val parent: String? = null,
    val niveauxLibres: Int? = null,
) {
    companion object {
        /** L'arbre servi, ACTIFS seulement. */
        fun lus(servis: List<PerimetreRead>): List<Perimetre> = servis.filter { it.actif }.map {
            Perimetre(it.code, it.libelle, it.profondeur, it.selectionnable, it.parDefaut ?: false, it.parent, it.niveauxLibres)
        }

        /**
         * Les lieux qu'un choix propose : les nœuds sélectionnables, jamais sous un parent
         * qui ne peut plus rien porter (`niveaux_libres` servi à 0) — la règle se lit sur ce
         * que le serveur sert. `garder` : le lieu déjà enregistré, proposé quand même.
         */
        fun aSaisir(perimetres: List<Perimetre>, garder: String? = null): List<Perimetre> {
            val libres = perimetres.associate { it.code to it.niveauxLibres }
            return perimetres.filter { p ->
                val place = p.parent?.let { libres[it] }
                p.code == garder || (p.selectionnable && (place ?: 1) > 0)
            }
        }

        fun codeParDefaut(perimetres: List<Perimetre>): String? = perimetres.firstOrNull { it.parDefaut }?.code
    }
}
