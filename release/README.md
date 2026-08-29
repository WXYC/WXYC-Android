# WXYC Android — release automation

Three scripts take a blank Mac to a signed, submittable `.aab`. Two short web actions in the Play Console can't be scripted (Google has no API for an upload-key reset), so they're called out inline as **MANUAL STEP** boxes.

## The whole flow

```
./release/01-setup-env.sh                 # once per machine
./release/02-create-upload-key.sh         # once — makes the new upload key + cert
        └─▶ [web] Play Console: upload the .pem, request upload-key reset, wait ~48h
# bump versionCode/versionName in app/build.gradle and commit — see below
./release/03-build-signed-aab.sh
        └─▶ [web] Play Console: upload the .aab, create release, roll out
```

## What each script does

| Script | Does | Idempotent? |
|---|---|---|
| `01-setup-env.sh` | Installs Homebrew, Temurin JDK 21, Android command-line tools; accepts SDK licenses; installs platform 36 + build-tools 36.0.0; writes `local.properties`; verifies Gradle configures. | Mostly — Homebrew, the JDK and the command-line tools are skipped if present, but the licence, SDK-package and Gradle steps re-run every time, and `local.properties` is rewritten with only `sdk.dir` (any other keys in it are lost). |
| `02-create-upload-key.sh` | Generates `~/wxyc-upload-keystore.jks` (alias `upload`, RSA 2048, 30-yr validity) and exports `~/wxyc-upload-certificate.pem`. Prints the cert SHA-256. | No — refuses to run against an existing keystore. `--force` skips that refusal but does **not** work: `keytool -genkeypair` won't overwrite a keystore, so the run dies on a Java exception instead. To genuinely replace a key, move the old file aside yourself first — and be certain, because builds signed by a key you discard can never be updated. |
| `03-build-signed-aab.sh` | Runs `:app:bundleRelease`, signs the bundle with the upload key via `jarsigner`, verifies it, and prints the output path. Also takes `--set-version-code`/`--version-name`, which rewrite `app/build.gradle` in the working tree — see the version-bump section below before using them. | The build is; the version flags are not — they write to a tracked file. |

## Prerequisites

- macOS.
- The repo cloned, and `secrets.properties` in the repo root (get it from Jake). The app builds without it, but song requests / artwork / analytics need it.

## Passwords

`02` and `03` need the keystore's store/key passwords. Resolution order: environment variable → macOS Keychain → interactive prompt. On first prompt you're offered to save them to your Keychain so later runs are non-interactive. Nothing is written to disk in plaintext unless you deliberately put it in `config.sh` (gitignored). The two passwords resolve independently, so a non-interactive run has to set **both** `UPLOAD_STORE_PASS` and `UPLOAD_KEY_PASS` even when they hold the same value — setting only the store one falls through to the interactive prompt and hangs.

## Config

Defaults are baked in; override only if needed by copying `config.example.sh` to `config.sh` (gitignored) or by exporting the same variable names. Keystore/cert paths, the alias, and the SDK location are all configurable.

## Ordering gotcha: the upload-key reset

The upload-key reset (after `02`) must be **applied by Google** before a build uploaded from `03` will be accepted. You can run `03` while you wait — just don't upload until the reset confirmation arrives. A "wrong signing key" error on upload = the reset isn't live yet.

## Commit the version bump before building

`03`'s `--set-version-code` / `--version-name` rewrite `app/build.gradle` in the working tree and commit nothing. Production build `38 (1.3)` shipped that way: the bump existed only in the tree that ran `03`, `master` still said `37 (1.2)`, and no commit in this repository corresponds to the artifact Play is serving. So commit the bump first and then run `03` with no version flags — it builds whatever the committed tree says. If you do let `03` write the bump, the run isn't finished until `git diff app/build.gradle` matches what you meant to ship and you have committed it, before uploading the `.aab` rather than after. Note that nothing enforces this: `03` will still build and sign a shippable bundle from an uncommitted tree, so until that changes this is an operator convention rather than a guarantee.

## Logs

Every run tees to `release/logs/<script>-<timestamp>.log`.

## Not included: fully automated store upload

Publishing the `.aab` could be automated with `fastlane supply` / the Play Developer API, but that needs a Google Cloud service account with Play Console access — more one-time setup than a single manual upload is worth. Ask if this becomes a frequent release.
