#!/usr/bin/env bash
# Optional overrides for the release scripts.
# Copy to release/config.sh (gitignored) and edit only if the defaults are wrong.
# Every value here can also be set as an environment variable instead.

# Where the new upload keystore and its exported certificate live:
# UPLOAD_KEYSTORE="$HOME/wxyc-upload-keystore.jks"
# UPLOAD_CERT_PEM="$HOME/wxyc-upload-certificate.pem"
# UPLOAD_ALIAS="upload"

# Android SDK location (the setup script installs here):
# ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"

# Passwords — NOT recommended to store here in plaintext. Prefer the interactive
# prompt (with the optional "save to Keychain" step). Only set these for CI:
# UPLOAD_STORE_PASS="..."
# UPLOAD_KEY_PASS="..."
