#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables de « Mes affaires » (#16), générées au build depuis
`contrat/tables-clients.json` — comme l'iOS (`scripts/generateurs/affaires.py`).

Les natures d'une affaire sont générées par la couche commune (`NaturesAffaire`,
scripts/generer_tables.py). Le contrat d'API sert les colonnes du Kanban, les états et les catégories
(`GET /tickets/referentiels`) ; ce qu'il ne sert pas vit dans le front du site, qui le
publie dans `tables-clients.json` :

- les groupes du filtre « Suivi » (`groupesSuivi`, `GROUPES_SUIVI`), sans leur émoji ;
- la teinte de chaque colonne du Kanban (`colonnesKanban`, `var(--kanban-*)`) → le jeton
  de la palette générée (`Couleurs.kanban*`), jamais une couleur ;
- ce qu'une colonne vide affiche (`motColonneVide`), la catégorie d'une actualité
  (`categorieActualite`) ;
- les options de mise en avant d'une affaire (`optionsTicket`, `OPTIONS_TICKET`), avec
  leurs mots (`optionsPublication`, `OPTIONS_PUBLICATION`) ;
- la pastille (`badge-*`) de chaque statut (`statutsTicket`, `statutsTicketHistoriques`,
  `statutsTicketSansCycle`) — la teinte seule : le libellé est SERVI.

Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_affaires.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine, sans_picto  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.affaires"


def valeur(tables: dict, nom: str):
    v = tables.get(nom, {}).get("valeur")
    if v is None:
        raise TableInattendue(f"{nom} absente")
    return v


def texte(entree: dict, cle: str, table: str) -> str:
    v = entree.get(cle)
    if not isinstance(v, str) or not v:
        raise TableInattendue(f"{table} : {cle} manquant ({entree})")
    return v


def jeton(var: str) -> str:
    """`var(--kanban-syndic)` → `kanbanSyndic`, le jeton de la palette générée."""
    m = re.fullmatch(r"var\(--(kanban-[a-z0-9-]+)\)", var or "")
    if not m:
        raise TableInattendue(f"colonnesKanban : teinte inattendue ({var})")
    tete, *suite = m.group(1).split("-")
    return tete + "".join(p.capitalize() for p in suite)


def pastilles(tables: dict) -> list[tuple[str, str]]:
    sortie = [(texte(s, "value", "statutsTicket"), texte(s, "badge", "statutsTicket"))
              for s in valeur(tables, "statutsTicket")]
    for nom in ("statutsTicketHistoriques", "statutsTicketSansCycle"):
        table = valeur(tables, nom)
        if not isinstance(table, dict):
            raise TableInattendue(f"{nom} n'est pas une table")
        sortie += [(code, texte(v, "badge", nom)) for code, v in table.items()]
    return sortie


def options(tables: dict) -> list[tuple[str, str, str, str]]:
    cles = valeur(tables, "optionsTicket")
    if not isinstance(cles, list) or not cles:
        raise TableInattendue("optionsTicket n'est pas une liste")
    mots = {o.get("cle"): o for o in valeur(tables, "optionsPublication")}
    manquantes = [c for c in cles if c not in mots]
    if manquantes:
        raise TableInattendue(f"optionsPublication ne décrit pas {manquantes}")
    return [(c, texte(mots[c], "action", "optionsPublication"), texte(mots[c], "etat", "optionsPublication"),
             texte(mots[c], "aide", "optionsPublication")) for c in cles]


def kotlin(tables: dict, source: str) -> str:
    groupes = []
    for g in valeur(tables, "groupesSuivi"):
        statuts = g.get("statuts")
        if not isinstance(statuts, list) or not statuts:
            raise TableInattendue(f"groupesSuivi : statuts manquants ({g})")
        groupes.append((texte(g, "value", "groupesSuivi"), sans_picto(texte(g, "label", "groupesSuivi")), statuts))
    teintes = [(texte(c, "id", "colonnesKanban"), jeton(texte(c, "color", "colonnesKanban")))
               for c in valeur(tables, "colonnesKanban")]
    vide, actualite = valeur(tables, "motColonneVide"), valeur(tables, "categorieActualite")
    if not isinstance(vide, str) or not isinstance(actualite, str):
        raise TableInattendue("motColonneVide ou categorieActualite n'est pas un texte")
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_affaires.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (groupesSuivi, colonnesKanban, motColonneVide, categorieActualite,",
        "// optionsTicket, optionsPublication, statutsTicket, statutsTicketHistoriques, statutsTicketSansCycle).",
        f"package {PAQUET}",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import fr.coprofirst.app.charte.Couleurs",
        "",
        "/** Ce que le site écrit des affaires et que le contrat ne sert pas. */",
        "object TablesAffaires {",
    ]
    l += ["    /** Des statuts que le filtre « Suivi » réunit sous une option (`GROUPES_SUIVI`). */",
          "    val groupesDeSuivi: List<GroupeDeSuivi> = listOf("]
    l += [f"        GroupeDeSuivi({chaine(c)}, {chaine(lib)}, setOf({', '.join(chaine(s) for s in st)}))," for c, lib, st in groupes]
    l += ["    )", "",
          "    /** La teinte de chaque colonne du Kanban, par son code (`KANBAN_COLS`) : un jeton de la palette. */",
          "    val teintesKanban: Map<String, (Couleurs) -> Color> = mapOf("]
    l += [f"        {chaine(c)} to {{ it.{j} }}," for c, j in teintes]
    l += ["    )", "",
          "    /** Les options de mise en avant d'une affaire, dans l'ordre du site (`OPTIONS_TICKET`, `OPTIONS_PUBLICATION`). */",
          "    val options: List<OptionAffaire> = listOf("]
    l += [f"        OptionAffaire({chaine(c)}, {chaine(a)}, {chaine(e)}, {chaine(d)})," for c, a, e, d in options(tables)]
    l += ["    )", "",
          "    /** La pastille (`badge-*`) de chaque statut — la teinte seule : le libellé est servi. */",
          "    val pastillesStatut: Map<String, String> = mapOf("]
    l += [f"        {chaine(c)} to {chaine(b)}," for c, b in pastilles(tables)]
    l += ["    )", "",
          "    /** Ce qu'une colonne vide affiche (`MOT_COLONNE_VIDE`). */",
          f"    const val COLONNE_VIDE = {chaine(vide)}",
          "",
          "    /** La catégorie d'une actualité (`CATEGORIE_ACTUALITE`). */",
          f"    const val CATEGORIE_ACTUALITE = {chaine(actualite)}",
          "}", ""]
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
    fichier = sortie / PAQUET.replace(".", "/") / "TablesAffaires.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
