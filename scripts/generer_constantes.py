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

Une constante qui manque ou change de forme : la génération s'arrête.

Usage : generer_constantes.py <constantes-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine  # noqa: E402

PAQUET = "fr.coprofirst.app.commun"
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


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        code = kotlin(json.loads(entree.read_text(encoding="utf-8"))["constantes"], entree.name)
    except (KeyError, TableInattendue) as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    fichier = sortie / PAQUET.replace(".", "/") / "ConstantesDuServeur.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
