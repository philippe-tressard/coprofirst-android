// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.acces

/**
 * Les deux familles d'accès — badge Vigik, télécommande — et leur code servi, comme l'iOS
 * (`TypeAcces`). Communes : « Mes lots & accès » (#18) et l'Espace CS (Badges, la file de
 * travail) les emploient toutes deux (#79).
 */
enum class TypeAcces(val code: String) {
    Vigik("vigik"),
    Telecommande("telecommande"),
    ;

    val libelle: String get() = LibellesAcces.typeAcces(code)
}
