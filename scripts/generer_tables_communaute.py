#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables de la Communauté (#20), générées au build depuis
`contrat/tables-clients.json` — comme l'iOS (`Genere/TablesCommunaute.swift`).

Le contrat d'API ne sert que des CODES (statut d'une idée, type, catégorie et statut
d'une annonce) : les libellés et les teintes vivent dans le front du site, qui les publie
dans `tables-clients.json` — `statutsIdee` (`idees.ts`), `typesAnnonce`,
`categoriesAnnonce`, `trisAnnonce`, `statutsAnnonce` et `teinteStatutAnnonce`
(`annonces.ts`).

Les seuils de la note d'un avis (`api/app/utils/avis.py`) ne sont pas encore publiés :
ils sont TENUS ici, aux valeurs de l'iOS, tant que `seuilsAvis` manque à la table
(coprofirst#2772) ; publiés, ils priment.

Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_communaute.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.communaute"

# Les seuils de l'avis, tenus aux valeurs de l'iOS tant que coprofirst ne les publie pas.
SEUILS_AVIS_TENUS = {"noteMin": 1, "noteMax": 5, "noteRemarque": 3, "notePubliable": 4}


def valeur(tables: dict, nom: str):
    v = tables.get(nom, {}).get("valeur")
    if v is None:
        raise TableInattendue(f"{nom} absente")
    return v


def texte(entree: dict, cles: tuple[str, ...], table: str) -> str:
    for cle in cles:
        v = entree.get(cle)
        if isinstance(v, str) and v:
            return v
    raise TableInattendue(f"{table} : {'/'.join(cles)} manquant ({entree})")


def codes(tables: dict, nom: str) -> list[tuple[str, str]]:
    entrees = valeur(tables, nom)
    if not isinstance(entrees, list) or not entrees:
        raise TableInattendue(f"{nom} n'est pas une liste")
    return [(texte(e, ("val", "value"), nom), texte(e, ("label",), nom)) for e in entrees]


def seuils(tables: dict) -> dict[str, int]:
    publies = tables.get("seuilsAvis", {}).get("valeur")
    if publies is None:
        return dict(SEUILS_AVIS_TENUS)
    if not isinstance(publies, dict) or set(publies) != set(SEUILS_AVIS_TENUS) \
            or not all(isinstance(v, int) for v in publies.values()):
        raise TableInattendue(f"seuilsAvis : forme inattendue ({publies})")
    if not publies["noteMin"] < publies["noteRemarque"] <= publies["notePubliable"] <= publies["noteMax"]:
        raise TableInattendue(f"seuilsAvis : seuils incohérents ({publies})")
    return publies


def liste_teintes(nom: str, entrees: list[tuple[str, str, str]], doc: str) -> list[str]:
    return [f"    /** {doc} */", f"    val {nom}: List<Pair<String, LibelleTeinte>> = listOf("] + [
        f"        {chaine(c)} to LibelleTeinte({chaine(l)}, {chaine(b)})," for c, l, b in entrees] + ["    )", ""]


def liste_codes(nom: str, entrees: list[tuple[str, str]], doc: str) -> list[str]:
    return [f"    /** {doc} */", f"    val {nom}: List<Pair<String, String>> = listOf("] + [
        f"        {chaine(c)} to {chaine(l)}," for c, l in entrees] + ["    )", ""]


def kotlin(tables: dict, source: str) -> str:
    idees = [(texte(e, ("val", "value"), "statutsIdee"), texte(e, ("label",), "statutsIdee"),
              texte(e, ("badge",), "statutsIdee")) for e in valeur(tables, "statutsIdee")]
    if not idees:
        raise TableInattendue("statutsIdee est vide")
    teintes = valeur(tables, "teinteStatutAnnonce")
    if not isinstance(teintes, dict):
        raise TableInattendue("teinteStatutAnnonce n'est pas une table")
    annonces = []
    for code, libelle in codes(tables, "statutsAnnonce"):
        badge = teintes.get(code)
        if not isinstance(badge, str):
            raise TableInattendue(f"teinteStatutAnnonce : {code} sans teinte")
        annonces.append((code, libelle, badge))
    s = seuils(tables)
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_communaute.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (statutsIdee, typesAnnonce, categoriesAnnonce, trisAnnonce, statutsAnnonce,",
        "// teinteStatutAnnonce, seuilsAvis).",
        f"package {PAQUET}",
        "",
        "import fr.coprofirst.app.commun.LibelleTeinte",
        "",
        "/** Ce que le site écrit de la Communauté et que le contrat ne sert pas. */",
        "object TablesCommunaute {",
    ]
    l += liste_teintes("statutsIdee", idees, "Les états d'une idée, dans l'ordre du cycle de vie (`STATUTS_IDEE`).")
    l += liste_codes("typesAnnonce", codes(tables, "typesAnnonce"), "Les types d'annonce (`TYPES_ANNONCE`).")
    l += liste_codes("categoriesAnnonce", codes(tables, "categoriesAnnonce"), "Les catégories d'annonce (`CATEGORIES_ANNONCE`).")
    l += liste_codes("trisAnnonce", codes(tables, "trisAnnonce"), "Les tris des annonces (`TRIS_ANNONCE`) ; le premier par défaut.")
    l += liste_teintes("statutsAnnonce", annonces,
                       "Les états d'une annonce et leur teinte (`STATUTS_ANNONCE`, `TEINTE_STATUT_ANNONCE`).")
    l += [
        "    /** La note d'un avis et ses seuils, la règle du SERVEUR : sous `NOTE_REMARQUE`, ce qui a manqué ;",
        "     *  à partir de `NOTE_PUBLIABLE`, l'accord de diffusion. */",
        f"    const val NOTE_MIN = {s['noteMin']}",
        f"    const val NOTE_MAX = {s['noteMax']}",
        f"    const val NOTE_REMARQUE = {s['noteRemarque']}",
        f"    const val NOTE_PUBLIABLE = {s['notePubliable']}",
        "}",
        "",
    ]
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
    fichier = sortie / PAQUET.replace(".", "/") / "TablesCommunaute.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
