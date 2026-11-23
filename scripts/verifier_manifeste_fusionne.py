#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les permissions du manifeste FUSIONNÉ de la release sont celles que la déclaration nomme (#327).

`verifier_securite_donnees.py` lit les manifestes du dépôt ; une bibliothèque ajoute les
siennes au manifeste fusionné (Firebase Messaging : `WAKE_LOCK`, `c2dm…RECEIVE`), et une
montée de version peut en ajouter une sans toucher au dépôt (`standards/14` §3 bis :
« déclaré = réel », pour chaque SDK). Ce juge lit le manifeste fusionné de la variante
publiée de chaque app (téléphone, montre), imprime ses permissions, et refuse celles que
`docs/play-store/securite-des-donnees.md` ne nomme ni dans `table:permissions` (celles
du dépôt) ni dans `table:permissions-bibliotheques` (celles des bibliothèques).

Il ne peut tourner qu'après un build de la release (`construire-release.sh`, qui l'appelle) :
un manifeste absent ou illisible rend INCONNU, jamais conforme. `--version X` : le
manifeste doit être celui de cette version (sinon il vient d'un autre build : INCONNU).

Usage : verifier_manifeste_fusionne.py [--version X] [manifeste…] | --autotest
Sortie : 0 conforme, 1 refus, 2 INCONNU.
"""

from __future__ import annotations

import importlib.util
import pathlib
import re
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parent.parent
FUSIONNES = [RACINE / f"{app}/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
             for app in ("app", "montre")]
PERMISSION = re.compile(r'<uses-permission(?:-sdk-23)?\b[^>]*?android:name="([^"]+)"', re.S)
VERSION = re.compile(r'android:versionName="([^"]+)"')


def _securite():
    """Le juge de la déclaration, chargé par son chemin (`-I`) : son lecteur de tableau."""
    spec = importlib.util.spec_from_file_location("verifier_securite_donnees",
                                                  RACINE / "scripts" / "verifier_securite_donnees.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


SECURITE = _securite()


def declarees(texte: str) -> set[str]:
    """Les permissions nommées par la déclaration : celles du dépôt et celles des bibliothèques."""
    return {ligne[0].strip("`") for marque in ("permissions", "permissions-bibliotheques")
            for ligne in SECURITE.tableau(texte, marque)}


def juger(manifeste: str, permises: set[str]) -> tuple[list[str], list[str]]:
    """(les permissions du manifeste, celles que la déclaration ne nomme pas)."""
    trouvees = sorted(set(PERMISSION.findall(manifeste)))
    return trouvees, [p for p in trouvees if p not in permises]


def autotest() -> bool:
    texte = ("<!-- table:permissions -->\n| P | U |\n|---|---|\n| `android.permission.INTERNET` | x |\n\n"
             "<!-- table:permissions-bibliotheques -->\n| P | U |\n|---|---|\n| `android.permission.WAKE_LOCK` | y |\n")
    permises = declarees(texte)
    sain = '<manifest android:versionName="1.2.3">\n<uses-permission android:name="android.permission.INTERNET" />\n' \
           '<uses-permission\n    android:name="android.permission.WAKE_LOCK" />\n</manifest>'
    fautif = sain.replace("</manifest>", '<uses-permission android:name="com.google.android.gms.permission.AD_ID" />\n</manifest>')
    ok = permises == {"android.permission.INTERNET", "android.permission.WAKE_LOCK"}
    ok &= juger(sain, permises) == (["android.permission.INTERNET", "android.permission.WAKE_LOCK"], [])
    ok &= juger(fautif, permises)[1] == ["com.google.android.gms.permission.AD_ID"]
    with tempfile.TemporaryDirectory() as d:
        absent = pathlib.Path(d) / "AndroidManifest.xml"
        ok &= juger_fichiers([absent], permises) == 2
        absent.write_text(sain, encoding="utf-8")
        ok &= juger_fichiers([absent], permises) == 0
        ok &= juger_fichiers([absent], permises, "1.2.3") == 0
        ok &= juger_fichiers([absent], permises, "9.9.9") == 2
        absent.write_text(fautif, encoding="utf-8")
        ok &= juger_fichiers([absent], permises) == 1
        absent.write_text("<manifest/>", encoding="utf-8")
        ok &= juger_fichiers([absent], permises) == 2
    return ok


def main(argv: list[str]) -> int:
    if argv[1:] == ["--autotest"]:
        ok = autotest()
        print("✅ autotest du manifeste fusionné" if ok else "❌ autotest du manifeste fusionné en échec")
        return 0 if ok else 1
    args = argv[1:]
    attendue = None
    if args[:1] == ["--version"]:
        attendue, args = args[1], args[2:]
    chemins = [pathlib.Path(a) for a in args] or FUSIONNES
    try:
        permises = declarees((RACINE / SECURITE.DECLARATION).read_text(encoding="utf-8"))
    except (OSError, SECURITE.Inconnu) as e:
        print(f"❓ INCONNU : déclaration illisible ({e})")
        return 2
    return juger_fichiers(chemins, permises, attendue)


def juger_fichiers(chemins: list[pathlib.Path], permises: set[str], attendue: str | None = None) -> int:
    code = 0
    for chemin in chemins:
        try:
            manifeste = chemin.read_text(encoding="utf-8")
        except OSError:
            print(f"❓ INCONNU : {chemin} absent — construire la release d'abord (construire-release.sh)")
            code = max(code, 2)
            continue
        version = (VERSION.search(manifeste) or [None, None])[1]
        if attendue and version != attendue:
            print(f"❓ INCONNU : {chemin} est le manifeste de {version}, pas de {attendue} — reconstruire la release")
            code = max(code, 2)
            continue
        trouvees, refusees = juger(manifeste, permises)
        if not trouvees:
            print(f"❓ INCONNU : {chemin} sans aucune permission lue")
            code = max(code, 2)
            continue
        print(f"   {chemin} ({version}) : " + ", ".join(trouvees))
        for p in refusees:
            print(f"❌ {p} : dans le manifeste fusionné, absente de la déclaration (securite-des-donnees.md)")
        if refusees:
            code = max(code, 1)
    if code == 0:
        print(f"✅ manifestes fusionnés : chaque permission est déclarée ({len(chemins)} apps)")
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv))
