// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.acces

import fr.coprofirst.app.commun.LibelleTeinte

/**
 * Les libellés d'un accès, lus dans la table GÉNÉRÉE (`TablesAcces`), comme l'iOS
 * (`LibellesAcces`). Une valeur inconnue s'affiche brute : un libellé manquant doit se voir.
 */
object LibellesAcces {
    /** L'état d'un badge ou d'une télécommande (`STATUTS_ACCES`) et sa pastille. */
    fun statutAcces(code: String): LibelleTeinte = TablesAcces.statutsAcces[code] ?: LibelleTeinte.brut(code)

    /** Le libellé d'une famille d'accès (`TYPES_ACCES`). */
    fun typeAcces(code: String): String = TablesAcces.typesAcces.firstOrNull { it.first == code }?.second ?: code
}
