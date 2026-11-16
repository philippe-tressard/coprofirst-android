// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.Recherche
import fr.coprofirst.app.commun.texte.TexteDuSite
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ce que les trois onglets partagent, chargé une fois, comme l'iOS (`DonneesPrestataires`) :
 * les fiches et les contrats en cours, les équipements (libellés et ordre, servis).
 */
data class DonneesPrestataires(
    val prestataires: List<Prestataire>,
    val contrats: List<Contrat>,
    val equipements: List<Equipement>,
) {
    /** `equipLabel` : le libellé servi, ou le code brut s'il est inconnu. */
    fun equipement(code: String): String = equipements.firstOrNull { it.code == code }?.libelle ?: code

    fun contrats(de: Prestataire): List<Contrat> = contrats.filter { it.prestataireId == de.id }

    fun prestataire(id: Int?): Prestataire? = prestataires.firstOrNull { it.id == id }

    companion object {
        /** Les équipements ne bloquent pas : sans eux, l'écran reste lisible (codes bruts). */
        suspend fun lire(service: ServicePrestataires): DonneesPrestataires = coroutineScope {
            val prestataires = async { service.prestataires(archives = false) }
            val contrats = async { service.contrats(archives = false) }
            val equipements = async {
                try {
                    service.equipements()
                } catch (_: ErreurService) {
                    emptyList()
                }
            }
            DonneesPrestataires(prestataires.await(), contrats.await(), equipements.await())
        }
    }
}

/**
 * Les données partagées, gardées le temps de la page : un onglet ouvert après un autre ne
 * relit pas ce que le premier a lu ; « tirer pour actualiser » (une `actualisation` neuve)
 * relit tout, comme un geste sur un avis (`oublier`, #209 : le nombre d'avis est servi
 * avec la liste).
 */
class DepotPrestataires(private val service: ServicePrestataires) {
    private val verrou = Mutex()
    private var lues: Pair<Int, DonneesPrestataires>? = null

    @Volatile private var perimees = false

    suspend fun donnees(actualisation: Int): DonneesPrestataires = verrou.withLock {
        lues?.takeIf { it.first == actualisation && !perimees }?.second
            ?: DonneesPrestataires.lire(service).also {
                lues = actualisation to it
                perimees = false
            }
    }

    /** La prochaine lecture repart du serveur. */
    fun oublier() {
        perimees = true
    }
}

/**
 * Les règles d'affichage de « Prestataires », testées sur la JVM.
 *
 * ⚠️ Aucune RÈGLE métier du site n'est recopiée (retard d'une visite, moyenne des avis,
 * équipement d'un contrat, prochaine visite d'un prestataire), comme l'iOS : l'écran montre
 * la donnée servie ou rien.
 */
object ReglesPrestataires {
    /** Le libellé d'une catégorie ; une catégorie inconnue s'affiche brute. */
    fun type(code: String?): String =
        if (code == null) FormatsDuSite.TIRET else TablesPrestataires.types.firstOrNull { it.code == code }?.libelle ?: code

    /** La recherche : tous les mots, sans accents ni casse, sur ce que la fiche affiche. */
    fun correspond(prestataire: Prestataire, equipement: String, recherche: String): Boolean {
        val contacts = prestataire.contacts.flatMap { listOf(it.prenom, it.nom, it.fonction, it.telephone, it.email) }
        val fiche = listOf(
            prestataire.nom, equipement, type(prestataire.type), prestataire.telephone, prestataire.email,
            prestataire.adresse, prestataire.description?.let(TexteDuSite::apercu),
        )
        return Recherche.contientTousLesMots(fiche + contacts, recherche)
    }

    /** `filtrerPrestataires` : catégorie, contrat (parmi les contrats en cours), recherche. */
    fun filtrer(
        liste: List<Prestataire>,
        donnees: DonneesPrestataires,
        categorie: String?,
        contrat: String?,
        recherche: String,
    ): List<Prestataire> {
        val sousContrat = donnees.contrats.map { it.prestataireId }.toSet()
        return liste.filter { p ->
            (categorie == null || p.type == categorie) &&
                (contrat == null || (contrat == SOUS_CONTRAT) == (p.id in sousContrat)) &&
                correspond(p, donnees.equipement(p.specialite), recherche)
        }
    }

    /** Les fiches par catégorie, dans l'ordre du site ; celles d'une catégorie inconnue à la fin. */
    fun parCategorie(liste: List<Prestataire>): List<Pair<TypePrestataire?, List<Prestataire>>> {
        val connus = TablesPrestataires.types.mapNotNull { t -> liste.filter { it.type == t.code }.takeIf { it.isNotEmpty() }?.let { t to it } }
        val autres = liste.filter { p -> TablesPrestataires.types.none { it.code == p.type } }
        return if (autres.isEmpty()) connus else connus + (null to autres)
    }

    /** La visite la plus proche d'abord, les contrats sans échéance à la fin (dates ISO). */
    val parEcheance: Comparator<Contrat> = compareBy(nullsLast()) { it.prochaineVisite }

    /** `groupesParEquipement` : l'ordre des équipements servis, puis l'alphabet ; dedans, par échéance. */
    fun groupes(donnees: DonneesPrestataires, contrats: List<Contrat> = donnees.contrats): List<Pair<String, List<Contrat>>> {
        val rang = { code: String -> donnees.equipements.firstOrNull { it.code == code }?.ordre ?: Int.MAX_VALUE }
        return contrats.groupBy { it.typeEquipement }
            .toList()
            .sortedWith(compareBy<Pair<String, List<Contrat>>> { rang(it.first) }.thenBy { donnees.equipement(it.first) })
            .map { (code, liste) -> code to liste.sortedWith(parEcheance) }
    }

    /** Le compte des contrats sans échéance (le résumé en tête de l'onglet). */
    fun sansEcheance(contrats: List<Contrat>): Int = contrats.count { it.prochaineVisite == null }

    /** Les relevés par année, les années récentes d'abord ; dans une année, l'ordre de l'API. */
    fun parAnnee(releves: List<Releve>): List<Pair<Int?, List<Releve>>> =
        releves.groupBy { it.dateReleve.take(ANNEE).toIntOrNull() }
            .toList()
            .sortedByDescending { it.first ?: Int.MIN_VALUE }

    /** Le numéro d'un lien d'appel : chiffres et « + » seulement ; `null` sans chiffre. */
    fun numeroAppel(telephone: String?): String? = telephone?.filter { it.isDigit() || it == '+' }?.takeIf { it.any(Char::isDigit) }

    const val SOUS_CONTRAT = "sous_contrat"
    private const val ANNEE = 4
}
