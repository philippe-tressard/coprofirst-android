// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.runtime.Composable
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.onglets.CadreAOnglets
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.onglets.PageAOnglets
import fr.coprofirst.app.navigation.ContexteDeMenu

/**
 * L'Espace conseil syndical (page `espace-cs`, #24), comme l'iOS (`EspaceCSView`) : six
 * onglets du site, dans son ordre, filtrés par le cadre selon les droits du compte. Un
 * onglet que l'app ne porte pas encore dit « Bientôt » (le cadre).
 *
 * Le service de la page est découpé PAR ONGLET, comme l'iOS (`ServiceComptesCS`,
 * `ServiceBadgesCS`…) : la file « Comptes & accès » sert aussi « À traiter » du
 * Paramétrage (#26), qui n'en prend que sa part.
 */
@Composable
fun EcranEspaceCS(contexte: ContexteDeMenu) = CadreAOnglets(contexte, PageEspaceCS)

/** La table des onglets de l'Espace CS : l'identifiant du site → son écran, une ligne par onglet. */
val PageEspaceCS = PageAOnglets(
    "espace-cs",
    mapOf<String, @Composable (ContexteOnglet) -> Unit>(
        "validations" to { onglet -> OngletValidations(onglet) },

        "badges" to { onglet -> OngletBadges(onglet) },

        "reporting" to { onglet -> OngletReporting(onglet) },

        "annonces-hall" to { onglet -> OngletAnnoncesHall(onglet) },

        "annuaire" to { onglet -> OngletAnnuaireCS(onglet) },

        "courriels" to { onglet -> OngletCourriels(onglet) },
    ),
    reserve = R.string.espacecs_reserve,
)

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesEspaceCS {
    const val COMPTEUR_COMPTES = "cs.compteur.comptes"
    const val COMPTEUR_COMMANDES = "cs.compteur.commandes"
    fun compte(id: Int) = "cs.compte.$id"
    fun commande(id: Int) = "cs.commande.$id"
    const val REJET_CONFIRMER = "cs.rejet.confirmer"
    const val COMMANDE_CODES = "cs.commande.codes"
    const val COMMANDE_ACCEPTER = "cs.commande.accepter"
    const val NOUVEL_ARRIVANT = "cs.validation.nouvel-arrivant"
    const val VALIDATION_ENREGISTRER = "cs.validation.enregistrer"

    const val BADGES_RECHERCHE = "cs.badges.recherche"
    const val BADGES_COMPTE = "cs.badges.compte"
    fun badgesType(code: String) = "cs.badges.type.$code"
    fun badge(id: String) = "cs.badge.$id"

    const val COURRIELS_RAFRAICHIR = "courriels.rafraichir"
    const val COURRIELS_VIDE = "courriels.vide"
    fun courriel(id: Int) = "courriel.$id"
    fun courrielAffaire(id: Int) = "courriel.$id.affaire"

    const val ANNONCES_VUE_NOUVELLE = "annonces-hall.vue.nouvelle"
    const val ANNONCES_VUE_ARCHIVES = "annonces-hall.vue.archives"
    const val ANNONCES_VIDE = "annonces-hall.vide"
    fun annonce(id: Int) = "annonce-hall.$id"
    fun annonceGestes(id: Int) = "annonce-hall.$id.gestes"
    const val ANNONCE_RENVOYER = "annonces-hall.renvoyer"
    const val ANNONCE_ARCHIVER = "annonces-hall.archiver"
    const val ANNONCE_SUPPRIMER = "annonces-hall.supprimer"
    const val ANNONCE_SUPPRIMER_CONFIRMER = "annonces-hall.supprimer.confirmer"
    const val NOUVELLE_TITRE = "nouvelle-annonce-hall.titre"
    const val NOUVELLE_MESSAGE = "nouvelle-annonce-hall.message"
    fun nouvelleFormat(code: String) = "nouvelle-annonce-hall.format.$code"
    const val NOUVELLE_ENVOYER_CS = "nouvelle-annonce-hall.envoyer-cs"
    const val NOUVELLE_CREER = "nouvelle-annonce-hall.creer"

    fun reportingMode(code: String) = "reporting.mode.$code"
    const val REPORTING_TOTAL = "reporting.total"
    const val REPORTING_VIDE = "reporting.vide"
    fun reportingAffaire(id: Int) = "reporting.affaire.$id"
    const val RENOUVELLEMENTS_PREAVIS = "renouvellements.preavis"
    fun renouvellementContrat(id: Int) = "renouvellements.contrat.$id"
    fun renouvellementDiagnostic(id: Int) = "renouvellements.diagnostic.$id"

    const val ANNUAIRE_CONSEIL = "cs.annuaire.conseil"
    const val ANNUAIRE_CONSEIL_MODIFIER = "cs.annuaire.conseil.modifier"
    const val ANNUAIRE_CONSEIL_AJOUTER = "cs.annuaire.conseil.ajouter"
    fun annuaireConseil(rang: Int) = "cs.annuaire.conseil.$rang"
    const val ANNUAIRE_SYNDIC = "cs.annuaire.syndic"
    const val ANNUAIRE_SYNDIC_MODIFIER = "cs.annuaire.syndic.modifier"
    const val ANNUAIRE_SYNDIC_AJOUTER = "cs.annuaire.syndic.ajouter"
    fun annuaireSyndic(rang: Int) = "cs.annuaire.syndic.$rang"
    const val ANNUAIRE_RETIRER_CONFIRMER = "cs.annuaire.retirer.confirmer"
    const val FEUILLE_ENTETE_CONSEIL = "cs.feuille.entete-conseil"
    const val FEUILLE_ENTETE_SYNDIC = "cs.feuille.entete-syndic"
    const val FEUILLE_MEMBRE_CONSEIL = "cs.feuille.membre-conseil"
    const val FEUILLE_MEMBRE_SYNDIC = "cs.feuille.membre-syndic"
    const val FEUILLE_PRENOM = "cs.feuille.prenom"
    const val FEUILLE_NOM = "cs.feuille.nom"
    const val FEUILLE_NOM_AFFICHE = "cs.feuille.nom-affiche"
    const val FEUILLE_LOGEMENT = "cs.feuille.logement"
}
