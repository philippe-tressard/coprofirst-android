// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.lecture

import fr.coprofirst.api.modeles.Lecture as LectureGeneree
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * « Qui la lit » : la pastille de lecture d'un contenu, telle que le serveur la SERT
 * (`lecture`, contrat 1.22.0, #129) — avec une affaire, une ligne du fil, une petite annonce,
 * une idée, un sondage —, comme l'iOS (`Lecture`).
 *
 * 🔴 L'app ne la recompose pas : qui lit dépend de la catégorie, de l'état, des
 * destinataires, du périmètre — la règle du serveur seul. Non servie : aucune pastille.
 */
data class Lecture(
    /** `tous`, `residents`, `coproprietaires`, `cs`, `concerne`, ou des codes joints par « + ». */
    val cle: String,
    /** Les pictogrammes du site (noms Lucide), dans l'ordre : `Pictogrammes`. */
    val icones: List<String>,
    /** Le libellé de la carte — « Copropriétaires », « CS ». */
    val court: String,
    /** Le titre de la pastille longue : le libellé, et le cadenas en toutes lettres. */
    val titre: String,
    /** Le cadenas : seuls ceux du périmètre lisent. */
    val perimetreReserve: Boolean,
    /** « Lue par… » — la bulle du site. */
    val phrase: String,
    /** « Pas les locataires, ni… » ; vide quand personne n'est exclu. */
    val exclus: String,
    /** Personne n'est exclu : rien sur la carte (`PastilleLecture` du site). */
    val parDefaut: Boolean,
    /** Ce qu'un formulaire doit dire en plus ; `null` ou vide : rien. Le `meta` du fil ne le sert pas toujours. */
    val avertissement: String? = null,
) {
    /** La phrase entière d'un détail : la bulle, puis ceux qui ne lisent pas. */
    val phraseEntiere: String get() = listOf(phrase, exclus).filter { it.isNotEmpty() }.joinToString(" ")

    companion object {
        /**
         * La pastille à MONTRER : `null` quand personne n'est exclu (`par_defaut`) ou qu'elle
         * n'est pas servie — la règle du site, écrite une fois pour la vue et le lecteur d'écran.
         */
        fun aMontrer(lecture: Lecture?): Lecture? = lecture?.takeUnless { it.parDefaut }

        /** La pastille SERVIE par une route qui la type (au plancher : contrat 1.27.0). */
        fun depuis(servie: LectureGeneree) = Lecture(
            servie.cle, servie.icones, servie.court, servie.titre, servie.perimetreReserve,
            servie.phrase, servie.exclus, servie.parDefaut, servie.avertissement,
        )

        /**
         * La même, relue dans un dictionnaire LIBRE du contrat (`meta.affaire.lecture` du fil) :
         * illisible, elle vaut `null` et n'emporte pas le reste de la ligne (`Tolere` de l'iOS).
         */
        fun lire(element: JsonElement?): Lecture? {
            val objet = element as? JsonObject ?: return null
            fun texte(cle: String) = (objet[cle] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun oui(cle: String) = (objet[cle] as? JsonPrimitive)?.booleanOrNull
            val icones = (objet["icones"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }
            return Lecture(
                cle = texte("cle") ?: return null,
                icones = icones ?: return null,
                court = texte("court") ?: return null,
                titre = texte("titre") ?: return null,
                perimetreReserve = oui("perimetre_reserve") ?: return null,
                phrase = texte("phrase") ?: return null,
                exclus = texte("exclus") ?: return null,
                parDefaut = oui("par_defaut") ?: return null,
                avertissement = texte("avertissement"),
            )
        }
    }
}

/**
 * Le LIEU d'un contenu, tel que le serveur le sert (`perimetre_libelle`, contrat 1.24.0,
 * #130), comme l'iOS (`LieuServi`) : « Bât. 2 › Toit », `""` pour le périmètre par défaut,
 * que le badge tait. La règle du serveur (chemins « › », groupes, nœuds tus) ne se recopie
 * pas. Le plancher (1.27.0) le sert toujours : pas de repli d'une installation antérieure.
 */
object LieuServi {
    /** Le lieu à montrer : servi, il fait foi ; `""` ou absent : rien. */
    fun lieu(perimetreLibelle: String?): String? = perimetreLibelle?.takeIf { it.isNotEmpty() }
}
