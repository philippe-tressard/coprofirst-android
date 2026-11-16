// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.texte

/** Un morceau du HTML riche du site, tel que `LectureHTML` le découpe. */
sealed interface JetonHTML {
    data class Texte(val texte: String) : JetonHTML

    data class Ouvrante(val nom: String, val attributs: Map<String, String>) : JetonHTML

    data class Fermante(val nom: String) : JetonHTML
}

/**
 * Le découpage du HTML riche du site, comme l'iOS (`LectureHTML`) : SANS vue web ni
 * lecteur HTML d'Android — ceux-là peuvent charger des ressources distantes ou exécuter
 * du code. Ce lecteur ne charge rien, n'exécute rien : il découpe en texte et en
 * balises, et c'est tout (`verifier-depot.sh` refuse les autres chemins).
 *
 * Il tolère le HTML abîmé — une balise coupée par une troncature du serveur
 * (`contenu[:300]`) est abandonnée, un `<` isolé (« a < b ») reste du texte.
 */
object LectureHTML {
    fun jetons(html: String): List<JetonHTML> {
        val sortie = mutableListOf<JetonHTML>()
        val texte = StringBuilder()
        fun publierTexte() {
            if (texte.isNotEmpty()) sortie.add(JetonHTML.Texte(Entites.decoder(texte.toString())))
            texte.clear()
        }
        var i = 0
        while (i < html.length) {
            if (html[i] != '<') {
                texte.append(html[i++])
                continue
            }
            if (html.startsWith("!--", i + 1)) { // commentaire : sauté jusqu'à sa fin
                publierTexte()
                val fin = html.indexOf("-->", i)
                i = if (fin < 0) html.length else fin + 3
                continue
            }
            val premier = html.getOrNull(i + 1)
            if (premier == null || !(premier.isLetter() || premier in "/!?")) {
                texte.append('<') // « a < b » : un chevron isolé est du texte
                i++
                continue
            }
            // Une balise coupée en fin de texte (troncature) : abandonnée.
            val fin = finDeBalise(html, i + 1)
            publierTexte()
            if (fin == null) return sortie
            balise(html.substring(i + 1, fin))?.let(sortie::add)
            i = fin + 1
        }
        publierTexte()
        return sortie
    }

    /** Le `>` qui ferme la balise, hors des valeurs d'attribut entre guillemets. */
    private fun finDeBalise(html: String, debut: Int): Int? {
        var guillemet: Char? = null
        for (i in debut until html.length) {
            val c = html[i]
            when {
                guillemet != null -> if (c == guillemet) guillemet = null
                c == '"' || c == '\'' -> guillemet = c
                c == '>' -> return i
            }
        }
        return null
    }

    /** `p class="x"`, `/p`, `br/` → un jeton ; `!DOCTYPE`, `?xml` → rien. */
    private fun balise(interieur: String): JetonHTML? {
        if (interieur.startsWith("!") || interieur.startsWith("?")) return null
        if (interieur.startsWith("/")) {
            val nom = nomEnTete(interieur.substring(1))
            return if (nom.isEmpty()) null else JetonHTML.Fermante(nom)
        }
        val nom = nomEnTete(interieur)
        if (nom.isEmpty()) return null
        return JetonHTML.Ouvrante(nom, attributs(interieur.substring(nom.length)))
    }

    private fun nomEnTete(texte: String) = texte.takeWhile { it.isLetterOrDigit() }.lowercase()

