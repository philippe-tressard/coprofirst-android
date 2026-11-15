// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import fr.coprofirst.app.commun.PhraseAvis
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** « Utilisateurs », comme l'iOS (`AdminTests`) : la liste, ses filtres, la fiche et ses gestes, par leurs routes. */
class UtilisateursAdminTest {
    private fun compte(id: Int, prenom: String, nom: String, vararg roles: String, actif: Boolean = true, statut: String = "locataire") =
        UtilisateurAdmin(id, prenom, nom, "${prenom.lowercase()}@exemple.test", statut, roles.toList(), actif, emailVerifie = true)

    @Test
    fun laListeFiltreEtTrieCommeLeSite() {
        val comptes = listOf(
            compte(1, "Zoé", "Été", "résident"),
            compte(2, "Alix", "", "résident"),
            compte(3, "Camille", "exemple", "conseil_syndical", statut = "copropriétaire_résident"),
            compte(4, "Bea", "Etienne", "résident", actif = false),
        )
        assertEquals("par nom sans accents ni casse ; sans nom en dernier", listOf(3, 4, 1, 2), ListeUtilisateurs.affiches(comptes, "", null, FiltreCompte.Tous).map { it.id })
        assertEquals(listOf(4), ListeUtilisateurs.affiches(comptes, "", null, FiltreCompte.EnAttente).map { it.id })
        assertEquals(listOf(3), ListeUtilisateurs.affiches(comptes, "", "copropriétaire_résident", FiltreCompte.Actifs).map { it.id })
        assertEquals("tous les mots, sans accents", listOf(1), ListeUtilisateurs.affiches(comptes, "zoe ete", null, FiltreCompte.Tous).map { it.id })
        assertEquals(1, ListeUtilisateurs.membresDuConseil(comptes))
    }

    @Test
    fun laConfirmationDitVotreCompteEtCeQuOnPerd() {
        val camille = compte(11, "Camille", "Exemple", "admin")
        val retirerAdmin = GesteDeRole("admin", GesteRole.Retirer)
        assertEquals(ConfirmationRole(false, ConfirmationRole.Suite.Notifie), ConfirmationRole.de(retirerAdmin, camille, lecteur = 99))
        assertEquals(ConfirmationRole(false, ConfirmationRole.Suite.Notifie), ConfirmationRole.de(retirerAdmin, camille, lecteur = null))
        assertEquals(ConfirmationRole(true, ConfirmationRole.Suite.PerdraLAdministration), ConfirmationRole.de(retirerAdmin, camille, 11))
        val aussiOperateur = camille.copy(roles = listOf("admin", "operateur"))
        assertEquals(ConfirmationRole.Suite.GarderaLaSupervision, ConfirmationRole.de(retirerAdmin, aussiOperateur, 11).suite)
        assertNull(ConfirmationRole.de(GesteDeRole("conseil_syndical", GesteRole.Retirer), camille, 11).suite)
    }

    @Test
    fun lExploitantSeDitSansGesteServi() {
        val operateur = compte(15, "Sacha", "Plateforme", "operateur", statut = "admin_technique")
        assertTrue(operateur.operateurDesigneParLExploitant(instanceCommune = true))
        assertFalse(operateur.operateurDesigneParLExploitant(instanceCommune = false))
        assertFalse(operateur.copy(gestesRoles = listOf(GesteDeRole("operateur", GesteRole.Retirer))).operateurDesigneParLExploitant(true))
        assertFalse(operateur.copy(actif = false).operateurDesigneParLExploitant(true))
    }

    @Test
    fun lesTextesDesGestesDeRole() {
        assertEquals("+ CS", TextesRoles.bouton(GesteDeRole("conseil_syndical", GesteRole.Ajouter)))
        assertTrue(TextesRoles.bouton(GesteDeRole("admin", GesteRole.Retirer)).startsWith("– "))
        val tc = EtiquetteCompte.toutes.first { it.cle == "tc" }
        assertEquals("un état absent se lit « manque »", tc.manque, tc.texte(null))
        assertEquals(tc.sansObjet, tc.texte("sans_objet"))
    }

