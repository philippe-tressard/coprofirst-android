#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables de « Prestataires » (#19), générées au build depuis
`contrat/tables-clients.json` — comme l'iOS (`Genere/TablesPrestataires.swift`).

Le contrat d'API ne sert que des CODES (`type_prestataire`) : les libellés vivent dans
le front du site, qui les publie dans `tables-clients.json` :

- les catégories de prestataires, leur libellé et ce qu'elles couvrent
  (`typesPrestataire`, `TYPES_PRESTATAIRE` de `prestataires.ts`) ;
- le filtre « Contrat » (`filtresContrat`, `FILTRES_CONTRAT`).

Les onglets de la page et leurs descriptifs sont dans `OngletsDuSite`
(scripts/generer_tables.py). Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_prestataires.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine, texte, valeur  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.prestataires"


def kotlin(tables: dict, source: str) -> str:
    types = [(texte(t, "val", "typesPrestataire"), texte(t, "label", "typesPrestataire"),
              texte(t, "desc", "typesPrestataire")) for t in valeur(tables, "typesPrestataire")]
    filtres = [(texte(f, "val", "filtresContrat"), texte(f, "label", "filtresContrat"))
               for f in valeur(tables, "filtresContrat")]
    if not types or not filtres:
        raise TableInattendue("typesPrestataire ou filtresContrat vide")
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_prestataires.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (typesPrestataire, filtresContrat).",
        f"package {PAQUET}",
        "",
        "/** Une catégorie de prestataire : son code, son libellé, ce qu'elle couvre. */",
        "data class TypePrestataire(val code: String, val libelle: String, val description: String)",
        "",
        "/** Ce que le site écrit de « Prestataires » et que le contrat ne sert pas. */",
        "object TablesPrestataires {",
        "    /** Les catégories, dans l'ordre du site (`TYPES_PRESTATAIRE`). */",
        "    val types: List<TypePrestataire> = listOf(",
    ]
    l += [f"        TypePrestataire({chaine(c)}, {chaine(lib)}, {chaine(d)})," for c, lib, d in types]
    l += ["    )", "",
          "    /** Le filtre « Contrat » (`FILTRES_CONTRAT`) : code → libellé. */",
          "    val filtresContrat: List<Pair<String, String>> = listOf("]
    l += [f"        {chaine(c)} to {chaine(lib)}," for c, lib in filtres]
    l += ["    )", "}", ""]
    return "\n".join(l)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        code = kotlin(json.loads(entree.read_text(encoding="utf-8"))["tables"], entree.name)
    except TableInattendue as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    fichier = sortie / PAQUET.replace(".", "/") / "TablesPrestataires.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
