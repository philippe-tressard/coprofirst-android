// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

/**
 * Les réponses des Annonces Hall et des Courriels affaires, en JSON, telles que leurs routes
 * les renvoient — comme l'iOS (`FixturesAnnoncesHall`, `ServiceCourrielsCS+Simule`). Des
 * objets et des noms inventés, aucune adresse réelle. Dans le build de débogage seulement.
 */
object FixturesAnnoncesCourriels {
    /** GET /courriels-affaires → `JournalCourriels`. */
    const val JOURNAL = """
        {"limite": 50, "messages": [
          {"id": 41, "releve_le": "2026-11-15T08:00:00", "envoye_le": "2026-11-15T07:48:00",
           "expediteur": "Cabinet Syndic Exemple", "objet": "RE: [TK-A00012] Fuite au plafond du parking",
           "decision": "accepte", "motif": "Réponse versée au fil de l'affaire TK-A00012.",
           "ticket_id": 12, "affaire": "TK-A00012"},
          {"id": 40, "releve_le": "2026-11-14T08:00:00", "envoye_le": "2026-11-14T07:30:00",
           "expediteur": "Camille Essai", "objet": "TR: Devis ascenseur TK-E000066", "decision": "refuse",
           "motif": "Aucune affaire ne porte le numéro TK-E000066 : vérifiez le numéro dans l'objet."},
          {"id": 39, "releve_le": "2026-11-14T04:00:00", "expediteur": "Cabinet Syndic Exemple",
           "objet": "Planning des visites d'entretien", "decision": "relance",
           "motif": "Sans numéro d'affaire : transmis au conseil syndical."},
          {"id": 38, "releve_le": "2026-11-13T06:00:00", "expediteur": "Notification automatique",
           "objet": "", "decision": "ignore", "motif": "Message automatique (réponse d'absence) : ignoré."}
        ]}
    """

    private fun annonce(id: Int, titre: String, archivee: Boolean, destinataires: String, extra: String = "") = """
        {"id": $id, "titre": "$titre", "message": "<p>$titre : <strong>merci de votre compréhension</strong>.</p>",
         "apercu": "$titre : merci de votre compréhension.", "perimetre_cible": ["bat_a"],
         "perimetre_label": "Bâtiment A", "perimetre_libelle": "Bât. A", "format_demande": "auto",
         "format_effectif": "a5", "format_label": "A5", "images": [], "pdf_nom": "annonce-$id.pdf",
         "destinataires": $destinataires, "archivee": $archivee, "archivee_manuellement": $archivee,
         "cree_le": "2026-11-0${id}T09:00:00", "auteur_nom": "Compte EXEMPLE",
         "peut_archiver": ${!archivee}, "peut_restaurer": $archivee, "peut_supprimer": true$extra}
    """.trimIndent()

    /** GET /annonces-hall?archivees=false → `[AnnonceHallRead]`. */
    val ACTIVES = "[" + annonce(5, "Coupure d'eau jeudi matin", false, """["Alix Exemple", "Basile Fictif"]""",
        """, "envoye_le": "2026-11-05T09:05:00"""") + "]"

    /** GET /annonces-hall?archivees=true. */
    val ARCHIVEES = "[" + annonce(2, "Nettoyage des caves", true, "[]") + "]"

    /** POST /annonces-hall → `AnnonceHallRead` : l'affiche créée, envoyée à un membre. */
    fun creee(titre: String) = annonce(9, titre, false, """["Alix Exemple"]""")
}
