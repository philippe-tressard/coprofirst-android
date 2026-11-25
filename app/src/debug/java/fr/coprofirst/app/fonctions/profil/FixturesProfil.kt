// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

/**
 * Ce qu'une résidence sert à l'écran Profil, en JSON, à la forme des routes — pour les
 * tests JVM et d'interface (variante de mise au point seulement, jamais livrée). Les dates
 * sont SANS fuseau, comme le serveur les sert (#44). Les
 * conséquences sont celles du serveur (`CONSEQUENCES` de `utils/demande_suppression`,
 * coprofirst v3.89.0), reprises de l'iOS (`FixturesSuppressionCompte`). Identité fictive.
 */
object FixturesProfil {
    /** GET /auth/me → `UserRead`. */
    const val MOI = """{
      "id": 7, "nom": "Exemple", "prenom": "Camille", "email": "camille@exemple.test",
      "statut": "copropriétaire_résident", "role": "conseil_syndical", "roles": ["propriétaire", "conseil_syndical"],
      "acces_menu": ["tous", "resident", "cs", "cs-ou-aidant"],
      "actif": true, "onboarding_complete": true, "onboarding_etape": 0,
      "preferences_notifications": "{\"autres_batiments_mail\": true}", "restreindre_a_mes_batiments": false,
      "cree_le": "2026-03-02T10:00:00", "nom_affiche": "Camille EXEMPLE", "nom_aide_affiche": "",
      "telephone": "+33 6 39 98 00 00", "batiment_nom": "Bât. A", "libelle_logement": "Bât. A, T3, 2ème",
      "derniere_connexion": "2026-11-14T18:30:00", "opt_out_telemetrie": false, "est_moderateur": true
    }"""

    /** GET /auth/demande-suppression, sans demande en cours → `EtatDemandeSuppression`. */
    const val ETAT_SANS_DEMANDE = """{
      "delai_annonce_jours": 30,
      "consequences": [
        "Votre compte et vos données personnelles sont effacés : profil, liens à vos lots, badges et demandes, notifications, sessions, petites annonces, avis, délégations et mandats.",
        "Ce que vous avez écrit dans la vie collective de la résidence — affaires et leurs messages, votes, soutiens, réponses dans les échanges, documents publiés — reste, sans votre nom : il est attribué à « Ancien résident ».",
        "L'effacement est définitif. Vous pouvez retirer votre demande tant que l'administration ne l'a pas traitée.",
        "L'administration de la résidence la traite sous 30 jours au plus."
      ]
    }"""

    /** POST /auth/demande-suppression, la demande ouverte → `DemandeSuppressionRead`. */
    const val DEMANDE = """{"id": 1, "statut": "ouverte", "cree_le": "2026-11-12T09:00:00", "traitee_le": null, "delai_annonce_jours": 30}"""

    /** GET /auth/demande-suppression, la demande en cours → `EtatDemandeSuppression`. */
    const val ETAT_AVEC_DEMANDE = """{"delai_annonce_jours": 30, "consequences": [], "demande": $DEMANDE}"""

    /** Le refus du serveur pour un mot de passe faux (400) → erreur. */
    const val MOT_DE_PASSE_INCORRECT = """{"detail": "Mot de passe incorrect."}"""
}

/**
 * Les publics du menu qu'un compte simulé reçoit (`acces_menu` de GET /auth/me, #333), tels que
 * le serveur les sert (coprofirst v3.124.0, `acces_du_menu`) : une page s'affiche si son public en
 * est. Pour les tests qui posent des droits à la main, compte par compte, comme l'iOS.
 */
object AccesSimules {
    /** Un résident (propriétaire, locataire) : les pages de tous et la Communauté. */
    val RESIDENT = setOf("tous", "resident")

    /** Un membre du conseil syndical : en plus, ses outils et les délégations. */
    val CONSEIL = setOf("tous", "resident", "cs", "cs-ou-aidant")

    /** Un administrateur qui a un lot : en plus des pages d'un résident, Admin. */
    val ADMINISTRATEUR = setOf("tous", "resident", "admin")

    /** Le compte technique (administrateur ou opérateur seul) : Admin seulement. */
    val TECHNIQUE = setOf("admin")

    /** Un proche aidant : les pages de tous et les délégations, sans la Communauté. */
    val AIDANT = setOf("tous", "cs-ou-aidant")
}
