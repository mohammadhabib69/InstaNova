#!/usr/bin/env bash
#
# InstaNova migration regression suite (task P12-06).
#
# Runs every automated gate the migration has, in the order that fails fastest:
#
#   1. JVM unit tests            app/build/test-results/testDebugUnitTest/*.xml
#   2. Debug packaging           :app:assembleDebug
#   3. Firestore/RTDB rules      201 checks under the Firebase emulator
#   4. Cloud Functions           35 checks (notification dispatch, story cleanup)
#   5. Index drift               firestore.indexes.json vs the live project
#
# Exit code is 0 only when every gate passes. Nothing here writes to Firebase
# except reading the index list, so it is safe to run at any time.
#
# The two Java versions are not a mistake: Gradle needs JDK 17, the Firebase
# emulator needs 21+. Exporting one JAVA_HOME for both breaks one of them, which
# has already cost an afternoon — see MIGRATION_LOG 2026-10-30.
#
# Usage:
#   tools/regression.sh              # all four gates
#   tools/regression.sh --fast       # skip both emulator gates (2 min saved)
#   GRADLE_JAVA_HOME=/path/to/jdk17 tools/regression.sh

set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

FAST=0
for arg in "$@"; do
  case "$arg" in
    --fast) FAST=1 ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

# ---- locate a JDK 17 for Gradle ------------------------------------------------
if [ -z "${GRADLE_JAVA_HOME:-}" ]; then
  for candidate in \
    /Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home \
    /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
    "${JAVA_HOME:-}"; do
    if [ -n "$candidate" ] && [ -x "$candidate/bin/javac" ]; then
      GRADLE_JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "${GRADLE_JAVA_HOME:-}" ]; then
  echo "FAIL  no JDK 17 found; set GRADLE_JAVA_HOME" >&2
  exit 1
fi

PASS=0
FAIL=0
declare -a RESULTS=()

record() { # record <status> <name> <detail>
  if [ "$1" = "PASS" ]; then PASS=$((PASS + 1)); else FAIL=$((FAIL + 1)); fi
  RESULTS+=("$(printf '%-4s %-26s %s' "$1" "$2" "$3")")
  printf '%-4s %s (%s)\n' "$1" "$2" "$3"
}

# ---- 1. JVM unit tests ---------------------------------------------------------
echo "== 1/5 JVM unit tests =="
rm -rf app/build/test-results/testDebugUnitTest
if JAVA_HOME="$GRADLE_JAVA_HOME" ./gradlew :app:testDebugUnitTest --console=plain -q >/tmp/instanova-unit.log 2>&1; then
  COUNTS=$(python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=0
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0))
print(f"{t} tests, {f} failures, {e} errors")
PY
)
  if grep -q " 0 failures, 0 errors" <<<"$COUNTS"; then
    record PASS "unit tests" "$COUNTS"
  else
    record FAIL "unit tests" "$COUNTS"
  fi
else
  record FAIL "unit tests" "gradle failed, see /tmp/instanova-unit.log"
fi

# ---- 2. Packaging --------------------------------------------------------------
echo "== 2/5 assembleDebug =="
if JAVA_HOME="$GRADLE_JAVA_HOME" ./gradlew :app:assembleDebug --console=plain -q >/tmp/instanova-apk.log 2>&1; then
  APK=$(ls -1 app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)
  if [ -n "$APK" ]; then
    record PASS "assembleDebug" "$(du -h "$APK" | cut -f1) $(basename "$APK")"
  else
    record FAIL "assembleDebug" "no APK produced"
  fi
else
  record FAIL "assembleDebug" "see /tmp/instanova-apk.log"
fi

# ---- 3. Rules suites -----------------------------------------------------------
if [ "$FAST" = "1" ]; then
  echo "== 3/5 rules suites (skipped, --fast) =="
  RESULTS+=("SKIP rules suites             --fast")
else
  echo "== 3/5 rules suites =="
  # JAVA_HOME must be unset: the emulator needs 21+, and some shells export 17.
  if ( cd tools/rules-tests && env -u JAVA_HOME npx -y firebase-tools@latest emulators:exec \
        --only firestore,database --project instanova-62ff0 "npm test" \
        >/tmp/instanova-rules.log 2>&1 ); then
    SUMMARY=$(grep -E "^[0-9]+/[0-9]+ .*tests passed" /tmp/instanova-rules.log \
              | sed -E 's#^([0-9]+)/[0-9]+.*#\1#' | paste -sd+ - | bc 2>/dev/null)
    if [ -n "${SUMMARY:-}" ]; then
      record PASS "rules suites" "${SUMMARY} checks"
    else
      record PASS "rules suites" "green (count unparsed, see /tmp/instanova-rules.log)"
    fi
  else
    record FAIL "rules suites" "see /tmp/instanova-rules.log"
  fi
fi

# ---- 4. Cloud Functions -------------------------------------------------------
if [ "$FAST" = "1" ]; then
  echo "== 4/5 functions (skipped, --fast) =="
  RESULTS+=("SKIP functions                --fast")
else
  echo "== 4/5 functions =="
  # Shares the emulator with gate 3 but is run separately so a rule regression and a
  # function regression do not hide behind one exit code.
  if ( env -u JAVA_HOME npx -y firebase-tools@latest emulators:exec \
        --only firestore,database,auth --project instanova-62ff0 "cd functions && npm test" \
        >/tmp/instanova-functions.log 2>&1 ); then
    FN=$(grep -hoE "^[0-9]+/[0-9]+ (notification|cleanup|account deletion) tests passed" /tmp/instanova-functions.log \
         | sed -E 's#^([0-9]+)/[0-9]+.*#\1#' | paste -sd+ - | bc 2>/dev/null)
    if [ -n "${FN:-}" ]; then
      record PASS "functions" "${FN} checks"
    else
      record PASS "functions" "green (count unparsed, see /tmp/instanova-functions.log)"
    fi
  else
    record FAIL "functions" "see /tmp/instanova-functions.log"
  fi
fi

# ---- 5. Index drift -----------------------------------------------------------
echo "== 5/5 index drift =="
LOCAL=$(python3 -c "import json;print(len(json.load(open('firestore.indexes.json'))['indexes']))" 2>/dev/null)
REMOTE=$(env -u JAVA_HOME npx -y firebase-tools@latest firestore:indexes --project instanova-62ff0 2>/dev/null \
         | grep -c '"collectionGroup"')
if [ -n "$LOCAL" ] && [ -n "$REMOTE" ] && [ "$LOCAL" = "$REMOTE" ]; then
  record PASS "index drift" "$REMOTE composites live = file"
else
  record FAIL "index drift" "file=${LOCAL:-?} live=${REMOTE:-?} — run: firebase deploy --only firestore:indexes"
fi

# ---- summary ------------------------------------------------------------------
echo
echo "================ SUMMARY ================"
printf '%s\n' "${RESULTS[@]}"
echo "-----------------------------------------"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
