#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Le LOGO de CoproFirst au format d'Android (VectorDrawable), généré au build depuis
`app/src/main/logo/logo.svg` — le dessin du site recopié À L'IDENTIQUE.

Une conversion de format, pas une modification : ni le dessin ni les couleurs ne
changent (LicenseRef-CoproFirst-Logo, usage 5 ; « redimensionner ou poser sur le fond
qu'exige une plateforme » n'en est pas une). Le convertisseur ne connaît que ce que le
logo emploie — `rect`, `path`, `g` et leurs attributs — et REFUSE le reste : un dessin
qui change de forme arrête le build au lieu de s'afficher faux.

Sortie, dans <dossier>/drawable/ :
- `logo.xml` : le logo entier, sa tuile comprise (écrans) ;
- `ic_launcher_premier_plan.xml` : le dessin sans la tuile, dans la zone sûre de
  l'icône adaptative (66 dp sur 108) ;
- `ic_launcher_fond.xml` : le fond de l'icône, de la couleur de la tuile.

Usage : generer_logo.py <logo.svg> <dossier de sortie>
"""

from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

SVG = "{http://www.w3.org/2000/svg}"
ANDROID = "http://schemas.android.com/apk/res/android"
HEX = re.compile(r"^#([0-9a-fA-F]{6})$")
TRANSFORMATION = re.compile(r"(translate|scale)\(([^)]*)\)")
# Les attributs que le convertisseur sait rendre ; tout autre arrête la génération.
CONNUS = {
    "rect": {"x", "y", "width", "height", "rx", "fill"},
    "path": {"d", "fill", "stroke", "stroke-width", "stroke-linejoin", "stroke-linecap"},
    "g": {"transform", "fill", "stroke", "stroke-width", "stroke-linejoin", "stroke-linecap"},
}
HERITES = ("fill", "stroke", "stroke-width", "stroke-linejoin", "stroke-linecap")
ZONE_SURE = 66 / 108


class LogoInconnu(Exception):
    """Le dessin emploie ce que le convertisseur ne sait pas rendre."""


def couleur(valeur: str) -> str:
    if m := HEX.match(valeur):
        return "#FF" + m.group(1).upper()
    raise LogoInconnu(f"couleur non littérale : {valeur}")


def nombre(valeur: str) -> float:
    return float(valeur)


def chemin_rect(a: dict) -> str:
    """Un `rect` (coins arrondis compris) en données de chemin."""
    x, y, l, h = (nombre(a.get(k, "0")) for k in ("x", "y", "width", "height"))
    r = min(nombre(a.get("rx", "0")), l / 2, h / 2)
    if r == 0:
        return f"M{x:g},{y:g}H{x + l:g}V{y + h:g}H{x:g}Z"
    return (f"M{x + r:g},{y:g}H{x + l - r:g}A{r:g},{r:g} 0 0 1 {x + l:g},{y + r:g}"
            f"V{y + h - r:g}A{r:g},{r:g} 0 0 1 {x + l - r:g},{y + h:g}"
            f"H{x + r:g}A{r:g},{r:g} 0 0 1 {x:g},{y + h - r:g}"
            f"V{y + r:g}A{r:g},{r:g} 0 0 1 {x + r:g},{y:g}Z")


def groupes(transform: str) -> list[dict]:
    """`translate(a b) scale(s) …` → un groupe par opération, de l'extérieur vers l'intérieur."""
    reste = TRANSFORMATION.sub("", transform).strip()
    if reste:
        raise LogoInconnu(f"transformation inconnue : {reste}")
    sortie = []
    for op, args in TRANSFORMATION.findall(transform):
        v = [nombre(n) for n in re.split(r"[\s,]+", args.strip())]
        if op == "translate":
            sortie.append({"translateX": f"{v[0]:g}", "translateY": f"{v[1] if len(v) > 1 else 0:g}"})
        else:
            sortie.append({"scaleX": f"{v[0]:g}", "scaleY": f"{v[1] if len(v) > 1 else v[0]:g}"})
    return sortie


def trace(d: str, style: dict) -> ET.Element:
    e = ET.Element("path", {f"{{{ANDROID}}}pathData": d})
    if style.get("fill", "#000000") != "none":
        e.set(f"{{{ANDROID}}}fillColor", couleur(style.get("fill", "#000000")))
    if style.get("stroke", "none") != "none":
        e.set(f"{{{ANDROID}}}strokeColor", couleur(style["stroke"]))
        e.set(f"{{{ANDROID}}}strokeWidth", f"{nombre(style.get('stroke-width', '1')):g}")
        for svg, android in (("stroke-linejoin", "strokeLineJoin"), ("stroke-linecap", "strokeLineCap")):
            if svg in style:
                e.set(f"{{{ANDROID}}}{android}", style[svg])
    return e


def convertir(noeud: ET.Element, parent: ET.Element, style: dict, sans_tuile: bool, premier: list) -> None:
    """Rend les enfants de `noeud` dans `parent` ; le premier `rect` de la racine est la tuile."""
    for enfant in noeud:
        nom = enfant.tag.removeprefix(SVG)
        if nom not in CONNUS:
            raise LogoInconnu(f"élément inconnu : <{nom}>")
        inconnus = set(enfant.attrib) - CONNUS[nom]
        if inconnus:
            raise LogoInconnu(f"<{nom}> : attributs inconnus {sorted(inconnus)}")
        local = {**style, **{k: v for k, v in enfant.attrib.items() if k in HERITES}}
        if nom == "g":
            cible = parent
            for g in groupes(enfant.get("transform", "")):
                cible = ET.SubElement(cible, "group", {f"{{{ANDROID}}}{k}": v for k, v in g.items()})
            convertir(enfant, cible, local, sans_tuile, premier)
        elif nom == "rect":
            if premier and premier.pop() and sans_tuile:
                continue
            parent.append(trace(chemin_rect(enfant.attrib), local))
        else:
            if premier:
                premier.pop()
            parent.append(trace(enfant.get("d", ""), local))


def vecteur(taille_dp: float) -> ET.Element:
    return ET.Element("vector", {
        "xmlns:android": ANDROID,
        f"{{{ANDROID}}}width": f"{taille_dp:g}dp", f"{{{ANDROID}}}height": f"{taille_dp:g}dp",
        f"{{{ANDROID}}}viewportWidth": "512", f"{{{ANDROID}}}viewportHeight": "512",
    })


def ecrire(element: ET.Element, fichier: pathlib.Path) -> None:
    ET.register_namespace("android", ANDROID)
    texte = ET.tostring(element, encoding="unicode").replace(f' xmlns:android="{ANDROID}"', "", 1)
    texte = texte.replace("<vector ", f'<vector xmlns:android="{ANDROID}" ', 1) if "xmlns:android" not in texte else texte
    entete = "<!-- GÉNÉRÉ par scripts/generer_logo.py depuis logo.svg — NE PAS MODIFIER. LicenseRef-CoproFirst-Logo. -->\n"
    fichier.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + entete + texte + "\n", encoding="utf-8")


def generer(svg: str, sortie: pathlib.Path) -> None:
    racine = ET.fromstring(svg)
    if racine.get("viewBox") != "0 0 512 512":
        raise LogoInconnu(f"viewBox inattendue : {racine.get('viewBox')}")
    tuile = next((e for e in racine if e.tag == SVG + "rect"), None)
    if tuile is None:
        raise LogoInconnu("pas de tuile (premier <rect>)")
    dossier = sortie / "drawable"
    dossier.mkdir(parents=True, exist_ok=True)

    logo = vecteur(112)
    convertir(racine, logo, {}, sans_tuile=False, premier=[True])
    ecrire(logo, dossier / "logo.xml")

    avant = vecteur(108)
    zone = ET.SubElement(avant, "group", {f"{{{ANDROID}}}pivotX": "256", f"{{{ANDROID}}}pivotY": "256",
                                          f"{{{ANDROID}}}scaleX": f"{ZONE_SURE:.4f}", f"{{{ANDROID}}}scaleY": f"{ZONE_SURE:.4f}"})
    convertir(racine, zone, {}, sans_tuile=True, premier=[True])
    ecrire(avant, dossier / "ic_launcher_premier_plan.xml")

    fond = vecteur(108)
    fond.append(trace("M0,0H512V512H0Z", {"fill": tuile.get("fill", "")}))
    ecrire(fond, dossier / "ic_launcher_fond.xml")


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    try:
        generer(pathlib.Path(argv[1]).read_text(encoding="utf-8"), pathlib.Path(argv[2]))
    except LogoInconnu as e:
        print(f"❌ logo : {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