    /** `href="…" title='…' disabled` → leurs valeurs, entités décodées ; noms en minuscules. */
    private fun attributs(texte: String): Map<String, String> {
        val sortie = mutableMapOf<String, String>()
        var i = 0
        while (true) {
            while (i < texte.length && (texte[i].isWhitespace() || texte[i] == '/')) i++
            if (i >= texte.length) return sortie
            val debutNom = i
            while (i < texte.length && !texte[i].isWhitespace() && texte[i] != '=' && texte[i] != '/') i++
            val nom = texte.substring(debutNom, i).lowercase()
            while (i < texte.length && texte[i].isWhitespace()) i++
            var valeur = ""
            if (i < texte.length && texte[i] == '=') {
                val (lue, suite) = valeurDAttribut(texte, i + 1)
                valeur = lue
                i = suite
            }
            if (nom.isNotEmpty() && nom !in sortie) sortie[nom] = Entites.decoder(valeur)
        }
    }

    /** La valeur qui suit `=` : entre guillemets doubles ou simples, ou jusqu'au blanc. */
    private fun valeurDAttribut(texte: String, depuis: Int): Pair<String, Int> {
        var i = depuis
        while (i < texte.length && texte[i].isWhitespace()) i++
        if (i >= texte.length) return "" to i
        val guillemet = texte[i].takeIf { it == '"' || it == '\'' }
        if (guillemet != null) i++
        val debut = i
        while (i < texte.length && (if (guillemet != null) texte[i] != guillemet else !texte[i].isWhitespace())) i++
        val valeur = texte.substring(debut, i)
        if (guillemet != null && i < texte.length) i++
        return valeur to i
    }
}

/**
 * Les entités HTML (`Entites` de l'iOS) : numériques (`&#39;`, `&#x2019;`) et les nommées
 * qu'un éditeur écrit. Une entité inconnue ou mal formée reste telle quelle — jamais avalée.
 */
object Entites {
    val nommees: Map<String, String> = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "laquo" to "«", "raquo" to "»", "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“",
        "rdquo" to "”", "ndash" to "–", "mdash" to "—", "euro" to "€", "deg" to "°", "middot" to "·",
        "times" to "×", "copy" to "©", "reg" to "®", "trade" to "™", "sup2" to "²", "sup3" to "³",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë", "agrave" to "à", "acirc" to "â",
        "ccedil" to "ç", "icirc" to "î", "iuml" to "ï", "ocirc" to "ô", "ugrave" to "ù", "ucirc" to "û",
        "uuml" to "ü", "oelig" to "œ", "aelig" to "æ", "Eacute" to "É", "Egrave" to "È", "Ecirc" to "Ê",
        "Agrave" to "À", "Ccedil" to "Ç", "OElig" to "Œ", "shy" to "",
    )

    /** Le `;` d'une entité se cherche dans ses 12 premiers caractères, `&` compris (comme l'iOS). */
    private const val PORTEE = 12

    fun decoder(texte: String): String {
        if ('&' !in texte) return texte
        val sortie = StringBuilder(texte.length)
        var i = 0
        while (i < texte.length) {
            val pointVirgule = if (texte[i] == '&') pointVirgule(texte, i) else -1
            val valeur = if (pointVirgule > i) valeur(texte.substring(i + 1, pointVirgule)) else null
            if (valeur == null) {
                sortie.append(texte[i++])
                continue
            }
            sortie.append(valeur)
            i = pointVirgule + 1
        }
        return sortie.toString()
    }

    private fun pointVirgule(texte: String, debut: Int): Int {
        for (j in debut + 1 until minOf(debut + PORTEE, texte.length)) if (texte[j] == ';') return j
        return -1
    }

    private fun valeur(nom: String): String? {
        if (!nom.startsWith("#")) return nommees[nom]
        val chiffres = nom.substring(1)
        val code = if (chiffres.startsWith("x") || chiffres.startsWith("X")) {
            chiffres.substring(1).toUIntOrNull(16)
        } else {
            chiffres.toUIntOrNull(10)
        } ?: return null
        // Le nul, les substituts et les codes hors Unicode deviennent U+FFFD, comme un navigateur.
        val point = code.toLong()
        if (point == 0L || point > Character.MAX_CODE_POINT || point in 0xD800L..0xDFFFL) return "�"
        return String(Character.toChars(point.toInt()))
    }
}
