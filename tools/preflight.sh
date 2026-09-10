#!/usr/bin/env bash
#
# Release preflight. Builds a signed release APK and refuses to hand it to you
# unless it is actually the app this project claims to be.
#
#   ./tools/preflight.sh
#
# Exit code 0 means the APK at the printed path is safe to install and publish.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RED=$'\033[31m'; YEL=$'\033[33m'; GRN=$'\033[32m'; DIM=$'\033[2m'; OFF=$'\033[0m'
FAILED=0
WARNED=0

fail()  { echo "${RED}FAIL${OFF}  $*"; FAILED=1; }
warn()  { echo "${YEL}WARN${OFF}  $*"; WARNED=1; }
pass()  { echo "${GRN} OK ${OFF}  $*"; }
step()  { echo; echo "── $* ──"; }

# ---------------------------------------------------------------- SDK tooling
sdk_root() {
  if [[ -f local.properties ]]; then
    local dir
    dir="$(sed -n 's/^sdk\.dir=//p' local.properties | tail -1)"
    [[ -n "$dir" ]] && { echo "$dir"; return; }
  fi
  echo "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
}

find_build_tool() {
  local name="$1" sdk newest candidate
  sdk="$(sdk_root)"
  newest="$(ls -d "$sdk"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
  if [[ -n "$newest" ]]; then
    candidate="${newest}${name}"
    [[ -x "$candidate" ]] && { echo "$candidate"; return 0; }
  fi
  command -v "$name" 2>/dev/null && return 0
  return 1
}

# ------------------------------------------------------- 1. irreversible bits
step "Decisions that cannot be undone after the first release"

APP_ID="$(sed -n 's/^ *applicationId *= *"\(.*\)"/\1/p' app/build.gradle.kts | head -1)"
if [[ -z "$APP_ID" ]]; then
  fail "Could not read applicationId from app/build.gradle.kts"
elif [[ "$APP_ID" == "org.riverbank.shop" ]]; then
  warn "applicationId is still the default '$APP_ID'."
  echo "      It is permanent once published: changing it later forces every user to"
  echo "      uninstall and lose their session. Set it now:"
  echo "        ${DIM}./tools/rename-package.sh io.github.YOURNAME.riverbank${OFF}"
else
  pass "applicationId: $APP_ID"
fi

VERSION_NAME="$(sed -n 's/^ *versionName *= *"\(.*\)"/\1/p' app/build.gradle.kts | head -1)"
VERSION_CODE="$(sed -n 's/^ *versionCode *= *\([0-9]*\)/\1/p' app/build.gradle.kts | head -1)"
pass "version: $VERSION_NAME (versionCode $VERSION_CODE)"

if [[ -n "$(git -C "$ROOT" status --porcelain 2>/dev/null)" ]]; then
  warn "Working tree has uncommitted changes; the tag will not match the build."
else
  pass "Working tree is clean"
fi

# ------------------------------------------------------------ 2. placeholders
step "Placeholders"

# release.yml substitutes OWNER itself at run time; FDROID.md and this script
# document the placeholder. Everything else with OWNER in it is a real gap.
PLACEHOLDERS="$(git grep -l 'OWNER' -- \
  ':!tools/preflight.sh' ':!docs/FDROID.md' ':!.github/workflows/release.yml' \
  2>/dev/null || true)"
if [[ -n "$PLACEHOLDERS" ]]; then
  warn "The literal 'OWNER' still appears in:"
  echo "$PLACEHOLDERS" | sed 's/^/        /'
  echo "      Settings links and F-Droid metadata will point nowhere. Fix with:"
  echo "        ${DIM}git grep -l OWNER | xargs sed -i 's|OWNER|your-username|g'${OFF}"
else
  pass "No OWNER placeholders left"
fi

if [[ -z "$(ls -A fastlane/metadata/android/en-US/images/phoneScreenshots 2>/dev/null)" ]]; then
  warn "No screenshots in fastlane/metadata/android/en-US/images/phoneScreenshots/"
  echo "      F-Droid clients show a blank listing without them. Capture with:"
  echo "        ${DIM}adb exec-out screencap -p > fastlane/metadata/android/en-US/images/phoneScreenshots/1.png${OFF}"
else
  pass "Screenshots present"
fi

# --------------------------------------------------------------- 3. signing
step "Signing material"

HAVE_SIGNING=0
if [[ -f keystore.properties ]]; then
  STORE="$(sed -n 's/^storeFile=//p' keystore.properties | tail -1)"
  if [[ -f "$STORE" ]]; then
    pass "keystore.properties -> $STORE"
    HAVE_SIGNING=1
  else
    fail "keystore.properties points at '$STORE', which does not exist"
  fi
