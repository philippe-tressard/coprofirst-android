// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.Separateur
import fr.coprofirst.app.commun.texte.TexteDuSite

/**
 * Les textes du fil d'une affaire — ceux des rubriques du site, comme `TextesFil` de
 * l'iOS. Un objet, pour que les règles du fil (`EtapeFil.du`) se testent sur la JVM.
 */
data class TextesFil(
    val demande: String,
    val suite: String,
    val comptesRendus: String,
    val synthese: String,
    val aucuneSuite: String,
    val aucunCompteRendu: String,
    val aucunCompteRenduClos: String,
    val comptesRendusIllisibles: String,
    val aVenir: String,
    val brouillon: String,
    val csEtPresents: String,
    val partieSynthese: String,
    val difficultes: String,
    val amelioration: String,
    val laResidence: String,
    val suites: (Int) -> String,
    val points: (Int) -> String,
    val dernierPoint: (String) -> String,
    val point: (String) -> String,
    val validee: (String?) -> String,
    val le: (String, String) -> String,
)

@Composable
fun rememberTextesFil(): TextesFil {
    val r = LocalResources.current
    return remember(r) {
        TextesFil(
            demande = r.getString(R.string.affaires_fil_demande),
            suite = r.getString(R.string.affaires_fil_suite),
            comptesRendus = r.getString(R.string.affaires_fil_comptes_rendus),
            synthese = r.getString(R.string.affaires_fil_synthese),
            aucuneSuite = r.getString(R.string.affaires_fil_aucune_suite),
            aucunCompteRendu = r.getString(R.string.affaires_fil_aucun_cr),
            aucunCompteRenduClos = r.getString(R.string.affaires_fil_aucun_cr_clos),
            comptesRendusIllisibles = r.getString(R.string.affaires_fil_cr_illisibles),
            aVenir = r.getString(R.string.affaires_fil_a_venir),
            brouillon = r.getString(R.string.affaires_fil_brouillon),
            csEtPresents = r.getString(R.string.affaires_fil_cs_et_presents),
            partieSynthese = r.getString(R.string.affaires_fil_partie_synthese),
            difficultes = r.getString(R.string.affaires_fil_difficultes),
            amelioration = r.getString(R.string.affaires_fil_amelioration),
            laResidence = r.getString(R.string.affaires_fil_la_residence),
            suites = { r.getQuantityString(R.plurals.affaires_fil_suites, it, it) },
            points = { r.getQuantityString(R.plurals.affaires_fil_points, it, it) },
            dernierPoint = { r.getString(R.string.affaires_fil_dernier_point, it) },
            point = { r.getString(R.string.affaires_fil_point, it) },
            validee = { date -> date?.let { r.getString(R.string.affaires_fil_validee_le, it) } ?: r.getString(R.string.affaires_fil_validee) },
            le = { etat, date -> r.getString(R.string.affaires_fil_etat_le, etat, date) },
        )
    }
}

/**
 * Un élément sous une section du fil : une entrée de la suite, un compte rendu, une partie
 * de la synthèse. Plié : un titre, une ligne, un aperçu ; ouvert : son texte et ses pièces.
 */
data class ElementFil(
    val id: String,
    val titre: String,
    val ligne: String,
    val apercu: String?,
    val html: String?,
    val precise: String? = null,
    val pieces: List<String> = emptyList(),
    val realise: Boolean = true,
    /** L'entrée de la suite qu'il montre : ses gestes servis s'y poseront. */
    val suite: SuiteAffaire? = null,
)

