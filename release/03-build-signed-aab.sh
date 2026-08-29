#!/usr/bin/env bash
# 03 — Build a release App Bundle and sign it with the upload key, producing a
# Play-submittable .aab. Optionally bumps versionCode/versionName first.
#
# Usage:
#   ./release/03-build-signed-aab.sh [--set-version-code N] [--version-name X] [--clean]
#
# If --set-version-code is omitted, the current value in app/build.gradle is
# used as-is (the script warns you what it is — it MUST exceed the versionCode
# already live on the Play Store).

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
start_logging
load_config
resolve_jdk

NEW_CODE=""; NEW_NAME=""; CLEAN=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --set-version-code) NEW_CODE="${2:?}"; shift 2;;
    --version-name)     NEW_NAME="${2:?}"; shift 2;;
    --clean)            CLEAN=1; shift;;
    *) die "Unknown argument: $1";;
  esac
done

GRADLE_FILE="$REPO_ROOT/app/build.gradle"
[[ -f "$GRADLE_FILE" ]] || die "Not found: $GRADLE_FILE"
[[ -f "$UPLOAD_KEYSTORE" ]] || die "Upload keystore missing: $UPLOAD_KEYSTORE (run 02 first)."

step "Version"
CUR_CODE="$(grep -Eo '^[[:space:]]*versionCode[[:space:]]+[0-9]+' "$GRADLE_FILE" | grep -Eo '[0-9]+' | head -1)"
CUR_NAME="$(grep -Eo 'versionName[[:space:]]+"[^"]*"' "$GRADLE_FILE" | sed -E 's/.*"([^"]*)".*/\1/' | head -1)"
info "Current: versionCode=$CUR_CODE versionName=\"$CUR_NAME\""

if [[ -n "$NEW_CODE" ]]; then
  [[ "$NEW_CODE" =~ ^[0-9]+$ ]] || die "--set-version-code must be an integer."
  sed -i.bak -E "s/(^[[:space:]]*versionCode[[:space:]]+)[0-9]+/\1${NEW_CODE}/" "$GRADLE_FILE"
  ok "Set versionCode -> $NEW_CODE"
else
  NEW_CODE="$CUR_CODE"
  warn "No --set-version-code given; building with versionCode=$CUR_CODE."
  warn "This MUST be higher than the versionCode already on the Play Store, or the upload is rejected."
fi
if [[ -n "$NEW_NAME" ]]; then
  sed -i.bak -E "s/(versionName[[:space:]]+\")[^\"]*(\")/\1${NEW_NAME}\2/" "$GRADLE_FILE"
  ok "Set versionName -> \"$NEW_NAME\""
else
  NEW_NAME="$CUR_NAME"
fi
[[ -f "$GRADLE_FILE.bak" ]] && rm -f "$GRADLE_FILE.bak"

step "Build release bundle"
GRADLE_TASKS=()
[[ $CLEAN -eq 1 ]] && GRADLE_TASKS+=(clean)
GRADLE_TASKS+=(:app:bundleRelease)
( cd "$REPO_ROOT" && JAVA_HOME="$JAVA_HOME" ./gradlew "${GRADLE_TASKS[@]}" )
UNSIGNED="$REPO_ROOT/app/build/outputs/bundle/release/app-release.aab"
[[ -f "$UNSIGNED" ]] || die "Expected bundle not found at $UNSIGNED"
ok "Built $UNSIGNED"

step "Sign with the upload key (jarsigner)"
get_passwords
SIGNED="$REPO_ROOT/app/build/outputs/bundle/release/wxyc-${NEW_NAME}-${NEW_CODE}-signed.aab"
"$JARSIGNER" -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore "$UPLOAD_KEYSTORE" -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -signedjar "$SIGNED" "$UNSIGNED" "$UPLOAD_ALIAS"

# Verify (a self-signed upload cert is expected; we check for "jar verified",
# NOT -strict, which would flag the missing CA chain).
if "$JARSIGNER" -verify "$SIGNED" | grep -q "jar verified"; then
  ok "Signature verified."
else
  die "Signature verification failed for $SIGNED"
fi

SHA="$(keystore_sha256 "$UPLOAD_KEYSTORE" "$STORE_PASS")"
step "Result"
echo "    Signed bundle : $SIGNED"
echo "    versionCode   : $NEW_CODE   versionName: $NEW_NAME"
echo "    Signed by SHA-256: $SHA"
info "That SHA-256 must match the 'Upload key certificate' in Play Console after your reset was applied."

manual \
  "In the Play Console:" \
  "  1. WXYC app  ->  Test and release  ->  Production." \
  "  2. 'Create new release'." \
  "  3. Upload:  $SIGNED" \
  "     (A 'wrong signing key' error here means the upload-key reset from step 02" \
  "      hasn't been applied yet — wait for Google's confirmation and retry.)" \
  "  4. Add release notes, then Save -> Review release -> Start rollout to Production."

info "Remember to commit the versionCode/versionName bump in app/build.gradle."
