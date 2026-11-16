// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.res.pluralStringResource
import fr.coprofirst.app.R
import fr.coprofirst.app.session.ErreurService

/**
 * Le nombre de non lues de chaque résidence, que la cloche affiche — comme `CompteurNonLues`
 * de l'iOS. Aucune route du contrat ne sert ce nombre seul : il se COMPTE sur la liste
 * (GET /notifications). La page des notifications le note à chaque lecture et à chaque geste
 * (`noter`) ; la résidence le relit à son ouverture (`relire`). Une résidence, une entrée —
 * l'adresse de l'installation (`Installation.url`) : le nombre de l'une ne passe jamais à
 * une autre.
 *
 * Lu en composition (`nombre`), il redessine la cloche dès qu'il change.
 */
class CompteurNonLues {
    private val parResidence = mutableStateMapOf<String, Int>()

    /** Le nombre noté pour cette résidence ; 0 tant qu'aucune lecture n'a abouti. */
    fun nombre(residence: String): Int = parResidence[residence] ?: 0

    /** Note le nombre de la liste qu'on vient de lire ou de changer ; 0 éteint le point. */
    fun noter(residence: String, nombre: Int) {
        parResidence[residence] = nombre.coerceAtLeast(0)
    }

    /**
     * Relit le nombre servi (`lire` compte la liste). Un échec laisse le nombre déjà noté :
     * la cloche ne s'éteint pas faute de réseau, et l'ouverture suivante réessaie.
     */
    suspend fun relire(residence: String, lire: suspend () -> Int) {
        val nombre = try {
            lire()
        } catch (_: ErreurService) {
            return
        }
        noter(residence, nombre)
    }

    companion object {
        /** LE compteur de l'app, partagé par la cloche et la page des notifications. */
        val partage = CompteurNonLues()

        /** « 1 non lue », « 12 non lues » : sous le titre des notifications, et dit par la cloche. */
        @Composable
        fun phrase(nombre: Int): String = pluralStringResource(R.plurals.non_lues, nombre, nombre)
    }
}
