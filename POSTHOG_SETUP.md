# PostHog API Key Configuration

To complete the PostHog integration, you need to add your PostHog API key to the `secrets.properties` file.

## Steps:

1. Open `secrets.properties` in the root of the WXYC-Android project (it should already exist for other secrets)

2. Add the following line:
   ```properties
   POSTHOG_API_KEY=your_posthog_api_key_here
   ```

3. Replace `your_posthog_api_key_here` with the actual PostHog API key from your PostHog account

## Notes:

- The PostHog SDK uses its default host (`https://us.i.posthog.com`) automatically
- The `secrets.properties` file is gitignored, so your API key won't be committed
- If the API key is blank, PostHog will log a warning but won't crash the app - events just won't be tracked

## Verification:

After adding the key, rebuild the app to ensure BuildConfig regenerates with the new value:
```bash
./gradlew clean assembleDebug
```

Then check logcat for these messages:
- `PostHog initialized successfully` - indicates PostHog started correctly
- `Event captured: app launch` - indicates the first event was tracked
