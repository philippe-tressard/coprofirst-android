#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Un hôte fictif se prend dans un nom RÉSERVÉ, jamais dans un domaine qui existe (#325).

`exemple.fr` ou `x.fr` sont des noms enregistrables : un test qui se trompe d'hôte y
envoie ses requêtes et ses cookies (constaté, #57), un exemple affiché se recopie
(socle `standards/03` §8 quater). Ce juge est une LISTE BLANCHE, de la même forme que
celui de l'iOS (`scripts/verifier-hotes.py` de coprofirst-ios) : tout domaine qui n'est
ni réservé (RFC 2606, 6761 : `example.com`, `example.org`, `example.net`, `.test`,
`.example`, `.invalid`, `.localhost`), ni un vrai domaine déclaré ci-dessous, est refusé.

Périmètre : les fichiers suivis par git sous `<module>/src/` de chaque module de
`settings.gradle.kts`, et les dossiers partagés (`ressources-communes/`,
`tests-interface-communs/`) — `.kt`, `.kts`, `.xml`, `.json`, lus ligne entière (chaînes
triples comprises). Les mots du code qui ont la forme d'un domaine sont nommés dans
`PAS_DES_HOTES`, et une entrée qui ne sert plus échoue.

Sortie : 0 conforme, 1 refus, 2 INCONNU. `--autotest` prouve qu'il refuse et accepte.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parent.parent
PARTAGES = ("ressources-communes", "tests-interface-communs")
EXTENSIONS = (".kt", ".kts", ".xml", ".json")
DOMAINE = re.compile(r"(?<![\w.-])((?:[a-z0-9-]+\.)+(?:fr|com|org|net|io|dev|eu|be|ch|app))(?![\w-]|\.\w)",
                     re.IGNORECASE)
RESERVES = re.compile(r"(^|\.)(example\.(com|org|net))$|\.(test|example|invalid|localhost)$")
#: Les vrais domaines que l'app ou ses tests nomment exprès — rien d'autre n'y entre.
VRAIS = (
    "coprofirst.fr", "5hostachy.fr",            # le produit et ses installations connues
    "fr.coprofirst.app",                        # l'identifiant de l'app (DNS inversé)
    "github.com",                               # le dépôt de l'app, ceux que cite la vitrine
    "schemas.android.com",                      # espaces de noms XML d'Android
    "chat.whatsapp.com",                        # l'indication du champ « URL communauté WhatsApp »
)
#: Des mots du CODE qui ont la forme d'un domaine (casse exacte) → leur raison.
PAS_DES_HOTES = {
    "Dispatchers.IO": "le répartiteur d'entrées-sorties de kotlinx.coroutines",
}


def modules() -> list[str]:
    reglages = (RACINE / "settings.gradle.kts").read_text(encoding="utf-8")
    return [m.strip('":') for m in re.findall(r'":[a-z-]+"', reglages)]


def perimetre(fichiers: list[str], mods: list[str]) -> list[str]:
    racines = tuple(f"{m}/src/" for m in mods) + tuple(f"{p}/" for p in PARTAGES)
    return [f for f in fichiers if f.startswith(racines) and f.endswith(EXTENSIONS)]


def autorise(domaine: str) -> bool:
    d = domaine.lower()
    return bool(RESERVES.search(d)) or any(d == v or d.endswith("." + v) for v in VRAIS)


def refus(chemin: str, texte: str, vus: set[str] | None = None) -> list[str]:
    """Les domaines refusés ; `vus` reçoit les mots de `PAS_DES_HOTES` rencontrés."""
    fautes = []
    for n, ligne in enumerate(texte.splitlines(), 1):
        for d in DOMAINE.findall(ligne):
            if d in PAS_DES_HOTES:
                if vus is not None:
                    vus.add(d)
            elif not autorise(d):
                fautes.append(f"{chemin}:{n} : {d}")
    return fautes


def autotest() -> bool:
    fautifs = [
        'val a = "residence.exemple.fr"',
        'url("  Residence.Exemple.FR ")',
        '    TexteDuSite.apercu("<p><img src=\\"https://x.fr/p.png\\"></p>")',
        'val site = "residence-des-chenes.fr"',
        '    // l\'adresse inventée https://tilleuls.org/x',
        '    PreuveHote.juger(200, """{"apps":["AUTRE.app"]}""")',
        '<string name="aide">Par exemple : residence.exemple.org</string>',
    ]
    sains = [
        'val a = "residence.exemple.test"',
        'val r = "https://tilleuls.example.com", p = "https://pirate.example/x", i = "x.invalid"',
        'val demo = "https://demo.coprofirst.fr"',
        'val id = "GUX8KASNTG.fr.coprofirst.app"',
        '    withContext(Dispatchers.IO) { lire() }',
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
    ]
    ok = all(refus("A.kt" if not f.startswith("<") else "a.xml", f) for f in fautifs)
    ok = ok and not any(refus("A.kt" if not s.startswith("<") else "a.xml", s) for s in sains)
    mods = ["app", "partage"]
    ok = ok and perimetre(["partage/src/test/kotlin/T.kt", "docs/x.md", "app/src/main/res/v.xml",
                           "tests-interface-communs/G.kt", "scripts/s.py"], mods) \
        == ["partage/src/test/kotlin/T.kt", "app/src/main/res/v.xml", "tests-interface-communs/G.kt"]
    return ok


def main() -> int:
    if sys.argv[1:] == ["--autotest"]:
        ok = autotest()
        print("✅ autotest des hôtes fictifs" if ok else "❌ autotest des hôtes fictifs en échec")
        return 0 if ok else 1
    mods = modules()
    fichiers = subprocess.run(["git", "ls-files", "--cached"], cwd=RACINE, capture_output=True,
                              text=True).stdout.splitlines()
    juges = [f for f in perimetre(fichiers, mods) if (RACINE / f).is_file()]
    if not mods or not juges:
        print("❓ INCONNU : aucun module lu ou aucun fichier à juger")
        return 2
    vus: set[str] = set()
    fautes = [x for f in juges for x in refus(f, (RACINE / f).read_text(encoding="utf-8"), vus)]
    fautes += [f"PAS_DES_HOTES : « {m} » ne se rencontre plus — retirer l'entrée"
               for m in sorted(set(PAS_DES_HOTES) - vus)]
    for faute in fautes:
        print(f"❌ {faute} — un nom réservé (example.com, .test…) ou un vrai domaine déclaré")
    if fautes:
        return 1
    print(f"✅ hôtes : {len(juges)} fichiers, aucun hors des noms réservés et des domaines déclarés")
    return 0


if __name__ == "__main__":
    sys.exit(main())
