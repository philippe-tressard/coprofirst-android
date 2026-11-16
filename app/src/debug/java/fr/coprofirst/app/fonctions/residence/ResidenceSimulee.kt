// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.perimetres.Perimetre
import fr.coprofirst.app.session.ErreurService

/**
 * « Ma résidence » simulée, pour les tests d'interface (variante de mise au point
 * seulement, jamais livrée) — les données inventées de l'iOS (`Apercu+Residence`) : une
 * résidence, des documents et des personnes qui n'existent pas. Le service réel, lui, est
 * éprouvé par les tests JVM contre les réponses JSON du serveur.
 */
class ResidenceSimulee(private val acces: Boolean = true) : ServiceResidence {
    /** Les questions reçues par `poser`. */
    val posees = mutableListOf<String>()

    override suspend fun fiche() = FICHE
    override suspend fun carnet(perimetre: String?) =
        ContenuCarnet(if (perimetre == "bat-a") CARNET.filter { it.equipement == "ascenseur" } else CARNET, EQUIPEMENTS)
    override suspend fun perimetres() = listOf(Perimetre("bat-a", "Bâtiment A"), Perimetre("bat-b", "Bâtiment B"))
    override suspend fun accesReglement() = acces
    override suspend fun reglement() = EtatReglement(disponible = true, texteDisponible = true) to listOf(QUESTION, QUESTION_AVANT)

    override suspend fun poser(question: String): QuestionReglement {
        posees += question
        if (question.contains("refus")) throw ErreurService.Refus("L'assistant est momentanément indisponible.", 503)
        return QUESTION_AVANT.copy(id = 99, question = question)
    }

