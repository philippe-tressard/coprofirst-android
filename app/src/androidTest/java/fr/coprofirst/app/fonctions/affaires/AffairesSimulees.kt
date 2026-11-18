// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.commun.masquages.AuteurAMasquer
import fr.coprofirst.app.commun.masquages.Masquage
import fr.coprofirst.app.commun.apercu.SaisieLecture
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.perimetres.Perimetre
import fr.coprofirst.app.session.ErreurService
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

/**
 * Une résidence simulée, comme l'API simulée de l'iOS : des affaires inventées (aucune
 * personne), le conseil syndical qui les lit — il déplace au Kanban et archive.
 */
internal class AffairesSimulees : ServiceAffaires {
    /** Les saisies dont « Qui la lira » a été demandé (#179). */
    val lectures = mutableListOf<SaisieLecture>()
    override suspend fun lecture(saisie: SaisieLecture): Lecture {
        synchronized(lectures) { lectures += saisie }
        return Lecture(
            "coproprietaires", listOf("key-round"), "Copropriétaires", "Copropriétaires (occupants et bailleurs)", false,
            "Lue par les copropriétaires, dans toute la copropriété.", "Pas les locataires.", false,
        )
    }
    val deplacements = mutableListOf<Pair<Int, String>>()
    val archivees = mutableListOf<Int>()
    private val maintenant = Instant.now()
    var affaires = listOf(
        affaire(1, "A-0001", "Fuite dans le local vélos", "ouvert", "Ouvert", "cs", urgente = true),
        affaire(2, "A-0002", "Ascenseur du bâtiment B en panne", "en_cours", "Chez le syndic", "syndic"),
        affaire(3, "A-0003", "Coupure d'eau jeudi", "publie", "Publiée", null, categorie = "actualite", natures = listOf("actualite")),
        affaire(4, "A-0004", "Porte du hall réparée", "résolu", "Résolu", null, archivee = true, jours = 400),
        affaire(5, "A-0005", "Éclairage du parking", "résolu", "Résolu", null, archivee = true, jours = 20),
    )

    private fun affaire(
        id: Int, numero: String, titre: String, statut: String, libelle: String, colonne: String?,
        urgente: Boolean = false, archivee: Boolean = false, categorie: String = "panne",
        natures: List<String> = listOf("activite"), jours: Long = 2,
    ) = Affaire(
        id = id, numero = numero, titre = titre, apercu = "<p>Le détail de « $titre ».</p>",
        creeLe = maintenant.minus(jours, ChronoUnit.DAYS), statut = statut, statutLibelle = libelle, close = archivee,
        categorie = categorie, categorieLibelle = if (categorie == "actualite") "Actualité" else "Panne", urgente = urgente,
        archivee = archivee, suiviKanban = colonne != null, natures = natures, colonne = colonne,
        peutDeplacer = true, peutArchiver = true,
    )

    override suspend fun affaires(): List<Affaire> {
        delay(100)
        return affaires
    }
    override suspend fun referentiels() = referentielsLus

