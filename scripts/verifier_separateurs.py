#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Un séparateur se rattache au mot qui le SUIT (#117), comme l'iOS (coprofirst-ios#188,
`verifier-separateurs.py`) et le site (coprofirst#2360).

Le site écrit ses trois séparateurs par une constante, `SEPARATEUR` de
`front/src/lib/separateurs.ts`, publiée avec la release (`separateurs` de
`tables-clients.json`). Elle met une espace SÉCABLE avant et une espace INSÉCABLE après :
la ligne se coupe devant le séparateur, jamais après. L'app la reçoit GÉNÉRÉE, dans la
table `Separateur` (`generer_tables.py`).

Ce juge refuse, dans le Kotlin de l'app et de ses tests :

- un séparateur suivi d'une espace ORDINAIRE, qui finit la ligne quand elle se coupe.
  « · » et « → » sont refusés partout. « — » ne l'est que quand il JOINT deux valeurs
  (`joinToString(" — ")`, `"$a — $b"`, `" — échéance"`). Le tiret d'une INCISE
  (« votre compte — validation, rôle — vous parvient ») n'est pas un séparateur et reste
  permis ;
- la RECOPIE de la constante (`" ·\\u00A0"`) : elle se lit dans `Separateur`.

Dans les RESSOURCES (`res/values*/*.xml`), la règle d'espacement est la même. Mais une
ressource ne peut pas lire une constante Kotlin : c'est la divergence déclarée d'Android.
L'espace insécable s'y écrit donc `\\u00A0`, et ce juge confronte chaque séparateur à la
table publiée.

Restent permis : les commentaires, et les textes SERVIS que l'API simulée recopie
(`app/src/debug`, `Fixtures*.kt`).

Sortie : 0 conforme, 1 refus, 2 INCONNU. `--autotest` prouve que le juge refuse et laisse passer.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parent.parent
TABLE = RACINE / "contrat" / "tables-clients.json"
DOSSIERS = ("app/src/main", "app/src/debug", "app/src/test", "app/src/androidTest", "partage/src/main", "partage/src/test", "partage-android/src/main", "partage-android/src/test",
            "montre/src/main", "montre/src/test", "montre/src/androidTest", "tests-interface-communs")
DISPENSES = (re.compile(r"^app/src/debug/.*/Fixtures[^/]*\.kt$"),)

# Kotlin : un séparateur entre deux espaces ordinaires (U+0020).
SECABLES_KT = (
    re.compile(r" [·→] "),
    # Le tiret qui joint : au bord d'un littéral, ou contre un gabarit (`$a`, `${…}`).
    re.compile(r'(?:"|\}|\$\w+) — | — (?:"|\$)'),
)
RECOPIE_KT = re.compile(r"[·—→](?:\\u00a0|\u00a0)", re.IGNORECASE)
COMMENTAIRE_KT = re.compile(r"^\s*(?://|/?\*)")

# Ressources : une valeur et ses arguments (`%1$s`).
SECABLES_XML = (
    re.compile(r" [·→] "),
    re.compile(r'(?:>|"|%\d\$[sd]) — | — (?:<|"|%\d\$[sd])'),
)
# Le séparateur que la ressource écrit : une espace ordinaire avant, `\u00A0` après.
INSECABLE_XML = re.compile(r"( ?)([·—→])\\u00A0", re.IGNORECASE)


def table(chemin: pathlib.Path = TABLE) -> set[str]:
    """Les symboles publiés (« · », « — », « → »), chacun entre une espace ordinaire et une
    espace insécable ; une autre forme arrête."""
    valeur = json.loads(chemin.read_text(encoding="utf-8"))["tables"]["separateurs"]["valeur"]
    symboles = set()
    for nom, texte in valeur.items():
        if not (isinstance(texte, str) and len(texte) == 3 and texte[0] == " " and texte[2] == "\u00a0"):
            raise ValueError(f"separateurs.{nom} : forme inattendue ({texte!r})")
        symboles.add(texte[1])
    return symboles


def refus_kotlin(chemin: str, texte: str) -> list[str]:
    if any(d.search(chemin) for d in DISPENSES):
        return []
    # Un test ÉCRIT la valeur attendue, et ses messages sont de la prose : on n'y refuse
    # que « · » et « → » suivis d'une espace sécable.
    test = chemin.startswith(("app/src/test/", "app/src/androidTest/", "partage/src/test/", "montre/src/test/",
                              "montre/src/androidTest/", "tests-interface-communs/"))
    regles = SECABLES_KT[:1] if test else SECABLES_KT
    fautes = []
    for numero, ligne in enumerate(texte.splitlines(), 1):
        if COMMENTAIRE_KT.match(ligne):
            continue
        code = ligne.split(" // ")[0]
        if '"' not in code:
            continue
        if any(m.search(code) for m in regles):
            fautes.append(f"{chemin}:{numero} : séparateur suivi d'une espace sécable — {ligne.strip()[:90]}")
        elif not test and RECOPIE_KT.search(code):
            fautes.append(f"{chemin}:{numero} : séparateur recopié — {ligne.strip()[:90]}")
    return fautes


def refus_xml(chemin: str, texte: str, symboles: set[str]) -> list[str]:
    fautes = []
    for numero, ligne in enumerate(texte.splitlines(), 1):
        if "<string" not in ligne and "<item" not in ligne:
            continue
        if any(m.search(ligne) for m in SECABLES_XML):
            fautes.append(f"{chemin}:{numero} : séparateur suivi d'une espace sécable — {ligne.strip()[:90]}")
            continue
        for avant, symbole in INSECABLE_XML.findall(ligne):
            if symbole not in symboles or avant != " ":
                fautes.append(f"{chemin}:{numero} : « {symbole}\\u00A0 » n'est pas un séparateur publié — {ligne.strip()[:90]}")
    return fautes


def juger(racine: pathlib.Path, symboles: set[str]) -> list[str]:
    fautes = []
    for dossier in DOSSIERS:
        base = racine / dossier
        if not base.is_dir():
            continue
        for f in sorted(base.rglob("*.kt")):
            fautes += refus_kotlin(f.relative_to(racine).as_posix(), f.read_text(encoding="utf-8"))
        for f in sorted(base.glob("res/values*/*.xml")):
            fautes += refus_xml(f.relative_to(racine).as_posix(), f.read_text(encoding="utf-8"), symboles)
    return fautes


def autotest() -> bool:
    kt = {
        ("app/src/main/A.kt", 'val s = morceaux.joinToString(" · ")'): True,
        ("app/src/main/A.kt", 'Text("$a — $b")'): True,
        ("app/src/main/A.kt", 'Text("${a.nom} — ${b}")'): True,
        ("app/src/main/A.kt", 'val d = "$debut → $fin"'): True,
        ("app/src/main/A.kt", 'return " — échéance $date"'): True,
        ("app/src/main/A.kt", '.joinToString(" — ")'): True,
        ("app/src/main/A.kt", 'val s = morceaux.joinToString(Separateur.point)'): False,
        ("app/src/main/A.kt", 'const val point = " ·\\u00A0"'): True,
        ("app/src/main/A.kt", 'val f = " →\u00a0"'): True,
        ("app/src/main/A.kt", '"Ce qui concerne votre compte — validation, rôle — vous parvient."'): False,
        ("app/src/main/A.kt", '"Hors ligne${Separateur.tiret}à jour"'): False,
        ("app/src/main/A.kt", "    /** « résident · Conseil syndical » */"): False,
        ("app/src/main/A.kt", " * « a · b »"): False,
        ("app/src/main/A.kt", 'val n = 1 // « a · b »'): False,
        ("app/src/debug/x/FixturesX.kt", '"titre": "Tous · du périmètre seulement",'): False,
        ("app/src/test/T.kt", 'assertEquals("Ouvert →\\u00A0Résolu", x)'): False,
        ("app/src/test/T.kt", 'assertEquals("$nom — l\'écart ne sert plus", x)'): False,
        ("app/src/androidTest/T.kt", 'ecran.attendreTexte("Ouvert → Chez le syndic")'): True,
    }
    ok = all(bool(refus_kotlin(c, t)) == attendu for (c, t), attendu in kt.items())
    symboles = {"·", "—", "→"}
    xml = {
        '<string name="a">%1$s · %2$s</string>': True,
        '<string name="a">Record jour — %1$s</string>': True,
        '<string name="a">%1$s — %2$s</string>': True,
        '<string name="a">Ce qui a manqué — </string>': True,
        '<string name="a">"Prestataires → Contrats"</string>': True,
        '<string name="a">%1$s ·\\u00A0%2$s</string>': False,
        '<string name="a">Record jour —\\u00A0%1$s</string>': False,
        '<string name="a">%1$s ×\\u00A0%2$s</string>': False,
        '<string name="a">%1$s·\\u00A0%2$s</string>': True,
        '<string name="a">Votre compte — validation, rôle — vous parvient.</string>': False,
        '<!-- un commentaire · libre -->': False,
    }
    ok = ok and all(bool(refus_xml("app/src/main/res/values/a.xml", t, symboles)) == a for t, a in xml.items())
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        (racine / "app/src/main/res/values").mkdir(parents=True)
        (racine / "app/src/main/V.kt").write_text('val s = " · "\n', encoding="utf-8")
        (racine / "app/src/main/res/values/s.xml").write_text('<string name="a">%1$s · %2$s</string>\n', encoding="utf-8")
        (racine / "app/src/androidTest").mkdir(parents=True)
        (racine / "app/src/androidTest/T.kt").write_text('ecran.attendreTexte("Ouvert → Chez le syndic")\n', encoding="utf-8")
        (racine / "t.json").write_text('{"tables": {"separateurs": {"valeur": {"point": " ·\\u00a0", "tiret": " —\\u00a0", "fleche": " →\\u00a0"}}}}',
                                       encoding="utf-8")
        ok = ok and len(juger(racine, table(racine / "t.json"))) == 3
        (racine / "u.json").write_text('{"tables": {"separateurs": {"valeur": {"point": " · "}}}}', encoding="utf-8")
        try:
            table(racine / "u.json")
            ok = False
        except ValueError:
            pass
    return ok


def main() -> int:
    if sys.argv[1:] == ["--autotest"]:
        if autotest():
            print("✅ autotest des séparateurs")
            return 0
        print("❌ autotest des séparateurs en échec")
        return 1
    if not (RACINE / "app/src/main").is_dir():
        print("❓ app/src/main introuvable : séparateurs INCONNUS")
        return 2
    try:
        symboles = table()
    except (OSError, KeyError, ValueError) as e:
        print(f"❓ table des séparateurs illisible ({e}) : séparateurs INCONNUS")
        return 2
    fautes = juger(RACINE, symboles)
    for faute in fautes:
        print(f"❌ {faute} → Separateur.point, .tiret ou .fleche (table générée)")
    if fautes:
        return 1
    print("✅ séparateurs du site générés, aucun recopié")
    return 0


if __name__ == "__main__":
    sys.exit(main())
