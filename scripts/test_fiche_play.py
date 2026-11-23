#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""La fiche du Play Store tient dans les limites de Google (#31) :
`python3 -I scripts/test_fiche_play.py`.

Nom : 30 caractères ; description courte : 80 ; description complète : 4000. Une fiche
qui déborde serait refusée à la saisie : elle se refuse ici, avant. Et elle ne promet
pas ce que l'app ne tient pas : la mesure d'audience existe, elle se dit ; et ce que
l'app dit elle-même s'ajouter « depuis le site » (ses libellés, « s'ajoutent… site »)
ne se nomme, dans la fiche, qu'au fil d'un verbe de lecture — « avec photos et
documents » promettait de les joindre (#318).
"""

import pathlib
import re
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
FICHE = RACINE / "docs" / "play-store" / "fiche.md"
RESSOURCES = RACINE / "app" / "src" / "main" / "res" / "values"
# Ce qui se fait au site s'y dit ainsi : « Les photos et les documents s'ajoutent depuis le site. »
AJOUT_AU_SITE = re.compile(r"(?:Les?|La|L')\s*(\w+)(?: et (?:les?|la|l')\s*(\w+))? s\\?'ajoutent?\b[^<]*\bsite\b")
LECTURE = re.compile(r"\b(consultez|voyez|retrouvez|lisez|regardez|ouvrez)\b", re.I)
LIMITES = {"nom": 30, "courte": 80, "complete": 4000}


def champs(texte: str) -> dict[str, str]:
    """Chaque bloc ``` qui suit une marque `<!-- champ:nom -->`, sans ses retours finaux."""
    return {m.group(1): m.group(2).rstrip("\n")
            for m in re.finditer(r"<!-- champ:([a-z]+) -->\s*```\n(.*?)```", texte, flags=re.S)}


def ajouts_au_site(ressources: str) -> set[str]:
    """Les noms que les libellés de l'app disent s'ajouter au site (photos, documents…)."""
    return {n.lower() for m in AJOUT_AU_SITE.finditer(ressources) for n in m.groups() if n}


def promesses(complete: str, noms: set[str]) -> list[str]:
    """Les propositions de la fiche qui nomment l'un d'eux sans verbe de lecture."""
    propositions = re.split(r"[,;:.\n•]", complete)
    return [p.strip() for p in propositions
            if any(re.search(rf"\b{n}\b", p, re.I) for n in noms) and not LECTURE.search(p)]


def lire_ressources() -> str:
    fichiers = sorted(RESSOURCES.glob("strings*.xml"))
    if not fichiers:
        raise AssertionError(f"INCONNU : aucune ressource dans {RESSOURCES}")
    return "\n".join(f.read_text(encoding="utf-8") for f in fichiers)


class FichePlay(unittest.TestCase):
    def setUp(self):
        self.champs = champs(FICHE.read_text(encoding="utf-8"))

    def test_chaque_champ_est_la_et_tient_dans_sa_limite(self):
        self.assertEqual(set(self.champs), set(LIMITES))
        for nom, limite in LIMITES.items():
            self.assertTrue(self.champs[nom].strip(), nom)
            self.assertLessEqual(len(self.champs[nom]), limite, nom)

    def test_rien_d_apple_ni_de_promesse_fausse(self):
        complete = self.champs["complete"]
        for mot in ("Face ID", "iPhone", "iPad", "App Store", "aucune mesure d'audience"):
            self.assertNotIn(mot, complete)
        self.assertIn("refuser dans votre profil", complete)

    def test_rien_de_ce_qui_s_ajoute_au_site_n_est_promis(self):
        noms = ajouts_au_site(lire_ressources())
        self.assertTrue({"photos", "documents"} <= noms, noms)  # le contrôle lit bien les libellés
        self.assertEqual(promesses(self.champs["complete"], noms), [])

    def test_une_promesse_de_joindre_se_voit(self):
        noms = ajouts_au_site("<string name=\"x_au_site\">Les photos et les documents s\\'ajoutent depuis le site.</string>")
        self.assertEqual(noms, {"photos", "documents"})
        avant = "• Affaires : signalez une panne, suivez-la jusqu'à sa résolution, avec photos et documents."
        apres = "• Affaires : signalez une panne et suivez-la ; consultez ses photos et ses documents."
        self.assertEqual(promesses(avant, noms), ["avec photos et documents"])
        self.assertEqual(promesses(apres, noms), [])

    def test_un_champ_trop_long_se_voit(self):
        trop = champs("<!-- champ:nom -->\n```\n" + "x" * 31 + "\n```")
        self.assertGreater(len(trop["nom"]), LIMITES["nom"])


if __name__ == "__main__":
    unittest.main()
