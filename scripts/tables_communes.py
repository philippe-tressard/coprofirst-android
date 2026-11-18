#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables COMMUNES à plusieurs menus, générées par `generer_tables.py` (qui les écrit) :
l'état d'un accès (`TablesAcces`, #79), la teinte du statut d'une affaire
(`StatutsAffaire`, #92) et le texte du bandeau d'une installation suspendue
(`BandeauSuspendue`, #288). Module à part pour que `generer_tables.py` reste sous 500 lignes ;
il ne s'appelle pas `generer_tables_<menu>.py`, que le build découvre comme table d'un menu.
"""

from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine, teintes, texte, valeur  # noqa: E402

PAQUET_ACCES = "fr.coprofirst.app.commun.acces"


def kotlin_acces(tables: dict, source: str) -> str:
    """`TablesAcces` : l'état d'un accès et ses deux familles, communs à deux menus (#79)."""
    statuts = [(texte(s, "val", "statutsAcces"), texte(s, "label", "statutsAcces"), texte(s, "badge", "statutsAcces"))
               for s in valeur(tables, "statutsAcces")]
    types = [(texte(t, "val", "typesAcces"), texte(t, "label", "typesAcces")) for t in valeur(tables, "typesAcces")]
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (statutsAcces, typesAcces).",
        f"package {PAQUET_ACCES}",
        "",
        "import fr.coprofirst.app.commun.LibelleTeinte",
        "",
        "/** Ce que le site écrit d'un badge ou d'une télécommande et que le contrat ne sert pas (`types-acces.ts`). */",
        "object TablesAcces {",
        *teintes("statutsAcces", statuts, "L'état d'un badge ou d'une télécommande (`STATUTS_ACCES`)."),
        "    /** Les deux familles d'accès, dans l'ordre du site (`TYPES_ACCES`) : code → libellé. */",
        "    val typesAcces: List<Pair<String, String>> = listOf(",
        *[f"        {chaine(c)} to {chaine(lib)}," for c, lib in types],
        "    )",
        "}",
        "",
    ])


def pastilles_statut(tables: dict) -> list[tuple[str, str]]:
    """La pastille (`badge-*`) de chaque statut d'une affaire : ceux du cycle (`statutsTicket`),
    les historiques et ceux hors cycle — la teinte seule, le libellé est servi."""
    sortie = [(texte(s, "value", "statutsTicket"), texte(s, "badge", "statutsTicket"))
              for s in valeur(tables, "statutsTicket")]
    for nom in ("statutsTicketHistoriques", "statutsTicketSansCycle"):
        table = valeur(tables, nom)
        if not isinstance(table, dict):
            raise TableInattendue(f"{nom} n'est pas une table")
        sortie += [(code, texte(v, "badge", nom)) for code, v in table.items()]
    return sortie


def kotlin_statuts_affaire(tables: dict, source: str) -> str:
    """`StatutsAffaire` : la teinte du statut d'une affaire, commune à Mes affaires, au
    Reporting de l'Espace CS et au fil (#92)."""
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (statutsTicket, statutsTicketHistoriques, statutsTicketSansCycle).",
        "package fr.coprofirst.app.commun",
        "",
        "/** La teinte du statut d'une affaire (`BadgeEtatAffaire` du site) ; le libellé est servi. */",
        "object StatutsAffaire {",
        "    /** La pastille (`badge-*`) de chaque statut, par son code. */",
        "    val pastilles: Map<String, String> = mapOf(",
        *[f"        {chaine(c)} to {chaine(b)}," for c, b in pastilles_statut(tables)],
        "    )",
        "}",
        "",
    ])


def kotlin_bandeau_suspendue(tables: dict, source: str) -> str:
    """`BandeauSuspendue` : le texte du bandeau d'une installation suspendue, tel que le site
    l'écrit (`BANDEAU_SUSPENDUE`, Registre R6, #288) — titre, précision, texte."""
    v = tables.get("bandeauSuspendue", {}).get("valeur")
    cles = ("titre", "precision", "texte")
    if not isinstance(v, dict) or set(v) != set(cles) or not all(isinstance(v[c], str) and v[c].strip() for c in cles):
        raise TableInattendue(f"bandeauSuspendue : forme inattendue ({v!r})")
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (bandeauSuspendue).",
        "package fr.coprofirst.app.commun",
        "",
        "/** Le bandeau d'une installation suspendue (`BandeauInstallation` du site), ses mots publiés. */",
        "object BandeauSuspendue {",
        f"    const val titre = {chaine(v['titre'])}",
        f"    const val precision = {chaine(v['precision'])}",
        f"    const val texte = {chaine(v['texte'])}",
        "}",
        "",
    ])
