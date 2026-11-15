// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import java.time.Instant

/**
 * La place des onglets pendant le chargement (`squelette`), estompée par `Chargement` —
 * comme l'iOS (`Apercu`). Rien n'y est lu : des mots qui ont la forme du contenu.
 */
internal object ApercuResidence {
    val fiche = ContenuFiche(
        fiche = FicheResidence(nom = "Résidence", adresse = "Adresse de la résidence", immatriculation = "AA0-000-000", lotsTotal = 100),
        regles = listOf(RegleResidence(1, "Une règle de la résidence", "<p>Le texte de la règle, sur quelques lignes.</p>")),
        plans = listOf(DocumentResidence(1, "Un plan de la résidence", "plan.pdf")),
    )

    val carnet = ContenuCarnet(
        listOf(
            EntreeCarnet("2026-01-01", "Une intervention", "intervention", detail = "Son détail."),
            EntreeCarnet("2026-01-01", "Un contrat", "contrat"),
        ),
    )

    val question = QuestionReglement(
        id = 0, question = "Une question posée à l'assistant ?", reponse = "", reponseSansRenvois = "<p>L'aperçu de la réponse.</p>",
        verdictLibelle = "Verdict", verdictTon = "neutre", poseeLe = Instant.EPOCH,
    )
}
