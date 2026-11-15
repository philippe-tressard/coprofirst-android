#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les PALETTES de CoproFirst, générées au build depuis `contrat/tables-clients.json` —
la pièce jointe de la release de coprofirst au plancher, recopiée à l'octet.

Les jetons viennent des tables publiées : `jetonsCharte` (`:root` de socle.css),
`jetonsCharteSombre`, `palettesClaires` et `palettesSombres`. La cascade est celle du
site, et celle de l'app iOS (`scripts/generateurs/palettes.py`) : au clair, la palette
sur la charte ; au sombre, la charte sombre par-dessus la palette claire, puis la
palette sombre par-dessus tout. La charte est `seine`.

⚠️ Doublon déclaré : la résolution des `var(--…)` et la cascade existent aussi côté
iOS (une vingtaine de lignes). Qu'elles disparaissent des deux apps est demandé à
coprofirst : publier les jetons déjà résolus (coprofirst#2754).

Usage : generer_charte.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

# Seuls ces préfixes deviennent des couleurs de `Couleurs` (comme l'iOS).
EXPOSES = ("color-", "fil-", "kanban-", "serie-", "palier-")
HEX = re.compile(r"^#([0-9a-fA-F]{6})$")
RGBA = re.compile(r"^rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([0-9.]+)\s*\)$")
VAR = re.compile(r"^var\(--([a-z][a-z0-9-]*)\)$")
CHARTE = "seine"
PAQUET = "fr.coprofirst.app.charte"


def jetons(table: dict) -> dict[str, str]:
    """Les jetons `--nom` d'une table publiée, sans leurs tirets ; le reste est ignoré."""
    return {k[2:]: str(v).strip() for k, v in table.items() if k.startswith("--")}


def resoudre(nom: str, table: dict[str, str], vus: tuple = ()) -> tuple | None:
    """(rouge, vert, bleu, alpha) du jeton dans `table`, ou None s'il n'est pas portable."""
    valeur = table.get(nom)
    if valeur is None or nom in vus:
        return None
    if m := HEX.match(valeur):
        h = int(m.group(1), 16)
        return (h >> 16 & 0xFF, h >> 8 & 0xFF, h & 0xFF, 1.0)
    if m := RGBA.match(valeur):
        return (int(m.group(1)), int(m.group(2)), int(m.group(3)), float(m.group(4)))
    if m := VAR.match(valeur):
        return resoudre(m.group(1), table, (*vus, nom))
    return None


def cascade(tables: dict) -> dict[str, tuple[dict, dict]]:
    """Par code de palette : (jetons au clair, jetons au sombre), selon la cascade du site."""
    socle = jetons(tables["jetonsCharte"]["valeur"])
    sombre = jetons(tables["jetonsCharteSombre"]["valeur"])
    claires = tables["palettesClaires"]["valeur"]
    sombres = tables["palettesSombres"]["valeur"]
    resultat = {CHARTE: (socle, {**socle, **sombre})}
    for code in sorted(claires):
        clair_p, sombre_p = jetons(claires[code]), jetons(sombres.get(code, {}))
        resultat[code] = ({**socle, **clair_p}, {**socle, **clair_p, **sombre, **sombre_p})
    return resultat


def portables(socle: dict, palettes: dict) -> tuple[list[str], list[str]]:
    """Les jetons exposés qui se résolvent dans TOUTES les palettes, et ceux écartés."""
    exposes = sorted(j for j in socle if j.startswith(EXPOSES))
    gardes = [j for j in exposes if all(resoudre(j, t) is not None for paire in palettes.values() for t in paire)]
    return gardes, [j for j in exposes if j not in gardes]


def propriete(jeton: str) -> str:
    """`color-primary` → `primary`, `fil-rouge-fond` → `filRougeFond`, `serie-1` → `serie1`."""
    nom = jeton[len("color-"):] if jeton.startswith("color-") else jeton
    morceaux = nom.split("-")
    return morceaux[0] + "".join(m.capitalize() for m in morceaux[1:])


def argb(c: tuple) -> str:
    r, v, b, a = c
    return f"Color(0x{round(a * 255):02X}{r:02X}{v:02X}{b:02X})"


def kotlin(tables: dict, source: str) -> str:
    palettes = cascade(tables)
    gardes, ecartes = portables(palettes[CHARTE][0], palettes)
    lignes = [
        "// GÉNÉRÉ par scripts/generer_charte.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (jetonsCharte, jetonsCharteSombre, palettesClaires, palettesSombres).",
        "// Jetons non portés (color-mix, valeur non littérale) : " + (", ".join(f"--{e}" for e in ecartes) or "aucun"),
        f"package {PAQUET}",
        "",
        "import androidx.compose.ui.graphics.Color",
        "",
        "/** Les couleurs d'une palette, dans un mode (clair ou sombre) : un jeton de la charte chacune. */",
        "data class Couleurs(",
        *[f"    /** `--{j}` */\n    val {propriete(j)}: Color," for j in gardes],
        ")",
        "",
        "/** Une palette de CoproFirst, claire et sombre. La charte est `seine` ; une résidence choisit la sienne (`site_palette`). */",
        "data class Palette(val code: String, val clair: Couleurs, val sombre: Couleurs)",
        "",
        "/** Toutes les palettes, par code. */",
        "object Palettes {",
        f'    const val CHARTE: String = "{CHARTE}"',
        "",
        "    val parCode: Map<String, Palette> = mapOf(",
    ]
    for code, (clair, fonce) in palettes.items():
        lignes.append(f'        "{code}" to Palette(')
        lignes.append(f'            "{code}",')
        for mode in (clair, fonce):
            lignes.append("            Couleurs(")
            lignes += [f"                {argb(resoudre(j, mode))}," for j in gardes]
            lignes.append("            ),")
        lignes.append("        ),")
    lignes += ["    )", "", "    /** La palette de ce code, ou la charte s'il est absent ou inconnu. */",
               "    fun pour(code: String?): Palette = parCode[code] ?: parCode.getValue(CHARTE)", "}", ""]
    return "\n".join(lignes)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    tables = json.loads(entree.read_text(encoding="utf-8"))["tables"]
    fichier = sortie / PAQUET.replace(".", "/") / "Palettes.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(kotlin(tables, entree.name), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
