#!/usr/bin/env bash
# 01 — Install every build tool from scratch: Homebrew, a JDK, the Android SDK,
# licenses, and local.properties. Safe to re-run; skips anything already present.

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
start_logging
load_config

step "1/6  Homebrew"
if command -v brew >/dev/null 2>&1; then
  ok "Homebrew present: $(brew --version | head -1)"
else
  info "Installing Homebrew (may prompt for your Mac password)…"
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  # Make brew available in this shell for the rest of the script.
  if [[ -x /opt/homebrew/bin/brew ]]; then eval "$(/opt/homebrew/bin/brew shellenv)"
  elif [[ -x /usr/local/bin/brew ]]; then eval "$(/usr/local/bin/brew shellenv)"; fi
  command -v brew >/dev/null 2>&1 || die "Homebrew install did not complete. Open a new terminal and re-run."
fi

step "2/6  JDK 21 (Temurin)"
if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  ok "JDK 21 already installed."
else
  info "Installing Temurin 21…"
  brew install --cask temurin@21
fi
resolve_jdk

step "3/6  Android command-line tools"
if command -v sdkmanager >/dev/null 2>&1; then
  SDKMANAGER="$(command -v sdkmanager)"
elif [[ -x "$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager" ]]; then
  SDKMANAGER="$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager"
else
  info "Installing android-commandlinetools…"
  brew install --cask android-commandlinetools
  SDKMANAGER="$(command -v sdkmanager || echo "$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager")"
fi
[[ -x "$SDKMANAGER" ]] || die "sdkmanager not found after install."
ok "sdkmanager: $SDKMANAGER"

step "4/6  Accept SDK licenses"
mkdir -p "$ANDROID_SDK_ROOT"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null || true
ok "Licenses accepted."

step "5/6  Install SDK packages (platform 36, build-tools 36.0.0)"
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0"
ok "SDK packages installed under $ANDROID_SDK_ROOT"

step "6/6  Write local.properties + verify Gradle can configure"
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > "$REPO_ROOT/local.properties"
ok "Wrote $REPO_ROOT/local.properties"
info "Running a quick Gradle configuration check (first run downloads Gradle)…"
if ( cd "$REPO_ROOT" && JAVA_HOME="$JAVA_HOME" ./gradlew -q :app:help >/dev/null ); then
  ok "Gradle configured successfully."
else
  die "Gradle could not configure the project. See the log above."
fi

step "Done"
cat <<EOF
Environment is ready.

Next:
  • If Jake gave you secrets.properties, drop it in the repo root:
        cp <path>/secrets.properties "$REPO_ROOT/secrets.properties"
  • Then create the upload key:
        ./release/02-create-upload-key.sh
EOF
