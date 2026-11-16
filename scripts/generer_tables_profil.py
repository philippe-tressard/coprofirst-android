#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables du Profil (#21, #47), générées au build depuis `contrat/tables-clients.json`
— comme l'iOS (`TablesSaisie`) : les deux saisies d'une affaire, Essentielle et Complète
(`optionsSaisie`, `OPTIONS_SAISIE` de `saisie-affaire.ts`), leur libellé et leur détail ;
les deux canaux de notification de « Ce que j'affiche » (`canauxNotifications`,
`CANAUX_NOTIFS` de `preferences.ts`), leur titre et leurs deux clés. Les défauts de ces
clés sont ceux du serveur, `ConstantesDuServeur.defautsPreferences`.

Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_profil.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine, texte, valeur  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.profil"


def kotlin(tables: dict, source: str) -> str:
    options = valeur(tables, "optionsSaisie")
    if not isinstance(options, list) or len(options) < 2:
        raise TableInattendue("optionsSaisie n'est pas une liste d'au moins deux saisies")
    canaux = valeur(tables, "canauxNotifications")
    if not isinstance(canaux, list) or not canaux:
        raise TableInattendue("canauxNotifications n'est pas une liste de canaux")
    lignes_canaux = [tuple(texte(c, k, "canauxNotifications") for k in ("titre", "mon", "autres")) for c in canaux]
    cles = [cle for _, mon, autres in lignes_canaux for cle in (mon, autres)]
    if len(set(cles)) != len(cles):
        raise TableInattendue(f"canauxNotifications : une clé revient ({cles})")
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_profil.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (optionsSaisie, canauxNotifications).",
        f"package {PAQUET}",
        "",
        "/** Une saisie d'une affaire : son code (`saisie_affaire`), son libellé, ce qu'elle montre. */",
        "data class OptionSaisie(val code: String, val libelle: String, val detail: String)",
        "",
        "/** Un canal de notification : son titre, la clé de mon ou mes bâtiments, celle des autres. */",
        "data class CanalNotifications(val titre: String, val mon: String, val autres: String)",
        "",
        "/** Ce que le site écrit du Profil et que le contrat ne sert pas. */",
        "object TablesProfil {",
        "    /** Les saisies d'une affaire, dans l'ordre du site (`OPTIONS_SAISIE`). */",
        "    val optionsSaisie: List<OptionSaisie> = listOf(",
    ]
    l += [f"        OptionSaisie({chaine(texte(o, 'val', 'optionsSaisie'))}, {chaine(texte(o, 'label', 'optionsSaisie'))}, "
          f"{chaine(texte(o, 'desc', 'optionsSaisie'))})," for o in options]
    l += [
        "    )",
        "",
        "    /** Les canaux de notification, dans l'ordre du site (`CANAUX_NOTIFS`). */",
        "    val canauxNotifications: List<CanalNotifications> = listOf(",
    ]
    l += [f"        CanalNotifications({chaine(titre)}, {chaine(mon)}, {chaine(autres)})," for titre, mon, autres in lignes_canaux]
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
    fichier = sortie / PAQUET.replace(".", "/") / "TablesProfil.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
