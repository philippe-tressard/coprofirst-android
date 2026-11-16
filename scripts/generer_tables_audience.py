#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Ce que le CONTRAT borne d'un lot de mesure d'audience (#29) et que le client généré
n'expose pas : le nombre d'évènements d'UN `POST /telemetry/collect` (`maxItems` de
`LotAudience.events`) et la longueur de la page et de l'action (`maxLength`
d'`EvenementAudience` — l'app n'envoie pas de `detail`), comme l'iOS
(`LimitesAudience`, `scripts/generateurs/audience.py`).

Le serveur refuse EN BLOC (422) un lot qui les dépasse : l'app vide sa file dès qu'elle
atteint le nombre, et n'y met jamais une page trop longue (`MesureAudience`). Écrites à la
main, ces bornes divergeraient le jour où le serveur les change.

La convention du build donne `contrat/tables-clients.json` ; les bornes se lisent dans
`openapi.json`, À CÔTÉ — les deux pièces jointes de la même release, recopiées ensemble
par `scripts/importer-contrat.sh`. Une forme inattendue : la génération s'arrête.

Usage : generer_tables_audience.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue  # noqa: E402

PAQUET = "fr.coprofirst.app.session.audience"
ROUTE = "/telemetry/collect"
LOT = "LotAudience"
EVENEMENT = "EvenementAudience"


def _nombre(valeur, quoi: str) -> int:
    # `bool` est un `int` en Python : un `true` ne passe pas pour une borne.
    if not isinstance(valeur, int) or isinstance(valeur, bool) or valeur < 1:
        raise TableInattendue(f"{quoi} n'est pas un nombre d'au moins 1 : {valeur!r}")
    return valeur


def bornes(schema: dict) -> dict[str, int]:
    """Les bornes du corps de la route — et la route doit bien prendre ce corps."""
    try:
        corps = schema["paths"][ROUTE]["post"]["requestBody"]["content"]["application/json"]["schema"]["$ref"]
        lot = schema["components"]["schemas"][LOT]["properties"]["events"]
        champs = schema["components"]["schemas"][EVENEMENT]["properties"]
        lues = {
            "evenementsParLot": (lot["maxItems"], f"{LOT}.events.maxItems"),
            "page": (champs["page"]["maxLength"], f"{EVENEMENT}.page.maxLength"),
            "action": (champs["action"]["maxLength"], f"{EVENEMENT}.action.maxLength"),
        }
    except (KeyError, TypeError) as erreur:
        raise TableInattendue(f"{ROUTE}, {LOT} ou {EVENEMENT} introuvable : {erreur}") from erreur
    if corps != f"#/components/schemas/{LOT}":
        raise TableInattendue(f"{ROUTE} ne prend plus {LOT} mais {corps} : les bornes sont à relire")
    if lot.get("items", {}).get("$ref") != f"#/components/schemas/{EVENEMENT}":
        raise TableInattendue(f"{LOT}.events ne porte plus des {EVENEMENT} : les bornes sont à relire")
    return {nom: _nombre(valeur, quoi) for nom, (valeur, quoi) in lues.items()}


def kotlin(schema: dict, source: str) -> str:
    b = bornes(schema)
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables_audience.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} ({LOT}, {EVENEMENT}).",
        f"package {PAQUET}",
        "",
        "/** Ce que le contrat borne d'un lot de mesure d'audience — au-delà, le serveur refuse le lot entier (422). */",
        "object LimitesAudience {",
        f"    /** Les évènements qu'accepte UN POST {ROUTE} (`maxItems` de `{LOT}.events`). */",
        f"    const val EVENEMENTS_PAR_LOT: Int = {b['evenementsParLot']}",
        "",
        f"    /** La longueur maximale d'une page (`maxLength` de `{EVENEMENT}.page`). */",
        f"    const val PAGE: Int = {b['page']}",
        "",
        f"    /** La longueur maximale d'une action (`maxLength` de `{EVENEMENT}.action`). */",
        f"    const val ACTION: Int = {b['action']}",
        "}",
        "",
    ])


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    contrat, sortie = pathlib.Path(argv[1]).with_name("openapi.json"), pathlib.Path(argv[2])
    try:
        code = kotlin(json.loads(contrat.read_text(encoding="utf-8")), contrat.name)
    except (TableInattendue, OSError, ValueError) as e:
        print(f"❌ {contrat.name} : {e}", file=sys.stderr)
        return 1
    fichier = sortie / PAQUET.replace(".", "/") / "LimitesAudience.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
