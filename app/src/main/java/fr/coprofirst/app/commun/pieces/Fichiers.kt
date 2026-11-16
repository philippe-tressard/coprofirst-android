// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Les noms et natures des fichiers servis, comme l'iOS (`Fichiers`) et le site
 * (`front/src/lib/fichiers.ts`) : la règle est dans `FichiersDuSite`, générée.
 */
object Fichiers {
    /** Une image affichable en vignette — le motif publié par le serveur (`motifImage`, généré). */
    fun estImage(chemin: String): Boolean = FichiersDuSite.motifImage.containsMatchIn(chemin)

    /**
     * Le nom montré d'un fichier servi (`nomFichier` du site) : le dernier segment de
     * l'URL, décodé, sans le préfixe que le serveur ajoute au téléversement (`prefixeStockage`).
     */
    fun nomAffiche(chemin: String): String {
        val segment = chemin.split('?').firstOrNull { it.isNotEmpty() } ?: chemin
        val brut = segment.split('/').lastOrNull { it.isNotEmpty() } ?: segment
        val base = decoder(brut) ?: brut
        // Un nom qui ne serait QUE le préfixe se montre tel quel.
        val prefixe = FichiersDuSite.prefixeStockage.find(base)?.value.orEmpty()
        return if (prefixe.isNotEmpty() && base.length > prefixe.length) base.drop(prefixe.length) else base
    }

    /** « PDF », « WORD », « EXCEL » — le type montré (`typeFichier` du site). */
    fun typeAffiche(chemin: String): String {
        val ext = extensionDe(nomAffiche(chemin)).uppercase()
        return if (ext.isEmpty()) "FICHIER" else FichiersDuSite.familles[ext] ?: ext
    }

    /** Le nom montré, sans son extension (`nomSansExtension` du site). */
    fun nomSansExtension(chemin: String): String {
        val nom = nomAffiche(chemin)
        val ext = extensionDe(nom)
        return if (ext.isEmpty()) nom else nom.dropLast(ext.length + 1)
    }

    /** Un nom de fichier sûr pour le disque : sans chemin, sans caractère de contrôle. */
    fun nomSur(nom: String?, defaut: String): String {
        val propre = (nom ?: "")
            .map { if (it in "/\\:" || estControle(it)) '-' else it }
            .joinToString("")
            .trim { it == '.' || it == ' ' || it.isWhitespace() }
        return propre.ifEmpty { defaut }
    }

    /** L'extension d'un nom (« pdf »), vide sans point ou pour un nom qui commence par un point. */
    fun extensionDe(nom: String): String {
        val point = nom.lastIndexOf('.')
        if (point <= 0) return ""
        val ext = nom.substring(point + 1)
        return if ('/' in ext) "" else ext
    }

    private const val HEXA = "0123456789abcdefABCDEF"

    private fun estControle(c: Char): Boolean {
        val type = Character.getType(c)
        return type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()
    }

    /**
     * Les `%XX` décodés en UTF-8 — `null` si une séquence est incomplète ou ne forme pas
     * de l'UTF-8 (comme `removingPercentEncoding`) ; un « + » reste un « + ».
     */
    internal fun decoder(texte: String): String? {
        if ('%' !in texte) return texte
        val octets = ByteArrayOutputStream()
        var i = 0
        while (i < texte.length) {
            if (texte[i] == '%') {
                val code = texte.substring(i + 1, minOf(i + 3, texte.length))
                if (code.length != 2 || !code.all { it in HEXA }) return null
                octets.write(code.toInt(16))
                i += 3
            } else {
                val fin = texte.indexOf('%', i).let { if (it < 0) texte.length else it }
                octets.write(texte.substring(i, fin).toByteArray(Charsets.UTF_8))
                i = fin
            }
        }
        val lecteur = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            lecteur.decode(ByteBuffer.wrap(octets.toByteArray())).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
