// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Les affaires d'une résidence, lues une fois et partagées par les trois onglets (Liste,
 * Kanban, Archives) : un geste fait dans l'un se voit dans les autres — comme l'iOS
 * (`EtatAffaires`). Un geste qui échoue se DIT (`avis`) et la carte revient où elle était.
 */
class EtatAffaires(private val service: ServiceAffaires) {
    sealed interface Phase {
        data object Chargement : Phase
        data object Pret : Phase
        data class Erreur(val erreur: ErreurService) : Phase
    }

    var phase by mutableStateOf<Phase>(Phase.Chargement)
        private set
    var affaires by mutableStateOf<List<Affaire>>(emptyList())
        private set

    /** Les colonnes, états et catégories SERVIS — `null` tant qu'ils ne sont pas lus. Le Kanban en dépend seul. */
    var referentiels by mutableStateOf<ReferentielsAffaires?>(null)
        private set
    var erreurReferentiels by mutableStateOf<ErreurService?>(null)
        private set
    var filtres by mutableStateOf(FiltresAffaires())
    var recherche by mutableStateOf("")
    var avecArchives by mutableStateOf(false)

    /** Les identifiants trouvés par le serveur, dans son ordre — `null` sans recherche. */
    var trouvees by mutableStateOf<List<Int>?>(null)
        private set
    var rechercheEnCours by mutableStateOf(false)
        private set
    var offrirMeConcerne by mutableStateOf(false)
        private set

    /** L'erreur d'un geste qui a échoué ; l'écran la dit, puis l'efface. */
    var avis by mutableStateOf<ErreurService?>(null)

    /** Les options « Suivi » : celles des statuts présents parmi les affaires actives. */
    val optionsDeSuivi: List<OptionDeSuivi>
        get() = OptionDeSuivi.presentes(affaires.filterNot { it.archivee }, referentiels?.statuts.orEmpty())

    val liste: List<Affaire> get() = ReglesAffaires.liste(affaires, filtres, optionsDeSuivi, trouvees, avecArchives)

    val archives: List<AnneeDArchives> get() = ReglesAffaires.archives(affaires)

    val auKanban: List<Affaire> get() = ReglesAffaires.auKanban(affaires)

    fun cartes(colonne: ColonneKanban): List<Affaire> = auKanban.filter { it.colonne == colonne.id }

    /** Combien d'affaires archivées la recherche a trouvées — pour les proposer. */
    val archiveesTrouvees: Int
        get() {
            val ids = trouvees ?: return 0
            val archivees = affaires.filter { it.archivee }.mapTo(HashSet()) { it.id }
            return ids.count { it in archivees }
        }

    suspend fun charger() = coroutineScope {
        if (affaires.isEmpty()) phase = Phase.Chargement
        val offrir = async { service.offrirMeConcerne() }
        val servis = async { lire { service.referentiels() } }
        when (val lues = lire { service.affaires() }) {
            is Lu.Ok -> {
                affaires = lues.valeur
                phase = Phase.Pret
            }
            is Lu.Echec -> phase = Phase.Erreur(lues.erreur)
        }
        when (val lus = servis.await()) {
            is Lu.Ok -> {
                referentiels = lus.valeur
                erreurReferentiels = null
            }
            is Lu.Echec -> erreurReferentiels = lus.erreur
        }
        offrirMeConcerne = offrir.await()
        retomberSurUneOptionPresente()
    }

    /**
     * Une recherche qui attend la fin de la frappe. Appelée par un `LaunchedEffect(recherche)` :
     * une frappe neuve ANNULE la précédente, si bien que seule la dernière réponse compte.
     */
    suspend fun chercher() {
        val terme = recherche.trim()
        if (terme.length < ReglesAffaires.RECHERCHE_MINIMALE) {
            trouvees = null
            rechercheEnCours = false
            return
        }
        rechercheEnCours = true
        try {
            delay(ReglesAffaires.DELAI_DE_FRAPPE_MS)
            trouvees = service.rechercher(terme)
            rechercheEnCours = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErreurService) {
            rechercheEnCours = false
            avis = e
        }
    }

    /** 📦 Archiver, et non supprimer : la carte part aux Archives aussitôt, et y revient si le serveur refuse. */
    suspend fun archiver(affaire: Affaire) {
        remplacer(affaire.id) { it.copy(archivee = true) }
        try {
            // La réponse du geste peut se lire sans ses prestataires : on garde ceux qu'on lisait.
            val lue = service.archiver(affaire.id)
            remplacer(affaire.id) { lue.copy(prestataires = lue.prestataires.ifEmpty { affaire.prestataires }) }
        } catch (e: ErreurService) {
            remplacer(affaire.id) { affaire }
            avis = e
        }
    }

    /**
     * Déplacer une carte au Kanban : une Suite d'état sans texte, qui n'avertit personne. La
     * carte rejoint aussitôt la colonne visée ; l'état est celui que les référentiels SERVIS
     * rangent dans cette colonne. Rend vrai si le serveur l'a accepté.
     */
    suspend fun deplacer(affaire: Affaire, statut: ReferentielsAffaires.Statut): Boolean {
        if (statut.code == affaire.statut) return false
        remplacer(affaire.id) { it.copy(statut = statut.code, statutLibelle = statut.libelle, colonne = statut.colonne) }
        val ok = try {
            service.deplacer(affaire.id, statut.code)
            true
        } catch (e: ErreurService) {
            remplacer(affaire.id) { affaire }
            avis = e
            false
        }
        retomberSurUneOptionPresente()
        return ok
    }

    /** Le nombre d'affaires qu'une clôture pourrait absorber ; un échec n'empêche pas de clore. */
    suspend fun candidatesALaFusion(affaire: Affaire): Int = (lire { service.candidatesALaFusion(affaire.id) } as? Lu.Ok)?.valeur ?: 0

    /** L'affaire créée par « Nouvelle affaire » : en tête de la liste. */
    fun ajouter(affaire: Affaire) {
        affaires = listOf(affaire) + affaires.filterNot { it.id == affaire.id }
    }

    private fun remplacer(id: Int, changer: (Affaire) -> Affaire) {
        affaires = affaires.map { if (it.id == id) changer(it) else it }
    }

    /**
     * ⚠️ Un filtre retenu sur un état qui vient de disparaître laisserait un écran vide ET
     * plus aucune pastille pour en sortir : on retombe sur « Toutes ».
     */
    private fun retomberSurUneOptionPresente() {
        val suivi = filtres.suivi
        if (suivi != null && optionsDeSuivi.none { it.code == suivi }) filtres = filtres.copy(suivi = null)
        if (!offrirMeConcerne && filtres.meConcerne) filtres = filtres.copy(meConcerne = false)
    }

    private sealed interface Lu<out T> {
        data class Ok<T>(val valeur: T) : Lu<T>
        data class Echec(val erreur: ErreurService) : Lu<Nothing>
    }

    private suspend fun <T> lire(lecture: suspend () -> T): Lu<T> = try {
        Lu.Ok(lecture())
    } catch (e: ErreurService) {
        Lu.Echec(e)
    }
}
