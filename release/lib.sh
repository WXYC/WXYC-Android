#!/usr/bin/env bash
# Shared helpers for the WXYC Android release scripts.
# Sourced by 01-setup-env.sh, 02-create-upload-key.sh, 03-build-signed-aab.sh.
# Not meant to be run directly.

set -euo pipefail

# --- Paths ---------------------------------------------------------------
RELEASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC2034  # REPO_ROOT is consumed by the scripts that source this file.
REPO_ROOT="$(cd "$RELEASE_DIR/.." && pwd)"
LOG_DIR="$RELEASE_DIR/logs"

# --- Colours / logging ---------------------------------------------------
if [[ -t 1 ]]; then
  _C_RESET=$'\033[0m'; _C_INFO=$'\033[0;34m'; _C_OK=$'\033[0;32m'
  _C_WARN=$'\033[0;33m'; _C_ERR=$'\033[0;31m'; _C_STEP=$'\033[1;36m'
else
  _C_RESET=""; _C_INFO=""; _C_OK=""; _C_WARN=""; _C_ERR=""; _C_STEP=""
fi

info()  { printf '%s[info]%s  %s\n'  "$_C_INFO" "$_C_RESET" "$*"; }
ok()    { printf '%s[ ok ]%s  %s\n'  "$_C_OK"   "$_C_RESET" "$*"; }
warn()  { printf '%s[warn]%s  %s\n'  "$_C_WARN" "$_C_RESET" "$*" >&2; }
err()   { printf '%s[fail]%s  %s\n'  "$_C_ERR"  "$_C_RESET" "$*" >&2; }
step()  { printf '\n%s==>%s %s%s%s\n' "$_C_STEP" "$_C_RESET" "$_C_STEP" "$*" "$_C_RESET"; }
die()   { err "$*"; exit 1; }

# Callout box for the MANUAL steps that cannot be scripted (Play Console).
manual() {
  printf '\n%s┌─ MANUAL STEP ─────────────────────────────────────────────%s\n' "$_C_WARN" "$_C_RESET"
  local line
  for line in "$@"; do printf '%s│%s %s\n' "$_C_WARN" "$_C_RESET" "$line"; done
  printf '%s└───────────────────────────────────────────────────────────%s\n\n' "$_C_WARN" "$_C_RESET"
}

# Tee all output of the calling script to a timestamped log file.
start_logging() {
  mkdir -p "$LOG_DIR"
  local name; name="$(basename "${0}" .sh)"
  local ts; ts="$(date +%Y%m%d-%H%M%S)"   # local time; filenames only
  LOG_FILE="$LOG_DIR/${name}-${ts}.log"
  exec > >(tee -a "$LOG_FILE") 2>&1
  info "Logging to $LOG_FILE"
}

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"; }

# --- Config --------------------------------------------------------------
# Load release/config.sh if present, then apply defaults. All values can also
# be overridden via environment variables of the same name.
load_config() {
  # shellcheck source=/dev/null
  [[ -f "$RELEASE_DIR/config.sh" ]] && source "$RELEASE_DIR/config.sh"
  UPLOAD_KEYSTORE="${UPLOAD_KEYSTORE:-$HOME/wxyc-upload-keystore.jks}"
  UPLOAD_ALIAS="${UPLOAD_ALIAS:-upload}"
  UPLOAD_CERT_PEM="${UPLOAD_CERT_PEM:-$HOME/wxyc-upload-certificate.pem}"
  ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
  export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"
}

# --- JDK resolution ------------------------------------------------------
# Sets JAVA_HOME (preferring Temurin/OpenJDK 21) and exports KEYTOOL/JARSIGNER.
resolve_jdk() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/keytool" ]]; then
    :
  elif /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  elif /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  elif [[ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool" ]]; then
    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  else
    die "No JDK 17/21 found. Run ./release/01-setup-env.sh first."
  fi
  export JAVA_HOME
  KEYTOOL="$JAVA_HOME/bin/keytool"
  JARSIGNER="$JAVA_HOME/bin/jarsigner"
  [[ -x "$KEYTOOL"   ]] || die "keytool not found under $JAVA_HOME"
  [[ -x "$JARSIGNER" ]] || die "jarsigner not found under $JAVA_HOME"
  info "Using JDK at $JAVA_HOME"
}

# --- Secrets (keystore passwords) ---------------------------------------
# Order of resolution: environment var -> macOS Keychain -> interactive prompt.
# Keychain service names keep the store and key passwords separate.
_KEYCHAIN_SERVICE_STORE="wxyc-android-upload-store"
_KEYCHAIN_SERVICE_KEY="wxyc-android-upload-key"

_keychain_get() { security find-generic-password -a "$USER" -s "$1" -w 2>/dev/null || true; }
_keychain_set() { security add-generic-password -a "$USER" -s "$1" -w "$2" -U >/dev/null 2>&1 || true; }

# get_passwords: populates STORE_PASS and KEY_PASS. Pass "confirm" to require
# double-entry (used when creating a new keystore).
get_passwords() {
  local mode="${1:-plain}"
  STORE_PASS="${UPLOAD_STORE_PASS:-$(_keychain_get "$_KEYCHAIN_SERVICE_STORE")}"
  KEY_PASS="${UPLOAD_KEY_PASS:-$(_keychain_get "$_KEYCHAIN_SERVICE_KEY")}"
  if [[ -z "$STORE_PASS" ]]; then STORE_PASS="$(_prompt_secret "keystore password" "$mode")"; _offer_keychain_save; fi
  if [[ -z "$KEY_PASS" ]]; then
    KEY_PASS="$(_prompt_secret "key password (Return to reuse the keystore password)" "$mode" allow_empty)"
    [[ -z "$KEY_PASS" ]] && KEY_PASS="$STORE_PASS"
    [[ "${_SAVE_KC:-0}" == "1" ]] && _keychain_set "$_KEYCHAIN_SERVICE_KEY" "$KEY_PASS"
  fi
  [[ -n "$STORE_PASS" ]] || die "Empty keystore password."
}

_prompt_secret() {
  local label="$1" mode="$2" allow_empty="${3:-}" p1 p2
  while :; do
    read -rs -p "Enter $label: " p1 </dev/tty; echo >/dev/tty
    if [[ -z "$p1" && -n "$allow_empty" ]]; then printf '%s' ""; return; fi
    if [[ "$mode" == "confirm" ]]; then
      read -rs -p "Confirm $label: " p2 </dev/tty; echo >/dev/tty
      [[ "$p1" == "$p2" ]] || { warn "Did not match, try again."; continue; }
    fi
    [[ -n "$p1" ]] || { warn "Cannot be empty."; continue; }
    printf '%s' "$p1"; return
  done
}

_offer_keychain_save() {
  local ans
  read -r -p "Save these passwords to your macOS Keychain for next time? [y/N] " ans </dev/tty || true
  if [[ "$ans" =~ ^[Yy]$ ]]; then _SAVE_KC=1; _keychain_set "$_KEYCHAIN_SERVICE_STORE" "$STORE_PASS"; else _SAVE_KC=0; fi
}

# --- Misc ----------------------------------------------------------------
# Prints the SHA-256 fingerprint of the cert for a given keystore alias.
keystore_sha256() {
  "$KEYTOOL" -list -v -keystore "$1" -alias "$UPLOAD_ALIAS" -storepass "$2" 2>/dev/null \
    | awk -F'SHA256: ' '/SHA256:/ {print $2; exit}'
}
