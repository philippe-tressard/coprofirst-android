#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Le contrôle de cohérence maximale AVANT CHAQUE SOUMISSION au Play Store (#4).

Règle de Philippe (15/11/2026, standards/09 §8 bis) : entre les lots, la vitesse ; à la
soumission, la cohérence maximale. Cette commande, unique, refuse de continuer au moindre
rouge. Un point qu'elle ne peut pas établir rend INCONNU, jamais OK.

1. Le commit soumis : l'arbre est propre, et HEAD est poussé sur origin/main.
2. La CI COMPLÈTE est verte sur ce commit exact : chaque job de .github/workflows/ci.yml,
   tests d'interface compris. Le job Interface joue le parcours des écrans principaux en
   sombre et au plus grand texte (`ParcoursSoumissionInterfaceTest`).
3. Le client d'API concorde avec le contrat SERVI en production, lu en direct
   (`concordance.py --plancher`).
4. Les mentions du magasin sont à jour :
   - la fiche tient dans les limites (`test_fiche_play.py`) ;
   - « Sécurité des données » dit ce que le code envoie (`verifier_securite_donnees.py`) ;
   - les notes de cette version sont écrites (`docs/play-store/notes/<base>.txt` : `0.8.txt`
     pour la version 0.8.512 ; 500 caractères au plus). La base, et non la version
     complète, dont le dernier nombre ne se connaît qu'après le commit (#272).
5. Aucun ticket ouvert ne porte le label `bloque-soumission`.
6. `--taguer`, seulement si tout est vert : le tag annoté `v<version>` est posé sur ce
   commit et poussé. C'est lui qui dit au serveur quel contrat chaque version en
   circulation parle.

Usage : controle_avant_soumission.py [--taguer] [--rapport FICHIER.md] | --autotest
Sortie : 0 tout est vert, 1 un rouge, 2 un INCONNU.
"""

from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parent.parent
CI = RACINE / ".github" / "workflows" / "ci.yml"
NOTES = RACINE / "docs" / "play-store" / "notes"
NOTES_MAX = 500
LABEL = "bloque-soumission"
OK, ROUGE, INCONNU = 0, 1, 2
MARQUES = {OK: "✅", ROUGE: "❌", INCONNU: "❓"}


def lancer(*commande: str) -> subprocess.CompletedProcess:
    return subprocess.run(commande, cwd=RACINE, capture_output=True, text=True, timeout=900)


def jobs_requis(texte: str) -> list[str]:
    """Le nom de chaque job de la CI, dans l'ordre du fichier."""
    noms, dans_jobs, cle = [], False, None
    for ligne in texte.splitlines():
        if re.match(r"^jobs:\s*$", ligne):
            dans_jobs = True
        elif dans_jobs and re.match(r"^\S", ligne):
            break
        elif dans_jobs and (m := re.match(r"^  ([A-Za-z0-9_-]+):\s*$", ligne)):
            cle = m.group(1)
            noms.append(cle)
        elif dans_jobs and cle and (m := re.match(r"^    name:\s*(.+?)\s*$", ligne)):
            noms[-1] = m.group(1).strip("'\"")
            cle = None
    return noms


def verdict_ci(executions: list[dict], requis: list[str]) -> tuple[int, list[str]]:
    """Chaque job requis a tourné sur ce commit, et a réussi."""
    if not requis:
        return INCONNU, ["aucun job lu dans ci.yml"]
    code, lignes = OK, []
    for nom in requis:
        siennes = [e for e in executions if e.get("name") == nom]
        if not siennes:
            code = max(code, INCONNU)
            lignes.append(f"« {nom} » n'a pas tourné sur ce commit")
            continue
        derniere = max(siennes, key=lambda e: e.get("started_at") or "")
        if derniere.get("status") != "completed":
            code = max(code, INCONNU)
            lignes.append(f"« {nom} » n'est pas terminé ({derniere.get('status')})")
        elif derniere.get("conclusion") != "success":
            code = max(code, ROUGE)
            lignes.append(f"« {nom} » : {derniere.get('conclusion')}")
    return code, lignes or [f"{len(requis)} jobs verts"]


def cle_des_notes(version: str) -> str | None:
    """La clé des notes d'une version : sa BASE (`0.8` pour `0.8.512`), seule partie connue
    avant le commit soumis (#272). Le dernier nombre est le nombre de commits de `main`
    (`app/build.gradle.kts`) : le commit qui écrit les notes le change lui-même. La base ne
    bouge que sur une décision de sortie (`coprofirst.version.base`) : une sortie, des notes."""
    m = re.fullmatch(r"(\d+\.\d+)\.\d+", version)
    return m.group(1) if m else None


