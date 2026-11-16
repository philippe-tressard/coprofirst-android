#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""La CONCORDANCE quotidienne avec coprofirst (#30 ; standards/09 §8, règle 2 : ce qu'un
client dérive du serveur se surveille chaque jour), comme `scripts/concordance.py` de
l'iOS. Elle ne lit que ce que coprofirst PUBLIE — les pièces jointes de ses releases et
`GET /config` des installations —, jamais son source (coprofirst-ios#237).

Deux verdicts, jamais confondus :

1. `--plancher` (défaut) — l'ÉCART AU PLANCHER, ROUGE (sortie 1) si :
   - une pièce de `contrat/` diffère de la release au plancher (`importer-contrat.sh
     --verifier`) : un fichier retouché à la main ;
   - une installation connue (`INSTALLATIONS` de `contrat/plancher.env`) SERT un contrat
     sous celui du plancher : l'app ne l'ouvrirait plus. Le plancher se MESURE.
2. `--montee` — une release publiée au-dessus du plancher : une INFORMATION (sortie 3),
   jamais un rouge. Le rapport dit ce que monter le plancher demanderait : la version du
   contrat, les routes que l'app appelle et qui disparaîtraient, les fichiers générés
   qui changeraient, les clés des tables et des constantes publiées au-dessus du plancher
   (que les générateurs liront quand il montera ; d'ici là, leurs valeurs tenues, #169) ;
   et si les installations connues servent déjà ce contrat. Sortie 0
   si la dernière release EST le plancher.

Une release, une pièce ou une installation illisible : INCONNU (sortie 2), jamais vert.

Usage : concordance.py [--plancher | --montee | --autotest] [--rapport FICHIER.md]
                       [--simuler-ecart]
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import shlex
import subprocess
import sys
import tempfile
import urllib.request

RACINE = pathlib.Path(__file__).resolve().parent.parent
DEPOT = "philippe-tressard/coprofirst"
PIECES = ("openapi.json", "tables-clients.json", "constantes-clients.json")
ROUGE, INCONNU, INFORMATION = 1, 2, 3
VERBES = ("get", "post", "patch", "put", "delete")


class Illisible(Exception):
    pass


def version(texte: str | None) -> tuple[int, int, int] | None:
    m = re.fullmatch(r"\s*v?(\d+)\.(\d+)\.(\d+)\s*", texte or "")
    return tuple(int(x) for x in m.groups()) if m else None


def lire_env(chemin: pathlib.Path) -> dict[str, str]:
    """Les clés de `plancher.env`, comme bash les lit (une valeur entre guillemets, sans eux)."""
    valeurs = {}
    for ligne in chemin.read_text(encoding="utf-8").splitlines():
        if "=" in ligne and not ligne.lstrip().startswith("#"):
            cle, valeur = ligne.split("=", 1)
            valeurs[cle.strip()] = " ".join(shlex.split(valeur))
    return valeurs


def operations(contrat: dict) -> dict[str, str]:
    """Chaque opération du contrat : son nom généré (`postAuthLogin`) → `VERBE /chemin`."""
    ops = {}
    for chemin, verbes in contrat.get("paths", {}).items():
        for verbe, op in verbes.items():
            if verbe in VERBES and "operationId" in op:
                morceaux = [m for m in re.split(r"[^A-Za-z0-9]+", op["operationId"]) if m]
                ops[morceaux[0] + "".join(m[:1].upper() + m[1:] for m in morceaux[1:])] = f"{verbe.upper()} {chemin}"
    return ops


def appelees(ops: dict[str, str], source: str) -> set[str]:
    return {nom for nom in ops if re.search(rf"\b{nom}\(", source)}


def source_de_l_app() -> str:
    return "\n".join(p.read_text(encoding="utf-8") for p in sorted((RACINE / "app/src/main/java").rglob("*.kt")))


# ── Le plancher ────────────────────────────────────────────────────────────────────


def ecarts_au_plancher(plancher: tuple, servis: dict[str, tuple | None]) -> tuple[list[str], list[str]]:
    """(rouges, inconnus) : une installation sous le plancher ; une installation illisible."""
    rouges = [f"{h} sert le contrat {'.'.join(map(str, v))}, sous le plancher {'.'.join(map(str, plancher))} : "
              "l'app ne l'ouvre plus" for h, v in servis.items() if v is not None and v < plancher]
    inconnus = [f"{h} : contrat servi illisible" for h, v in servis.items() if v is None]
    return rouges, inconnus


# Un agent nommé, générique : la passerelle des installations refuse celui de Python (403).
AGENT = "CoproFirst-Android-concordance"


def contrat_servi(hote: str) -> tuple | None:
    requete = urllib.request.Request(f"https://{hote}/api/config", headers={"User-Agent": AGENT})
    try:
        with urllib.request.urlopen(requete, timeout=20) as reponse:
            return version(json.load(reponse).get("version_contrat"))
    except (OSError, ValueError):
        return None


def verdict_plancher(simuler: bool) -> tuple[int, list[str]]:
    env = lire_env(RACINE / "contrat/plancher.env")
    contrat = json.loads((RACINE / "contrat/openapi.json").read_text(encoding="utf-8"))
    plancher = version(contrat.get("info", {}).get("version"))
    hotes = env.get("INSTALLATIONS", "").split()
    if plancher is None or not hotes:
        raise Illisible("contrat du dépôt ou INSTALLATIONS illisibles")
    lignes = [f"Plancher : coprofirst {env.get('PLANCHER')} — contrat {'.'.join(map(str, plancher))}.", ""]
    pieces = subprocess.run(["bash", "scripts/importer-contrat.sh", "--verifier"], cwd=RACINE,
                            capture_output=True, text=True)
    sortie = (pieces.stdout + pieces.stderr).strip()
    lignes.append(f"- Pièces de `contrat/` : {sortie or 'aucune sortie'}")
    servis = {h: contrat_servi(h) for h in hotes}
    if simuler:
        servis[hotes[0]] = (0, 0, 0)
    rouges, inconnus = ecarts_au_plancher(plancher, servis)
    for h, v in servis.items():
        lignes.append(f"- {h} : contrat servi {'.'.join(map(str, v)) if v else 'illisible'}")
    if pieces.returncode == ROUGE:
        rouges.append("une pièce de contrat/ diffère de la release au plancher")
    if pieces.returncode not in (0, ROUGE):
        inconnus.append("pièces de la release illisibles (gh ?)")
    lignes += ["", *(f"❌ {r}" for r in rouges), *(f"❓ {i}" for i in inconnus)]
    code = ROUGE if rouges else INCONNU if inconnus else 0
    lignes.append("✅ Aucun écart au plancher." if code == 0 else "")
    return code, lignes


# ── La montée ──────────────────────────────────────────────────────────────────────


def derniere_release() -> str:
    sortie = subprocess.run(["gh", "release", "view", "-R", DEPOT, "--json", "tagName", "-q", ".tagName"],
                            capture_output=True, text=True)
    if sortie.returncode != 0 or not version(sortie.stdout):
        raise Illisible("dernière release de coprofirst illisible (gh auth status ?)")
    return sortie.stdout.strip()


def telecharger(tag: str, dossier: pathlib.Path) -> None:
    for piece in PIECES:
        if subprocess.run(["gh", "release", "download", tag, "-R", DEPOT, "-p", piece, "-D", str(dossier)],
                          capture_output=True).returncode != 0:
            raise Illisible(f"{piece} de {tag} illisible")


def generes(tables: pathlib.Path, constantes: pathlib.Path, sortie: pathlib.Path) -> dict[str, str]:
    """Ce que les générateurs de tables et de constantes produisent de ces pièces."""
    for script in sorted((RACINE / "scripts").glob("generer_tables*.py")) + [RACINE / "scripts/generer_charte.py"]:
        subprocess.run([sys.executable, "-I", str(script), str(tables), str(sortie)], capture_output=True)
    subprocess.run([sys.executable, "-I", str(RACINE / "scripts/generer_constantes.py"), str(constantes), str(sortie)],
                   capture_output=True)
    return {str(f.relative_to(sortie)): f.read_text(encoding="utf-8").split("\n", 2)[-1]
            for f in sorted(sortie.rglob("*.kt"))}


def cles_neuves(ancien: dict, neuf: dict, rubrique: str) -> list[str]:
    """Les clés d'une rubrique (`tables`, `constantes`) publiées au-dessus du plancher."""
    return sorted(set(neuf.get(rubrique, {})) - set(ancien.get(rubrique, {})))


def ce_que_monter_demande(ancien: dict, neuf: dict, source: str) -> list[str]:
    """Les routes que l'app appelle et que le nouveau contrat n'a plus."""
    avant, apres = operations(ancien), operations(neuf)
    return [f"{nom} ({avant[nom]}) disparaît" for nom in sorted(appelees(avant, source) - set(apres))]


def verdict_montee() -> tuple[int, list[str]]:
    env = lire_env(RACINE / "contrat/plancher.env")
    tag = derniere_release()
    if version(tag) == version(env.get("PLANCHER")):
        return 0, [f"La dernière release ({tag}) est le plancher : rien à monter."]
    with tempfile.TemporaryDirectory() as tmp:
        dossier = pathlib.Path(tmp)
        telecharger(tag, dossier)
        ancien = json.loads((RACINE / "contrat/openapi.json").read_text(encoding="utf-8"))
        neuf = json.loads((dossier / "openapi.json").read_text(encoding="utf-8"))
        cible = version(neuf.get("info", {}).get("version"))
        neuves = [f"`{fichier}` › `{cle}`" for fichier, rubrique in (("tables-clients.json", "tables"),
                                                                     ("constantes-clients.json", "constantes"))
                  for cle in cles_neuves(json.loads((RACINE / "contrat" / fichier).read_text(encoding="utf-8")),
                                         json.loads((dossier / fichier).read_text(encoding="utf-8")), rubrique)]
        avant = generes(RACINE / "contrat/tables-clients.json", RACINE / "contrat/constantes-clients.json", dossier / "avant")
        apres = generes(dossier / "tables-clients.json", dossier / "constantes-clients.json", dossier / "apres")
    if not avant or not apres:
        raise Illisible("les générateurs n'ont rien produit : la comparaison ne prouverait rien")
    disparues = ce_que_monter_demande(ancien, neuf, source_de_l_app())
    changes = sorted(f for f in set(avant) | set(apres) if avant.get(f) != apres.get(f))
    servis = {h: contrat_servi(h) for h in env.get("INSTALLATIONS", "").split()}
    lignes = [f"Release {tag} au-dessus du plancher {env.get('PLANCHER')} — contrat "
              f"{'.'.join(map(str, cible)) if cible else 'illisible'}.", "",
              "**Routes appelées par l'app qui disparaîtraient** :", *(f"- {d}" for d in disparues or ["aucune"]), "",
              "**Fichiers générés qui changeraient** :", *(f"- `{c}`" for c in changes or ["aucun"]), "",
              "**Clés publiées au-dessus du plancher** (lues dès qu'il montera ; valeurs tenues d'ici là, #169) :",
              *(f"- {n}" for n in neuves or ["aucune"]), "",
              "**Contrat servi par les installations connues** :"]
    lignes += [f"- {h} : {'.'.join(map(str, v)) if v else 'illisible'}" for h, v in servis.items()]
    possible = cible is not None and all(v is not None and v >= cible for v in servis.values())
    lignes += ["", "➡️ Monter le plancher à cette release est possible (toutes les installations la servent)."
               if possible else "⏸ Pas encore : une installation connue sert un contrat plus ancien (ou illisible)."]
    return INFORMATION, lignes


# ── Autotest : un écart posé exprès est vu, un cas zéro passe ───────────────────────


def autotest() -> int:
    plancher = (2, 0, 0)
    assert ecarts_au_plancher(plancher, {"a": (2, 0, 0), "b": (2, 1, 0)}) == ([], [])
    rouges, _ = ecarts_au_plancher(plancher, {"a": (1, 9, 9)})
    assert rouges and "a sert le contrat 1.9.9" in rouges[0]
    assert ecarts_au_plancher(plancher, {"a": None})[1]
    ancien = {"paths": {"/x": {"get": {"operationId": "get_x"}}, "/y": {"post": {"operationId": "post_y-z"}}}}
    neuf = {"paths": {"/x": {"get": {"operationId": "get_x"}}}}
    assert ce_que_monter_demande(ancien, neuf, "api { postYZ(corps) }") == ["postYZ (POST /y) disparaît"]
    assert ce_que_monter_demande(ancien, ancien, "api { postYZ(corps) }") == []
    assert version("v3.103.0") == (3, 103, 0) and version("x") is None
    assert cles_neuves({"tables": {"a": 1}}, {"tables": {"a": 1, "b": 2}}, "tables") == ["b"]
    assert cles_neuves({"tables": {"a": 1}}, {"tables": {"a": 1}}, "tables") == []
    print("✅ autotest : un écart posé exprès est vu, un cas zéro passe")
    return 0


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    mode = p.add_mutually_exclusive_group()
    mode.add_argument("--plancher", action="store_true")
    mode.add_argument("--montee", action="store_true")
    mode.add_argument("--autotest", action="store_true")
    p.add_argument("--rapport", type=pathlib.Path)
    p.add_argument("--simuler-ecart", action="store_true")
    args = p.parse_args(argv)
    if args.autotest:
        return autotest()
    try:
        code, lignes = verdict_montee() if args.montee else verdict_plancher(args.simuler_ecart)
    except (Illisible, OSError, ValueError, KeyError) as e:
        code, lignes = INCONNU, [f"❓ INCONNU : {e}"]
    texte = "\n".join(["## " + ("Montée possible" if args.montee else "Écart au plancher"), "", *lignes, ""])
    print(texte)
    if args.rapport:
        args.rapport.write_text(texte, encoding="utf-8")
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
