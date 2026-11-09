#!/usr/bin/env bash
#
# Build a signed InstaNova APK and produce the matching latest.json for the in-app
# updater. Play Store is not involved: the two files are uploaded to a GitHub Release.
#
# Usage
#   tools/ota/release.sh                      # build + generate build/ota/ artefacts
#   tools/ota/release.sh --notes-file n.txt   # use explicit release notes
#   tools/ota/release.sh --notes "Faster feed" --notes "Fixed reels"
#   tools/ota/release.sh --publish            # also create the GitHub release via gh
#
# Signing (required — the updater compares signing certificates):
#   add RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS /
#   RELEASE_KEY_PASSWORD to local.properties. Never commit the keystore.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

GRADLE="./gradlew"
OUT_DIR="build/ota"
NOTES_FILE=""
NOTES=()
PUBLISH=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --notes-file) NOTES_FILE="${2:-}"; shift 2 ;;
    --notes) NOTES+=("${2:-}"); shift 2 ;;
    --publish) PUBLISH=1; shift ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

echo "==> reading version from Gradle"
VERSION_INFO="$($GRADLE -q :app:printOtaVersion)"
VERSION_CODE="$(printf '%s\n' "$VERSION_INFO" | sed -n 's/^versionCode=//p')"
VERSION_NAME="$(printf '%s\n' "$VERSION_INFO" | sed -n 's/^versionName=//p')"
APPLICATION_ID="$(printf '%s\n' "$VERSION_INFO" | sed -n 's/^applicationId=//p')"

if [[ -z "$VERSION_CODE" || -z "$VERSION_NAME" ]]; then
  echo "could not read versionCode/versionName" >&2
  exit 1
fi
echo "    $APPLICATION_ID $VERSION_NAME ($VERSION_CODE)"

TAG="v${VERSION_NAME}"
if git rev-parse -q --verify "refs/tags/ota-${TAG}" >/dev/null; then
  echo "WARNING: tag ota-${TAG} already exists — did you forget to bump versionCode?" >&2
fi

echo "==> building release APK"
$GRADLE :app:assembleRelease

APK_SRC="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$APK_SRC" ]]; then
  if [[ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]]; then
    echo "release APK is unsigned: configure RELEASE_* in local.properties" >&2
  else
    echo "release APK not found at $APK_SRC" >&2
  fi
  exit 1
fi

mkdir -p "$OUT_DIR"
APK_NAME="InstaNova-${VERSION_NAME}.apk"
APK_OUT="${OUT_DIR}/${APK_NAME}"
cp "$APK_SRC" "$APK_OUT"

SHA256="$(shasum -a 256 "$APK_OUT" | awk '{print $1}')"
SIZE_BYTES="$(wc -c < "$APK_OUT" | tr -d ' ')"
REPO_SLUG="$(git remote get-url origin 2>/dev/null \
  | sed -E 's#.*github\.com[:/]([^/]+/[^/.]+)(\.git)?#\1#')"
REPO_SLUG="${REPO_SLUG:-mohammadhabib69/InstaNova}"
APK_URL="https://github.com/${REPO_SLUG}/releases/download/${TAG}/${APK_NAME}"

# Release notes: explicit flags win, otherwise the commits since the previous OTA tag.
if [[ ${#NOTES[@]} -eq 0 && -z "$NOTES_FILE" ]]; then
  PREV_TAG="$(git tag --list 'ota-v*' --sort=-v:refname | head -n1)"
  RANGE="HEAD"
  [[ -n "$PREV_TAG" ]] && RANGE="${PREV_TAG}..HEAD"
  while IFS= read -r line; do
    [[ -n "$line" ]] && NOTES+=("$line")
  done < <(git log "$RANGE" --no-merges --pretty=format:'%s' | head -n 12)
fi

JSON_NOTES=""
if [[ -n "$NOTES_FILE" ]]; then
  while IFS= read -r line; do
    line="$(printf '%s' "$line" | sed -E 's/^[-*•][[:space:]]*//')"
    [[ -n "$line" ]] && NOTES+=("$line")
  done < "$NOTES_FILE"
fi

for note in "${NOTES[@]:-}"; do
  [[ -z "$note" ]] && continue
  escaped="$(printf '%s' "$note" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
  [[ -n "$JSON_NOTES" ]] && JSON_NOTES+=","
  JSON_NOTES+=$'\n    "'"$escaped"$'"'
done

# Committed timestamp comes from git so the manifest never carries a made-up date.
PUBLISHED_AT="$(git log -1 --pretty=format:'%cI' 2>/dev/null || date -u +%Y-%m-%dT%H:%M:%SZ)"

cat > "${OUT_DIR}/latest.json" <<JSON
{
  "versionCode": ${VERSION_CODE},
  "versionName": "${VERSION_NAME}",
  "apkUrl": "${APK_URL}",
  "sha256": "${SHA256}",
  "sizeBytes": ${SIZE_BYTES},
  "publishedAt": "${PUBLISHED_AT}",
  "releaseNotes": [${JSON_NOTES}
  ]
}
JSON

echo
echo "artefacts in ${OUT_DIR}:"
ls -lh "${OUT_DIR}/latest.json" "$APK_OUT"
echo
cat "${OUT_DIR}/latest.json"
echo
echo "Upload BOTH files as assets of release ${TAG}."
echo "The in-app manifest URL resolves to releases/latest/download/latest.json, so no"
echo "client-side change is needed for the next version — only a higher versionCode."

if [[ $PUBLISH -eq 1 ]]; then
  if ! command -v gh >/dev/null 2>&1; then
    echo "--publish needs the GitHub CLI (gh)" >&2
    exit 1
  fi
  echo "==> creating GitHub release ${TAG}"
  gh release create "$TAG" "$APK_OUT" "${OUT_DIR}/latest.json" \
     --title "InstaNova ${VERSION_NAME}" \
     --notes "InstaNova ${VERSION_NAME} (${VERSION_CODE})"
fi