/** Une section du fil — un jalon sur le rail, ses éléments en retrait. */
data class EtapeFil(
    val cle: Cle,
    val titre: String,
    val etat: Etat,
    /** Ce qu'elle dit sous son titre : un compte, une date, ou son absence. */
    val meta: String,
    /** Ce qu'elle dit PLIÉE, sur une ligne — l'état, jamais l'invitation. */
    val resumePlie: String? = null,
    val elements: List<ElementFil> = emptyList(),
    /** La suite d'une affaire ouverte reste ouverte : on n'en connaît pas la fin. */
    val ouverteALaSuite: Boolean = false,
) {
    enum class Cle(val repere: String) { Demande("demande"), Suite("suite"), ComptesRendus("comptes-rendus"), Synthese("synthese") }

    /** Pleine pour le réalisé, anneau de la charte pour ce qui avance, anneau gris à venir. */
    enum class Etat { Realise, EnCours, AVenir }

    /** La suite et les comptes rendus se plient, quand ils ont de quoi l'être. */
    val pliable: Boolean get() = (cle == Cle.Suite || cle == Cle.ComptesRendus) && elements.isNotEmpty()

    companion object {
        /** Les sections, dans l'ordre du site. Une actualité n'a ni compte rendu ni synthèse. */
        fun du(affaire: DetailAffaire, t: TextesFil): List<EtapeFil> {
            val etapes = mutableListOf(demande(affaire, t), suite(affaire, t))
            if (affaire.estActualite) return etapes
            etapes += comptesRendus(affaire.comptesRendus, affaire.close, t)
            synthese(affaire, t)?.let { etapes += it }
            return etapes
        }

        fun demande(affaire: DetailAffaire, t: TextesFil) = EtapeFil(
            Cle.Demande, t.demande, Etat.Realise,
            listOfNotNull(FormatsDuSite.date(affaire.creeLe), affaire.auteur).joinToString(Separateur.point),
        )

        fun suite(affaire: DetailAffaire, t: TextesFil): EtapeFil {
            // La Suite qui porte la synthèse n'est pas une étape : elle est sa conclusion.
            val entrees = affaire.suites.filter { it.type != "synthese" }
            val etat = when {
                affaire.close -> Etat.Realise
                entrees.isEmpty() -> Etat.AVenir
                else -> Etat.EnCours
            }
            return EtapeFil(
                Cle.Suite, t.suite, etat, if (entrees.isEmpty()) t.aucuneSuite else t.suites(entrees.size),
                resumePlie = entrees.maxByOrNull { it.date }?.let { resume(it, affaire.referentiels, t) },
                elements = entrees.map { element(it, affaire.referentiels, t) },
                ouverteALaSuite = !affaire.close,
            )
        }

        /** « Chez le syndic le 9 nov. 2026 — Le syndic est prévenu… » (`resumeEntree` du site). */
        fun resume(suite: SuiteAffaire, servis: ReferentielsAffaires, t: TextesFil): String {
            val date = FormatsDuSite.date(suite.date)
            val tete = suite.nouveauStatut?.let { t.le(servis.libelle(it, suite.nouveauStatutLibelle), date) } ?: date
            val texte = suite.contenu?.let(TexteDuSite::apercu).orEmpty()
            return if (texte.isEmpty()) tete else "$tete${Separateur.tiret}$texte"
        }

        fun element(suite: SuiteAffaire, servis: ReferentielsAffaires, t: TextesFil): ElementFil {
            val changement = suite.changementDEtat(servis)
            val auteur = suite.auteur ?: t.laResidence
            val ligne = listOfNotNull(if (changement == null) null else auteur, FormatsDuSite.dateHeure(suite.date))
            return ElementFil(
                id = "suite.${suite.id}", titre = changement ?: auteur, ligne = ligne.joinToString(Separateur.point),
                apercu = suite.contenu?.let(TexteDuSite::apercu)?.takeIf { it.isNotEmpty() }, html = suite.contenu,
                precise = suite.perimetrePrecise, pieces = suite.pieces, suite = suite,
            )
        }

        fun comptesRendus(lus: List<CompteRenduAffaire>?, close: Boolean, t: TextesFil): EtapeFil {
            if (lus == null) return EtapeFil(Cle.ComptesRendus, t.comptesRendus, Etat.AVenir, t.comptesRendusIllisibles)
            val dernier = lus.maxByOrNull { it.debut }
                ?: return EtapeFil(Cle.ComptesRendus, t.comptesRendus, Etat.AVenir, if (close) t.aucunCompteRenduClos else t.aucunCompteRendu)
            return EtapeFil(
                Cle.ComptesRendus, t.comptesRendus, Etat.Realise, t.points(lus.size),
                resumePlie = t.dernierPoint(FormatsDuSite.date(dernier.debut)),
                elements = lus.sortedByDescending { it.debut }.map { cr ->
                    ElementFil(
                        id = "cr.${cr.id}", titre = t.point(FormatsDuSite.dateHeure(cr.debut)),
                        ligne = listOfNotNull(cr.ou, if (cr.conseilSeul) t.csEtPresents else null).joinToString(Separateur.point),
                        apercu = TexteDuSite.apercu(cr.contenu).takeIf { it.isNotEmpty() }, html = cr.contenu,
                    )
                },
            )
        }

        fun synthese(affaire: DetailAffaire, t: TextesFil): EtapeFil? {
            val s = affaire.synthese ?: return if (affaire.close) null else EtapeFil(Cle.Synthese, t.synthese, Etat.AVenir, t.aVenir)
            val parties = listOfNotNull(
                partie("texte", t.partieSynthese, s.texte),
                partie("difficultes", t.difficultes, s.difficultes),
                partie("amelioration", t.amelioration, s.amelioration),
            ).map { it.copy(realise = s.validee) }
            return EtapeFil(
                Cle.Synthese, t.synthese, if (s.validee) Etat.Realise else Etat.EnCours,
                if (s.validee) t.validee(s.valideeLe?.let(FormatsDuSite::date)) else t.brouillon,
                elements = parties,
            )
        }

        private fun partie(cle: String, titre: String, texte: String?): ElementFil? {
            val html = texte?.takeIf { it.isNotBlank() } ?: return null
            return ElementFil("synthese.$cle", titre, "", TexteDuSite.apercu(html), html)
        }
    }
}

/** Le trait qui part d'une pastille : plein entre deux étapes engagées, discret avant ce qui vient, pointillé si le fil reste ouvert. */
enum class TraitDuRail {
    Engage, Discret, Pointille;

    companion object {
        /** Le trait sous la rangée `index` : rien sous la dernière, sauf le pointillé d'un fil ouvert. */
        fun sous(index: Int, etats: List<EtapeFil.Etat>, ouvert: Boolean = false): TraitDuRail? {
            if (index >= etats.size - 1) return if (ouvert) Pointille else null
            return if (etats[index] != EtapeFil.Etat.AVenir && etats[index + 1] != EtapeFil.Etat.AVenir) Engage else Discret
        }
    }
}
