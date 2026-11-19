// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

/** Les réglages de l'appareil, en mémoire : le double des tests de la montre et du push. */
class ReglagesEnMemoire : ReglagesAppareil {
    val valeurs = mutableMapOf<String, String>()

    override fun lire(cle: String) = valeurs[cle]

    override fun ecrire(cle: String, valeur: String?) {
        if (valeur == null) valeurs.remove(cle) else valeurs[cle] = valeur
    }

    override fun toutes(): Map<String, String> = valeurs.toMap()
}