def verdict_notes(version: str, dossier: pathlib.Path = NOTES) -> tuple[int, list[str]]:
    cle = cle_des_notes(version)
    if cle is None:
        return INCONNU, [f"version {version!r} illisible : notes introuvables"]
    fichier = dossier / f"{cle}.txt"
    if not fichier.is_file():
        return ROUGE, [f"notes de version absentes : {fichier.relative_to(RACINE) if fichier.is_relative_to(RACINE) else fichier}"]
    texte = fichier.read_text(encoding="utf-8").strip()
    if not texte or len(texte) > NOTES_MAX:
        return ROUGE, [f"notes de version de {len(texte)} caractères (1 à {NOTES_MAX})"]
    return OK, [f"notes de version : {len(texte)} caractères"]


def verdict_script(*commande: str) -> tuple[int, list[str]]:
    """Un contrôle du dépôt : sa sortie 0 est un vert, 1 un rouge, le reste INCONNU."""
    try:
        r = lancer(*commande)
    except (OSError, subprocess.TimeoutExpired) as e:
        return INCONNU, [f"{' '.join(commande)} : {e}"]
    code = {0: OK, 1: ROUGE}.get(r.returncode, INCONNU)
    derniere = (r.stdout.strip().splitlines() or r.stderr.strip().splitlines() or ["(sortie vide)"])[-1]
    return code, [f"{' '.join(commande[2:] or commande)} : {derniere}"]


def commit_soumis() -> tuple[int, list[str], str]:
    if lancer("git", "status", "--porcelain").stdout.strip():
        return ROUGE, ["l'arbre n'est pas propre : on soumet un commit, pas un brouillon"], ""
    if lancer("git", "fetch", "-q", "origin", "main").returncode != 0:
        return INCONNU, ["origin/main illisible (git fetch)"], ""
    sha = lancer("git", "rev-parse", "HEAD").stdout.strip()
    if lancer("git", "merge-base", "--is-ancestor", "HEAD", "origin/main").returncode != 0:
        return ROUGE, [f"{sha[:7]} n'est pas sur origin/main : il n'a pas passé la CI de main"], sha
    return OK, [f"commit {sha[:7]}, sur origin/main"], sha


def version() -> str | None:
    r = lancer("./gradlew", "-q", ":app:afficherVersion")
    m = re.search(r"versionName=(\d+\.\d+\.\d+)", r.stdout)
    return m.group(1) if m else None


def executions_ci(sha: str) -> list[dict] | None:
    r = lancer("gh", "api", f"repos/{{owner}}/{{repo}}/commits/{sha}/check-runs?per_page=100", "--jq", ".check_runs")
    try:
        return json.loads(r.stdout) if r.returncode == 0 else None
    except json.JSONDecodeError:
        return None


def tickets_bloquants() -> tuple[int, list[str]]:
    r = lancer("gh", "issue", "list", "--state", "open", "--label", LABEL, "--json", "number,title")
    if r.returncode != 0:
        return INCONNU, [f"tickets « {LABEL} » illisibles (gh)"]
    tickets = json.loads(r.stdout or "[]")
    if tickets:
        return ROUGE, [f"#{t['number']} {t['title']}" for t in tickets]
    return OK, [f"aucun ticket « {LABEL} » ouvert"]


def controler() -> tuple[int, list[str], str, str | None]:
    lignes: list[str] = []
    total = OK

    def noter(titre: str, verdict: tuple[int, list[str]]):
        nonlocal total
        code, details = verdict
        total = max(total, code)  # INCONNU l'emporte sur ROUGE : ni l'un ni l'autre ne se soumet
        lignes.append(f"- {MARQUES[code]} **{titre}** — " + " ; ".join(details))

    code, details, sha = commit_soumis()
    noter("Commit soumis", (code, details))
    nom = version()
    noter("Version", (OK, [f"v{nom}"]) if nom else (INCONNU, ["versionName illisible (:app:afficherVersion)"]))
    if sha:
        executions = executions_ci(sha)
        noter("CI complète sur ce commit", verdict_ci(executions, jobs_requis(CI.read_text(encoding="utf-8")))
              if executions is not None else (INCONNU, ["check-runs illisibles (gh api)"]))
    else:
        noter("CI complète sur ce commit", (INCONNU, ["pas de commit établi : rien à juger"]))
    noter("Contrat servi en production", verdict_script(sys.executable, "-I", "scripts/concordance.py", "--plancher"))
    noter("Fiche du magasin", verdict_script(sys.executable, "-I", "scripts/test_fiche_play.py"))
    noter("Sécurité des données", verdict_script(sys.executable, "-I", "scripts/verifier_securite_donnees.py"))
    noter("Notes de version", verdict_notes(nom) if nom else (INCONNU, ["version inconnue"]))
    noter("Tickets bloquants", tickets_bloquants())
    return total, lignes, sha, nom


