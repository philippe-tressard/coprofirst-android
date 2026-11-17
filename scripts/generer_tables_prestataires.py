#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables de « Prestataires » (#19), générées au build depuis
`contrat/tables-clients.json` — comme l'iOS (`Genere/TablesPrestataires.swift`).

Le contrat d'API ne sert que des CODES (`type_prestataire`) : les libellés vivent dans
le front du site, qui les publie dans `tables-clients.json` :

- les catégories de prestataires, leur libellé et ce qu'elles couvrent
  (`typesPrestataire`, `TYPES_PRESTATAIRE` de `prestataires.ts`) ;
- le filtre « Contrat » (`filtresContrat`, `FILTRES_CONTRAT`) ;
- `TablesNotations` : les états d'un avis, le libellé et la teinte de chacun, et l'état
  d'une moyenne par ses seuils (`notations`, `notations.ts` : `ETATS`, `PAR_ETAT`,
  `SEUILS_DE_MOYENNE`, `ETAT_AU_DELA`, coprofirst#2775), comme l'iOS
  (`Genere/TablesNotations.swift`, #229) ; et les motifs d'un avis à cocher, avec la forme
  de la phrase qu'ils composent (`commentaireAvis` : `POINTS_FORTS`, `POINTS_FAIBLES`,
  `FORME_COMMENTAIRE`, tables 1.5.0, coprofirst#2816).

Les onglets de la page et leurs descriptifs sont dans `OngletsDuSite`
(scripts/generer_tables.py). Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_prestataires.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import re
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
        f"// Source : {source} (typesPrestataire, filtresContrat, notations).",
        f"package {PAQUET}",
        "",
        "import fr.coprofirst.app.commun.LibelleTeinte",
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
    l += ["    )", "}"]
    l += kotlin_notations(tables)
    return "\n".join(l)


BADGE = re.compile(r"^badge-[a-z]+$")


def kotlin_notations(tables: dict) -> list[str]:
    """`TablesNotations` : les états d'un avis, leur libellé et teinte, l'état d'une moyenne."""
    n = valeur(tables, "notations")
    etats, par_etat = n.get("etats"), n.get("parEtat")
    seuils, au_dela = n.get("seuilsDeMoyenne"), n.get("etatAuDela")
    if (not isinstance(etats, list) or not etats or len(set(etats)) != len(etats)
            or not all(isinstance(e, str) and re.match(r"^[a-z_]+$", e) for e in etats)):
        raise TableInattendue(f"notations.etats : forme inattendue ({etats})")
    if not isinstance(par_etat, dict) or set(par_etat) != set(etats):
        raise TableInattendue("notations.parEtat ne couvre pas exactement les états")
    for e, v in par_etat.items():
        if (not isinstance(v, dict) or not isinstance(v.get("libelle"), str) or not v["libelle"].strip()
                or not isinstance(v.get("badge"), str) or not BADGE.match(v["badge"])):
            raise TableInattendue(f"notations.parEtat.{e} : forme inattendue ({v})")
    bornes = [s.get("borne") if isinstance(s, dict) else None for s in seuils or []]
    if (not isinstance(seuils, list) or not seuils
            or not all(isinstance(b, (int, float)) and not isinstance(b, bool) for b in bornes)
            or bornes != sorted(set(bornes)) or not all(s.get("etat") in etats for s in seuils)):
        raise TableInattendue(f"notations.seuilsDeMoyenne : forme inattendue ({seuils})")
    if au_dela not in etats:
        raise TableInattendue(f"notations.etatAuDela inconnu ({au_dela})")
    l = ["", "/** Les états d'un avis du conseil et ce qu'en dit le site (`notations.ts`). */",
         "object TablesNotations {",
         "    /** Les états, dans l'ordre du site (`ETATS`). */",
         "    val etats: List<String> = listOf(" + ", ".join(chaine(e) for e in etats) + ")",
         "", "    /** Le libellé et la teinte de chaque état (`PAR_ETAT`). */",
         "    val parEtat: Map<String, LibelleTeinte> = mapOf("]
    l += [f"        {chaine(e)} to LibelleTeinte({chaine(par_etat[e]['libelle'])}, {chaine(par_etat[e]['badge'])})," for e in etats]
    l += ["    )", "",
          "    /** L'état d'une moyenne : sous la borne, cet état — dans l'ordre (`SEUILS_DE_MOYENNE`). */",
          "    val seuilsDeMoyenne: List<Pair<Double, String>> = listOf("]
    l += [f"        {float(s['borne'])} to {chaine(s['etat'])}," for s in seuils]
    l += ["    )", "", "    /** Au-delà du dernier seuil (`ETAT_AU_DELA`). */",
          f"    const val ETAT_AU_DELA = {chaine(au_dela)}"]
    l += kotlin_commentaire(tables)
    l += ["}", ""]
    return l


FORME = ("prefixeForts", "prefixeFaibles", "entreMotifs", "finDeGroupe", "entreGroupes", "avantLibre")


def kotlin_commentaire(tables: dict) -> list[str]:
    """Les motifs à cocher et la forme de la phrase qu'ils composent (`commentaireAvis`)."""
    c = valeur(tables, "commentaireAvis")
    forme = c.get("forme")
    if not isinstance(forme, dict) or set(forme) != set(FORME) or not all(
            isinstance(forme[k], str) and forme[k] for k in FORME if k != "entreGroupes"):
        raise TableInattendue(f"commentaireAvis.forme : forme inattendue ({forme})")
    if not isinstance(forme["entreGroupes"], str):
        raise TableInattendue("commentaireAvis.forme.entreGroupes n'est pas un texte")
    listes = {}
    for nom in ("pointsForts", "pointsFaibles"):
        motifs = c.get(nom)
        if (not isinstance(motifs, list) or not motifs or len(set(motifs)) != len(motifs)
                or not all(isinstance(m, str) and m.strip() for m in motifs)):
            raise TableInattendue(f"commentaireAvis.{nom} : forme inattendue ({motifs})")
        # Un motif qui contient un séparateur rendrait la phrase indécomposable : la correction
        # d'un avis ne recocherait plus ses cases.
        for m in motifs:
            if any(forme[k] in m for k in ("entreMotifs", "finDeGroupe", "avantLibre")):
                raise TableInattendue(f"commentaireAvis.{nom} : « {m} » contient un séparateur de la phrase")
        listes[nom] = motifs
    l = ["", "    /** Les points forts à cocher, dans l'ordre du site, qui est celui de la phrase (`POINTS_FORTS`). */",
         "    val pointsForts: List<String> = listOf(" + ", ".join(chaine(m) for m in listes["pointsForts"]) + ")",
         "", "    /** Les points faibles à cocher (`POINTS_FAIBLES`). */",
         "    val pointsFaibles: List<String> = listOf(" + ", ".join(chaine(m) for m in listes["pointsFaibles"]) + ")",
         "", "    /** La forme de la phrase composée par les cases (`FORME_COMMENTAIRE`, `phraseGeneree`). */"]
    l += [f"    const val {constante(nom)} = {chaine(forme[nom])}" for nom in FORME]
    return l


def constante(nom: str) -> str:
    """`prefixeForts` → `PREFIXE_FORTS`."""
    return re.sub(r"([A-Z])", r"_\1", nom).upper()


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
