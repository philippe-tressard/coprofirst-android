#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les TICKETS de la CI : un résultat a un destinataire (`standards/07` §1 et §1 bis).

Une seule écriture des tickets, `tenir` : ouvrir un ticket, ou compléter celui qui est ouvert
sous le MÊME titre (et la même étiquette, quand elle est nommée) — jamais deux tickets pour un
même sujet. Trois usages :

- `--signaler --etat ÉTAT` (#345) — le DERNIER pas de chaque job de chaque workflow, sous la
  garde `(failure() || cancelled()) && github.event_name != 'pull_request'`
  (`scripts/verifier_ci.py` refuse un job qui ne l'a pas). Un rouge, ou une annulation (un job
  coupé par son délai finit CANCELLED, #315), d'un push sur `main`, d'un passage planifié ou
  d'un lancement à la main sur `main` ouvre « CI rouge : <workflow> », ou le complète — au plus
  un commentaire toutes les `DELAI_HEURES` (`standards/07` §3) : les jobs d'un même passage
  rougissent ensemble. Une PR ou une autre branche : rien, le rouge se voit sur la PR. Le
  ticket se ferme à la main, avec le passage revenu au vert qui le prouve.
- `--concordance PLANCHER MONTEE RAPPORT_PLANCHER RAPPORT_MONTEE` — les tickets de la
  concordance quotidienne, d'après les codes de `scripts/concordance.py` (0 rien, 1 écart,
  2 INCONNU, 3 montée possible) : un écart au plancher ouvre ou complète le sien, un vert le
  ferme ; une montée possible (#344, comme l'iOS) ouvre UNE fois le ticket `a-monter`, puis en
  remplace le corps — une information tenue à jour, jamais un rouge ; il se ferme à la main
  quand le plancher monte. Sortie 1 si la montée n'a ni produit ni trouvé son ticket. Un
  INCONNU ne touche à aucun ticket : il rougit le job, et l'alerte de `--signaler` le porte.
- `--autotest` — les décisions, et les gestes sur un dépôt simulé : création, complément,
  délai, course de deux jobs, étiquette absente, panne de `gh`, cas zéro.

Deux jobs qui rougissent à la même seconde peuvent créer chacun le ticket : après une
création, la liste est relue et les doublons se ferment sur le plus ancien.

Réécrit d'après le comportement de `scripts/signaler-echec-ci.py` de coprofirst-site (licence
AGPL-3.0-or-later, sans la permission des magasins) : rien n'en est recopié.
"""

from __future__ import annotations

import json
import os
import pathlib
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone

DELAI_HEURES = 12
LIMITE = 500  # une liste pleine est INCONNUE : un ticket au-delà échapperait au dédoublonnage
ETIQUETTES_ROUGE = ("bug", "priorité haute")
TITRE_PLANCHER = "Concordance avec CoproFirst : écart au plancher"
# Le ticket de la montée, son titre et son étiquette À L'IDENTIQUE de l'iOS (coprofirst-ios) :
# il se reconnaît au titre ET à l'étiquette (un ticket étiqueté par erreur n'est pas le sien).
TITRE_A_MONTER = "CoproFirst : une version publiée au-dessus du plancher de l'app"
A_MONTER = ("a-monter", "0E8A16", "Une version de coprofirst publiée au-dessus du plancher de l'app (#20)")


class Illisible(Exception):
    pass


# ── Les décisions (pures) ──────────────────────────────────────────────────────────


def titre_rouge(workflow: str) -> str:
    return f"CI rouge : {workflow}"


def doit_signaler(evenement: str, ref: str) -> tuple[bool, str]:
    """Un passage planifié, ou un push ou un lancement à la main sur `main` ; jamais une PR."""
    if evenement == "schedule" or (evenement in ("push", "workflow_dispatch") and ref == "refs/heads/main"):
        return True, f"{evenement} sur {ref}"
    return False, f"{evenement} sur {ref} : le rouge se voit là où l'on travaille, aucun ticket"


def les_siens(ouverts: list[dict], titre: str, etiquette: str | None = None) -> list[dict]:
    """Les tickets ouverts qui portent CE titre (et l'étiquette nommée), du plus ancien au plus récent."""
    return sorted((t for t in ouverts if t.get("title") == titre
                   and (etiquette is None or etiquette in [e.get("name") for e in t.get("labels", [])])),
                  key=lambda t: t["number"])


def geste(siens: list[dict], mode: str, maintenant: datetime, delai_heures: int | None = None) -> tuple[str, int | None]:
    """("creer", None) | ("commenter" ou "remplacer", n) | ("rien", n).

    mode : « commenter » (un rouge s'ajoute au fil) ou « remplacer » (le corps est l'état du jour).
    delai_heures : un ticket touché depuis moins longtemps n'est pas recommenté."""
    if not siens:
        return "creer", None
    numero = siens[0]["number"]
    if delai_heures is not None:
        vu = datetime.fromisoformat(siens[0].get("updatedAt", "1970-01-01T00:00:00Z").replace("Z", "+00:00"))
        if maintenant - vu < timedelta(hours=delai_heures):
            return "rien", numero
    return mode, numero


def decider_concordance(plancher: str, montee: str) -> tuple[str, str]:
    """(geste du ticket d'écart, geste du ticket a-monter). Un INCONNU (2, ou un code vide) ne
    touche à rien : ce qui n'a pas pu être comparé n'est pas un vert, et ne ferme rien."""
    ecart = {"1": "tenir", "0": "fermer"}.get(plancher, "rien")
    return ecart, "tenir" if montee == "3" else "rien"


# ── Les gestes (gh injecté : le vrai en CI, un dépôt simulé dans l'autotest) ─────


def gh_reel(args: list[str]) -> str:
    return subprocess.run(["gh", *args], capture_output=True, text=True, check=True, timeout=60).stdout


def lister(gh, depot: str, etiquette: str | None = None) -> list[dict]:
    args = ["issue", "list", "-R", depot, "--state", "open", "--limit", str(LIMITE),
            "--json", "number,title,updatedAt,labels"]
    tickets = json.loads(gh(args + (["--label", etiquette] if etiquette else [])))
    if not isinstance(tickets, list) or len(tickets) >= LIMITE:
        raise Illisible(f"liste des tickets ouverts illisible ou pleine ({LIMITE}) : le dédoublonnage ne se prouverait pas")
    return tickets


def numero_de(url: str) -> int:
    try:
        return int(url.strip().rstrip("/").rsplit("/", 1)[1])
    except (IndexError, ValueError) as e:
        raise Illisible(f"numéro du ticket créé illisible : {url!r}") from e


def tenir(gh, depot: str, titre: str, corps: str, *, mode: str, maintenant: datetime,
          etiquettes: tuple[str, ...] = (), filtre: str | None = None, delai_heures: int | None = None) -> int:
    """LA fonction qui ouvre ou complète un ticket. Rend son numéro."""
    action, numero = geste(les_siens(lister(gh, depot, filtre), titre, filtre), mode, maintenant, delai_heures)
    if action == "rien":
        print(f"ticket #{numero} déjà complété il y a moins de {delai_heures} h : rien de plus")
        return numero
    if action == "commenter":
        gh(["issue", "comment", str(numero), "-R", depot, "--body", corps])
        print(f"ticket #{numero} complété")
        return numero
    if action == "remplacer":
        gh(["issue", "edit", str(numero), "-R", depot, "--body", corps])
        print(f"ticket #{numero} tenu à jour")
        return numero
    base = ["issue", "create", "-R", depot, "--title", titre, "--body", corps]
    try:
        numero = numero_de(gh(base + [a for e in etiquettes for a in ("--label", e)]))
    except subprocess.CalledProcessError:
        if filtre:
            raise  # l'étiquette EST la reconnaissance du ticket : sans elle, il ne serait jamais retrouvé
        numero = numero_de(gh(base))  # une étiquette renommée ne tait pas l'alerte
    siens = les_siens(lister(gh, depot, filtre), titre, filtre)
    for doublon in siens[1:]:
        gh(["issue", "close", str(doublon["number"]), "-R", depot, "--reason", "not planned",
            "--comment", f"Doublon de #{siens[0]['number']} (deux jobs ont rougi ensemble)."])
    print(f"ticket #{siens[0]['number'] if siens else numero} ouvert")
    return siens[0]["number"] if siens else numero


def fermer(gh, depot: str, titre: str, commentaire: str) -> None:
    for t in les_siens(lister(gh, depot), titre):
        gh(["issue", "close", str(t["number"]), "-R", depot, "--reason", "completed", "--comment", commentaire])
        print(f"ticket #{t['number']} fermé : retour au vert")


def assurer_etiquette(gh, depot: str, nom: str, couleur: str, description: str) -> None:
    """Créée si elle manque, à l'identique ; jamais modifiée si elle existe."""
    noms = [e.get("name") for e in json.loads(gh(["label", "list", "-R", depot, "--limit", "200", "--json", "name"]))]
    if nom not in noms:
        gh(["label", "create", nom, "-R", depot, "--color", couleur, "--description", description])
        print(f"étiquette « {nom} » créée")


def passage(env: dict) -> str:
    return (f"{env.get('GITHUB_SERVER_URL', 'https://github.com')}/{env.get('GITHUB_REPOSITORY', '?')}"
            f"/actions/runs/{env.get('GITHUB_RUN_ID', '?')}")


def corps_rouge(env: dict, etat: str) -> str:
    """L'exécution courante, et elle seule (`standards/04` §8)."""
    quoi = "annulé — un délai de job dépassé, ou une annulation à la main" if etat == "cancelled" else "en échec"
    return (f"Le job « {env.get('GITHUB_JOB', '?')} » du workflow « {env.get('GITHUB_WORKFLOW', '?')} » est {quoi} "
            f"({env.get('GITHUB_EVENT_NAME', '?')} sur {env.get('GITHUB_REF', '?')}, commit "
            f"{env.get('GITHUB_SHA', '?')[:7]}) : {passage(env)}\n\n"
            "Signalé par `scripts/signaler_ci.py --signaler`, le dernier pas de chaque job. Un rouge suivant "
            f"du même workflow s'ajoute ici (au plus un commentaire toutes les {DELAI_HEURES} h) ; fermer à "
            "la main, avec le passage revenu au vert qui le prouve.")


def signaler(env: dict, etat: str, gh, maintenant: datetime) -> int:
    depot, workflow = env.get("GITHUB_REPOSITORY", ""), env.get("GITHUB_WORKFLOW", "")
    if not depot or not workflow:
        print("::error::signaler_ci : GITHUB_REPOSITORY ou GITHUB_WORKFLOW absent — rien n'a été signalé")
        return 1
    oui, raison = doit_signaler(env.get("GITHUB_EVENT_NAME", ""), env.get("GITHUB_REF", ""))
    if not oui:
        print(f"signaler_ci : {raison}")
        return 0
    try:
        tenir(gh, depot, titre_rouge(workflow), corps_rouge(env, etat), mode="commenter", maintenant=maintenant,
              etiquettes=ETIQUETTES_ROUGE, delai_heures=DELAI_HEURES)
        return 0
    except (OSError, ValueError, Illisible, subprocess.SubprocessError) as e:
        print(f"::error::signaler_ci : le ticket n'a pas pu être ouvert ni complété ({e})")
        return 1


def concordance(env: dict, plancher: str, montee: str, rapport_plancher: pathlib.Path,
                rapport_montee: pathlib.Path, gh, maintenant: datetime) -> int:
    depot = env.get("GITHUB_REPOSITORY", "")
    if not depot:
        print("::error::signaler_ci : GITHUB_REPOSITORY absent")
        return 1
    ecart, a_monter = decider_concordance(plancher, montee)
    print(f"plancher={plancher!r} montee={montee!r} → écart : {ecart}, a-monter : {a_monter}")
    try:
        if ecart == "tenir":
            corps = rapport_plancher.read_text(encoding="utf-8") + f"\nExécution : {passage(env)}\n"
            tenir(gh, depot, TITRE_PLANCHER, corps, mode="commenter", maintenant=maintenant)
        elif ecart == "fermer":
            fermer(gh, depot, TITRE_PLANCHER, f"Retour au vert : {passage(env)}")
        if a_monter == "tenir":
            assurer_etiquette(gh, depot, *A_MONTER)
            corps = rapport_montee.read_text(encoding="utf-8") + f"\nMis à jour par {passage(env)}\n"
            tenir(gh, depot, TITRE_A_MONTER, corps, mode="remplacer", maintenant=maintenant,
                  etiquettes=(A_MONTER[0],), filtre=A_MONTER[0])
            # Le garde-fou de #344 : une montée possible a UN ticket, relu après coup.
            if len(les_siens(lister(gh, depot, A_MONTER[0]), TITRE_A_MONTER, A_MONTER[0])) != 1:
                print(f"::error::montée possible sans ticket « {A_MONTER[0]} » unique, relu après coup")
                return 1
        return 0
    except (OSError, ValueError, Illisible, subprocess.SubprocessError) as e:
        print(f"::error::signaler_ci : tickets de la concordance non tenus ({e})")
        return 1


# ── Autotest : les décisions, puis les gestes sur un dépôt simulé ─────────────────


class DepotSimule:
    """Un dépôt GitHub en mémoire, joué par `gh` : tickets, étiquettes, et les pannes à éprouver."""

    def __init__(self, tickets=(), etiquettes=("bug", "priorité haute"), cache_au_premier=False, panne=False,
                 creation_perdue=False):
        self.tickets = [dict(t, commentaires=list(t.get("commentaires", []))) for t in tickets]
        self.etiquettes = set(etiquettes)
        self.cache_au_premier = cache_au_premier  # course : la 1re liste ne voit pas le ticket d'un autre job
        self.panne, self.creation_perdue, self.gestes = panne, creation_perdue, []

    def __call__(self, args: list[str]) -> str:
        self.gestes.append(args[:2])
        if self.panne:
            raise subprocess.CalledProcessError(1, "gh")

        def option(nom):
            return args[args.index(nom) + 1] if nom in args else None

        if args[:2] == ["issue", "list"]:
            vus = [t for t in self.tickets if t["state"] == "open"
                   and (option("--label") is None or option("--label") in [e["name"] for e in t["labels"]])]
            if self.cache_au_premier:
                self.cache_au_premier, vus = False, []
            return json.dumps(vus)
        if args[:2] == ["issue", "create"]:
            noms = [args[i + 1] for i, a in enumerate(args) if a == "--label"]
            if any(n not in self.etiquettes for n in noms):
                raise subprocess.CalledProcessError(1, "gh")
            numero = max([t["number"] for t in self.tickets], default=0) + 1
            if not self.creation_perdue:
                self.tickets.append({"number": numero, "title": option("--title"), "state": "open",
                                     "body": option("--body"), "updatedAt": "2026-11-24T12:00:00Z",
                                     "labels": [{"name": n} for n in noms], "commentaires": []})
            return f"https://github.com/o/d/issues/{numero}\n"
        if args[:2] == ["label", "list"]:
            return json.dumps([{"name": n} for n in sorted(self.etiquettes)])
        if args[:2] == ["label", "create"]:
            self.etiquettes.add(args[2])
            return ""
        ticket = next(t for t in self.tickets if str(t["number"]) == args[2])
        if args[:2] == ["issue", "comment"]:
            ticket["commentaires"].append(option("--body"))
        elif args[:2] == ["issue", "edit"]:
            ticket["body"] = option("--body")
        elif args[:2] == ["issue", "close"]:
            ticket["state"] = "closed"
        return ""

    def ouverts(self, titre: str) -> list[dict]:
        return [t for t in self.tickets if t["state"] == "open" and t["title"] == titre]


def autotest() -> int:
    rates = []

    def cas(nom, obtenu, attendu):
        if obtenu != attendu:
            rates.append(nom)
            print(f"❌ {nom} : {obtenu!r} (attendu {attendu!r})")

    t = datetime(2026, 11, 24, 12, tzinfo=timezone.utc)  # l'horloge est figée (standards/04 §11)
    ancien = {"number": 7, "title": titre_rouge("CI"), "state": "open", "updatedAt": "2026-11-23T06:00:00Z",
              "labels": []}
    recent = dict(ancien, updatedAt="2026-11-24T11:30:00Z")
    # Quand signaler : jamais une PR ni une autre branche.
    cas("push sur main", doit_signaler("push", "refs/heads/main")[0], True)
    cas("passage planifié", doit_signaler("schedule", "refs/heads/main")[0], True)
    cas("lancé à la main sur main", doit_signaler("workflow_dispatch", "refs/heads/main")[0], True)
    cas("PR", doit_signaler("pull_request", "refs/pull/3/merge")[0], False)
    cas("lancé à la main sur une branche", doit_signaler("workflow_dispatch", "refs/heads/x")[0], False)
    cas("push sur une branche", doit_signaler("push", "refs/heads/claude/x")[0], False)
    # Le geste : créer, compléter, attendre le délai ; le titre exact et l'étiquette reconnaissent.
    cas("aucun ticket (cas zéro)", geste([], "commenter", t, 12), ("creer", None))
    cas("ticket d'hier", geste([ancien], "commenter", t, 12), ("commenter", 7))
    cas("ticket touché il y a 30 min", geste([recent], "commenter", t, 12), ("rien", 7))
    cas("remplacer sans délai", geste([recent], "remplacer", t), ("remplacer", 7))
    cas("étiqueté a-monter par erreur, autre titre",
        les_siens([dict(ancien, labels=[{"name": "a-monter"}])], TITRE_A_MONTER, "a-monter"), [])
    cas("bon titre, sans l'étiquette", les_siens([dict(ancien, title=TITRE_A_MONTER)], TITRE_A_MONTER, "a-monter"), [])
    cas("concordance : écart", decider_concordance("1", "0"), ("tenir", "rien"))
    cas("concordance : vert et montée", decider_concordance("0", "3"), ("fermer", "tenir"))
    cas("concordance : INCONNU ne touche à rien", decider_concordance("2", "2"), ("rien", "rien"))
    cas("concordance : un pas sans code est INCONNU", decider_concordance("", ""), ("rien", "rien"))

    env = {"GITHUB_REPOSITORY": "o/d", "GITHUB_WORKFLOW": "CI", "GITHUB_EVENT_NAME": "push", "GITHUB_JOB": "build",
           "GITHUB_REF": "refs/heads/main", "GITHUB_RUN_ID": "42", "GITHUB_SHA": "abcdef0123"}
    # --signaler, sur un dépôt simulé.
    d = DepotSimule()
    cas("rouge de main : un ticket ouvert", (signaler(env, "failure", d, t), len(d.ouverts(titre_rouge("CI")))), (0, 1))
    cas("son corps cite le passage", "/o/d/actions/runs/42" in d.ouverts(titre_rouge("CI"))[0]["body"], True)
    cas("annulation : dite comme telle", "annulé" in corps_rouge(env, "cancelled"), True)
    d = DepotSimule([ancien])
    signaler(env, "failure", d, t)
    cas("ticket ouvert hier : complété, pas doublé",
        (len(d.ouverts(titre_rouge("CI"))), len(d.tickets[0]["commentaires"])), (1, 1))
    d = DepotSimule([recent])
    signaler(env, "failure", d, t)
    cas("délai : aucun commentaire de plus", d.tickets[0]["commentaires"], [])
    d = DepotSimule()
    cas("PR : aucun geste sur le dépôt", (signaler(dict(env, GITHUB_EVENT_NAME="pull_request",
                                                        GITHUB_REF="refs/pull/3/merge"), "failure", d, t), d.gestes), (0, []))
    d = DepotSimule([ancien], cache_au_premier=True)
    signaler(env, "failure", d, t)
    cas("course de deux jobs : un seul ticket reste ouvert", [x["number"] for x in d.ouverts(titre_rouge("CI"))], [7])
    d = DepotSimule(etiquettes=())
    cas("étiquettes absentes : le ticket part sans elles",
        (signaler(env, "failure", d, t), len(d.ouverts(titre_rouge("CI")))), (0, 1))
    cas("gh en panne : sortie 1, jamais un faux succès", signaler(env, "failure", DepotSimule(panne=True), t), 1)
    cas("dépôt inconnu : sortie 1", signaler({}, "failure", DepotSimule(), t), 1)
    plein = DepotSimule([dict(ancien, number=n, title=f"t{n}") for n in range(1, LIMITE + 1)])
    cas("liste pleine : INCONNU, sortie 1", signaler(env, "failure", plein, t), 1)

    # --concordance : l'écart, le vert, la montée (#344).
    with tempfile.TemporaryDirectory() as dossier:
        rp, rm = pathlib.Path(dossier, "plancher.md"), pathlib.Path(dossier, "montee.md")
        rp.write_text("## Écart au plancher\n", encoding="utf-8")
        rm.write_text("## Montée possible\nRelease v9.9.9\n", encoding="utf-8")
        d = DepotSimule(etiquettes=())
        cas("montée : ouverte une fois, étiquette créée",
            (concordance(env, "0", "3", rp, rm, d, t), len(d.ouverts(TITRE_A_MONTER)), "a-monter" in d.etiquettes),
            (0, 1, True))
        rm.write_text("## Montée possible\nRelease v9.9.10\n", encoding="utf-8")
        concordance(env, "0", "3", rp, rm, d, t)
        a_monter = d.ouverts(TITRE_A_MONTER)
        cas("montée suivante : le même ticket, corps tenu à jour",
            (len(a_monter), "v9.9.10" in a_monter[0]["body"], a_monter[0]["commentaires"]), (1, True, []))
        cas("écart : ouvert", (concordance(env, "1", "0", rp, rm, d, t), len(d.ouverts(TITRE_PLANCHER))), (0, 1))
        concordance(env, "1", "0", rp, rm, d, t)
        cas("écart suivant : complété", [len(x["commentaires"]) for x in d.ouverts(TITRE_PLANCHER)], [1])
        cas("INCONNU : rien ne se ferme", (concordance(env, "2", "0", rp, rm, d, t), len(d.ouverts(TITRE_PLANCHER))), (0, 1))
        cas("vert : l'écart se ferme, a-monter reste",
            (concordance(env, "0", "0", rp, rm, d, t), len(d.ouverts(TITRE_PLANCHER)), len(d.ouverts(TITRE_A_MONTER))),
            (0, 0, 1))
        cas("montée sans ticket produit : sortie 1 (garde-fou)",
            concordance(env, "0", "3", rp, rm, DepotSimule(creation_perdue=True), t), 1)
        cas("montée, gh en panne : sortie 1", concordance(env, "0", "3", rp, rm, DepotSimule(panne=True), t), 1)
    print("✅ signaler_ci : autotest" if not rates else f"❌ signaler_ci : {len(rates)} cas en échec")
    return 0 if not rates else 1


def main(argv: list[str]) -> int:
    maintenant = datetime.now(timezone.utc)
    if argv == ["--autotest"]:
        return autotest()
    if len(argv) == 3 and argv[:2] == ["--signaler", "--etat"]:
        return signaler(dict(os.environ), argv[2], gh_reel, maintenant)
    if len(argv) == 5 and argv[0] == "--concordance":
        return concordance(dict(os.environ), argv[1], argv[2], pathlib.Path(argv[3]), pathlib.Path(argv[4]),
                           gh_reel, maintenant)
    print("usage : signaler_ci.py --signaler --etat ÉTAT | --concordance PLANCHER MONTEE RAPPORT_PLANCHER "
          "RAPPORT_MONTEE | --autotest", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
