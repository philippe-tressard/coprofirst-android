#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables du site que l'écran Profil lit, générées au build depuis
`contrat/tables-clients.json` — comme l'iOS (`TablesProfil`, `RolesUtilisateur`) :

- les libellés de statut (`attributsStatut`, `libellesHerites`) et de rôle
  (`attributsRole`), les rôles mis en avant (`rolesMisEnAvant`) ;
- les profils qu'on peut demander depuis son profil (`statutsDemandables`).

⚠️ Les libellés de statut et de rôle serviront à d'autres écrans : ils passeront à la
couche commune (coprofirst-android#41), et ce générateur n'en gardera que le propre du
Profil. Une table qui change de forme arrête la génération.

Usage : generer_tables_profil.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from generer_tables import TableInattendue, chaine  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.profil"


def valeur(tables: dict, table: str):
    if table not in tables or "valeur" not in tables[table]:
        raise TableInattendue(f"{table} absente")
    return tables[table]["valeur"]


def chaines(tables: dict, table: str) -> list[str]:
    v = valeur(tables, table)
    if not isinstance(v, list) or not v or not all(isinstance(x, str) for x in v):
        raise TableInattendue(f"{table} n'est pas une liste de chaînes non vide")
    return v


def libelles(tables: dict, table: str, champ: str | None) -> dict[str, str]:
    """Une table `clé → libellé` ; `champ` : le libellé est dans cet attribut de l'objet."""
    v = valeur(tables, table)
    if not isinstance(v, dict) or not v:
        raise TableInattendue(f"{table} n'est pas un objet non vide")
    sortie = {}
    for cle, x in v.items():
        texte = x.get(champ) if champ and isinstance(x, dict) else x
        if not isinstance(texte, str) or not texte.strip():
            raise TableInattendue(f"{table}.{cle} : libellé manquant")
        sortie[cle] = texte.strip()
    return sortie


def table_kotlin(nom: str, doc: str, table: dict[str, str]) -> list[str]:
    l = [f"    /** {doc} */", f"    val {nom}: Map<String, String> = mapOf("]
    l += [f"        {chaine(c)} to {chaine(v)}," for c, v in table.items()]
    return l + ["    )", ""]


def liste_kotlin(nom: str, doc: str, valeurs: list[str]) -> list[str]:
    return [f"    /** {doc} */", f"    val {nom}: List<String> = listOf(" + ", ".join(chaine(v) for v in valeurs) + ")", ""]


def kotlin(tables: dict, source: str) -> str:
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_profil.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (attributsStatut, libellesHerites, attributsRole, rolesMisEnAvant, statutsDemandables).",
        f"package {PAQUET}",
        "",
        "/** Ce que le site écrit des statuts et des rôles, et que le contrat ne sert pas. */",
        "object TablesProfil {",
    ]
    l += table_kotlin("libellesStatut", "Le libellé de chaque statut (`ATTRIBUTS_STATUT`).",
                      libelles(tables, "attributsStatut", "libelle"))
    l += table_kotlin("libellesHerites", "Les anciens statuts encore servis (`LIBELLES_HERITES`).",
                      libelles(tables, "libellesHerites", None))
    l += table_kotlin("libellesRole", "Le libellé de chaque rôle (`ATTRIBUTS_ROLE`).",
                      libelles(tables, "attributsRole", "libelle"))
    l += liste_kotlin("rolesMisEnAvant", "Les rôles que l'en-tête annonce après le statut (`ROLES_MIS_EN_AVANT`).",
                      chaines(tables, "rolesMisEnAvant"))
    l += liste_kotlin("statutsDemandables", "Les profils qu'on peut demander depuis son profil (`STATUTS_DEMANDABLES`).",
                      chaines(tables, "statutsDemandables"))
    return "\n".join(l).rstrip() + "\n}\n"


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        texte = kotlin(json.loads(entree.read_text(encoding="utf-8"))["tables"], entree.name)
    except TableInattendue as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    fichier = sortie / PAQUET.replace(".", "/") / "TablesProfil.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(texte, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
