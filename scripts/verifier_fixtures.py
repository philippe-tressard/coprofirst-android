#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les réponses SIMULÉES jugées contre le schéma du SERVEUR (#30), comme
`scripts/verifier-fixtures.py` de l'iOS.

Bogue de l'iOS, 05/11/2026 : l'API simulée servait un champ que la route n'a jamais servi ;
les tests étaient verts, l'appareil affichait une erreur. Chaque réponse écrite dans
`app/src/debug/**/*.kt` (`val NOM = \"\"\"…\"\"\"`) déclare donc, dans le commentaire qui
la précède, ce qu'elle est :

    /** GET /lots/mes-lots → `[LotRead]`. */
    /** GET /auth/me → `UserRead`. */
    /** Le refus du serveur (400) → erreur. */

et ce juge la confronte à `components/schemas` du contrat, à tous les niveaux : un champ
que le serveur ne sert pas, un champ requis absent, une valeur hors de son énumération,
un type faux font échouer. Une réponse sans déclaration échoue aussi : elle échapperait
au juge. Une `erreur` est le corps d'un refus de FastAPI (`{"detail": …}`).

Une réponse peut citer une autre réponse du MÊME fichier (`$DEMANDE`) : elle se juge
remplacée. Tout autre modèle Kotlin (`${f(x)}`, une variable) ne se juge pas : la réponse
s'écrit en entier. Hors du juge, nommément : les réponses construites par une fonction
(`suivi(…)`, `moi(…)`) — à écrire en entier quand on y touche.

Usage : verifier_fixtures.py [contrat.json]   (par défaut contrat/openapi.json)
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parent.parent
REPONSE = re.compile(r'(/\*\*(?:(?!\*/).)*\*/)?\s*(?:(?:private|internal)\s+)?(?:const\s+)?val\s+(\w+)(?:\s*:\s*String)?\s*=\s*"""(.*?)"""',
                     flags=re.S)
DECLARATION = re.compile(r"→\s*(?:`(\[?)([A-Z]\w*)\]?`|(erreur))")
TYPES = {"string": str, "integer": int, "number": (int, float), "boolean": bool, "array": list, "object": dict}


def reponses(texte: str) -> list[tuple[str, str | None, str]]:
    """(nom, déclaration du commentaire ou None, JSON) de chaque réponse écrite."""
    return [(m.group(2), m.group(1), m.group(3)) for m in REPONSE.finditer(texte)]


def juger(valeur, schema: dict, schemas: dict, chemin: str) -> list[str]:
    """Les écarts de `valeur` au schéma ; vide : conforme."""
    if "$ref" in schema:
        return juger(valeur, schemas[schema["$ref"].rsplit("/", 1)[-1]], schemas, chemin)
    for cle in ("anyOf", "oneOf"):
        if cle in schema:
            essais = [juger(valeur, s, schemas, chemin) for s in schema[cle]]
            return [] if any(not e for e in essais) else min(essais, key=len)
    if "allOf" in schema:
        return [e for s in schema["allOf"] for e in juger(valeur, s, schemas, chemin)]
    if "enum" in schema and valeur not in schema["enum"]:
        return [f"{chemin} : {valeur!r} hors de l'énumération {schema['enum']}"]
    genre = schema.get("type")
    if genre == "null":
        return [] if valeur is None else [f"{chemin} : null attendu"]
    if genre in TYPES:
        attendu = TYPES[genre]
        if not isinstance(valeur, attendu) or (genre in ("integer", "number") and isinstance(valeur, bool)):
            return [f"{chemin} : {genre} attendu, {type(valeur).__name__} servi"]
    if isinstance(valeur, list) and "items" in schema:
        return [e for i, v in enumerate(valeur) for e in juger(v, schema["items"], schemas, f"{chemin}[{i}]")]
    if isinstance(valeur, dict) and ("properties" in schema or genre == "object"):
        proprietes = schema.get("properties", {})
        ecarts = [f"{chemin}.{c} : requis, absent" for c in schema.get("required", []) if c not in valeur]
        libre = schema.get("additionalProperties")
        for cle, v in valeur.items():
            if cle in proprietes:
                ecarts += juger(v, proprietes[cle], schemas, f"{chemin}.{cle}")
            elif isinstance(libre, dict):
                ecarts += juger(v, libre, schemas, f"{chemin}.{cle}")
            elif not libre:
                ecarts.append(f"{chemin}.{cle} : champ que le serveur ne sert pas")
        return ecarts
    return []


def verifier(fichiers: dict[str, str], schemas: dict) -> list[str]:
    erreurs = []
    for nom_fichier, texte in sorted(fichiers.items()):
        connues = {nom: corps for nom, _, corps in reponses(texte)}
        for nom, commentaire, corps in reponses(texte):
            ou = f"{nom_fichier} › {nom}"
            corps = re.sub(r"\$\{([A-Z][A-Z0-9_]*)\}|\$([A-Z][A-Z0-9_]*)",
                           lambda m: connues.get(m.group(1) or m.group(2), m.group(0)), corps)
            if re.search(r"\$[{A-Za-z_]", corps):
                erreurs.append(f"{ou} : modèle Kotlin dans la réponse — l'écrire en entier")
                continue
            declaration = DECLARATION.search(commentaire or "")
            if not declaration:
                erreurs.append(f"{ou} : aucune déclaration (→ `Schéma`, → `[Schéma]` ou → erreur)")
                continue
            try:
                valeur = json.loads(corps)
            except ValueError as e:
                erreurs.append(f"{ou} : JSON illisible ({e})")
                continue
            liste, schema_nom, erreur = declaration.groups()
            if erreur:
                if not (isinstance(valeur, dict) and "detail" in valeur):
                    erreurs.append(f"{ou} : une erreur porte « detail »")
                continue
            if schema_nom not in schemas:
                erreurs.append(f"{ou} : schéma `{schema_nom}` absent du contrat")
                continue
            schema = {"type": "array", "items": {"$ref": f"#/components/schemas/{schema_nom}"}} if liste \
                else {"$ref": f"#/components/schemas/{schema_nom}"}
            erreurs += [f"{ou} : {e}" for e in juger(valeur, schema, schemas, "$")]
    return erreurs


def fixtures() -> dict[str, str]:
    return {str(f.relative_to(RACINE)): f.read_text(encoding="utf-8")
            for f in sorted((RACINE / "app/src/debug").rglob("*.kt"))}


def main(argv: list[str]) -> int:
    contrat = pathlib.Path(argv[1]) if len(argv) > 1 else RACINE / "contrat/openapi.json"
    try:
        schemas = json.loads(contrat.read_text(encoding="utf-8"))["components"]["schemas"]
        lues = fixtures()
    except (OSError, ValueError, KeyError) as e:
        print(f"❓ INCONNU : fixtures ou contrat illisibles — {e}", file=sys.stderr)
        return 2
    if not any(reponses(t) for t in lues.values()):
        print("❓ INCONNU : aucune réponse simulée trouvée — le juge ne prouverait rien", file=sys.stderr)
        return 2
    erreurs = verifier(lues, schemas)
    for e in erreurs:
        print(f"❌ {e}", file=sys.stderr)
    return 1 if erreurs else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
