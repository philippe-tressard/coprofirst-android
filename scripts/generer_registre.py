#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Le registre des menus (#109), généré au build depuis les INSCRIPTIONS des écrans.

Chaque écran s'inscrit dans SON paquet : `fonctions/<menu>/Inscription.kt` déclare UN
`object Inscription… : InscriptionDeMenu` (ses pages, ses fiches). Ce script les liste,
dans l'ordre des dossiers, et écrit `RegistreGenere.kt` : aucun fichier commun n'est
touché par un écran, deux écrans ajoutés en parallèle ne se rencontrent jamais.

Une inscription illisible (pas de paquet, aucun objet, deux objets, un paquet qui n'est
pas celui du dossier) arrête la génération.

Usage : generer_registre.py <dossier fonctions> <dossier de sortie>
"""

from __future__ import annotations

import pathlib
import re
import sys

PAQUET_SORTIE = "fr.coprofirst.app.navigation"
PAQUET_FONCTIONS = "fr.coprofirst.app.fonctions"
PAQUET = re.compile(r"^package\s+([\w.]+)\s*$", re.M)
OBJET = re.compile(r"^(?:internal\s+)?object\s+(\w+)\s*:\s*InscriptionDeMenu\b", re.M)


class InscriptionIllisible(Exception):
    pass


def inscription(fichier: pathlib.Path) -> str:
    """Le nom complet de l'objet inscrit dans ce fichier."""
    texte = fichier.read_text(encoding="utf-8")
    paquets = PAQUET.findall(texte)
    objets = OBJET.findall(texte)
    menu = fichier.parent.name
    if len(paquets) != 1:
        raise InscriptionIllisible(f"{menu}/Inscription.kt : un paquet attendu")
    if paquets[0] != f"{PAQUET_FONCTIONS}.{menu}":
        raise InscriptionIllisible(f"{menu}/Inscription.kt : paquet {paquets[0]}, attendu {PAQUET_FONCTIONS}.{menu}")
    if len(objets) != 1:
        raise InscriptionIllisible(f"{menu}/Inscription.kt : un seul « object … : InscriptionDeMenu » attendu, {len(objets)} trouvé(s)")
    return f"{paquets[0]}.{objets[0]}"


def kotlin(fonctions: pathlib.Path) -> str:
    noms = [inscription(f) for f in sorted(fonctions.glob("*/Inscription.kt"))]
    l = [
        "// GÉNÉRÉ par scripts/generer_registre.py — NE PAS MODIFIER À LA MAIN.",
        "// Source : fonctions/*/Inscription.kt.",
        f"package {PAQUET_SORTIE}",
        "",
        "/** Les inscriptions des écrans, une par paquet `fonctions/<menu>` (#109). */",
        "internal object RegistreGenere {",
        "    val inscriptions: List<InscriptionDeMenu> = listOf(",
    ]
    l += [f"        {n}," for n in noms]
    l += ["    )", "}", ""]
    return "\n".join(l)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    fonctions, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        code = kotlin(fonctions)
    except InscriptionIllisible as e:
        print(f"❌ {e}", file=sys.stderr)
        return 1
    fichier = sortie / PAQUET_SORTIE.replace(".", "/") / "RegistreGenere.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