    @Test
    fun lesGestesChangentLaListeCommeLeServeur() = runTest {
        val service = ServiceSimule()
        val etat = EtatUtilisateurs(service)
        etat.charger()
        assertEquals(Lecteur(11, instanceCommune = true), etat.lecteur)
        val dominique = etat.compte(12)!!

        assertEquals(AnnonceCommunaute.BanniUnMois, etat.basculerCommunaute(dominique))
        assertTrue(etat.compte(12)!!.ban.banni)
        assertEquals(AnnonceCommunaute.Reautorise, etat.basculerCommunaute(etat.compte(12)!!))

        val lectures = service.lectures
        etat.changerRole(dominique, GesteDeRole("conseil_syndical", GesteRole.Ajouter))
        assertEquals("un geste de rôle relit la liste", lectures + 1, service.lectures)
        assertEquals(listOf(12 to GesteDeRole("conseil_syndical", GesteRole.Ajouter)), service.roles)

        etat.supprimer(dominique)
        assertNull(etat.compte(12))
        assertNull("le geste fini, la fiche se retouche", etat.enCours)

        service.refus = ErreurService.Refus("Utilisateur introuvable", 404)
        val erreur = runCatching { etat.supprimer(etat.compte(11)!!) }.exceptionOrNull()
        assertEquals(service.refus, erreur)
        assertEquals("un refus ne retire rien", 1, etat.comptes!!.size)
    }

    @Test
    fun uneDemandeTraiteeQuitteLaFile() = runTest {
        val service = object : ServiceAdmin by ServiceAbsent {
            var refus: ErreurService? = null
            override suspend fun demandesProfil() = listOf(DemandeProfil(41, "Sacha EXEMPLE"), DemandeProfil(42, "Morgan FICTIF"))
            override suspend fun traiterDemandeProfil(id: Int, approuver: Boolean, motif: String?): PhraseAvis {
                refus?.let { throw it }
                return PhraseAvis(if (approuver) "Demande approuvée." else "Demande rejetée.")
            }
        }
        val etat = EtatDemandesProfil(service)
        etat.charger()
        assertEquals(PhraseAvis("Demande approuvée."), etat.traiter(etat.demandes!!.first(), approuver = true))
        assertEquals(listOf(42), etat.demandes!!.map { it.id })
        service.refus = ErreurService.Refus("Cette demande a déjà été traitée.", 409)
        assertEquals(service.refus, runCatching { etat.traiter(etat.demandes!!.first(), approuver = false, motif = "x") }.exceptionOrNull())
        assertEquals("un refus ne retire rien", listOf(42), etat.demandes!!.map { it.id })
        assertNull(etat.enCours)
    }

    private class ServiceSimule : ServiceAdmin by ServiceAbsent {
        var lectures = 0
        val roles = mutableListOf<Pair<Int, GesteDeRole>>()
        var refus: ErreurService? = null
        private var comptes = listOf(
            UtilisateurAdmin(11, "Camille", "Exemple", "camille@exemple.test", "copropriétaire_résident", listOf("admin"), true, true),
            UtilisateurAdmin(12, "Dominique", "Fictif", "dominique@exemple.test", "locataire", listOf("résident"), true, true),
        )

        override suspend fun utilisateurs(): List<UtilisateurAdmin> {
            lectures++
            return comptes
        }

        override suspend fun lecteur() = Lecteur(11, instanceCommune = true)

        override suspend fun changerRole(id: Int, geste: GesteDeRole) {
            roles += id to geste
        }

        override suspend fun bannirCommunaute(id: Int, interdit: Boolean) = BanCommunaute(interdit = false, banni = interdit)

        override suspend fun relancerRapprochement(id: Int) = PhraseAvis("Aucun lot trouvé.", TonAvis.Avertissement)

