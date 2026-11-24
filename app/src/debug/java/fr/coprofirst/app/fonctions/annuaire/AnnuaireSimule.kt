// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.annuaire

import fr.coprofirst.app.commun.annuaire.Annuaire
/**
 * L'annuaire simulé, pour les tests d'interface (variante de mise au point seulement) —
 * celui de l'iOS (`Apercu.annuaire`) : des prénoms et des noms qui n'appartiennent à
 * personne, des adresses en `.test`.
 */
class AnnuaireSimule : ServiceAnnuaire {
    override suspend fun annuaire() = ANNUAIRE

    companion object {
        val ANNUAIRE = Annuaire(
            cs = Annuaire.ConseilSyndical(
                agAnnee = 2026, agDate = "2026-06-12",
                membres = listOf(
                    Annuaire.Membre(1, "Mme", "Alix", "Exemple", "Alix EXEMPLE", batimentNom = "A", estGestionnaireSite = true, estPresident = true),
                    Annuaire.Membre(2, "M.", "Basile", "Fictif", "Basile FICTIF", batimentNom = "A"),
                    Annuaire.Membre(3, "Mme", "Céleste", "Inventé", "Céleste INVENTÉ", batimentNom = "B"),
                    Annuaire.Membre(4, "M.", "Désiré", "Modèle", "Désiré MODÈLE"),
                ),
            ),
            syndic = Annuaire.Syndic(
                nomSyndic = "Cabinet Exemple Gestion", adresse = "1 place de l'Exemple, 00000 Ville-Exemple",
                siteWeb = "https://syndic.exemple.test",
                membres = listOf(
                    Annuaire.Membre(
                        10, "Mme", "Elsa", "Gestion", "Elsa GESTION", fonction = "Gestionnaire de copropriété",
                        email = "gestion@syndic.exemple.test", telephones = listOf("01 99 00 00 61", "06 39 98 00 02"), estPrincipal = true,
                    ),
                    Annuaire.Membre(
                        11, "M.", "Firmin", "Compta", "Firmin COMPTA", fonction = "Comptabilité",
                        email = "compta@syndic.exemple.test", telephones = listOf("01 99 00 00 63"),
                    ),
                ),
            ),
            whatsappUrl = "https://chat.whatsapp.example/exemple",
        )
    }
}