def taguer(sha: str, nom: str) -> tuple[int, list[str]]:
    tag = f"v{nom}"
    if lancer("git", "rev-parse", "-q", "--verify", f"refs/tags/{tag}").returncode == 0:
        return ROUGE, [f"{tag} existe déjà : une version soumise ne se repose pas"]
    if lancer("git", "tag", "-a", tag, "-m", f"CoproFirst Android {nom} — soumise au Play Store", sha).returncode != 0:
        return INCONNU, [f"{tag} non posé"]
    if lancer("git", "push", "origin", f"refs/tags/{tag}").returncode != 0:
        return INCONNU, [f"{tag} posé, mais pas poussé : git push origin refs/tags/{tag}"]
    return OK, [f"{tag} posé sur {sha[:7]} et poussé"]


def autotest() -> bool:
    ci = "on:\n  push:\njobs:\n  a:\n    name: Dépôt\n    runs-on: x\n  b:\n    runs-on: y\n  c:\n    name: 'Interface (émulateur)'\n"
    ok = jobs_requis(ci) == ["Dépôt", "b", "Interface (émulateur)"]
    vert = {"name": "Dépôt", "status": "completed", "conclusion": "success", "started_at": "1"}
    ok &= verdict_ci([vert], ["Dépôt"])[0] == OK
    ok &= verdict_ci([vert, {**vert, "conclusion": "failure", "started_at": "2"}], ["Dépôt"])[0] == ROUGE
    ok &= verdict_ci([{**vert, "conclusion": "failure", "started_at": "0"}, vert], ["Dépôt"])[0] == OK
    ok &= verdict_ci([vert], ["Dépôt", "Interface"])[0] == INCONNU
    ok &= verdict_ci([{**vert, "status": "in_progress", "conclusion": None}], ["Dépôt"])[0] == INCONNU
    ok &= verdict_ci([], [])[0] == INCONNU
    with tempfile.TemporaryDirectory() as d:
        dossier = pathlib.Path(d)
        # #272 : les notes s'écrivent AVANT le commit soumis, dont le versionName (base +
        # nombre de commits) ne se connaît qu'après. Elles se rangent sous la BASE : les
        # versions 0.8.512 et 0.8.513 lisent les mêmes notes.
        ok &= verdict_notes("0.8.512", dossier)[0] == ROUGE  # un AAB sans notes : rouge
        (dossier / "0.8.512.txt").write_text("Au nom complet, écrit après coup.\n", encoding="utf-8")
        ok &= verdict_notes("0.8.512", dossier)[0] == ROUGE  # le nom complet ne compte pas
        (dossier / "0.8.txt").write_text("Première version de CoproFirst sur Android.\n", encoding="utf-8")
        ok &= verdict_notes("0.8.512", dossier)[0] == OK
        ok &= verdict_notes("0.8.513", dossier)[0] == OK
        ok &= verdict_notes("0.9.514", dossier)[0] == ROUGE  # une base neuve, ses notes à écrire
        ok &= verdict_notes("v0.8", dossier)[0] == INCONNU
        (dossier / "0.9.txt").write_text("x" * (NOTES_MAX + 1), encoding="utf-8")
        ok &= verdict_notes("0.9.514", dossier)[0] == ROUGE
    # Les jobs d'interface sont requis à la soumission, où qu'ils soient dans ci.yml : le
    # téléphone, la tablette et la montre Wear OS (#207).
    ok &= {"Interface (émulateur)", "Interface tablette (échantillon)", "Interface montre (Wear OS)"} \
        <= set(jobs_requis(CI.read_text(encoding="utf-8")))
    return ok


def main(argv: list[str]) -> int:
    if argv == ["--autotest"]:
        bon = autotest()
        print("✅ autotest du contrôle avant soumission" if bon else "❌ autotest du contrôle avant soumission en échec")
        return 0 if bon else 1
    rapport = argv[argv.index("--rapport") + 1] if "--rapport" in argv else None
    code, lignes, sha, nom = controler()
    if "--taguer" in argv:
        if code == OK and sha and nom:
            c, d = taguer(sha, nom)
            code = c
            lignes.append(f"- {MARQUES[c]} **Tag** — " + " ; ".join(d))
        else:
            lignes.append("- ⏸️ **Tag** — non posé : le contrôle n'est pas vert")
    texte = "\n".join(["## Contrôle avant soumission au Play Store", "", *lignes, "",
                       {OK: "**Verdict : vert.**", ROUGE: "**Verdict : ROUGE — ne pas soumettre.**",
                        INCONNU: "**Verdict : INCONNU — ne pas soumettre.**"}[code], ""])
    print(texte)
    if rapport:
        pathlib.Path(rapport).write_text(texte, encoding="utf-8")
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
