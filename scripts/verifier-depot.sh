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
#   7 quinquies. aucun hôte hors des noms réservés et des vrais domaines déclarés (liste
#      blanche, verifier_hotes.py), libellés, tests et API simulée compris ;
#   7 sexies. aucun `performClick` dans les tests d'interface hors du geste commun, ni
#      d'attente recopiée hors des attentes communes (#139) ;
#   7 septies. la règle des tests d'interface est la v2 (junit4.v2), jamais la v1 ;
#   7 octies. aucun séparateur du site (· — →) suivi d'une espace sécable ni recopié (#117) ;
#   7 nonies. aucune version de contrat écrite dans les tests ni l'API simulée (#203) ;
#   7 decies. aucune dépendance Google Play services ni Firebase que la licence ne nomme ;
#   7 undecies. un seul client HTTP, ClientHttp.partage, dans les deux apps (#206) ;
#   7 duodecies. chaque module inclus a son build.gradle.kts suivi par git ;
#   7 terdecies. aucun « /* » dans un commentaire Kotlin : il en ouvre un imbriqué (#293) ;
#   8. un seul bouton plein, `BoutonPlein` (commun/Boutons.kt) ;
#   8 bis. un seul écran à tirer, `Actualisable` (Ctrl+R compris), et aucune largeur de
#      lecture écrite à la main : `largeurDeLecture()` (#69) ;
#   9. les générateurs du build passent leurs tests (scripts/test_*.py) ;
#  10. aucun chemin de .claude/worktrees/ suivi.
#  11. aucun nom de personne hors des noms inventés déclarés (liste blanche, #326).
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

# Les modules du build, lus dans settings.gradle.kts — la seule liste (#206) : un module
# ajouté entre d'office dans le périmètre des règles. `:client-api` compris (#319) : son code
# généré vit sous build/, que git ne suit pas, donc que les règles ne lisent pas ; ce qui est
# écrit à la main sous src/ est jugé comme le reste.
modules=$(grep -oE '":[a-z-]+"' settings.gradle.kts | tr -d '":')
[ -n "$modules" ] || { echo "❓ INCONNU : settings.gradle.kts, aucun module lu (include)"; exit 2; }
MODULES_CODE=$(paste -sd '|' - <<< "$modules")

