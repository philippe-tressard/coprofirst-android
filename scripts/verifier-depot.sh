#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# verifier-depot.sh — les garde-fous du dépôt qui ne demandent ni JDK ni SDK.
# Lancé par la CI, par le hook pre-commit et à la main ; sortie non nulle = refus.
#
#   1. aucun fichier versionné de plus de 500 lignes (standards/02 §6)
#   2. ni BOM ni CRLF (standards/10)
#   3. bit d'exécution sur les scripts, et sur eux seuls (standards/08 §3)
#   4. syntaxe de chaque script bash et Python (standards/05 §6)
#   5. LICENSE = permission magasins + texte de l'AGPL, assemblés par
#      `--ecrire-licence` : LICENSE n'est que la réunion des deux textes de LICENSES/.
#   6. aucune couleur écrite à la main dans le Kotlin de l'app : la charte se génère
#      depuis coprofirst ;
#   7. aucune requête HTTP ni route écrite à la main dans l'app : le client généré
#      (client-api) seul — autotest d'abord pour chaque motif.
#   7 bis. aucun format de date ou de nombre hors de FormatsDuSite ;
#   7 ter. aucun HTML rendu par une vue web ou le lecteur HTML d'Android : TexteDuSite ;
#   7 quater. aucune version écrite à la main (elle se déduit de git) ;
#   7 quinquies. aucun hôte fictif sur un vrai domaine (exemple.fr…), libellés compris ;
#   7 sexies. aucun `performClick` dans les tests d'interface hors du geste commun ;
#   7 septies. la règle des tests d'interface est la v2 (junit4.v2), jamais la v1 ;
#   8. un seul bouton plein, `BoutonPlein` (commun/Boutons.kt) ;
#   9. les générateurs du build passent leurs tests (scripts/test_*.py) ;
#  10. aucun chemin de .claude/worktrees/ suivi.
#
# Il juge les fichiers que git connaît (index compris) : un fichier ignoré n'est
# pas livré, il n'a pas à passer.
set -uo pipefail
cd "$(dirname "$0")/.."

PERMISSION=LICENSES/LicenseRef-CoproFirst-Magasins.txt
AGPL=LICENSES/AGPL-3.0-or-later.txt

assembler_licence() {
  cat "$PERMISSION"
  printf '\n%s\n\n' "================================================================================"
  cat "$AGPL"
}

if [ "${1:-}" = "--ecrire-licence" ]; then
  assembler_licence > LICENSE
  echo "✅ LICENSE réassemblé depuis LICENSES/"
  exit 0
fi

ECHECS=0
echec() { echo "❌ $*"; ECHECS=$((ECHECS + 1)); }

FICHIERS=$(git ls-files --cached)
[ -n "$FICHIERS" ] || { echo "❓ INCONNU : git ne connaît aucun fichier (rien d'indexé ?)"; exit 2; }

# Dispensés de la limite de longueur : produits par un outil ou textes de licence,
# jamais écrits à la main. Toute autre exception se discute, elle ne s'ajoute pas.
DISPENSES_LONGUEUR='^(LICENSE|LICENSES/.*|contrat/openapi\.json|contrat/tables-clients\.json)$'

while IFS= read -r f; do
  [ -f "$f" ] || continue
  grep -Iq . "$f" 2>/dev/null || continue   # un binaire n'a ni lignes ni fins de ligne
  if ! [[ "$f" =~ $DISPENSES_LONGUEUR ]]; then
    n=$(wc -l < "$f" | tr -d ' ')
    [ "$n" -le 500 ] || echec "$f : $n lignes (> 500) — découper (standards/02 §6)"
  fi
  [ "$(head -c 3 "$f" | od -An -tx1 | tr -d " \n")" != "efbbbf" ] || echec "$f : BOM UTF-8"
  ! grep -q $'\r' "$f" || echec "$f : fins de ligne CRLF"
done <<< "$FICHIERS"