elif [[ -n "${RIVERBANK_KEYSTORE:-}" && -f "$RIVERBANK_KEYSTORE" ]]; then
  pass "RIVERBANK_KEYSTORE -> $RIVERBANK_KEYSTORE"
  HAVE_SIGNING=1
else
  fail "No signing key. Create one, then write keystore.properties:"
  echo "        ${DIM}keytool -genkeypair -v -keystore riverbank-upload.p12 \\"
  echo "          -storetype PKCS12 -alias riverbank -keyalg RSA -keysize 4096 \\"
  echo "          -validity 10000${OFF}"
fi

LEAKED=0
if git -C "$ROOT" ls-files --error-unmatch keystore.properties >/dev/null 2>&1; then
  fail "keystore.properties is TRACKED BY GIT. Remove it from the index now."
  LEAKED=1
fi
for tracked in $(git -C "$ROOT" ls-files '*.p12' '*.jks' '*.keystore' 2>/dev/null); do
  fail "Signing key '$tracked' is tracked by git. Remove it from the index now."
  LEAKED=1
done
[[ $LEAKED -eq 0 ]] && pass "No signing material is tracked by git"

if [[ $HAVE_SIGNING -eq 0 ]]; then
  echo; echo "${RED}Stopping: cannot build a release APK without a key.${OFF}"
  exit 1
fi

# ----------------------------------------------------------------- 4. build
step "Building release APK (R8 + resource shrinking are ON — debug never exercises these)"

if ! ./gradlew --no-daemon clean assembleRelease; then
  echo; echo "${RED}Release build failed.${OFF}"
  echo "If the debug build works and only release fails, R8 is the first suspect."
  echo "Bisect by setting isMinifyEnabled = false in app/build.gradle.kts, and if"
  echo "that fixes it, add the missing -keep rule to app/proguard-rules.pro rather"
  echo "than leaving minification off."
  exit 1
fi

APK="$(find app/build/outputs/apk/release -name '*.apk' | head -1)"
[[ -f "$APK" ]] || { echo "${RED}No APK produced.${OFF}"; exit 1; }
pass "Built $APK ($(du -h "$APK" | cut -f1))"

# ------------------------------------------------------------- 5. verify APK
step "Verifying the artifact"

APKSIGNER="$(find_build_tool apksigner || true)"
if [[ -n "$APKSIGNER" ]]; then
  if "$APKSIGNER" verify --print-certs "$APK" > /tmp/riverbank-certs.txt 2>&1; then
    pass "Signature verifies"
    echo "${DIM}$(grep -i 'SHA-256 digest' /tmp/riverbank-certs.txt | head -1 | sed 's/^/        /')${OFF}"
    echo "      Publish that certificate digest so users can check what they install."
  else
    fail "apksigner could not verify the APK:"
    sed 's/^/        /' /tmp/riverbank-certs.txt
  fi
else
  warn "apksigner not found in the SDK; skipping signature verification"
fi

AAPT2="$(find_build_tool aapt2 || true)"
if [[ -n "$AAPT2" ]]; then
  FOUND="$("$AAPT2" dump permissions "$APK" 2>/dev/null \
    | sed -n "s/^uses-permission: name='android.permission.\([A-Z_]*\)'.*/\1/p" \
    | sort -u)"
  EXPECTED=$'INTERNET\nUSE_BIOMETRIC'
  if [[ "$FOUND" == "$EXPECTED" ]]; then
    pass "Permissions in the shipped APK: INTERNET, USE_BIOMETRIC — and nothing else"
  else
    fail "Permission set changed. This is the app's entire premise. Found:"
    echo "$FOUND" | sed 's/^/        /'
  fi
else
  warn "aapt2 not found in the SDK; could not verify the shipped permission set"
fi

echo
echo "${DIM}sha256: $(sha256sum "$APK" | cut -d' ' -f1)${OFF}"

# ------------------------------------------------------------------ verdict
step "Result"
if [[ $FAILED -ne 0 ]]; then
  echo "${RED}Not ready.${OFF} Fix the FAIL lines above."
  exit 1
fi
if [[ $WARNED -ne 0 ]]; then
  echo "${YEL}Buildable, but read the WARN lines before you publish this.${OFF}"
else
  echo "${GRN}Ready.${OFF}"
fi
echo
echo "Install on a connected device:"
echo "  ${DIM}adb install -r \"$APK\"${OFF}"
echo "Or copy the APK to the phone and open it in a file manager."
exit 0
