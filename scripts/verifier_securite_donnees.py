#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""La section « Sécurité des données » de Google Play tenue AVEC le code (#32) — comme
`scripts/verifier-confidentialite.py` de l'iOS : déclaré = réel (`standards/14` §3 bis).

`docs/play-store/securite-des-donnees.md` porte deux tableaux marqués, comparés dans les
deux sens :

- `table:envois` — chaque écriture du client généré (`post`, `patch`, `put`, `delete`
  du contrat) que `app/src/main/java` appelle a sa ligne, et chaque ligne nomme une
  écriture du contrat que l'app appelle. Seule exception, nommée : une ligne marquée
  « à venir (#N) », prête pour l'envoi qu'un ticket ajoute. Les données d'une ligne sont
  des codes du §3 (ou « — ») ;
- `table:permissions` — exactement les `uses-permission` du manifeste.

Un tableau absent ou vide rend INCONNU (sortie 2), jamais conforme.

Usage : verifier_securite_donnees.py [racine du dépôt]
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

DECLARATION = "docs/play-store/securite-des-donnees.md"
ECRITURES = ("post", "patch", "put", "delete")
A_VENIR = re.compile(r"à venir \(#\d+\)")


class Inconnu(Exception):
    pass


def nom_kotlin(operation: str) -> str:
    """`post_auth_change-password` → `postAuthChangePassword` (openapi-generator)."""
    morceaux = [m for m in re.split(r"[^A-Za-z0-9]+", operation) if m]
    return morceaux[0] + "".join(m[:1].upper() + m[1:] for m in morceaux[1:])


def ecritures_du_contrat(contrat: dict) -> set[str]:
    return {nom_kotlin(op["operationId"]) for chemin in contrat["paths"].values()
            for verbe, op in chemin.items() if verbe in ECRITURES}


def appelees(ecritures: set[str], source: str) -> set[str]:
    """Les écritures appelées, au nom exact suivi d'une parenthèse (`api { postX(…) }`, `.postX(`)."""
    return {e for e in ecritures if re.search(rf"\b{e}\(", source)}


def tableau(texte: str, marque: str) -> list[list[str]]:
    """Les lignes du tableau qui suit `<!-- table:marque -->`, en-tête et séparateur ôtés."""
    debut = texte.find(f"<!-- table:{marque} -->")
    if debut < 0:
        raise Inconnu(f"tableau « {marque} » absent de {DECLARATION}")
    lignes = []
    for ligne in texte[debut:].splitlines()[1:]:
        if not ligne.startswith("|"):
            if lignes:
                break
            continue
        lignes.append([c.strip() for c in ligne.strip().strip("|").split("|")])
    lignes = lignes[2:]
    if not lignes:
        raise Inconnu(f"tableau « {marque} » vide")
    return lignes


def codes_declares(texte: str) -> set[str]:
    """Les codes de données du §3 : la première colonne de son tableau."""
    section = texte.split("## 3.", 1)[1].split("## 4.", 1)[0]
    return set(re.findall(r"^\| `([^`]+)` \|", section, flags=re.M))


def verifier(texte: str, ecritures: set[str], source: str, manifeste: str) -> list[str]:
    erreurs = []
    codes = codes_declares(texte)
    if not codes:
        raise Inconnu("aucun code de donnée au §3")
    lignes = {}
    for envoi, donnees in tableau(texte, "envois"):
        nom = envoi.strip("`")
        lignes[nom] = donnees
        if nom not in ecritures:
            erreurs.append(f"{nom} : pas une écriture du contrat")
        if donnees.split(" — ")[0].strip() != "—":
            inconnus = set(re.findall(r"`([^`]+)`", donnees.split(" — ")[0])) - codes
            erreurs += [f"{nom} : donnée « {c} » absente du §3" for c in sorted(inconnus)]
    utilisees = appelees(ecritures, source)
    erreurs += [f"{e} : appelée par l'app, absente de table:envois" for e in sorted(utilisees - set(lignes))]
    erreurs += [f"{e} : déclarée sans être appelée (ou la marquer « à venir (#N) »)"
                for e in sorted(set(lignes) - utilisees) if not A_VENIR.search(lignes[e])]
    permissions = set(re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', manifeste))
    declarees = {p.strip("`") for p, _ in tableau(texte, "permissions")}
    erreurs += [f"{p} : demandée par le manifeste, absente de table:permissions" for p in sorted(permissions - declarees)]
    erreurs += [f"{p} : déclarée sans être demandée" for p in sorted(declarees - permissions)]
    return erreurs


def main(argv: list[str]) -> int:
    racine = pathlib.Path(argv[1] if len(argv) > 1 else pathlib.Path(__file__).resolve().parent.parent)
    try:
        texte = (racine / DECLARATION).read_text(encoding="utf-8")
        contrat = json.loads((racine / "contrat/openapi.json").read_text(encoding="utf-8"))
        source = "\n".join(p.read_text(encoding="utf-8") for p in sorted((racine / "app/src/main/java").rglob("*.kt")))
        manifeste = (racine / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        erreurs = verifier(texte, ecritures_du_contrat(contrat), source, manifeste)
    except (OSError, KeyError, ValueError, Inconnu) as e:
        print(f"❓ INCONNU : sécurité des données — {e}", file=sys.stderr)
        return 2
    for e in erreurs:
        print(f"❌ {DECLARATION} : {e}", file=sys.stderr)
    return 1 if erreurs else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
