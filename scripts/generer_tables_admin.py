#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables du Paramétrage (#26), générées au build depuis `contrat/tables-clients.json`
— comme l'iOS (`scripts/generateurs/admin.py`, `TablesAdmin`).

Le contrat d'API ne sert que des CODES (`GET /admin/maintenance/sante` : `tache`,
`statut`) : les noms et les statuts vivent dans le front du site (`taches.ts`), qui les
publie dans `tables-clients.json` :

- le nom de chaque tâche planifiée (`libellesTache`, `LIBELLE_TACHE`) ;
- le libellé, l'aide et la teinte de chaque statut de santé (`statutsTache`, `STATUTS_TACHE`).

Les descriptifs et les adresses des onglets de la page `admin` sont dans `OngletsDuSite`
(scripts/generer_tables.py) ; la teinte et l'abrégé d'un rôle ou d'un statut de compte,
dans `TablesCommunes` (`Roles`). Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_admin.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.admin"


def valeur(tables: dict, nom: str):
    v = tables.get(nom, {}).get("valeur")
    if not isinstance(v, dict) or not v:
        raise TableInattendue(f"{nom} absente ou vide")
    return v


def kotlin(tables: dict, source: str) -> str:
    libelles = valeur(tables, "libellesTache")
    if not all(isinstance(v, str) for v in libelles.values()):
        raise TableInattendue("libellesTache change de forme")
    statuts = valeur(tables, "statutsTache")
    for code, statut in statuts.items():
        if not isinstance(statut, dict) or set(statut) != {"libelle", "aide", "classe"} \
                or not all(isinstance(v, str) for v in statut.values()):
            raise TableInattendue(f"le statut de tâche « {code} » change de forme")
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_admin.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (libellesTache, statutsTache).",
        f"package {PAQUET}",
        "",
        "/** Le statut de santé d'une tâche planifiée : son libellé, ce qu'il veut dire, sa teinte (`badge-*`). */",
        "data class StatutTache(val libelle: String, val aide: String, val badge: String)",
        "",
        "/** Ce que le site écrit du Paramétrage et que le contrat ne sert pas. */",
        "object TablesAdmin {",
        "    /** Le nom de chaque tâche planifiée (`LIBELLE_TACHE`), par son code servi. */",
        "    val libellesTache: Map<String, String> = mapOf(",
    ]
    l += [f"        {chaine(c)} to {chaine(v)}," for c, v in libelles.items()]
    l += ["    )", "", "    /** Chaque statut de santé d'une tâche (`STATUTS_TACHE`), par son code servi. */",
          "    val statutsTache: Map<String, StatutTache> = mapOf("]
    l += [f"        {chaine(c)} to StatutTache({chaine(s['libelle'])}, {chaine(s['aide'])}, {chaine(s['classe'])}),"
          for c, s in statuts.items()]
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
    fichier = sortie / PAQUET.replace(".", "/") / "TablesAdmin.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
