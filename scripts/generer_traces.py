#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les TRACÉS des icônes du site, générés au build depuis `contrat/icones-svg.json` (#183)
— le catalogue que coprofirst publie avec sa release depuis v3.106.0 (coprofirst#2792),
dessiné comme l'iOS les dessine dans la gouttière du fil (`Pictogrammes.xcassets/Lucide`).

Le trait vient de `attributsTrace` (tables-clients.json, lu à côté) : la grille, son
épaisseur, ses bouts et ses jointures arrondis. Chaque élément devient un chemin :
`path` tel quel ; `line`, `polyline`, `circle`, `ellipse` et `rect` (coins arrondis
compris) traduits en commandes de chemin. Un `fill` (« currentColor », une couleur) rend
l'élément plein ; `stroke="none"` lui retire son trait. La teinte se pose à l'affichage
(`Icon(tint = …)`) : le noir écrit ici n'est qu'un masque.

Un élément ou un attribut que ce générateur ne sait pas rendre ARRÊTE la génération :
l'app ne dessinera jamais une icône à moitié.

Usage : generer_traces.py <icones-svg.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine  # noqa: E402

PAQUET = "fr.coprofirst.app.charte"
TABLES = "tables-clients.json"
NOM = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
ELEMENT = re.compile(r"<(\w+)((?:\s+[a-z][a-z0-9-]*=\"[^\"]*\")*)\s*/>")
ATTRIBUT = re.compile(r'([a-z][a-z0-9-]*)="([^"]*)"')
# Ce que chaque élément porte, et rien d'autre.
ATTRIBUTS = {
    "path": {"d"}, "line": {"x1", "y1", "x2", "y2"}, "polyline": {"points"},
    "circle": {"cx", "cy", "r"}, "ellipse": {"cx", "cy", "rx", "ry"},
    "rect": {"x", "y", "width", "height", "rx", "ry"},
}
PEINTURE = {"fill", "stroke"}


def n(texte: str) -> float:
    try:
        return float(texte)
    except ValueError:
        raise TableInattendue(f"nombre illisible : {texte!r}") from None


def f(x: float) -> str:
    """Un nombre court, sans zéro inutile (10.27, 2, -0.5)."""
    s = f"{x:.4f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def chemin(element: str, a: dict[str, str]) -> str:
    """L'élément en commandes de chemin (`d`)."""
    if element == "path":
        return a["d"]
    if element == "line":
        return f"M{f(n(a['x1']))},{f(n(a['y1']))}L{f(n(a['x2']))},{f(n(a['y2']))}"
    if element == "polyline":
        points = [n(p) for p in re.split(r"[\s,]+", a["points"].strip())]
        if len(points) < 4 or len(points) % 2:
            raise TableInattendue(f"polyline : points illisibles ({a['points']!r})")
        paires = [f"{f(points[i])},{f(points[i + 1])}" for i in range(0, len(points), 2)]
        return "M" + paires[0] + "L" + "L".join(paires[1:])
    if element in ("circle", "ellipse"):
        cx, cy = n(a["cx"]), n(a["cy"])
        rx, ry = (n(a["r"]), n(a["r"])) if element == "circle" else (n(a["rx"]), n(a["ry"]))
        return (f"M{f(cx - rx)},{f(cy)}a{f(rx)},{f(ry)} 0 1,0 {f(2 * rx)},0"
                f"a{f(rx)},{f(ry)} 0 1,0 {f(-2 * rx)},0Z")
    # rect : coins arrondis si rx/ry (l'un vaut l'autre s'il manque), bornés à la demi-taille.
    x, y, w, h = n(a["x"]), n(a["y"]), n(a["width"]), n(a["height"])
    rx = n(a["rx"]) if "rx" in a else (n(a["ry"]) if "ry" in a else 0.0)
    ry = n(a["ry"]) if "ry" in a else rx
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    if rx <= 0 or ry <= 0:
        return f"M{f(x)},{f(y)}h{f(w)}v{f(h)}h{f(-w)}Z"
    return (f"M{f(x + rx)},{f(y)}h{f(w - 2 * rx)}a{f(rx)},{f(ry)} 0 0 1 {f(rx)},{f(ry)}"
            f"v{f(h - 2 * ry)}a{f(rx)},{f(ry)} 0 0 1 {f(-rx)},{f(ry)}"
            f"h{f(-(w - 2 * rx))}a{f(rx)},{f(ry)} 0 0 1 {f(-rx)},{f(-ry)}"
            f"v{f(-(h - 2 * ry))}a{f(rx)},{f(ry)} 0 0 1 {f(rx)},{f(-ry)}Z")


def traits(nom: str, svg: str) -> list[tuple[str, bool, bool]]:
    """Les traits d'une icône : (chemin, plein, tracé)."""
    reste = ELEMENT.sub("", svg).strip()
    if reste:
        raise TableInattendue(f"{nom} : contenu que l'app ne sait pas rendre ({reste[:60]!r})")
    sortie = []
    for element, brut in ELEMENT.findall(svg):
        if element not in ATTRIBUTS:
            raise TableInattendue(f"{nom} : élément <{element}> que l'app ne sait pas rendre")
        a = dict(ATTRIBUT.findall(brut))
        inconnus = set(a) - ATTRIBUTS[element] - PEINTURE
        manquants = ATTRIBUTS[element] - set(a) - {"rx", "ry"}
        if inconnus or manquants:
            raise TableInattendue(f"{nom} : <{element}> attributs inattendus {sorted(inconnus)} ou absents {sorted(manquants)}")
        plein = a.get("fill", "none") != "none"
        trace = a.get("stroke", "") != "none"
        if not plein and not trace:
            raise TableInattendue(f"{nom} : <{element}> ni plein ni tracé")
        sortie.append((chemin(element, a), plein, trace))
    if not sortie:
        raise TableInattendue(f"{nom} : aucun tracé")
    return sortie


def trait_publie(tables: dict) -> tuple[float, float]:
    """`attributsTrace` : la grille (carrée, depuis 0) et l'épaisseur ; bouts et jointures ronds."""
    v = tables.get("attributsTrace", {}).get("valeur")
    if not isinstance(v, dict):
        raise TableInattendue(f"attributsTrace : forme inattendue ({v!r})")
    boite = [n(x) for x in str(v.get("viewBox", "")).split()]
    if len(boite) != 4 or boite[:2] != [0, 0] or boite[2] != boite[3] or boite[2] <= 0:
        raise TableInattendue(f"attributsTrace.viewBox : une grille carrée depuis 0 attendue ({v.get('viewBox')!r})")
    if v.get("stroke-linecap") != "round" or v.get("stroke-linejoin") != "round" or v.get("fill") != "none":
        raise TableInattendue(f"attributsTrace : trait rond et sans remplissage attendu ({v!r})")
    return boite[2], n(str(v.get("stroke-width")))


def kotlin(icones: dict, tables: dict, source: str) -> str:
    if not isinstance(icones, dict) or not icones:
        raise TableInattendue("icones-svg.json : un objet {nom: tracés} non vide attendu")
    grille, epaisseur = trait_publie(tables)
    lignes = []
    for nom in sorted(icones):
        if not NOM.match(nom) or not isinstance(icones[nom], str):
            raise TableInattendue(f"icône au nom ou au tracé illisible : {nom!r}")
        t = ", ".join(f"Trait({chaine(d)}" + (", plein = true" if plein else "") + ("" if trace else ", trace = false") + ")"
                      for d, plein, trace in traits(nom, icones[nom]))
        lignes.append(f"        {chaine(nom)} to listOf({t}),")
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_traces.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (Lucide : ISC, MIT ; Simple Icons : CC0-1.0) et {TABLES} (attributsTrace).",
        f"package {PAQUET}",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.graphics.SolidColor",
        "import androidx.compose.ui.graphics.StrokeCap",
        "import androidx.compose.ui.graphics.StrokeJoin",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "import androidx.compose.ui.graphics.vector.addPathNodes",
        "import androidx.compose.ui.unit.dp",
        "",
        "/** Un trait d'une icône du site : son chemin, plein ou non, tracé ou non. */",
        "data class Trait(val chemin: String, val plein: Boolean = false, val trace: Boolean = true)",
        "",
        "/** Les icônes du site, dessinées d'après leurs tracés publiés (`icones-svg.json`, #183). */",
        "object TracesDuSite {",
        f"    const val GRILLE = {f(grille)}f",
        f"    const val EPAISSEUR = {f(epaisseur)}f",
        "",
        "    val traces: Map<String, List<Trait>> = mapOf(",
        *lignes,
        "    )",
        "",
        "    private val dessinees = mutableMapOf<String, ImageVector>()",
        "",
        "    /** L'icône de ce nom, ou `null` si le site n'en publie pas le tracé. */",
        "    fun icone(nom: String): ImageVector? = traces[nom]?.let { t -> synchronized(dessinees) { dessinees.getOrPut(nom) { dessiner(nom, t) } } }",
        "",
        "    // Le noir n'est qu'un masque : l'affichage pose sa teinte (`Icon(tint = …)`).",
        "    private fun dessiner(nom: String, traits: List<Trait>): ImageVector =",
        "        ImageVector.Builder(nom, GRILLE.dp, GRILLE.dp, GRILLE, GRILLE).apply {",
        "            traits.forEach { t ->",
        "                addPath(",
        "                    pathData = addPathNodes(t.chemin),",
        "                    fill = if (t.plein) SolidColor(Color.Black) else null,",
        "                    stroke = if (t.trace) SolidColor(Color.Black) else null,",
        "                    strokeLineWidth = EPAISSEUR,",
        "                    strokeLineCap = StrokeCap.Round,",
        "                    strokeLineJoin = StrokeJoin.Round,",
        "                )",
        "            }",
        "        }.build()",
        "}",
        "",
    ])


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        icones = json.loads(entree.read_text(encoding="utf-8"))
        tables = json.loads((entree.parent / TABLES).read_text(encoding="utf-8"))["tables"]
        code = kotlin(icones, tables, entree.name)
    except (KeyError, TableInattendue) as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    fichier = sortie / PAQUET.replace(".", "/") / "TracesDuSite.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