    private val referentielsLus = ReferentielsAffaires(
        listOf(ColonneKanban("cs", "CS (en cours)", "CS"), ColonneKanban("syndic", "Syndic (en cours)", "Syndic"), ColonneKanban("termine", "Terminé", "Terminé")),
        listOf(
            ReferentielsAffaires.Statut("ouvert", "Ouvert", false, "cs", true),
            ReferentielsAffaires.Statut("en_cours", "Chez le syndic", false, "syndic", true),
            ReferentielsAffaires.Statut("résolu", "Résolu", true, "termine", true),
            ReferentielsAffaires.Statut("publie", "Publiée", false, null, false),
        ),
        listOf(
            ReferentielsAffaires.Categorie(
                "panne", "Panne", "🛠️", "Ascenseur, chauffage, éclairage", carnet = true, permise = true,
                sectionsEssentielles = listOf("titre", "description", "perimetre"),
            ),
            ReferentielsAffaires.Categorie("question", "Question", "❓", "Une question au conseil", permise = true, sectionsEssentielles = listOf("titre", "description")),
        ),
    )
    override suspend fun rechercher(terme: String) = affaires.filter { terme.lowercase() in it.titre.lowercase() }.map { it.id }
    override suspend fun offrirMeConcerne() = true
    override suspend fun candidatesALaFusion(id: Int) = 2
    override suspend fun deplacer(id: Int, statut: String) {
        deplacements += id to statut
    }
    override suspend fun archiver(id: Int): Affaire {
        archivees += id
        return affaires.first { it.id == id }.copy(archivee = true)
    }
    val creees = mutableListOf<SaisieAffaire>()
    val reponses = mutableListOf<Pair<Int, String>>()
    val suites = mutableListOf<Pair<Int, SaisieSuite>>()
    override suspend fun perimetres() = listOf(
        Perimetre("copro", "Toute la copropriété", parDefaut = true), Perimetre("bat-b", "Bâtiment B", 1, parent = "copro"),
    )
    override suspend fun lieu(codes: List<String>) = if (codes == listOf("bat-b")) "Bâtiment B" else "Toute la copropriété"
    override suspend fun creer(saisie: SaisieAffaire): Affaire {
        creees += saisie
        return affaires.first().copy(id = 9, numero = "A-0009", titre = saisie.titre.trim(), urgente = false, colonne = null, suiviKanban = false)
    }
    override suspend fun repondre(id: Int, texte: String) {
        reponses += id to texte
    }
    override suspend fun ajouterSuite(id: Int, suite: SaisieSuite) {
        suites += id to suite
    }
    override suspend fun regler(id: Int, option: OptionAffaire, active: Boolean) = Unit
    override suspend fun prestatairesDeLaResidence() = listOf(ASCENSEURS, PLOMBERIE)
    val editions = mutableListOf<EditionAffaire>()
    override suspend fun enregistrer(id: Int, edition: EditionAffaire) {
        editions += edition
    }
    val corrections = mutableListOf<CorrectionSuite>()
    val supprimees = mutableListOf<Int>()
    override suspend fun corrigerSuite(id: Int, suite: Int, correction: CorrectionSuite): IssueCorrection? {
        corrections += correction
        return IssueCorrection("L'affaire a avancé depuis : elle garde son état.", "en_cours", "Chez le syndic")
    }
    override suspend fun supprimerSuite(id: Int, suite: Int) {
        supprimees += suite
    }
    val messagesCorriges = mutableListOf<Pair<Int, String>>()
    val messagesSupprimes = mutableListOf<Int>()
    override suspend fun corrigerMessage(id: Int, message: Int, texte: String) {
        messagesCorriges += message to texte
    }
    override suspend fun supprimerMessage(id: Int, message: Int) {
        messagesSupprimes += message
    }
    val signalements = mutableListOf<Pair<CibleSignalement, String>>()
    override suspend fun signalerAuConseil(cible: CibleSignalement, motif: String) {
        signalements += cible to motif
    }
    val masquesPoses = mutableListOf<AuteurAMasquer>()
    override suspend fun masques() = masquesPoses.map { Masquage(it.id, it.nom, Instant.EPOCH) }
    override suspend fun masquer(auteur: AuteurAMasquer) {
        if (masquesPoses.none { it.id == auteur.id }) masquesPoses += auteur
    }
    override suspend fun demasquer(auteurId: Int) {
        masquesPoses.removeAll { it.id == auteurId }
    }
    override suspend fun affaire(id: Int): DetailAffaire {
        val a = affaires.firstOrNull { it.id == id } ?: throw ErreurService.Introuvable
        return DetailAffaire(
            id = a.id, numero = a.numero, titre = a.titre, description = a.apercu, categorie = a.categorie, statut = a.statut,
            statutLibelle = a.statutLibelle, creeLe = a.creeLe, auteur = "Le conseil syndical",
            suites = listOf(
                SuiteAffaire(
                    31, "etat", a.creeLe.plusSeconds(3600), "Le syndic", "<p>Le syndic est prévenu.</p>", "ouvert", "en_cours",
                    peutModifier = true, peutSupprimer = true, auteurId = 31,
                ),
            ),
            messages = listOf(MessageAffaire(41, "<p>Merci pour le suivi.</p>", a.creeLe.plusSeconds(7200), true, null, null, false)),
            referentiels = referentielsLus,
            droits = DroitsServisAffaire(repondre = true, changerEtat = true, modifier = true, corriger = true, changerPrestataires = true),
            perimetres = listOf("copro"),
            prestataires = listOf(ASCENSEURS),
        )
    }

    companion object {
        val ASCENSEURS = PrestataireAffaire(3, "Ascenseurs du Parc")
        val PLOMBERIE = PrestataireAffaire(5, "Plomberie Martin")
    }
}