        override suspend fun supprimerUtilisateur(id: Int) {
            refus?.let { throw it }
            comptes = comptes.filterNot { it.id == id }
        }
    }
}

/** Chaque geste de la fiche part par SA route du contrat, avec son corps, par le client généré. */
class ServiceUtilisateursApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val session = SessionOuverte(
        Installation("https://residence.exemple.test", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
    ) { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val service = ServiceAdminApi(session)

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String) = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    /**
     * Le corps d'une requête, champ → valeur, un champ `null` et un champ absent confondus
     * (la sérialisation du client en décide, coprofirst-android#99), après sa méthode et son chemin.
     */
    private fun corps(attendue: String): Map<String, String?> {
        val r = serveur.takeRequest()
        assertEquals(attendue, "${r.method} ${r.url.encodedPath}")
        return Json.parseToJsonElement(r.body!!.utf8()).jsonObject
            .mapValues { (_, v) -> (v as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content }
            .let { lu -> mapOf("action" to lu["action"], "motif_refus" to lu["motif_refus"]) }
    }

    private fun requete() = serveur.takeRequest().let { "${it.method} ${it.url.encodedPath} ${it.body?.utf8().orEmpty()}".trim() }

    @Test
    fun laListeSeLitAvecSesGestesServis() = runTest {
        repondre(200, "[${FixturesUtilisateurs.CAMILLE}]")
        val camille = service.utilisateurs().single()
        assertEquals("GET /api/admin/utilisateurs", requete())
        assertEquals("Camille EXEMPLE", camille.nomAffiche)
        assertEquals(listOf("propriétaire", "conseil_syndical", "admin"), camille.roles)
        assertEquals(
            "dans l'ordre du site ; un rôle sans geste n'offre rien",
            listOf(GesteDeRole("conseil_syndical", GesteRole.Retirer), GesteDeRole("operateur", GesteRole.Ajouter)),
            camille.gestesRoles,
        )
        assertEquals("manque", camille.etiquettes["tc"])
        assertTrue(camille.ban.banni)
    }

    @Test
    fun chaqueGestePartParSaRoute() = runTest {
        repondre(200, FixturesUtilisateurs.USER)
        service.changerRole(11, GesteDeRole("conseil_syndical", GesteRole.Ajouter))
        assertEquals("""POST /api/admin/utilisateurs/11/ajouter-role {"role":"conseil_syndical"}""", requete())
        repondre(200, FixturesUtilisateurs.USER)
        service.changerRole(11, GesteDeRole("admin", GesteRole.Retirer))
        assertEquals("""POST /api/admin/utilisateurs/11/retirer-role {"role":"admin"}""", requete())
        repondre(200, FixturesUtilisateurs.USER)
        val ban = service.bannirCommunaute(11, interdit = true)
        assertEquals("""PATCH /api/admin/utilisateurs/11/ban-communaute {"interdit":true}""", requete())
        assertTrue(ban.banni)
        repondre(200, """{"ok": true, "auto_match": {"lots": 0}, "annonce": {"ton": "warning", "texte": "Aucun lot trouvé."}}""")
        assertEquals(PhraseAvis("Aucun lot trouvé.", TonAvis.Avertissement), service.relancerRapprochement(11))
        assertEquals("POST /api/admin/utilisateurs/11/auto-match", requete())
        repondre(204, "")
        service.supprimerUtilisateur(11)
        assertEquals("DELETE /api/admin/utilisateurs/11", requete())
    }

    @Test
    fun lesDemandesDeProfilSeTraitentParLeurRoute() = runTest {
        repondre(
            200,
            """[{"id": 41, "utilisateur_id": 31, "statut_demande": "en_attente", "statut_souhaite": "copropriétaire_bailleur",
               "motif": "Je mets mon appartement en location.", "cree_le": "2026-11-05T09:30:00Z",
               "utilisateur_nom": "Sacha EXEMPLE", "utilisateur_email": "sacha@exemple.test",
               "statut_actuel": "copropriétaire_résident", "batiment_actuel": "Bâtiment A"}]""",
        )
        val demande = service.demandesProfil().single()
        assertEquals("GET /api/admin/demandes-profil", requete())
        assertEquals("copropriétaire_bailleur", demande.statutSouhaite)
        assertEquals("Bâtiment A", demande.batimentActuel)

        repondre(200, """{"statut": "approuvee", "annonce": {"ton": "success", "texte": "Demande approuvée."}}""")
        assertEquals(PhraseAvis("Demande approuvée.", TonAvis.Succes), service.traiterDemandeProfil(41, approuver = true, motif = "ignoré"))
        assertEquals(mapOf("action" to "approuver", "motif_refus" to null), corps("POST /api/admin/demandes-profil/41/traiter"))
        repondre(200, """{"statut": "rejetee", "annonce": {"ton": "success", "texte": "Demande rejetée."}}""")
        service.traiterDemandeProfil(41, approuver = false, motif = "  Bail non fourni ")
        assertEquals(mapOf("action" to "rejeter", "motif_refus" to "Bail non fourni"), corps("POST /api/admin/demandes-profil/41/traiter"))
        repondre(200, """{"statut": "rejetee", "annonce": {"ton": "success", "texte": "Demande rejetée."}}""")
        service.traiterDemandeProfil(41, approuver = false, motif = " ")
        assertEquals("un motif vide ne s'envoie pas", mapOf("action" to "rejeter", "motif_refus" to null), corps("POST /api/admin/demandes-profil/41/traiter"))
    }

    @Test
    fun leLecteurEtLInstanceCommune() = runTest {
        repondre(200, FixturesUtilisateurs.USER)
        repondre(200, """{"operateur_par_exploitant": "1", "site_nom": "Les Glycines"}""")
        assertEquals(Lecteur(11, instanceCommune = true), service.lecteur())
        assertEquals("GET /api/auth/me", requete())
        assertEquals("GET /api/config", requete())
        repondre(500, "{}")
        repondre(200, """{"site_nom": "Les Glycines"}""")
        assertEquals("illisibles, ni lecteur ni instance commune", Lecteur(null, instanceCommune = false), service.lecteur())
    }
}

/** Des comptes inventés, à la forme des routes : aucun nom réel. */
object FixturesUtilisateurs {
    const val CAMILLE = """{"id": 11, "prenom": "Camille", "nom": "Exemple", "nom_affiche": "Camille EXEMPLE",
      "nom_aide_affiche": "", "est_moderateur": true, "email": "camille@exemple.test",
      "statut": "copropriétaire_résident", "role": "propriétaire", "roles": ["propriétaire", "conseil_syndical", "admin"],
      "actif": true, "email_verifie": true, "batiment_nom": "Bâtiment A", "has_lots": true, "has_tc": false,
      "has_vigik": true, "has_bail": false, "etiquettes": {"loti": "ok", "tc": "manque", "vigik": "ok", "bail": "sans_objet"},
      "onboarding_complete": true, "onboarding_etape": 4, "preferences_notifications": "{}",
      "communaute_ban_jusqu_au": "2026-12-05T10:00:00Z", "communaute_banni": true,
      "gestes_roles": {"conseil_syndical": "retirer", "admin": null, "operateur": "ajouter"},
      "derniere_connexion": "2026-11-09T18:20:00Z", "cree_le": "2025-03-02T10:00:00Z"}"""

    const val USER = """{"id": 11, "prenom": "Camille", "nom": "Exemple", "email": "camille@exemple.test",
      "statut": "copropriétaire_résident", "role": "propriétaire", "actif": true, "onboarding_complete": true,
      "onboarding_etape": 4, "preferences_notifications": "{}", "cree_le": "2025-03-02T10:00:00Z",
      "nom_affiche": "Camille EXEMPLE", "nom_aide_affiche": "", "communaute_banni": true}"""
}
