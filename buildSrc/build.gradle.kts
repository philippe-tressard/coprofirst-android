// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

/**
 * L'outillage du build commun aux modules (#206) : la génération depuis un script Python du
 * dépôt, que le téléphone et la bibliothèque partagée avec la montre emploient tous deux.
 */
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}
