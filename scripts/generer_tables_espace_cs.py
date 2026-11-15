#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables de l'Espace conseil syndical (#24), générées au build depuis
`contrat/tables-clients.json` — comme l'iOS (`Fonctions/EspaceCS/Genere/`).

Le contrat d'API ne sert que des CODES : les libellés et les teintes vivent dans le front
du site, qui les publie dans `tables-clients.json` :

- `verdictsReleve` (`VERDICTS_RELEVE`, ce que la relève a fait d'un courriel), teinté par
  `teinteDuTon` ;
- `formatsAffiche` (`AH_FORMATS`, le format d'une annonce du hall) ;
- `civilites` (`CIVILITES`, une fiche de l'annuaire) ;
- `teinteAge`, `modesSuivi`, `etatsVisite`, `motifsEvolution`, `motifEvolutionParDefaut`
  (le reporting) ;
- `statutsTicket` (`STATUTS_TICKET`) : la TEINTE du statut d'une affaire (le libellé est
  servi avec l'affaire) — provisoire, jusqu'à la pastille commune (#92) ;
- `tranchesRepartition` (`BarresCroisees.svelte`) : la couleur de chaque tranche, en
  JETONS de la charte (`serie-1` → `serie1` de `Couleurs`) — deux jetons, des hachures.

Une table qui change de forme, ou un jeton que la charte ne publie pas : la génération
s'arrête.

Usage : generer_tables_espace_cs.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_charte import propriete  # noqa: E402
from generer_tables import TableInattendue, chaine, sans_picto  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.espacecs"


def valeur(tables: dict, nom: str):
    v = tables.get(nom, {}).get("valeur")
    if v is None:
        raise TableInattendue(f"{nom} absente")
    return v


def texte(v, table: str) -> str:
    if not isinstance(v, str) or not v:
        raise TableInattendue(f"{table} : valeur inattendue ({v!r})")
    return v


def table_de(tables: dict, nom: str) -> dict:
    v = valeur(tables, nom)
    if not isinstance(v, dict) or not v:
        raise TableInattendue(f"{nom} n'est pas une table")
    return v


def liste_de(tables: dict, nom: str) -> list:
    v = valeur(tables, nom)
    if not isinstance(v, list) or not v:
        raise TableInattendue(f"{nom} n'est pas une liste")
    return v


def verdicts(tables: dict) -> list[tuple[str, str, str]]:
    tons = table_de(tables, "teinteDuTon")
    sortie = []
    for code, v in table_de(tables, "verdictsReleve").items():
        if not isinstance(v, dict):
            raise TableInattendue(f"verdictsReleve : {code}")
        ton = texte(v.get("ton"), "verdictsReleve")
        if ton not in tons:
            raise TableInattendue(f"verdictsReleve : ton inconnu {ton}")
        sortie.append((code, texte(v.get("libelle"), "verdictsReleve"), texte(tons[ton], "teinteDuTon")))
    return sortie


def jetons_publies(tables: dict) -> set[str]:
    return {k[2:] for k in table_de(tables, "jetonsCharte") if k.startswith("--")}


def tranches(tables: dict) -> list[tuple[str, list[str]]]:
    connus = jetons_publies(tables)
    sortie = []
    for code, v in table_de(tables, "tranchesRepartition").items():
        fond = v.get("background") if isinstance(v, dict) else None
        noms = re.findall(r"var\(--([a-z0-9-]+)\)", texte(fond, "tranchesRepartition"))
        if not noms or len(noms) > 2:
            raise TableInattendue(f"tranchesRepartition : {code} sans jeton lisible")
        for n in noms:
            if n not in connus:
                raise TableInattendue(f"tranchesRepartition : jeton --{n} absent de la charte")
        sortie.append((code, [propriete(n) for n in noms]))
    return sortie


def kotlin(tables: dict, source: str) -> str:
    formats = [(texte(f.get("val"), "formatsAffiche"), texte(f.get("label"), "formatsAffiche"))
               for f in liste_de(tables, "formatsAffiche")]
    civilites = [texte(c, "civilites") for c in liste_de(tables, "civilites")]
    ages = [(c, texte(b, "teinteAge")) for c, b in table_de(tables, "teinteAge").items()]
    modes = [(texte(m.get("valeur"), "modesSuivi"), texte(m.get("libelle"), "modesSuivi")) for m in liste_de(tables, "modesSuivi")]
    visites = [(c, sans_picto(texte(v.get("libelle"), "etatsVisite")), texte(v.get("teinte"), "etatsVisite"))
               for c, v in table_de(tables, "etatsVisite").items()]
    motifs = [(c, texte(m, "motifsEvolution")) for c, m in table_de(tables, "motifsEvolution").items()]
    defaut = texte(valeur(tables, "motifEvolutionParDefaut"), "motifEvolutionParDefaut")
    statuts = [(texte(t.get("value"), "statutsTicket"), texte(t.get("badge"), "statutsTicket"))
               for t in liste_de(tables, "statutsTicket")]

    def teintes(nom: str, entrees: list, doc: str) -> list[str]:
        return [f"    /** {doc} */", f"    val {nom}: Map<String, LibelleTeinte> = mapOf("] + [
            f"        {chaine(c)} to LibelleTeinte({chaine(l)}, {chaine(b)})," for c, l, b in entrees] + ["    )", ""]

    def paires(nom: str, entrees: list, doc: str, genre: str = "List<Pair<String, String>> = listOf(") -> list[str]:
        return [f"    /** {doc} */", f"    val {nom}: {genre}"] + [
            f"        {chaine(c)} to {chaine(l)}," for c, l in entrees] + ["    )", ""]

    l = [
        "// GÉNÉRÉ par scripts/generer_tables_espace_cs.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (verdictsReleve, teinteDuTon, formatsAffiche, civilites, teinteAge,",
        "// modesSuivi, etatsVisite, motifsEvolution, motifEvolutionParDefaut, statutsTicket, tranchesRepartition).",
        f"package {PAQUET}",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import fr.coprofirst.app.charte.Couleurs",
        "import fr.coprofirst.app.commun.LibelleTeinte",
        "",
        "/** Ce que le site écrit de l'Espace CS et que le contrat ne sert pas. */",
        "object TablesEspaceCS {",
    ]
    l += teintes("verdicts", verdicts(tables), "Ce que la relève a fait d'un courriel (`VERDICTS_RELEVE`), teinté par son ton.")
    l += paires("formatsAffiche", formats, "Les formats qu'on peut demander pour une affiche (`AH_FORMATS`) : code → libellé.")
    l += ["    /** Les civilités d'une fiche de l'annuaire, dans l'ordre du site (`CIVILITES`). */",
          "    val civilites: List<String> = listOf(" + ", ".join(chaine(c) for c in civilites) + ")", ""]
    l += paires("teintesAge", ages, "La pastille de l'âge d'une affaire, selon son ancienneté servie (`TEINTE_AGE`).",
                "Map<String, String> = mapOf(")
    l += paires("modesSuivi", modes, "Les modes du Suivi des affaires, dans l'ordre du site (`MODES_SUIVI`).")
    l += teintes("etatsVisite", visites, "L'état d'une visite d'entretien, calculé au serveur (`ETATS_VISITE`).")
    l += paires("motifsEvolution", motifs, "Ce que le signal « a évolué » dit d'une affaire (`MOTIFS_EVOLUTION`).",
                "Map<String, String> = mapOf(")
    l += paires("teintesStatutAffaire", statuts,
                "La teinte du statut d'une affaire (`STATUTS_TICKET`) ; le libellé est servi. Provisoire (#92).",
                "Map<String, String> = mapOf(")
    l += ["    /** Un motif inconnu ou absent (`MOTIF_EVOLUTION_PAR_DEFAUT`). */",
          f"    const val MOTIF_EVOLUTION_PAR_DEFAUT: String = {chaine(defaut)}", ""]
    l += ["    /**",
          "     * La couleur de chaque tranche d'une répartition (`BarresCroisees.svelte`), en jetons de",
          "     * la charte : une couleur, un aplat ; deux, des hachures.",
          "     */",
          "    val tranches: Map<String, List<(Couleurs) -> Color>> = mapOf("]
    l += [f"        {chaine(c)} to listOf(" + ", ".join(f"{{ it.{n} }}" for n in noms) + ")," for c, noms in tranches(tables)]
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
    fichier = sortie / PAQUET.replace(".", "/") / "TablesEspaceCS.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
