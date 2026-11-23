#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Chaque app du dépôt a son inventaire des composants tiers (#317) :
`python3 -I scripts/test_inventaire_apps.py`.

La montre a été signée sans inventaire : rien ne demandait à un module d'application de
confronter ce qu'il embarque à la déclaration. Ce test lit les modules de
`settings.gradle.kts` ; chacun qui applique le plugin d'application Android appelle
`composantsTiers(` (buildSrc, ComposantsTiers.kt) et branche sa sortie sur les ressources
de ses variantes — sans quoi la confrontation, qui ne tourne qu'avec la génération des
ressources, ne s'exécuterait jamais.
"""

import pathlib
import re
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
APPLICATION = re.compile(r"alias\(libs\.plugins\.android\.application\)|id\(\"com\.android\.application\"\)")
APPEL = re.compile(r"\bval\s+(\w+)\s*=\s*composantsTiers\(autreApp\s*=\s*\"(:[a-z-]+)\"\)")


def modules() -> list[str]:
    reglages = (RACINE / "settings.gradle.kts").read_text(encoding="utf-8")
    return [m.strip('":') for m in re.findall(r'":[a-z-]+"', reglages)]


def fautes(build: str, autres_apps: set[str]) -> list[str]:
    """Ce qui manque à un build d'application pour que son inventaire se confronte."""
    appel = APPEL.search(build)
    if not appel:
        return ["n'appelle pas composantsTiers(autreApp = …)"]
    nom, autre = appel.groups()
    erreurs = []
    if not re.search(rf"sources\.assets\?\.addGeneratedSourceDirectory\(\s*{nom}\b", build):
        erreurs.append(f"{nom} n'est pas branché sur les ressources (assets) de ses variantes")
    if autre not in autres_apps:
        erreurs.append(f"autreApp = {autre} n'est pas une autre app du dépôt ({', '.join(sorted(autres_apps))})")
    return erreurs


class InventaireDesApps(unittest.TestCase):
    def test_chaque_app_a_son_inventaire(self):
        apps = {m: (RACINE / m / "build.gradle.kts").read_text(encoding="utf-8") for m in modules()
                if (RACINE / m / "build.gradle.kts").is_file()}
        apps = {m: b for m, b in apps.items() if APPLICATION.search(b)}
        self.assertGreaterEqual(len(apps), 2, "INCONNU : moins de deux apps lues (téléphone, montre)")
        for module, build in apps.items():
            self.assertEqual(fautes(build, {f":{m}" for m in apps if m != module}), [], module)

    def test_le_juge_departage(self):
        sain = ('val genererComposantsTiers = composantsTiers(autreApp = ":app")\n'
                "androidComponents { onVariants { v ->\n"
                "    v.sources.assets?.addGeneratedSourceDirectory(genererComposantsTiers, GenererDepuisPython::sortie)\n} }\n")
        self.assertEqual(fautes(sain, {":app"}), [])
        self.assertTrue(fautes("android { namespace = \"x\" }", {":app"}))
        self.assertTrue(fautes(sain.split("androidComponents")[0], {":app"}))
        self.assertTrue(fautes(sain, {":montre"}))


if __name__ == "__main__":
    unittest.main()
