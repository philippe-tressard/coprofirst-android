#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""L'arrêt de l'émulateur de la CI, BORNÉ (#315).

`reactivecircus/android-emulator-runner` (v2.38.0) lance l'émulateur en arrière-plan, puis,
après le script, l'arrête par `adb emu kill` et rend la main quand ses processus ont lâché la
sortie du pas — sans délai. Le 23/11/2026, un émulateur a répondu « bye bye » sans s'arrêter :
qemu, netsimd et crashpad_handler ont tenu le pas jusqu'au `timeout-minutes` du job, et des
tests verts ont fini CANCELLED, 22 minutes plus tard.

Ce script s'intercale dans le script de l'émulateur, en UNE ligne (l'action s'arrête à la
première ligne en échec : une seconde ligne ne serait pas jouée sur des tests rouges) :

    script: python3 -I scripts/arreter_emulateur.py -- ./gradlew … :app:connectedDebugAndroidTest

Il joue la commande, garde son code, puis arrête lui-même l'émulateur : `adb emu kill` borné
(`BORNE_ADB`), une attente bornée de la fin de ses processus (`MOTIFS`, `DELAI`), puis SIGTERM,
puis SIGKILL après `GRACE`, et le serveur adb en dernier. En tout, moins d'une minute. Quand
l'action envoie ensuite son propre `adb emu kill`, il n'y a plus d'émulateur : adb répond
« device not found », l'action le journalise et rend la main. `scripts/verifier_ci.py` refuse
un émulateur dont le script ne passe pas par ici.

Il ne tue qu'en CI (`GITHUB_ACTIONS=true`) : au poste partagé, un émulateur d'une autre
session porte les mêmes noms. Hors CI, seuls des motifs nommés (`--motifs`) se tuent — l'essai.

Non couvert : un échec AVANT le script (démarrage trop long) ; l'action arrête alors
l'émulateur elle-même, sans délai, et le `timeout-minutes` du job reste la borne.

Usage : arreter_emulateur.py [--motifs a,b] [--delai S] -- COMMANDE…
        arreter_emulateur.py --autotest   (un arrêt qui pend, simulé, coupé en temps borné)
Sortie : celle de la commande ; si un processus survit au SIGKILL, ou si l'arrêt ne se vérifie
pas, au moins 1 — jamais un vert sur un arrêt non prouvé.
"""

from __future__ import annotations

import contextlib
import io
import os
import select
import shlex
import signal
import subprocess
import sys
import time

MOTIFS = ("qemu-system", "netsimd", "crashpad_handler", "emulator/emulator ")
SERIE = "emulator-5554"  # le port par défaut de l'action (`emulator-port`)
DELAI, GRACE, BORNE_ADB = 20, 5, 10


def vivants(motifs) -> dict[int, str]:
    """Les processus dont la ligne de commande porte un motif (pgrep -f), sauf celui-ci."""
    trouves = {}
    for motif in motifs:
        r = subprocess.run(["pgrep", "-f", motif], capture_output=True, text=True, timeout=10)
        if r.returncode not in (0, 1):
            raise OSError(f"pgrep -f {motif} : sortie {r.returncode}")
        trouves.update({int(p): motif for p in r.stdout.split() if int(p) != os.getpid()})
    return trouves


def signaler_tous(pids, sig) -> None:
    for pid in pids:
        try:
            os.kill(pid, sig)
        except ProcessLookupError:
            pass


def attendre(motifs, secondes: float) -> dict[int, str]:
    fin = time.monotonic() + secondes
    restants = vivants(motifs)
    while restants and time.monotonic() < fin:
        time.sleep(0.5)
        restants = vivants(motifs)
    return restants


def borne(commande: list[str], secondes: float) -> None:
    try:
        subprocess.run(commande, timeout=secondes)
    except (OSError, subprocess.TimeoutExpired) as e:
        print(f"arrêt de l'émulateur : « {' '.join(commande)} » sans réponse ({e}), on poursuit")


def arreter(motifs, delai: float, grace: float, serie: str | None) -> tuple[list[str], list[str]]:
    """(forcés, survivants) : ce qu'il a fallu tuer, ce qui a survécu au SIGKILL."""
    if serie:
        borne(["adb", "-s", serie, "emu", "kill"], BORNE_ADB)
    restants = attendre(motifs, delai)
    forces = sorted(set(restants.values()))
    if restants:
        signaler_tous(restants, signal.SIGTERM)
        restants = attendre(motifs, grace)
        signaler_tous(restants, signal.SIGKILL)
        restants = attendre(motifs, 2)
        if serie:
            borne(["adb", "kill-server"], 5)
            borne(["pkill", "-KILL", "-x", "adb"], 5)
    return forces, sorted(set(restants.values()))


