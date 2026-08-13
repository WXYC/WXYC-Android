# WXYCandroidApp

## CI

Every pull request runs on `ubuntu-latest` via `.github/workflows/ci.yml`:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Run both locally before pushing so CI mirrors what you've already verified.
