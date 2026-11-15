// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.coprofirst.app.commun.PhraseAvis
import fr.coprofirst.app.commun.TonAvis
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** La file chargée : comptes à valider, demandes d'accès. */
data class FileDeTravail(val comptes: List<CompteEnAttente>, val commandes: List<CommandeEnAttente>)

/** Les deux lectures de la file, ensemble, comme l'iOS (`async let`). */
suspend fun ServiceValidations.file(): FileDeTravail = coroutineScope {
    val comptes = async { comptesEnAttente() }
    val commandes = async { commandesEnAttente() }
    FileDeTravail(comptes.await(), commandes.await())
}

/**
 * La file de « Comptes & accès » et les quatre gestes du conseil, comme l'iOS
 * (`EtatComptesCS`). Un geste réussi retire l'élément de la file, comme le site ; un refus
 * (une `ErreurService` levée) garde la file, et l'écran dit la phrase du serveur.
 */
class EtatValidations(private val service: ServiceValidations, file: FileDeTravail) {
    var comptes by mutableStateOf(file.comptes)
        private set
    var commandes by mutableStateOf(file.commandes)
        private set

    /** L'élément dont un geste est en cours : ses boutons attendent. */
    var enCours by mutableStateOf<Int?>(null)
        private set

    /**
     * Valider, puis — case « Nouvel Arrivant » cochée — lancer l'accueil. Rend l'annonce du
     * SERVEUR avec son ton : un avertissement (« ⚠️ Aucun lot trouvé dans l'import ») reste
     * un avertissement. `accueilEnvoye` : la phrase ajoutée quand l'accueil est parti.
     */
    suspend fun valider(compte: CompteEnAttente, accueil: AccueilArrivant?, accueilEnvoye: String): PhraseAvis? = geste(compte.id) {
        val annonce = service.traiterCompte(compte.id, valider = true)
        comptes = comptes.filterNot { it.id == compte.id }
        if (accueil == null) return@geste annonce
        service.accueillir(compte.id, accueil)
        PhraseAvis(listOfNotNull(annonce?.texte, accueilEnvoye).joinToString(" "), annonce?.ton ?: TonAvis.Succes)
    }

    suspend fun rejeter(compte: CompteEnAttente) = geste(compte.id) {
        service.traiterCompte(compte.id, valider = false)
        comptes = comptes.filterNot { it.id == compte.id }
    }

    /** Accepter (`codes` : ceux des badges remis, éventuellement aucun) ou refuser (`null`). */
    suspend fun traiter(commande: CommandeEnAttente, codes: List<String>?) = geste(commande.id) {
        service.traiterCommande(commande.id, codes)
        commandes = commandes.filterNot { it.id == commande.id }
    }

    private suspend fun <T> geste(id: Int, faire: suspend () -> T): T {
        enCours = id
        try {
            return faire()
        } finally {
            enCours = null
        }
    }
}