# Dispensés de la limite de longueur : produits par un outil ou textes de licence,
# jamais écrits à la main. Toute autre exception se discute, elle ne s'ajoute pas.
DISPENSES_LONGUEUR='^(LICENSE|LICENSES/.*|contrat/openapi\.json|contrat/au-dessus/openapi\.json|contrat/tables-clients\.json)$'

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
# ressources de chaque module (settings.gradle.kts) — `src/main` et `src/debug`, l'API simulée
# de l'app de débogage, qu'on lance et qu'on montre (#319) — et ressources-communes/. Les tests
# ont leurs motifs à eux ; buildSrc n'est pas du code de l'app. Une dispense qui ne couvre plus
# aucun motif échoue : une exception sans objet se retire.
interdire() {
  local f motif dispense=${3:-} perimetre=${4:-"^(($MODULES_CODE)/src/(main|debug)|ressources-communes)/.*\\.(kt|xml)$"}
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
# Les exceptions, nommées, chacune dans SON fichier et lui seul :
# - la preuve qu'un hôte est une installation de CoproFirst se lit dans un fichier qui
#   n'est pas une route de l'API (/.well-known/…, hors contrat) — comme l'iOS
#   (PreuveAASA.swift) ;
# - les fichiers protégés (photos, pièces jointes de /uploads/) sont servis derrière la
#   session, hors du contrat — comme l'iOS (fichierProtege) : la porte n'accepte que
#   l'hôte de la session (commun/pieces/FichierProtege.kt) — et le logo d'une résidence,
#   dont le client généré type l'image en JSON (adresse tirée de sa configuration, #137) ;
# - le contenu public de la vitrine, coprofirst.fr/contenu.json, relu une fois par
#   lancement, sans cookie ni donnée de l'utilisateur — comme l'iOS (Vitrine.actualiser,
#   #153) : son adresse est une constante, jamais une saisie (entree/vitrine/VitrineDuSite.kt) ;
# - le décorateur du client généré qui envoie un champ à `null` pour l'effacer (#209) : il
#   rejoue la requête que le client généré a construite, sans route ni hôte à lui
#   (client-api/src/main/kotlin/fr/coprofirst/api/NulsExplicites.kt).
DISPENSES_REQUETE='^(app/src/main/java/fr/coprofirst/app/(session/PreuveHote|commun/pieces/FichierProtege|entree/vitrine/VitrineDuSite)|client-api/src/main/kotlin/fr/coprofirst/api/NulsExplicites)\.kt$'
interdire "$REQUETE_MAIN" "requête ou route écrite à la main — passer par le client généré (client-api)" "$DISPENSES_REQUETE"

# Le bouton plein (texte `surPlein` sur un fond plein de la charte, comme le `.plein` de
# l'iOS) s'écrit une fois, dans `Boutons.kt` (`BoutonPlein`, `BoutonPleinDestructif`) : un
# `Button(` de Material ailleurs reprendrait les couleurs par défaut, illisibles au sombre
# (constaté le 15/11/2026).
BOUTON_PLEIN='(^|[^A-Za-z])Button\('
autotest_motif "bouton plein" "$BOUTON_PLEIN" '    Button(onClick = f) { Text("x") }' '    TextButton(onClick = f) { Text("x") }'
interdire "$BOUTON_PLEIN" "bouton plein écrit à la main — passer par BoutonPlein ou BoutonPleinDestructif" '^app/src/main/java/fr/coprofirst/app/commun/Boutons\.kt$'

# Tirer pour actualiser s'écrit une fois, `Actualisable` (commun/Actualisable.kt), qui offre
# aussi Ctrl+R au clavier d'une tablette (⌘R de l'iOS, #69) : un `PullToRefreshBox` nu en
# priverait son écran — le `.refreshable` nu que refuse l'iOS.
TIRER_NU='PullToRefreshBox\('
autotest_motif "tirer pour actualiser nu" "$TIRER_NU" '    PullToRefreshBox(isRefreshing = false, onRefresh = f) {' '    Actualisable(enCours = false, actualiser = f) {'
interdire "$TIRER_NU" "PullToRefreshBox nu — passer par Actualisable (Ctrl+R)" '^app/src/main/java/fr/coprofirst/app/commun/Actualisable\.kt$'
# La largeur de lecture (`Mise.lecture`, `Mise.formulaire`) se pose par `largeurDeLecture()`,
# qui suit la taille du texte (#69) : treize `widthIn(max = …)` recopiés divergeaient
# déjà (480, 640, 720). Une petite borne de composant (moins de 400 dp) n'est pas une lecture.
LECTURE_MAIN='widthIn\(max = ([4-9][0-9]{2}|[0-9]{4,})\.dp'
autotest_motif "largeur de lecture à la main" "$LECTURE_MAIN" '    Column(Modifier.widthIn(max = 720.dp)) {' '    Text(t, Modifier.widthIn(max = 120.dp))'
interdire "$LECTURE_MAIN" "largeur de lecture écrite à la main — passer par largeurDeLecture() (charte/Mise.kt)"

# Le libellé en capitales (`.styleLibelle()` de l'iOS) s'écrit une fois, `Libelle` de
# commun/Fiche.kt : recopié à la main dans une vingtaine d'écrans, il divergeait déjà —
# ici sans capitales, là marqué titre, ailleurs en `titleSmall` (#84).
LIBELLE_MAIN='Typo\.libelle\('
autotest_motif "libellé à la main" "$LIBELLE_MAIN" '    Text(t.uppercase(), style = Typo.libelle())' '    Libelle(t, Modifier.semantics { heading() })'
interdire "$LIBELLE_MAIN" "libellé en capitales écrit à la main — passer par Libelle (commun/Fiche.kt)" '^app/src/main/java/fr/coprofirst/app/commun/Fiche\.kt$'
# Le titre de section aussi (#323) : deux niveaux, ceux de l'iOS — `Intertitre` (serif) et
# `TitreDeBloc` (titleMedium) —, réécrits cinq fois sous d'autres noms et déjà en deux styles.
TITRE_MAIN='fun (Intertitre|TitreDeBloc|Titre(De)?(Section|File))[[:space:]]*\(|titleMedium.*heading\(\)'
autotest_motif "titre de section à la main" "$TITRE_MAIN" 'private fun TitreSection(texte: Int) {' '    Intertitre(stringResource(t), ecartAuDessus = 0.dp)'
autotest_motif "titre de bloc à la main" "$TITRE_MAIN" '    Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })' '    TitreDeBloc(t, intro = i)'
interdire "$TITRE_MAIN" "titre de section écrit à la main — passer par Intertitre ou TitreDeBloc (commun/Fiche.kt)" '^app/src/main/java/fr/coprofirst/app/commun/Fiche\.kt$'

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
done < <(grep -E "^($MODULES_CODE)/src/main/.*\\.kt$" <<< "$FICHIERS")

# Les dates et les nombres s'écrivent par `FormatsDuSite`, seul, à la règle du site
# (fr-FR, Paris) : aucun écran ne compose son propre format.
FORMAT_MAIN='DateTimeFormatter|SimpleDateFormat|NumberFormat|DecimalFormat|String\.format\(|"%[0-9.]*[df]"'
autotest_motif "formats" "$FORMAT_MAIN" 'val t = DateTimeFormatter.ofPattern("dd/MM")' 'val t = FormatsDuSite.dateCourte(instant)'
interdire "$FORMAT_MAIN" "format de date ou de nombre écrit à la main — passer par FormatsDuSite" '^partage/src/main/kotlin/fr/coprofirst/app/commun/formats/FormatsDuSite\.kt$'

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
  '^tests-interface-communs/fr/coprofirst/app/commun/Gestes\.kt$' '^((app|montre)/src/androidTest|tests-interface-communs)/.*\.kt$'

# Kotlin IMBRIQUE ses commentaires : un « /* » dans un commentaire (un chemin `/ecrans/moi/*`)
# en ouvre un second que rien ne ferme, et le fichier ne compile plus (#293). Au poste, le
# verrou peut priver de compilation : ce motif le voit avant la CI.
COMMENTAIRE_IMBRIQUE='^[[:space:]]*(/\*\*?|\*).*/\*'
autotest_motif "commentaire imbriqué" "$COMMENTAIRE_IMBRIQUE" ' * la part de l écran (`/ecrans/moi/*`) n est pas' ' * la part de l écran (`/ecrans/moi/…`) n est pas'
interdire "$COMMENTAIRE_IMBRIQUE" "« /* » dans un commentaire Kotlin — il ouvre un commentaire imbriqué jamais fermé" '' '\.kts?$'

# Une page de « Plus » s'ouvre par LE geste des tests (#69) : la barre latérale d'une fenêtre large
# (la tablette de la CI), sinon « Plus » puis l'entrée. Recopier « Plus » fait tomber la tablette.
PLUS_MAIN='toucherTexte\("Plus"\)'
autotest_motif "« Plus » hors du geste commun" "$PLUS_MAIN" '        ecran.toucherTexte("Plus")' '        ecran.ouvrirLaPageDePlus("profil")'
interdire "$PLUS_MAIN" "« Plus » touché à la main dans un test d'interface — passer par ouvrirLaPageDePlus (navigation/NavigationDeTest.kt)" \
  '^app/src/androidTest/java/fr/coprofirst/app/navigation/NavigationDeTest\.kt$' '^app/src/androidTest/.*\.kt$'

# Les tests d'interface ATTENDENT par les attentes communes (commun/Gestes.kt : attendre,
# attendreTag, attendreTexte, attendreDisparition…, attendreQue) : 24 aides privées les
# recopiaient — chacune son délai, son arbre fusionné ou non, son message (#139).
ATTENTE_PRIVEE='private fun (attendre|disparait|disparu|absent)[A-Za-z]*\('
autotest_motif "attente recopiée" "$ATTENTE_PRIVEE" '    private fun attendre(tag: String) = ecran.waitUntil(DELAI_MS) {' '        ecran.attendreTag(tag)'
interdire "$ATTENTE_PRIVEE" "attente écrite à la main dans un test d'interface — passer par les attentes de commun/Gestes.kt" \
  "" '^((app|montre)/src/androidTest|tests-interface-communs)/.*\.kt$'

# La règle des tests d'interface est la v2 (`junit4.v2.createComposeRule`) : ses effets
# tournent sur un `StandardTestDispatcher`, au fil principal. La v1 (dépréciée) les menait
# sur un `UnconfinedTestDispatcher` : un chargement revenu du fil d'E/S ou une animation
# reprenait sur un autre fil, et Compose y observait des lectures — « Detected
# multithreaded access to SnapshotStateObserver », par intermittence (CI de #80, #119).
REGLE_V1='androidx\.compose\.ui\.test\.junit4\.create[A-Za-z]*ComposeRule'
autotest_motif "règle de test v1" "$REGLE_V1" 'import androidx.compose.ui.test.junit4.createComposeRule' 'import androidx.compose.ui.test.junit4.v2.createComposeRule'
interdire "$REGLE_V1" "règle de test v1 (dépréciée, effets hors du fil principal) — junit4.v2" "" '^((app|montre)/src/(androidTest|test)|tests-interface-communs)/.*\.kt$'

# La version se DÉDUIT de git (build racine, commun au téléphone et à la montre) : aucune
# PR ne l'écrit. Un `versionCode`/`versionName` littéral reviendrait au conflit des PR
# parallèles.
VERSION_ECRITE='^[[:space:]]*version(Code|Name)[[:space:]]*=[[:space:]]*("|[0-9])'
autotest_motif "version écrite" "$VERSION_ECRITE" '        versionCode = 9' '        versionCode = versionDeGit.code'
for f in build.gradle.kts app/build.gradle.kts montre/build.gradle.kts; do
  [ -f "$f" ] || { echec "$f absent : la règle de version ne s'exerce plus"; continue; }
  motif=$(premier_motif "$VERSION_ECRITE" "$f")
  [ -z "$motif" ] || echec "$f : version écrite à la main ($motif) — elle se déduit de git"
done

# Une version de contrat ÉCRITE dans un test ou l'API simulée vieillit au premier plancher
# qui monte : l'installation simulée ne s'ouvre plus (#203, Audience). Elle se lit dans
# VERSION_CONTRAT_PLANCHER ; seule une version « à venir » (99.x) s'écrit.
CONTRAT_ECRIT='(version_contrat"?[[:space:]]*:[[:space:]]*|"version_contrat"[[:space:]]+to[[:space:]]+|versionContrat[[:space:]]*=[[:space:]]*|contrat: String\?[[:space:]]*=[[:space:]]*)"([0-8][0-9]?|9[0-8]?)\.[0-9]+\.[0-9]+"'
autotest_motif "contrat écrit" "$CONTRAT_ECRIT" '    """{"site_nom": "G", "version_contrat": "2.0.0"}"""' '    """{"site_nom": "G", "version_contrat": "$VERSION_CONTRAT_PLANCHER"}"""'
autotest_motif "contrat écrit (Installation)" "$CONTRAT_ECRIT" '    Installation(a, "G", versionContrat = "2.1.0")' '    mapOf("version_contrat" to "99.0.0")'
interdire "$CONTRAT_ECRIT" "version de contrat écrite à la main — VERSION_CONTRAT_PLANCHER" "" '^app/src/(test|androidTest|debug)/.*\.kt$'

# La résidence simulée des tests d'interface SERT LE PLANCHER (#311, leçon de coprofirst-ios#333) :
# une `Installation(` sans contrat y cache ce que le plancher offre par défaut (signaler,
# masquer), et un test affirme l'absence d'un geste que la vraie app montre. `residenceSimulee`.
INSTALLATION_NUE='(^|[^[:alnum:]_])Installation\('
autotest_motif "installation simulée" "$INSTALLATION_NUE" '    SessionOuverte(Installation("https://r.test", "G"), s)' '    SessionOuverte(residenceSimulee("https://r.test", "G"), s)'
interdire "$INSTALLATION_NUE" "Installation construite à la main dans un test d'interface — residenceSimulee (elle sert le plancher)" '/commun/ResidenceSimulee\.kt$' '^app/src/androidTest/.*\.kt$'

# UN client HTTP pour le téléphone ET la montre (#206) : `ClientHttp.partage`, qui ne suit
# aucune redirection. Un client construit ailleurs échapperait à ces réglages.
CLIENT_NEUF='OkHttpClient[[:space:]]*\([[:space:]]*\)|OkHttpClient\.Builder[[:space:]]*\('
autotest_motif "client HTTP neuf" "$CLIENT_NEUF" '    val c = OkHttpClient.Builder().build()' '    val c = ClientHttp.partage.newBuilder().build()'
interdire "$CLIENT_NEUF" "client HTTP construit hors de ClientHttp — passer par ClientHttp.partage" '^partage/src/main/kotlin/fr/coprofirst/app/session/ClientHttp\.kt$'

# Les petits doublons (#335) : chacun a sa source, et sa copie se refuse. La route des affaires
# se lit dans la table générée (CheminDuSite.affaires ; le manifeste : test_liens_application.py),
# le toucher nommé est cliquable, l'empreinte celle des lectures gardées, le serveur TLS des tests
# d'interface ServeurDeTest (récidive de #42).
ROUTE_AFFAIRES='"/?tickets[/"]'
autotest_motif "route des affaires" "$ROUTE_AFFAIRES" '    lien("/tickets/$id", adresse)' '    CheminDuSite.affaire(id)'
interdire "$ROUTE_AFFAIRES" "route des affaires écrite à la main — CheminDuSite.affaires (table générée)" '' "^($MODULES_CODE)/src/main/.*\\.kt$"
CLIC_NOMME='clickable\([[:space:]]*onClickLabel'
autotest_motif "toucher nommé" "$CLIC_NOMME" '    .clickable(onClickLabel = a, role = Role.Button, onClick = f)' '    .cliquable(a, f)'
interdire "$CLIC_NOMME" "toucher nommé écrit à la main — passer par Modifier.cliquable (commun/Cliquable.kt)" '^app/src/main/java/fr/coprofirst/app/commun/Cliquable\.kt$'
EMPREINTE_MAIN='MessageDigest\.getInstance'
autotest_motif "empreinte" "$EMPREINTE_MAIN" '    MessageDigest.getInstance("SHA-256")' '    LecturesGardees.empreinte(texte)'
interdire "$EMPREINTE_MAIN" "empreinte écrite à la main — LecturesGardees.empreinte" '^partage-android/src/main/kotlin/fr/coprofirst/app/session/LecturesGardees\.kt$'
TLS_DE_TEST='HeldCertificate\.Builder'
autotest_motif "serveur TLS de test" "$TLS_DE_TEST" '    val c = HeldCertificate.Builder().build()' '    val serveur = ServeurDeTest()'
interdire "$TLS_DE_TEST" "serveur TLS monté à la main dans un test d'interface — ServeurDeTest (session, dispatcher)" \
  '^app/src/androidTest/java/fr/coprofirst/app/entree/ServeurDeTest\.kt$' '^((app|montre)/src/androidTest|tests-interface-communs)/.*\.kt$'

# Un module inclus est un module SUIVI : la liste blanche du .gitignore doit l'ouvrir, sinon
# ses fichiers neufs restent sur le poste, verts en local, absents de la PR (#211, #214).
for m in $modules; do
  grep -qx "$m/build.gradle.kts" <<< "$FICHIERS" \
    || echec "$m/build.gradle.kts : module inclus mais non suivi — ouvrir !/$m/ dans .gitignore, puis git add $m"
done

# Les hôtes FICTIFS sont des noms réservés (RFC 2606/6761 : `.test`, `.invalid`,
# `example.com`), jamais `exemple.fr` ni `x.fr`, enregistrables : un test qui partirait vers
# eux enverrait des cookies de test à un tiers (constaté, #57), et un libellé d'aide AFFICHÉ
# se recopie (standards/03 §8 quater). Une LISTE BLANCHE (#325) : noms réservés et vrais
# domaines déclarés, sur le périmètre de settings.gradle.kts — scripts/verifier_hotes.py.
python3 -I scripts/verifier_hotes.py --autotest >/dev/null \
  || echec "verifier_hotes.py : autotest en échec"
python3 -I scripts/verifier_hotes.py \
  || echec "un hôte n'est ni réservé ni déclaré (python3 -I scripts/verifier_hotes.py)"

# Les générateurs du build (palettes, logo) : leurs tests, dont la génération des
# fichiers recopiés. Chaque `scripts/test_*.py` versionné est joué.
while IFS= read -r t; do
  python3 -I "$t" >/dev/null 2>&1 || echec "$t en échec (python3 -I $t)"
done < <(grep -E '^scripts/test_[a-z_]+\.py$' <<< "$FICHIERS")

# La section « Sécurité des données » de Google Play dit ce que le code envoie (#32) :
# chaque écriture du client généré et chaque permission y sont classées.
# Celles du manifeste FUSIONNÉ se jugent après le build de la release (construire-release.sh,
# #327) : ici, l'autotest du juge seulement.
python3 -I scripts/verifier_manifeste_fusionne.py --autotest >/dev/null \
  || echec "verifier_manifeste_fusionne.py : autotest en échec"
python3 -I scripts/verifier_securite_donnees.py \
  || echec "docs/play-store/securite-des-donnees.md ne dit pas ce que l'app envoie (python3 -I scripts/verifier_securite_donnees.py)"

# Les réponses de l'API simulée (app/src/debug) disent ce que le serveur sert (#30) :
# chacune déclare son schéma, jugé contre le contrat au plancher.
python3 -I scripts/verifier_fixtures.py \
  || echec "une réponse simulée ne dit pas ce que le serveur sert (python3 -I scripts/verifier_fixtures.py)"

# Aucun nom de personne réelle dans ce dépôt public (standards/14 §9, #326) : une LISTE
# BLANCHE de noms inventés (prénom + nom-étiquette, « Camille EXEMPLE »), jamais une liste
# noire, qui republierait les vrais noms.
python3 -I scripts/verifier_identites.py --autotest >/dev/null \
  || echec "verifier_identites.py : autotest en échec"
python3 -I scripts/verifier_identites.py \
  || echec "un nom de personne n'est pas un nom inventé déclaré (python3 -I scripts/verifier_identites.py)"

# Aucune bibliothèque Google Play services ni Firebase que la permission additionnelle ne
# nomme (point 4 de LicenseRef-CoproFirst-Magasins, décision du 16/11/2026) : la liste se
# lit dans la licence elle-même.
python3 -I scripts/verifier_dependances_google.py --autotest >/dev/null \
  || echec "verifier_dependances_google.py : autotest en échec"
python3 -I scripts/verifier_dependances_google.py \
  || echec "une dépendance Google n'est pas permise par la licence (python3 -I scripts/verifier_dependances_google.py)"

# La CI prépare Android par UNE action commune, qui tient l'image de chaque émulateur en
# cache et la télécharge avec un réessai borné (#232) : aucun job ne s'en passe. Chaque job
# finit par l'alerte (#345), chaque émulateur s'arrête en temps borné (#315).
python3 -I scripts/verifier_ci.py --autotest >/dev/null \
  || echec "verifier_ci.py : autotest en échec"
python3 -I scripts/verifier_ci.py \
  || echec "un job de la CI se prépare hors de l'action commune, ou finit sans alerte (python3 -I scripts/verifier_ci.py)"

# L'alerte et les tickets de la concordance (#345, #344), sur un dépôt simulé ; l'arrêt borné
# de l'émulateur (#315), sur un arrêt qui pend simulé — un motif unique, jamais l'émulateur du poste.
python3 -I scripts/signaler_ci.py --autotest >/dev/null \
  || echec "signaler_ci.py : autotest en échec (python3 -I scripts/signaler_ci.py --autotest)"
python3 -I scripts/arreter_emulateur.py --autotest >/dev/null \
  || echec "arreter_emulateur.py : autotest en échec (python3 -I scripts/arreter_emulateur.py --autotest)"

# Le contrôle avant soumission (#4) se prouve : il départage un vert, un rouge et un INCONNU.
python3 -I scripts/controle_avant_soumission.py --autotest >/dev/null \
  || echec "controle_avant_soumission.py : autotest en échec"

# Un séparateur du site (· — →) ne finit jamais une ligne (#117) : il se lit dans la table
# générée `Separateur`, et une ressource l'écrit suivi de `\u00A0`, confronté à la table.
python3 -I scripts/verifier_separateurs.py --autotest >/dev/null \
  || echec "verifier_separateurs.py : autotest en échec"
python3 -I scripts/verifier_separateurs.py \
  || echec "un séparateur du site est suivi d'une espace sécable, ou recopié (python3 -I scripts/verifier_separateurs.py)"

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
