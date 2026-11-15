// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.api.modeles.CategorieTicket
import fr.coprofirst.api.modeles.PerimetreRead
import fr.coprofirst.api.modeles.TicketEvolutionRead
import fr.coprofirst.app.commun.texte.TexteDuSite

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

    /** Ce qui a changé depuis `affaire` — et rien d'autre ; d'un contenu figé, l'urgence seule. */
    fun edition(affaire: DetailAffaire): EditionAffaire {
        val origine = modifiant(affaire)
        val urgence = urgente.takeIf { it != origine.urgente }
        if (affaire.contenuFige) return EditionAffaire(urgente = urgence)
        val lieu = if (estBogue) null else perimetre
        return EditionAffaire(
            titre = titre.trim().takeIf { it != origine.titre.trim() },
            descriptionHtml = if (affaire.descriptionSaisie != null && description != origine.description) TexteDuSite.html(description) else null,
            categorie = categorie.takeIf { it != origine.categorie },
            urgente = urgence,
            perimetres = if (affaire.lieuModifiable && lieu != origine.perimetre) listOfNotNull(lieu) else null,
        )
    }

    /** Prête : un titre, une description si la feuille la porte, et un changement. */
    fun prete(affaire: DetailAffaire): Boolean =
        titre.isNotBlank() && (affaire.descriptionSaisie == null || description.isNotBlank()) && !edition(affaire).vide

    companion object {
        /** La saisie de « Modifier l'affaire » : l'affaire telle que le serveur la sert. */
        fun modifiant(affaire: DetailAffaire) = SaisieAffaire(
            affaire.categorie, affaire.titre, affaire.descriptionSaisie.orEmpty(), affaire.urgente, affaire.perimetres.firstOrNull(),
        )
    }
}

/**
 * Ce que « Modifier l'affaire » envoie (PATCH /tickets/{id}) : les SEULS champs changés — un
 * champ `null` ne part pas (#99), donc n'est pas réécrit. `perimetres` vide : plus aucun lieu
 * désigné, le serveur pose son défaut.
 */
data class EditionAffaire(
    val titre: String? = null,
    val descriptionHtml: String? = null,
    val categorie: String? = null,
    val urgente: Boolean? = null,
    val perimetres: List<String>? = null,
) {
    val vide: Boolean get() = this == EditionAffaire()
}

/** La description en texte simple, si la réenregistrer ne perd rien — sinon `null` : elle se modifie depuis le site. */
val DetailAffaire.descriptionSaisie: String? get() = TexteDuSite.saisi(description)

/** Le crayon servi (`peut_corriger`) sans le droit sur le contenu (`peut_modifier`) : l'urgence seule se règle. */
val DetailAffaire.contenuFige: Boolean get() = !droits.modifier

/** Un lieu au plus : le choix le porte. Plusieurs se corrigent depuis le site. */
val DetailAffaire.lieuModifiable: Boolean get() = perimetres.size <= 1

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
 * (`ActionsTicket.svelte`) : 🔗 · 💬 · 🔄 · ✏️ · options · 📦, comme l'iOS (`GesteAffaire`).
 */
enum class GesteAffaire {
    Lien, Repondre, Suite, Modifier, Options, Archiver;