def principal(argv: list[str]) -> int:
    if "--" not in argv or argv.index("--") == len(argv) - 1:
        print("usage : arreter_emulateur.py [--motifs a,b] [--delai S] -- COMMANDE… | --autotest", file=sys.stderr)
        return 2
    options, commande = argv[:argv.index("--")], argv[argv.index("--") + 1:]
    motifs, delai = None, DELAI
    while options:
        nom, valeur, options = options[0], options[1] if len(options) > 1 else "", options[2:]
        if nom == "--motifs" and valeur:
            motifs = tuple(valeur.split(","))
        elif nom == "--delai" and valeur.replace(".", "", 1).isdigit():
            delai = float(valeur)
        else:
            print(f"option inconnue : {nom}", file=sys.stderr)
            return 2
    try:
        code = subprocess.call(commande)
    except OSError as e:
        print(f"commande introuvable : {e}")
        code = 127
    if motifs is None and os.environ.get("GITHUB_ACTIONS") != "true":
        print("arrêt de l'émulateur non joué hors CI : un émulateur du poste peut être celui d'une autre session")
        return code
    print("::group::Arrêt borné de l'émulateur (#315)")
    debut = time.monotonic()
    try:
        forces, survivants = arreter(motifs or MOTIFS, delai, GRACE, SERIE if motifs is None else None)
    except (OSError, subprocess.SubprocessError) as e:
        print(f"::error::arrêt de l'émulateur INCONNU ({e})\n::endgroup::")
        return code or 1
    duree = time.monotonic() - debut
    print(f"arrêt en {duree:.1f} s" + (f" — forcé : {', '.join(forces)}" if forces else ""))
    print("::endgroup::")
    if forces:
        print(f"::warning::l'émulateur ne s'arrêtait pas : {', '.join(forces)} tué(s) en {duree:.0f} s (#315)")
    if survivants:
        print(f"::error::survivent au SIGKILL : {', '.join(survivants)}")
        return code or 1
    return code


def pendre(marque: str, ignorer_term: bool) -> subprocess.Popen:
    """Un arrêt qui PEND, comme celui de #315 : un parent (ce test, à la place de l'action) lit
    une sortie qu'un petit-enfant détaché (à la place de qemu) a héritée ; le petit-enfant
    ignore SIGTERM et ne se trouve que par son motif, pas par un PID connu."""
    code = ("import signal, time\n" + ("signal.signal(signal.SIGTERM, signal.SIG_IGN)\n" if ignorer_term else "")
            + "time.sleep(300)")
    lancement = f"{shlex.quote(sys.executable)} -c {shlex.quote(code)} {shlex.quote(marque)} &"
    return subprocess.Popen(["sh", "-c", lancement], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)


def sortie_liberee(parent: subprocess.Popen, secondes: float) -> bool:
    """La sortie héritée est-elle fermée (fin de fichier) ? C'est ce qu'attendait l'action."""
    prets, _, _ = select.select([parent.stdout], [], [], secondes)
    return bool(prets) and parent.stdout.read() == b""


def autotest() -> int:
    rates = []
    base = f"essai-arret-emulateur-{os.getpid()}-{time.time_ns()}"

    def cas(nom, obtenu, attendu):
        if obtenu != attendu:
            rates.append(nom)
            print(f"❌ {nom} : {obtenu!r} (attendu {attendu!r})")

    cas("cas zéro : rien à arrêter", arreter((f"{base}-absent",), 1, 1, None), ([], []))
    for nom, ignorer in (("pend (ignore SIGTERM)", True), ("cède au SIGTERM", False)):
        marque = f"{base}-{int(ignorer)}"
        parent = pendre(marque, ignorer)
        parent.wait(timeout=10)
        time.sleep(0.5)
        cas(f"{nom} : trouvé par son motif", list(vivants((marque,)).values()), [marque])
        cas(f"{nom} : la sortie reste tenue", sortie_liberee(parent, 0.5), False)
        debut = time.monotonic()
        cas(f"{nom} : forcé, rien ne survit", arreter((marque,), 1, 1, None), ([marque], []))
        cas(f"{nom} : coupé en moins de 60 s", time.monotonic() - debut < 60, True)
        cas(f"{nom} : la sortie est libérée", sortie_liberee(parent, 5), True)
    # Le code de la commande traverse l'arrêt ; une commande absente vaut 127.
    cas("le code de la commande est rendu", principal(["--motifs", f"{base}-x", "--delai", "0", "--",
                                                       sys.executable, "-c", "raise SystemExit(3)"]), 3)
    cas("commande introuvable : 127", principal(["--motifs", f"{base}-x", "--delai", "0", "--", f"{base}-absente"]), 127)
    with contextlib.redirect_stderr(io.StringIO()):
        cas("sans commande : usage", principal(["--motifs", "x"]), 2)
    print("✅ arreter_emulateur : autotest" if not rates else f"❌ arreter_emulateur : {len(rates)} cas en échec")
    return 0 if not rates else 1


if __name__ == "__main__":
    sys.exit(autotest() if sys.argv[1:] == ["--autotest"] else principal(sys.argv[1:]))