# `git ls-files --stage` : « <mode> <objet> <étape>\t<chemin> ».
while IFS=$'\t' read -r meta f; do
  mode=${meta%% *}
  if [[ "$f" == *.sh || "$f" == .githooks/* || "$f" == gradlew ]]; then
    [ "$mode" = "100755" ] || echec "$f : mode $mode, attendu 100755 (git update-index --chmod=+x)"
    bash -n "$f" 2>/dev/null || echec "$f : syntaxe bash invalide"
  elif [[ "$f" == *.py ]]; then
    if head -1 "$f" | grep -q '^#!'; then
      [ "$mode" = "100755" ] || echec "$f : shebang sans bit d'exécution (git update-index --chmod=+x)"
    fi
    python3 -c 'import ast, sys; ast.parse(open(sys.argv[1], encoding="utf-8").read())' "$f" 2>/dev/null \
      || echec "$f : syntaxe Python invalide"
  elif [ "$mode" = "100755" ]; then
    echec "$f : exécutable sans être un script (git update-index --chmod=-x)"
  fi
done < <(git ls-files --stage)

[ -f LICENSE ] && cmp -s LICENSE <(assembler_licence) \
  || echec "LICENSE diverge de LICENSES/ : bash scripts/verifier-depot.sh --ecrire-licence"

# Motifs interdits dans le code de l'app : chacun a sa raison, et son autotest prouve
# qu'il refuse ce qu'il doit refuser — un contrôle qui ne refuse rien ne prouve rien.
# $1 : le motif (ERE) ; $2 : le fichier. Rend la première ligne fautive, ou rien.
premier_motif() { grep -noE "$1" "$2" | head -1; }
# $1 : nom du contrôle ; $2 : motif ; $3 : un fichier qui doit être refusé ; $4 : un
# fichier qui doit passer. Échoue si le contrôle ne départage pas les deux.
autotest_motif() {
  local d ok=0
  d=$(mktemp -d) || return 1
  printf '%s\n' "$3" > "$d/fautif"
  printf '%s\n' "$4" > "$d/propre"
  [ -n "$(premier_motif "$2" "$d/fautif")" ] || ok=1
  [ -z "$(premier_motif "$2" "$d/propre")" ] || ok=1
  rm -rf "${d:?}"
  [ $ok -eq 0 ] || echec "verifier-depot.sh : autotest « $1 » en échec"
}
# $1 : motif ; $2 : message ; $3 (facultatif) : les fichiers DISPENSÉS, nommément (ERE
# sur le chemin) ; $4 (facultatif) : les fichiers jugés (ERE), par défaut le code et les
# ressources de l'app. Une dispense qui ne couvre plus aucun motif échoue : une
# exception sans objet se retire.
interdire() {
  local f motif dispense=${3:-} perimetre=${4:-'^app/src/main/.*\.(kt|xml)$'}
  while IFS= read -r f; do
    [ -f "$f" ] || continue
    motif=$(premier_motif "$1" "$f")
    if [ -n "$dispense" ] && [[ "$f" =~ $dispense ]]; then
      [ -n "$motif" ] || echec "$f : dispensé de « $2 » sans en avoir besoin — retirer la dispense"
      continue
    fi
    [ -z "$motif" ] || echec "$f : $2 ($motif)"
  done < <(grep -E "$perimetre" <<< "$FICHIERS")
}

# Les couleurs se GÉNÈRENT depuis les sources de coprofirst : aucune écrite à la main.
COULEURS_MAIN='Color\(0x|Color\([0-9]|Color\.(Red|Blue|Green|Yellow|Cyan|Magenta|Black|White|Gray|DarkGray|LightGray)\b|#[0-9A-Fa-f]{6,8}\b'
autotest_motif couleurs "$COULEURS_MAIN" 'val c = Color(0xFF112233)' 'val c = MaterialTheme.colorScheme.primary'
autotest_motif "couleurs nommées" "$COULEURS_MAIN" 'val c = Color.Red' 'val c = colorScheme.error'
autotest_motif "couleurs XML" "$COULEURS_MAIN" '<color name="x">#112233</color>' '<string name="x">Bonjour</string>'
interdire "$COULEURS_MAIN" "couleur écrite à la main — à générer depuis coprofirst"

# La signature de téléversement (#33) : au poste seulement. Aucun mot de passe écrit dans
# un fichier de build ; aucun job de CI ne reçoit la clé ni son mot de passe.
SIGNATURE_EN_CLAIR='(storePassword|keyPassword)[[:space:]]*=[[:space:]]*"'
autotest_motif "signature en clair" "$SIGNATURE_EN_CLAIR" 'storePassword = "secret"' 'storePassword = motDePasseTeleversement'
interdire "$SIGNATURE_EN_CLAIR" "mot de passe de signature écrit en clair" '' '\.(kts|properties)$'
SIGNATURE_EN_CI='COPROFIRST_SIGNATURE|coprofirst\.signature\.|construire-release'
autotest_motif "signature en CI" "$SIGNATURE_EN_CI" '  COPROFIRST_SIGNATURE_MDP: x' '  run: ./gradlew :app:assembleRelease'
interdire "$SIGNATURE_EN_CI" "la signature n'entre pas en CI (#33)" '' '^\.github/'

# L'API se parle par le client GÉNÉRÉ (client-api/) : aucune requête écrite à la main,
# aucune route en dur. Une route absente est un ticket dans coprofirst.
REQUETE_MAIN='Request\.Builder|HttpURLConnection|openConnection\(|\.newCall\(|"/api/|/api/[a-z]'
autotest_motif "requêtes à la main" "$REQUETE_MAIN" 'val r = Request.Builder().url(u).build()' 'ConfigApi(base, client).getConfig()'
autotest_motif "routes en dur" "$REQUETE_MAIN" 'val u = "https://" + hote + "/api/config"' 'val base = "https://$hote/api"'
# Deux exceptions, nommées, chacune dans SON fichier et lui seul :
# - la preuve qu'un hôte est une installation de CoproFirst se lit dans un fichier qui
#   n'est pas une route de l'API (/.well-known/…, hors contrat) — comme l'iOS
#   (PreuveAASA.swift) ;
# - les fichiers protégés (photos, pièces jointes de /uploads/) sont servis derrière la
#   session, hors du contrat — comme l'iOS (fichierProtege) : la porte n'accepte que
#   l'hôte de la session (commun/pieces/FichierProtege.kt).
DISPENSES_REQUETE='^app/src/main/java/fr/coprofirst/app/(session/PreuveHote|commun/pieces/FichierProtege)\.kt$'
interdire "$REQUETE_MAIN" "requête ou route écrite à la main — passer par le client généré (client-api)" "$DISPENSES_REQUETE"

# Le bouton plein (texte `surPlein` sur un fond plein de la charte, comme le `.plein` de
# l'iOS) s'écrit une fois, dans `Boutons.kt` (`BoutonPlein`, `BoutonPleinDestructif`) : un
# `Button(` de Material ailleurs reprendrait les couleurs par défaut, illisibles au sombre
# (constaté le 15/11/2026).
BOUTON_PLEIN='(^|[^A-Za-z])Button\('
autotest_motif "bouton plein" "$BOUTON_PLEIN" '    Button(onClick = f) { Text("x") }' '    TextButton(onClick = f) { Text("x") }'
interdire "$BOUTON_PLEIN" "bouton plein écrit à la main — passer par BoutonPlein ou BoutonPleinDestructif" '^app/src/main/java/fr/coprofirst/app/commun/Boutons\.kt$'

# Le libellé en capitales (`.styleLibelle()` de l'iOS) s'écrit une fois, `Libelle` de
# commun/Fiche.kt : recopié à la main dans une vingtaine d'écrans, il divergeait déjà —
# ici sans capitales, là marqué titre, ailleurs en `titleSmall` (#84).
LIBELLE_MAIN='Typo\.libelle\('
autotest_motif "libellé à la main" "$LIBELLE_MAIN" '    Text(t.uppercase(), style = Typo.libelle())' '    Libelle(t, Modifier.semantics { heading() })'
interdire "$LIBELLE_MAIN" "libellé en capitales écrit à la main — passer par Libelle (commun/Fiche.kt)" '^app/src/main/java/fr/coprofirst/app/commun/Fiche\.kt$'

# Une feuille s'ouvre ENTIÈRE (`rememberModalBottomSheetState(skipPartiallyExpanded = true)`,
# passé en `sheetState`) : à mi-hauteur, son bouton de pied restait sous le bord sur un petit
# écran (« Me connecter à cette adresse », #119). Le défaut de Material est la mi-hauteur.
feuille_mi_hauteur() {  # $1 : un fichier ; écrit les lignes fautives
  grep -nE 'ModalBottomSheet\(' "$1" | grep -v 'sheetState' || true
  grep -nE 'rememberModalBottomSheetState\(\)' "$1" || true
}
d=$(mktemp -d)
printf '    ModalBottomSheet(onDismissRequest = fermer) {\n' > "$d/sans.kt"
printf '    val etat = rememberModalBottomSheetState()\n' > "$d/defaut.kt"
printf '    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)\n    ModalBottomSheet(onDismissRequest = fermer, sheetState = etat) {\n' > "$d/sain.kt"
{ [ -n "$(feuille_mi_hauteur "$d/sans.kt")" ] && [ -n "$(feuille_mi_hauteur "$d/defaut.kt")" ] && [ -z "$(feuille_mi_hauteur "$d/sain.kt")" ]; } \
  || echec "verifier-depot.sh : autotest « feuille à mi-hauteur » en échec"
rm -rf "${d:?}"
while IFS= read -r f; do
  [ -f "$f" ] || continue
  fautes=$(feuille_mi_hauteur "$f")
  [ -z "$fautes" ] || echec "$f : feuille ouverte à mi-hauteur — sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) ($fautes)"
done < <(grep -E '^app/src/main/.*\.kt$' <<< "$FICHIERS")

# Les dates et les nombres s'écrivent par `FormatsDuSite`, seul, à la règle du site
# (fr-FR, Paris) : aucun écran ne compose son propre format.
FORMAT_MAIN='DateTimeFormatter|SimpleDateFormat|NumberFormat|DecimalFormat|String\.format\(|"%[0-9.]*[df]"'
autotest_motif "formats" "$FORMAT_MAIN" 'val t = DateTimeFormatter.ofPattern("dd/MM")' 'val t = FormatsDuSite.dateCourte(instant)'
interdire "$FORMAT_MAIN" "format de date ou de nombre écrit à la main — passer par FormatsDuSite" '^app/src/main/java/fr/coprofirst/app/commun/formats/FormatsDuSite\.kt$'

# Le texte riche du site passe par SA porte, `commun/texte` (`TexteDuSite`, `TexteRiche`) :
# un lecteur maison sur la liste blanche générée, qui ne charge ni n'exécute rien. Une vue
# web ou le lecteur HTML d'Android chargeraient des ressources distantes — l'iOS refuse de
# même WKWebView et l'import HTML de NSAttributedString. Aucune dispense.
HTML_MAIN='WebView|Html\.fromHtml|HtmlCompat|loadData\('
autotest_motif "HTML hors de TexteDuSite" "$HTML_MAIN" '    Text(HtmlCompat.fromHtml(html, 0).toString())' '    TexteRiche(html = affaire.description)'
autotest_motif "vue web" "$HTML_MAIN" '    AndroidView(factory = { WebView(it) })' '    Text(TexteDuSite.apercu(html))'
interdire "$HTML_MAIN" "texte HTML rendu hors de commun/texte (vue web, lecteur HTML d'Android) — passer par TexteDuSite ou TexteRiche"

# Les tests d'interface touchent par LE geste commun, `toucher` (androidTest, commun/Gestes.kt) :
# il amène l'élément à l'écran (liste paresseuse, page ou barre qui défile), vérifie qu'il
# est affiché, puis touche. Un `performClick` direct sur un élément hors de l'écran se
# perd, et le test tombe plus loin sans dire pourquoi (trois chutes le même soir, #119).
CLIC_DIRECT='\.performClick\('
autotest_motif "toucher hors du geste commun" "$CLIC_DIRECT" '        ecran.onNodeWithTag(r).performClick()' '        ecran.toucherTag(r)'
interdire "$CLIC_DIRECT" "toucher écrit à la main dans un test d'interface — passer par toucher (commun/Gestes.kt)" \
  '^app/src/androidTest/java/fr/coprofirst/app/commun/Gestes\.kt$' '^app/src/androidTest/.*\.kt$'

# La règle des tests d'interface est la v2 (`junit4.v2.createComposeRule`) : ses effets
# tournent sur un `StandardTestDispatcher`, au fil principal. La v1 (dépréciée) les menait
# sur un `UnconfinedTestDispatcher` : un chargement revenu du fil d'E/S ou une animation
# reprenait sur un autre fil, et Compose y observait des lectures — « Detected
# multithreaded access to SnapshotStateObserver », par intermittence (CI de #80, #119).
REGLE_V1='androidx\.compose\.ui\.test\.junit4\.create[A-Za-z]*ComposeRule'
autotest_motif "règle de test v1" "$REGLE_V1" 'import androidx.compose.ui.test.junit4.createComposeRule' 'import androidx.compose.ui.test.junit4.v2.createComposeRule'
interdire "$REGLE_V1" "règle de test v1 (dépréciée, effets hors du fil principal) — junit4.v2" "" '^app/src/(androidTest|test)/.*\.kt$'

# La version se DÉDUIT de git (app/build.gradle.kts) : aucune PR ne l'écrit. Un
# `versionCode`/`versionName` littéral reviendrait au conflit des PR parallèles.
VERSION_ECRITE='^[[:space:]]*version(Code|Name)[[:space:]]*=[[:space:]]*("|[0-9])'
autotest_motif "version écrite" "$VERSION_ECRITE" '        versionCode = 9' '        versionCode = versionDeGit.code'
motif=$(premier_motif "$VERSION_ECRITE" app/build.gradle.kts)
[ -z "$motif" ] || echec "app/build.gradle.kts : version écrite à la main ($motif) — elle se déduit de git"

# Les hôtes FICTIFS sont des noms réservés (RFC 2606/6761 : `.test`, `.invalid`,
# `example.com`), jamais `exemple.fr` ni `exemple.org`, qui sont de VRAIS domaines : un
# test qui partirait vers eux enverrait des cookies de test à un tiers (constaté, #57),
# et un libellé d'aide AFFICHÉ se recopie (standards/03 §8 quater) : aucune dispense.
HOTE_REEL='[Ee][Xx][Ee][Mm][Pp][Ll][Ee]\.([Ff][Rr]|[Oo][Rr][Gg]|[Cc][Oo][Mm])\b'
autotest_motif "hôtes fictifs" "$HOTE_REEL" 'val a = "residence.exemple.fr"' 'val a = "residence.exemple.test"'
autotest_motif "hôtes fictifs (casse)" "$HOTE_REEL" 'url("  Residence.Exemple.FR ")' 'url("  Residence.Exemple.TEST ")'
while IFS= read -r f; do
  [ -f "$f" ] || continue
  motif=$(premier_motif "$HOTE_REEL" "$f")
  [ -z "$motif" ] || echec "$f : hôte fictif sur un VRAI domaine ($motif) — un nom réservé (.test, .invalid)"
done < <(grep -E '^(app/src/(main|test|androidTest)|client-api/src/test)/.*\.(kt|xml)$' <<< "$FICHIERS")

# Les générateurs du build (palettes, logo) : leurs tests, dont la génération des
# fichiers recopiés. Chaque `scripts/test_*.py` versionné est joué.
while IFS= read -r t; do
  python3 -I "$t" >/dev/null 2>&1 || echec "$t en échec (python3 -I $t)"
done < <(grep -E '^scripts/test_[a-z_]+\.py$' <<< "$FICHIERS")

# Les réponses de l'API simulée (app/src/debug) disent ce que le serveur sert (#30) :
# chacune déclare son schéma, jugé contre le contrat au plancher.
python3 -I scripts/verifier_fixtures.py \
  || echec "une réponse simulée ne dit pas ce que le serveur sert (python3 -I scripts/verifier_fixtures.py)"

# Un écran s'inscrit dans SON paquet (#109) : ni le build ni la navigation ne nomment un
# menu. Les tables d'un menu se découvrent (`scripts/generer_tables_<menu>.py`), ses
# pages et ses fiches s'inscrivent (`fonctions/<menu>/Inscription.kt`) — sans quoi deux
# sessions parallèles se rencontrent à nouveau sur build.gradle.kts et le registre.
! grep -nE 'generer_tables_[a-z]' app/build.gradle.kts \
  || echec "app/build.gradle.kts nomme un script de tables de menu — il se découvre par convention (#109)"
while IFS= read -r f; do
  ! grep -n 'fr\.coprofirst\.app\.fonctions\.' "$f" \
    || echec "$f : la navigation nomme un menu — il s'inscrit dans son paquet (fonctions/<menu>/Inscription.kt, #109)"
done < <(grep -E '^app/src/main/java/fr/coprofirst/app/navigation/.*\.kt$' <<< "$FICHIERS")

! grep -q '^\.claude/worktrees/' <<< "$FICHIERS" || echec ".claude/worktrees/ suivi : git rm -r --cached .claude/worktrees"

if [ "$ECHECS" -gt 0 ]; then
  echo "── $ECHECS échec(s)"
  exit 1
fi
echo "✅ verifier-depot.sh : dépôt conforme"
