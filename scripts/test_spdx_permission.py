#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Toute part AGPL du dépôt porte la permission magasins (#337) : `python3 -I scripts/test_spdx_permission.py`.

`CLAUDE.md` : « Chaque fichier se déclare `AGPL-3.0-or-later WITH
LicenseRef-CoproFirst-Magasins` ». `reuse lint` valide une expression sans la permission :
c'est sa cohérence avec cette règle que ce test tient. Il lit chaque en-tête
`SPDX-License-Identifier` des fichiers suivis et chaque annotation de `REUSE.toml` ; une
expression qui nomme `AGPL-3.0-or-later` sans le `WITH` est refusée, sauf exception nommée
avec sa raison — et une exception qui ne sert plus échoue.
"""

import pathlib
import re
import subprocess
import tomllib
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
PERMISSION = "AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins"
#: chemin (annotation de REUSE.toml ou fichier) → raison. Une décision de l'auteur, seul
#: titulaire des droits, à prendre : elle se retire quand elle est prise.
EXCEPTIONS = {
    "contrat/icones-svg.json": "décision attendue de Philippe (#337) : la part AGPL de cette pièce "
                               "jointe du serveur (l'icône `stairs`) porte-t-elle la permission magasins ?",
}
#: La balise s'écrit en deux morceaux : écrite d'un bloc, REUSE lirait ce motif comme une déclaration.
BALISE = "SPDX-License-" + "Identifier"
EN_TETE = re.compile(BALISE + r":\s*(.+?)\s*(?:-->|\*/|$)", re.M)


def sans_permission(expression: str) -> bool:
    """L'expression nomme l'AGPL sans la permission magasins."""
    return any(not reste.startswith(" WITH LicenseRef-CoproFirst-Magasins")
               for reste in expression.split("AGPL-3.0-or-later")[1:])


def expressions() -> list[tuple[str, str]]:
    """(chemin, expression) de chaque en-tête suivi et de chaque annotation de REUSE.toml."""
    suivis = subprocess.run(["git", "ls-files", "--cached"], cwd=RACINE, capture_output=True,
                            text=True, check=True).stdout.splitlines()
    trouvees = []
    for f in suivis:
        if f.startswith("LICENSES/") or f in ("LICENSE", "REUSE.toml") or not (RACINE / f).is_file():
            continue
        try:
            texte = (RACINE / f).read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        trouvees += [(f, m.group(1)) for m in EN_TETE.finditer(texte)]
    reuse = tomllib.loads((RACINE / "REUSE.toml").read_text(encoding="utf-8"))
    for a in reuse.get("annotations", []):
        chemins = a["path"] if isinstance(a["path"], list) else [a["path"]]
        trouvees += [(c, a[BALISE]) for c in chemins]
    return trouvees


class PermissionMagasins(unittest.TestCase):
    def test_toute_part_agpl_porte_la_permission(self):
        toutes = expressions()
        self.assertGreater(len(toutes), 100, "INCONNU : presque aucune déclaration lue")
        fautives = {c for c, e in toutes if sans_permission(e)}
        self.assertEqual(sorted(fautives - set(EXCEPTIONS)), [])
        self.assertEqual(sorted(set(EXCEPTIONS) - fautives), [], "exception sans objet : la retirer")

    def test_le_juge_departage(self):
        self.assertFalse(sans_permission(PERMISSION))
        self.assertFalse(sans_permission("MIT"))
        self.assertTrue(sans_permission("AGPL-3.0-or-later"))
        self.assertTrue(sans_permission("AGPL-3.0-or-later AND ISC AND MIT"))
        self.assertFalse(sans_permission(f"{PERMISSION} AND ISC"))
        self.assertEqual(EN_TETE.search(f"<!-- {BALISE}: MIT -->").group(1), "MIT")


if __name__ == "__main__":
    unittest.main()