    companion object {
        /**
         * Les gestes offerts à CE lecteur. Chaque droit est servi avec l'affaire ; l'app n'en
         * ajoute aucun, elle en retire : 💬 jamais au conseil syndical (il suit par 🔄), ni sur
         * une actualité ou une affaire fusionnée ; 📦 tant que l'affaire n'est pas aux Archives.
         */
        fun offerts(affaire: DetailAffaire, conseil: Boolean): List<GesteAffaire> = entries.filter { geste ->
            when (geste) {
                Lien -> true
                Repondre -> affaire.droits.repondre && !conseil && !affaire.estActualite && !affaire.fusionnee
                Suite -> affaire.droits.changerEtat
                Modifier, Options -> affaire.droits.corriger
                Archiver -> affaire.droits.archiver && !affaire.archivee
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

/** Un geste sur UNE entrée de la suite, dans l'ordre du site (`RubriqueHistorique.svelte`) : ✏️ puis 🗑️. */
enum class GesteSuite {
    Modifier, Supprimer;

    companion object {
        /** Ceux que le serveur a servis AVEC l'entrée (`peut_modifier`, `peut_supprimer`), et aucun autre. */
        fun offerts(suite: SuiteAffaire): List<GesteSuite> = entries.filter { if (it == Modifier) suite.peutModifier else suite.peutSupprimer }
    }
}

/**
 * Ce que « Modifier la suite » envoie (PATCH /tickets/{id}/evolutions/{evol_id}) : les SEULS
 * champs que la saisie a changés, comme l'iOS (`CorrectionSuite`). `contenuHtml` vide :
 * l'entrée n'a plus de texte ; `suivi` : ce qu'elle devient (`etat` et l'état donné, ou
 * `commentaire`).
 */
data class CorrectionSuite(val contenuHtml: String? = null, val suivi: Suivi? = null) {
    data class Suivi(val type: String, val statut: String?)

    val vide: Boolean get() = contenuHtml == null && suivi == null
}

/** Le texte de l'entrée en texte simple, si le réenregistrer ne perd rien — sinon `null` : il se montre sans se modifier. */
val SuiteAffaire.contenuSaisi: String? get() = TexteDuSite.saisi(contenu.orEmpty())

/**
 * La saisie de « Modifier la suite » : le Suivi part de l'état que l'entrée a donné ; l'état
 * « courant » est celui d'AVANT elle (`ancien_statut`, sinon `statut_avant`), à défaut celui de l'affaire.
 */
fun SaisieSuite.Companion.corrigeant(suite: SuiteAffaire, affaire: DetailAffaire): SaisieSuite {
    val avant = suite.ancienStatut ?: affaire.statut
    return SaisieSuite(avant, suite.nouveauStatut ?: avant, suite.contenuSaisi.orEmpty())
}

/** Le Suivi d'une entrée se corrige quand la liste sait montrer l'état d'avant ET celui qu'elle donne. */
fun SaisieSuite.suiviCorrigeable(etats: List<String>): Boolean = statutActuel in etats && statut in etats

private val SaisieSuite.suiviDeLEntree: CorrectionSuite.Suivi
    get() = if (changeEtat) CorrectionSuite.Suivi("etat", statut) else CorrectionSuite.Suivi("commentaire", null)

/** Ce qui a changé depuis `suite` — et rien d'autre. Le texte ne part que si la feuille le montrait à modifier. */
fun SaisieSuite.correction(suite: SuiteAffaire, affaire: DetailAffaire): CorrectionSuite {
    val origine = SaisieSuite.corrigeant(suite, affaire)
    val texteChange = suite.contenuSaisi != null && texte != origine.texte
    return CorrectionSuite(
        contenuHtml = if (texteChange) (if (texte.isEmpty()) "" else TexteDuSite.html(texte)) else null,
        suivi = suiviDeLEntree.takeIf { it != origine.suiviDeLEntree },
    )
}

/** Prête : ce qu'une Suite exige (un état, ou un texte — celui qu'on montre sans le modifier compte), et un changement. */
fun SaisieSuite.prete(suite: SuiteAffaire, affaire: DetailAffaire): Boolean =
    (changeEtat || texte.isNotEmpty() || suite.contenuSaisi == null) && !correction(suite, affaire).vide

/**
 * Ce que l'AFFAIRE a fait d'une correction qu'elle n'a pas suivie (`affaire_suivie: false`) :
 * SA phrase, et l'état qu'elle propose d'appliquer — l'app ne décide rien, comme l'iOS
 * (`IssueCorrection`).
 */
data class IssueCorrection(val phrase: String, val etatPropose: String?, val etatProposeLibelle: String?) {
    /** L'état à proposer : celui servi, si le compte peut changer l'état ; jamais celui où l'affaire est déjà. */
    fun proposition(affaire: DetailAffaire): SaisieSuite? {
        val etat = etatPropose ?: return null
        if (!affaire.droits.changerEtat) return null
        return SaisieSuite(affaire.statut, etat).takeIf { it.changeEtat }
    }

    /** Le libellé en clair de l'état proposé. */
    fun libelleProposition(affaire: DetailAffaire): String? =
        proposition(affaire)?.let { affaire.referentiels.libelle(it.statut, etatProposeLibelle) }

    companion object {
        /** `null` quand l'affaire a suivi, ou sans phrase servie : l'app ne compose pas la sienne. */
        fun lue(lu: TicketEvolutionRead): IssueCorrection? {
            if (lu.affaireSuivie != false) return null
            val phrase = lu.affaireSuivieLibelle?.takeIf { it.isNotBlank() } ?: return null
            return IssueCorrection(phrase, lu.etatPropose?.takeIf { it.isNotBlank() }, lu.etatProposeLibelle.takeIf { it.isNotBlank() })
        }
    }
}
