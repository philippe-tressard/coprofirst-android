#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les CONSTANTES du serveur, générées au build depuis `contrat/constantes-clients.json` —
la pièce jointe de la release de coprofirst au plancher (depuis v3.102.0), recopiée à
l'octet, comme l'iOS (#74).

Ce que l'API applique sans le dire dans le contrat : les schémas d'un lien du texte
riche (`schemasDeLien`, `SCHEMAS_URL`), la longueur minimale d'un mot de passe, la note
d'un avis et ses seuils, les défauts des préférences de notification. L'app ne les
recopie plus : elle les lit ici, une fois.

Et `FichiersDuSite` (paquet `commun.pieces`), depuis v3.105.0 (#169) : ce qu'est une photo
parmi les pièces jointes (`motifImage`) et le préfixe retiré du nom montré
(`prefixeStockage`), comme l'iOS (`generateurs/fichiers.py`).

Une constante qui manque ou change de forme : la génération s'arrête.

Usage : generer_constantes.py <constantes-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine  # noqa: E402

PAQUET = "fr.coprofirst.app.commun"
PAQUET_PIECES = "fr.coprofirst.app.commun.pieces"
# La famille montrée de chaque extension (`typeFichier` de front/src/lib/fichiers.ts), aux
# valeurs de l'iOS : la seule règle des fichiers que la release ne publie pas encore — le
# `famillesFichiers` publié décrit le TÉLÉVERSEMENT (types, plafonds), pas l'affichage.
# Tenue ici jusqu'à philippe-tressard/coprofirst#2798, puis générée.
FAMILLES_TENUES = (("JPEG", "JPG"), ("XLSX", "EXCEL"), ("XLS", "EXCEL"), ("DOCX", "WORD"), ("DOC", "WORD"),
                   ("PPTX", "POWERPOINT"), ("PPT", "POWERPOINT"), ("TXT", "TEXTE"))
EXTENSION = re.compile(r"^[A-Z0-9]+$")
NOTES = ("noteMin", "noteMax", "noteRemarque", "notePubliable")


def valeur(constantes: dict, nom: str, forme: type):
    v = constantes.get(nom, {}).get("valeur")
    if not isinstance(v, forme) or isinstance(v, bool) != (forme is bool):
        raise TableInattendue(f"{nom} : forme inattendue ({v!r})")
    return v


def kotlin(constantes: dict, source: str) -> str:
    schemas = valeur(constantes, "schemasDeLien", list)
    if not schemas or not all(isinstance(s, str) and s.isalpha() and s.islower() for s in schemas):
        raise TableInattendue(f"schemasDeLien : forme inattendue ({schemas})")
    longueur = valeur(constantes, "longueurMinMotDePasse", int)
    n = {nom: valeur(constantes, nom, int) for nom in NOTES}
    if not n["noteMin"] < n["noteRemarque"] <= n["notePubliable"] <= n["noteMax"]:
        raise TableInattendue(f"seuils de l'avis incohérents ({n})")
    defauts = valeur(constantes, "defautsPreferences", dict)
    if not defauts or not all(isinstance(v, bool) for v in defauts.values()):
        raise TableInattendue(f"defautsPreferences : forme inattendue ({defauts})")
    l = [
        "// GÉNÉRÉ par scripts/generer_constantes.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source}.",
        f"package {PAQUET}",
        "",
        "/** Ce que l'API applique sans le dire dans le contrat, publié par coprofirst à chaque release. */",
        "object ConstantesDuServeur {",
        "    /** Les schémas d'un lien touchable du texte riche (`SCHEMAS_URL`, appliqué par l'API). */",
        "    val schemasDeLien: Set<String> = setOf(" + ", ".join(chaine(s) for s in schemas) + ")",
        "",
        "    /** Le nombre de caractères qu'un mot de passe doit atteindre. */",
        f"    const val LONGUEUR_MIN_MOT_DE_PASSE = {longueur}",
        "",
        "    /** La note d'un avis et ses seuils : sous `NOTE_REMARQUE`, ce qui a manqué ;",
        "     *  à partir de `NOTE_PUBLIABLE`, l'accord de diffusion. */",
        f"    const val NOTE_MIN = {n['noteMin']}",
        f"    const val NOTE_MAX = {n['noteMax']}",
        f"    const val NOTE_REMARQUE = {n['noteRemarque']}",
        f"    const val NOTE_PUBLIABLE = {n['notePubliable']}",
        "",
        "    /** La valeur d'une préférence de notification jamais réglée, par clé. */",
        "    val defautsPreferences: Map<String, Boolean> = mapOf(",
        *[f"        {chaine(k)} to {str(v).lower()}," for k, v in defauts.items()],
        "    )",
        "}",
        "",
    ]
    return "\n".join(l)


def motif(constantes: dict, nom: str) -> str:
    """Une expression publiée (`motif`, `ignorerCasse`) : le littéral `Regex` Kotlin."""
    v = valeur(constantes, nom, dict)
    texte, casse = v.get("motif"), v.get("ignorerCasse")
    if not isinstance(texte, str) or not texte or not isinstance(casse, bool):
        raise TableInattendue(f"{nom} : forme inattendue ({v!r})")
    try:
        re.compile(texte)
    except re.error as e:
        raise TableInattendue(f"{nom} : motif illisible ({e})") from None
    # Ce que Python écrit autrement que Kotlin (groupe nommé `(?P`, fin `\Z`, commentaire) :
    # refusé plutôt que traduit de travers.
    if re.search(r"\(\?P|\\Z|\(\?#", texte):
        raise TableInattendue(f"{nom} : construction que l'app ne sait pas traduire ({texte})")
    return f"Regex({chaine(texte)}" + (", RegexOption.IGNORE_CASE)" if casse else ")")


def kotlin_fichiers(constantes: dict, source: str, familles=FAMILLES_TENUES) -> str:
    """`FichiersDuSite` : photo ou document, nom montré, type montré — la règle du site."""
    image, prefixe = motif(constantes, "motifImage"), motif(constantes, "prefixeStockage")
    cles = [c for c, _ in familles] if isinstance(familles, (list, tuple)) else []
    if (not cles or len(set(cles)) != len(cles)
            or not all(isinstance(c, str) and isinstance(v, str) and EXTENSION.match(c)
                       and v.strip() and v == v.upper() for c, v in familles)):
        raise TableInattendue("les familles de typeFichier ne sont pas des extensions en capitales, sans doublon")
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_constantes.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (motifImage, prefixeStockage) ; familles : FAMILLES_TENUES (coprofirst#2798).",
        f"package {PAQUET_PIECES}",
        "",
        "/** Les pièces jointes comme le site les range et les nomme. */",
        "object FichiersDuSite {",
        "    /** Le nom ou l'adresse d'un fichier qui s'affiche en vignette (`motifImage`). */",
        f"    val motifImage: Regex = {image}",
        "",
        "    /** Le préfixe technique d'un fichier stocké, retiré du nom montré (`prefixeStockage`). */",
        f"    val prefixeStockage: Regex = {prefixe}",
        "",
        "    /** L'extension en capitales, ramenée à sa famille (`typeFichier`) — tenue, coprofirst#2798. */",
        "    val familles: Map<String, String> = mapOf(",
        *[f"        {chaine(c)} to {chaine(v)}," for c, v in familles],
        "    )",
        "}",
        "",
    ])


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        constantes = json.loads(entree.read_text(encoding="utf-8"))["constantes"]
        fichiers = {
            (PAQUET, "ConstantesDuServeur.kt"): kotlin(constantes, entree.name),
            (PAQUET_PIECES, "FichiersDuSite.kt"): kotlin_fichiers(constantes, entree.name),
        }
    except (KeyError, TableInattendue) as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    for (paquet, nom), code in fichiers.items():
        fichier = sortie / paquet.replace(".", "/") / nom
        fichier.parent.mkdir(parents=True, exist_ok=True)
        fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
