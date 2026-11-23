#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Une divergence d'ÉTAPE nomme le ticket qui la lève (#336), et ce ticket est ouvert (#338).

`python3 -I scripts/test_divergences.py` (joué par verifier-depot.sh). La règle (`CLAUDE.md`,
« L'app iOS est la référence fonctionnelle ») distingue deux cas :

- une divergence **définitive** (une contrainte d'Android : pas de barre dans une feuille,
  pas de balayage dans une liste) se déclare avec sa raison, sans ticket — aucun ne la
  résorbera ;
- une divergence d'**étape** (« pas encore », « d'ici là », « en attendant »…) nomme le
  ticket qui la résorbe : sans lui, l'étape devient définitive sans que personne l'ait décidé.

La liste des divergences se lit par recherche : `git grep -n -i "divergence déclarée"`. Ce
test lit chaque mention jusqu'à la fin de son commentaire ou de son paragraphe, et refuse une
étape sans `#N`.

`--tickets` (#338) lit GitHub, donc hors du pre-commit : la concordance quotidienne le joue
(`.github/workflows/concordance.yml`). Chaque `#N` ou `dépôt#N` cité par une divergence
d'étape, ou par le commentaire d'une attente (« pour l'heure », « en attendant », « d'ici là », « à retirer
quand »), doit être ouvert : un ticket clos laisse un commentaire qui renvoie au vide. Sortie :
0, 1 (un ticket clos), 2 (INCONNU : GitHub illisible, ou rien de lu).
"""

from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
MENTION = re.compile(r"divergence déclarée", re.I)
ETAPE = re.compile(r"pas encore|pour l'heure|pour le moment|en attendant|d'ici là|à retirer quand|"
                   r"étape intermédiaire|temporaire|viendra|au lot suivant", re.I)
ATTENTE = re.compile(r"pour l'heure|pour le moment|en attendant|d'ici là|à retirer quand|au lot suivant", re.I)
TICKET = re.compile(r"(?<![\w/#])([\w-]*)#(\d+)\b")
JUGES = re.compile(r"\.(kt|kts|xml|md|sh|py|yml|properties)$")
PROPRIETAIRE = "philippe-tressard"
DEPOT = "coprofirst-android"
PLAFOND = 12
#: Un marqueur entre guillemets (« d'ici là ») CITE la règle, il n'attend rien : il ne compte pas.
CITATION = re.compile(r"«[^»\n]*»")


def sans_citations(texte: str) -> str:
    return CITATION.sub("", texte)


def passage(lignes: list[str], i: int, debut: int) -> str:
    """De `debut` sur la ligne `i` à la fin de son commentaire (`*/`, `-->`, fin des `//` ou des `#`)
    ou de son paragraphe ; une ligne de code qui suit n'en est pas."""
    premiere = lignes[i][debut:]
    if re.search(r"\*/|-->", premiere):
        return premiere
    marque = re.match(r"\s*(//|#|\*|<!--)?", lignes[i]).group(1)
    morceau = [premiere]
    for ligne in lignes[i + 1:i + PLAFOND]:
        nue = ligne.strip()
        if not nue or nue.startswith(("| ", "```", "##")):
            break
        if marque in ("//", "#") and not nue.startswith(marque):
            break
        if marque == "*" and not nue.startswith("*"):
            break
        morceau.append(ligne)
        if "*/" in ligne or "-->" in ligne or nue == "*":
            break
    return "\n".join(morceau)


def debut_du_bloc(lignes: list[str], i: int) -> int:
    """La première ligne du commentaire qui porte la ligne `i` (une attente vaut pour tout son bloc)."""
    marque = re.match(r"\s*(//|#|\*)?", lignes[i]).group(1)
    if not marque:
        return i
    while i > 0 and lignes[i - 1].strip().startswith(marque) and "/**" not in lignes[i - 1]:
        i -= 1
    return i


def fautes(textes: dict[str, str]) -> tuple[list[str], int]:
    refus, mentions = [], 0
    for chemin, texte in sorted(textes.items()):
        lignes = texte.splitlines()
        for i, ligne in enumerate(lignes):
            m = MENTION.search(ligne)
            if not m:
                continue
            mentions += 1
            lu = passage(lignes, i, m.start())
            etape = ETAPE.search(sans_citations(lu))
            if etape and not TICKET.search(lu):
                refus.append(f"{chemin}:{i + 1} : divergence d'étape (« {etape.group(0)} ») sans le ticket qui la lève")
    return refus, mentions


def tickets_cites(textes: dict[str, str]) -> dict[str, list[str]]:
    """`dépôt#N` → lieux : les tickets que cite une divergence d'ÉTAPE (de la mention à la fin de
    son commentaire), ou le commentaire entier qui porte une attente. Ceux d'une divergence
    définitive disent d'où elle vient : ils peuvent être clos."""
    cites: dict[str, list[str]] = {}
    for chemin, texte in sorted(textes.items()):
        lignes = texte.splitlines()
        for i, ligne in enumerate(lignes):
            if MENTION.search(ligne):
                lu = passage(lignes, i, MENTION.search(ligne).start())
                if not ETAPE.search(sans_citations(lu)):
                    continue          # définitive : ses tickets disent d'où elle vient
            elif ATTENTE.search(sans_citations(ligne)):
                debut = debut_du_bloc(lignes, i)
                lu = "\n".join(lignes[debut:i]) + "\n" + passage(lignes, i, 0)
            else:
                continue
            for t in TICKET.finditer(lu):
                cites.setdefault(f"{t.group(1) or DEPOT}#{t.group(2)}", []).append(f"{chemin}:{i + 1}")
    return cites


def fichiers_juges() -> dict[str, str]:
    suivis = subprocess.run(["git", "ls-files", "--cached"], cwd=RACINE, capture_output=True,
                            text=True, check=True).stdout.splitlines()
    return {f: (RACINE / f).read_text(encoding="utf-8") for f in suivis
            if JUGES.search(f) and not f.startswith(("contrat/", "LICENSES/")) and f != "scripts/test_divergences.py"}


def etat(cle: str) -> str | None:
    depot, numero = cle.split("#")
    r = subprocess.run(["gh", "api", f"repos/{PROPRIETAIRE}/{depot}/issues/{numero}", "--jq", ".state"],
                       capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 and r.stdout.strip() else None


def juger_tickets() -> int:
    cites = tickets_cites(fichiers_juges())
    if not cites:
        print("❓ INCONNU : aucun ticket cité par une divergence ou une attente — le motif ne lit plus rien")
        return 2
    clos, inconnus = [], []
    for cle, lieux in sorted(cites.items()):
        e = etat(cle)
        if e is None:
            inconnus.append(cle)
        elif e != "open":
            clos.append(f"{cle} est clos, et toujours cité comme une attente : {', '.join(lieux)}")
    for c in clos:
        print(f"❌ {c}")
    if inconnus:
        print(f"❓ INCONNU : {', '.join(inconnus)} illisibles")
    if clos:
        return 1
    if inconnus:
        return 2
    print(f"✅ {len(cites)} tickets cités par une divergence ou une attente, tous ouverts")
    return 0


class Divergences(unittest.TestCase):
    def test_le_depot(self):
        refus, mentions = fautes(fichiers_juges())
        self.assertGreater(mentions, 20, "INCONNU : trop peu de divergences lues")
        self.assertEqual(refus, [])

    def test_autotest_etape_sans_ticket(self):
        cas = {
            "etape.kt": "/**\n * ⚠️ Divergence déclarée : une notification touchée n'ouvre pas encore\n * son élément.\n */\n",
            "etape_ticket.kt": "/**\n * ⚠️ Divergence déclarée : pas encore servie par le site\n * (coprofirst#2900, #333).\n */\n",
            "definitive.kt": "// Divergence déclarée : pas de barre dans une feuille d'Android.\n// Le pied la remplace.\n",
            "ligne.kt": "// Divergence déclarée : en attendant le site.\nval x = 1 // #12\n",
            "doc.md": "⚠️ Divergence déclarée : l'iOS teinte d'un aplat vert ;\nici, deux contours.\n\n#12 plus loin\n",
            "res.xml": "<!-- Divergence déclarée : d'ici là, le libellé local. -->\n<string>#4</string>\n",
        }
        refus, mentions = fautes(cas)
        self.assertEqual(mentions, 6)
        self.assertEqual(sorted(r.split(":")[0] for r in refus), ["etape.kt", "ligne.kt", "res.xml"])

    def test_autotest_tickets_cites(self):
        cites = tickets_cites({
            "a.sh": "# Une suite iOS ne prend pas le verrou (coprofirst-ios#213) : on la regarde\n"
                    "# encore ici. À retirer quand elle passera par lui (#40).\nx=1 # #99\n",
            "b.kt": "/**\n * ⚠️ Divergence déclarée : un toucher n'ouvre pas encore son élément (#23).\n */\nval y = 2 // #98\n",
            "d.kt": "// Divergence déclarée : l'iOS ouvre le navigateur ; ici le fil, comme le demande #147.\n",
            "c.kt": "// la ligne d'une liste pas encore composée (#119)\n// Le mien (#138) : servi.\n",
            # La règle elle-même cite ses marqueurs entre guillemets : elle n'attend rien.
            "CLAUDE.md": "- Une divergence se déclare (« Divergence déclarée ») : d'étape (« pas encore »,\n"
                         "  « d'ici là »), avec son ticket — test_divergences.py (#336, #338).\n",
            "c.yml": "      - name: Tickets attendus (un « d'ici là » qui cite un ticket clos, #338)\n",
        })
        self.assertEqual(sorted(cites), ["coprofirst-android#23", "coprofirst-android#40", "coprofirst-ios#213"])


if __name__ == "__main__":
    if sys.argv[1:] == ["--tickets"]:
        sys.exit(juger_tickets())
    unittest.main()
