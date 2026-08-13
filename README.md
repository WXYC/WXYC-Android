# WXYCandroidApp

## CI

Every pull request — and each push to `master` — runs on `ubuntu-latest` via `.github/workflows/ci.yml`:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

CI runs on JDK 17 (Temurin), and the build pins the same version through a Gradle toolchain (auto-provisioned by the foojay resolver if you don't have 17 installed), so the two commands above run on the JDK CI uses regardless of your local `JAVA_HOME`. Lint fails the build on any finding not frozen in `app/lint-baseline.xml` — the pre-CI debt snapshot; don't add to it, shrink it. Test and lint reports are uploaded as a run artifact on every CI run, including failures.