    companion object {
        private fun jour(iso: String) = FormatsDuSite.lire(iso)

        val FICHE = ContenuFiche(
            fiche = FicheResidence(
                nom = "Résidence Les Tilleuls", adresse = "12 allée des Tilleuls, 00000 Ville-Exemple",
                immatriculation = "AA0-000-000", lotsPrincipaux = 48, lotsTotal = 112, parkingsCommuns = 4,
                assurance = "Mutuelle Exemple", assuranceEcheance = jour("2027-03-31"), assuranceReconduit = true,
                syndic = "Cabinet Exemple Gestion", syndicEcheance = jour("2026-06-30"), syndicEchu = true,
            ),
            batiments = listOf(Batiment(1, "A", 18, 24, 24, 1), Batiment(2, "B", 14, 22, 23, 0)),
            regles = listOf(
                RegleResidence(1, "Tri des déchets", "<p>Les bacs <strong>jaunes</strong> reçoivent les emballages.</p><ul><li>Sortie des bacs le mardi soir</li></ul>"),
                RegleResidence(2, "Travaux bruyants", "<p>En semaine de 8 h à 19 h. Jamais le dimanche.</p>"),
            ),
            plans = listOf(
                DocumentResidence(10, "Plan de masse de la résidence", "plan-masse.pdf"),
                DocumentResidence(11, "Plan du sous-sol", "sous-sol.pdf", description = "Caves et locaux techniques.", batimentId = 1),
            ),
            reglements = listOf(
                DocumentResidence(20, "Règlement de copropriété", "reglement.pdf", description = "Version consolidée.", date = jour("2019-05-14")),
            ),
            comptesRendus = listOf(
                DocumentResidence(30, "PV de l'assemblée générale ordinaire", "pv-ag-2026.pdf", annee = 2026, date = jour("2026-06-12")),
            ),
            diagnostics = listOf(
                TypeDiagnostic(
                    1, "Diagnostic technique global", "Article L.731-1 du Code de la construction et de l'habitation.",
                    "Tous les 10 ans", false,
                    listOf(RapportDiagnostic(5, "DTG — rapport final", "dtg.pdf", jour("2024-11-04"), "<p>Bon état général.</p>")),
                ),
                TypeDiagnostic(3, "Termites", "Hors zone délimitée.", null, true, emptyList()),
            ),
            annexes = listOf(
                DocumentResidence(40, "Calendrier de collecte", "collecte.pdf", regle = 1),
                DocumentResidence(41, "Annexe : plan des réseaux", "reseaux.pdf", parent = 11),
            ),
        )

        val EQUIPEMENTS = mapOf("chaufferie" to "Chaufferie")

        val CARNET = listOf(
            EntreeCarnet("2026-10-28", "Révision annuelle de la chaudière", "intervention", detail = "Remplacement du brûleur.", equipement = "chaufferie"),
            EntreeCarnet("2026-09-15", "Contrat d'entretien de l'ascenseur", "contrat", equipement = "ascenseur", alerte = "visite attendue le 01/10/2026"),
            EntreeCarnet("2026-08-02", "Fuite sur la colonne d'eau froide", "affaire", numero = "A-0029", equipement = "chaufferie", lien = "/tickets/29"),
            EntreeCarnet("2025-12-03", "Remplacement d'un luminaire du hall", "affaire", numero = "A-0012", lien = "/tickets/12"),
        )

        /** Un avis à renvois : R3 sans extrait, R2 non retrouvé mot pour mot. */
        val QUESTION = QuestionReglement(
            id = 1, question = "Puis-je installer une climatisation sur mon balcon ?",
            reponse = "<p>Oui, <strong>sous conditions</strong> : une autorisation de l'assemblée générale [R1].</p>",
            reponseSansRenvois = "<p>Oui, <strong>sous conditions</strong> : une autorisation de l'assemblée générale.</p>",
            verdictLibelle = "Sous conditions", verdictTon = "alerte", poseeLe = jour("2026-10-20T08:05:00")!!,
            selonReglement = "<p>L'aspect extérieur ne se modifie pas sans l'accord de l'assemblée [R1].</p>",
            selonAG = "<p>La résolution 12 autorise les unités extérieures [AG1].</p>",
            reserves = "<p>Le bruit de l'unité reste soumis aux règles de voisinage.</p>",
            provenance = "le règlement de copropriété de 2019", modele = "l'assistant",
            extraits = listOf(
                ExtraitReglement(
                    "Aucune modification de l'aspect extérieur ne pourra être faite sans l'autorisation de l'assemblée générale.",
                    verifie = true, reference = "Art. 9", acte = "Règlement de copropriété", rubrique = "Chapitre II", page = "p. 14",
                    source = "reglement", renvoi = "R1",
                ),
                ExtraitReglement("Les balcons restent à l'usage exclusif du lot.", verifie = false, reference = "Art. 4", source = "reglement", renvoi = "R2"),
                ExtraitReglement(
                    "L'assemblée autorise l'installation d'unités extérieures sur les balcons donnant sur cour.",
                    verifie = true, acte = "AG du 12 juin 2024", rubrique = "Résolution 12", page = "p. 3", source = "ag", renvoi = "AG1",
                ),
            ),
            renvoisSansExtrait = listOf("R3"),
        )

        /** Un avis d'avant les renvois : il se lit section après section. */
        val QUESTION_AVANT = QuestionReglement(
            id = 2, question = "Puis-je poser une jardinière sur le garde-corps ?",
            reponse = "<p><strong>Non</strong> : le règlement interdit tout objet en surplomb de la façade.</p>",
            reponseSansRenvois = "<p><strong>Non</strong> : le règlement interdit tout objet en surplomb de la façade.</p>",
            verdictLibelle = "Non", verdictTon = "danger", poseeLe = jour("2026-06-02T18:40:00")!!,
            selonReglement = "<p>L'article 11 interdit les objets suspendus aux garde-corps.</p>", dansLaFAQ = true,
            extraits = listOf(
                ExtraitReglement("Il ne pourra être suspendu aucun objet aux garde-corps des balcons.", verifie = true, reference = "Art. 11", source = "reglement"),
            ),
        )
    }
}
