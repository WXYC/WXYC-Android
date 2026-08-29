#!/usr/bin/env bash
# 02 — Generate a brand-new upload keystore and export its certificate (.pem)
# for the Play Console upload-key reset. Refuses to overwrite an existing
# keystore unless --force is given (a keystore is irreplaceable — protect it).

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
start_logging
load_config
resolve_jdk

FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

step "Create upload keystore: $UPLOAD_KEYSTORE"
if [[ -f "$UPLOAD_KEYSTORE" && "$FORCE" -ne 1 ]]; then
  die "Keystore already exists at $UPLOAD_KEYSTORE.
     Refusing to overwrite. Re-run with --force ONLY if you are certain you want
     to discard it (any build signed by the old one becomes un-updatable)."
fi

info "Choose passwords for the new keystore. Store them somewhere permanent —"
info "losing them is what started this whole exercise."
get_passwords confirm

"$KEYTOOL" -genkeypair -v \
  -keystore "$UPLOAD_KEYSTORE" -alias "$UPLOAD_ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -dname "CN=WXYC Radio, OU=WXYC 89.3 FM, O=WXYC, L=Chapel Hill, ST=NC, C=US"
ok "Keystore created."

step "Export certificate for Play Console"
"$KEYTOOL" -export -rfc \
  -keystore "$UPLOAD_KEYSTORE" -alias "$UPLOAD_ALIAS" \
  -storepass "$STORE_PASS" -file "$UPLOAD_CERT_PEM"
ok "Certificate written to $UPLOAD_CERT_PEM"

SHA="$(keystore_sha256 "$UPLOAD_KEYSTORE" "$STORE_PASS")"
step "New upload certificate SHA-256"
echo "    $SHA"

manual \
  "In the Play Console (as the account owner):" \
  "  1. Select the WXYC app (org.wxyc.WXYCCH)." \
  "  2. Test and release  ->  App integrity  ->  App signing." \
  "  3. Click 'Request upload key reset'." \
  "  4. Reason: 'Lost or compromised upload key'." \
  "  5. Upload this file:  $UPLOAD_CERT_PEM" \
  "" \
  "Google applies the reset within ~48h. Wait for the confirmation email" \
  "BEFORE uploading a build (earlier uploads are rejected)." \
  "" \
  "BACK UP $UPLOAD_KEYSTORE and its passwords now — in two places."

step "After Google confirms the reset"
echo "    ./release/03-build-signed-aab.sh --set-version-code <N> --version-name <X>"
