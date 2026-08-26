# 1. Android and iOS share one PostHog project, with a property contract

Date: 2026-08-25

Status: Accepted

## Context

The Android app reports into PostHog project `134292` ("WXYC iOS") using the same write token as the iOS app, and has done since 2026-08-16. It emitted iOS event names verbatim, on the stated intent of mirroring them.

On 2026-08-24 the project's "Average Playback Duration" chart read 337 ms. Two independent causes: the chart plotted `median(duration) / count(pause)`, which is not an average of anything; and Android's `duration` was in milliseconds against iOS's seconds, with four events carrying an absolute Unix epoch (~1.787e12) from a start time that defaulted to zero. Android was 0.76% of the project's events and owned essentially all of the project-wide mean.

That last fact is the one that shaped this decision. **Volume share bounds the blast radius for counting metrics and bounds nothing at all for aggregating ones.** A mean has no minority; neither does a max, a p95, or any formula built on one. So the usual reassurance — "Android is small, it can't distort much" — is true of DAU and false of every average on the dashboard.

The org's existing convention for a deliberately shared project, written for the dj-site / wxyc-dj-ios pair, is that "a new event should be distinguishable by **name**, not only by `$lib`". Android violated it. The question was whether to comply or to diverge.

## Decision

**We diverge, deliberately, and invert the convention for the mobile pair.**

The two situations are not alike. dj-site and wxyc-dj-ios are *different surfaces of one product*, where the event name usefully carries which surface acted. iOS and Android are *the same product on two platforms*, where the whole point of several tiles is to compare or combine them. Distinguishing by name there would forbid the comparison the project exists to enable.

So for the mobile pair:

1. **Event names are shared and identical.** `play` means the same thing on both platforms. Platform is carried by `$lib` (`posthog-ios` covers iOS/macOS/iPadOS/watchOS; `posthog-android`).

2. **Compatibility lives in the properties, not the names.** One unit and one vocabulary per shared property name, so that a chart built by someone who forgot the `$lib` filter degrades gracefully instead of catastrophically. This is the load-bearing rule; everything below is its consequence.

3. **`source` is a closed enum that must remain a subset of iOS's.** Android's subset is `app`, `remote`, `auto`, `unknown`. A value iOS does not define splits a shared breakdown in two. `PlaybackAttributionTest` asserts the subset property, so adding a stray value fails a test rather than reaching the fleet.

4. **`reason` is free text and may extend.** Where the same thing happens on both platforms it reuses iOS's exact wording (`interruption began`, `route disconnected`); Android-specific causes may add values.

5. **`duration` is seconds, on `pause` only. `session_id` is a per-listen UUID, on both `play` and `pause`.** iOS puts no `duration` on `play` either — `PlaybackStartedEvent` carries only `reason`, `source` and `session_id`.

   The two platforms end a listen on different signals, and this is deliberate rather than an oversight. iOS preserves its `session_id` across `interruption began` and `route disconnected` because on iOS those are the first half of "pause, then auto-resume" — the listen isn't over. Android does not auto-resume from either: media3 routes a *transient* focus loss to playback suppression, which never flips `playWhenReady` and so emits nothing at all, and only a *permanent* focus loss or a headphone unplug reaches those reasons, with no resume to follow. There is nothing to bridge, so Android mints on every play and clears on every pause. The thing Android *does* auto-resume from — a stream reconnect — already cannot split a session, because the reconnect path never flips playback intent.

   The consequence to know when comparing: **the same `reason` string selects a different population on each platform.** `interruption began` on iOS covers every interruption including transient ones that resume seconds later; on Android it covers only permanent losses. Android therefore reports fewer pauses and longer durations than iOS for identical listener behaviour. `source` is unaffected, so filtering `source != 'auto'` recovers a comparable population.

6. **Android moved *onto* the current iOS names rather than away from them.** iOS snake_cased every event name in 3.2; Android was emitting the retired space-separated spellings, which land in the old leg of the union actions bridging that rename — a bridge that could then never decay to zero and retire. Aligning increases nominal collision on purpose: once `$lib` is the platform axis, a shared name costs nothing and a *stale* shared name costs real signal.

### Rejected

**Namespacing Android's events** (`android_play`). 82% of Android's volume is `$screen`, `Application Opened`, `Application Backgrounded` and `Application Installed`, emitted by the PostHog SDK itself with no rename hook. Namespacing would have covered 318 events of 1,784 and left `$lib` filtering mandatory for the rest — a rebuild that does not deliver correct-by-default.

**A separate PostHog project.** The org is on the free tier at its six-project limit, so this means paying or displacing an existing surface, to isolate 29 people. Revisit if Android grows an order of magnitude.

**Documentation as the only enforcement.** This incident is the evidence against it: nothing about the 2026-08-16 ingestion violated a written rule, because there wasn't one, and had there been one nothing would have read it. A convention that must be remembered is the same hazard as a filter that must be remembered.

## Consequences

**Enforcement is by construction plus detection, not by review convention.** The `PlaybackSource` enum makes an off-vocabulary `source` unrepresentable at the play/pause call sites, and `AnalyticsEventNamesTest` / `PlaybackAttributionTest` fail on a rename or a stray enum value. Both suites were verified non-vacuous by injecting the exact regressions they claim to catch.

Two honest limits on that. **The unit is not enforced by a type**: `capturePause` takes a bare `Double?`, so a millisecond value still compiles — only `PlaybackDurationTracker`'s division makes it seconds, and the guard alert below is what would actually catch a regression. And **the typing covers play/pause only**: `PostHogManager.capture(event: String, properties: Map<String, Any>)` remains public and is how `stream_error`, `stream_reconnected` and the UI events are emitted, so those are convention-enforced.

Backing that up, PostHog insight [`95Ns1Slm`](https://us.posthog.com/project/134292/insights/95Ns1Slm) and its "Pause duration unit guard" alert check `max(pause.duration)` daily against an upper bound of 259,200 s (72 h). That bound sits above the observed legitimate maximum of 175,213 s (48.7 h over 60 days) — radio listeners genuinely leave the stream running for days, so a 24 h bound would false-fire. Any unit error, from either platform, surfaces within a day rather than in eight.

**Play/pause capture moved from `PlayerViewModel` to `AudioPlaybackService`.** Capturing at the in-app button meant `source` had exactly one value in 30 days of data, and that Android's `play` counted only in-app taps while the identically-named iOS event counted playback beginning from anywhere — 46% of iOS plays come from surfaces Android was not instrumenting. Attribution is read in `MediaSession.Callback.onPlayerCommandRequest`, which receives the issuing `ControllerInfo`; by the time `onPlayWhenReadyChanged` fires, the reason code reads `USER_REQUEST` for the in-app button and the notification alike. Capture is driven by playback *intent* (`playWhenReady`), not audio flow (`isPlaying`), so a stall does not split one listen into two.

**The corrupted rows stay.** No ingested data is deleted or rewritten. The permanent exclusion rule for any duration math is:

```
NOT ($lib = 'posthog-android' AND toFloat64OrNull(toString(properties.$app_build)) <= 38)
```

**The cast is required, not decoration.** PostHog stores `$app_build` as `Nullable(String)` (verified in project 134292), so a bare `$app_build <= 38` compares lexicographically: `'100' <= '38'` is **true**, and the rule would silently start excluding every build from 100 onward — and 4 through 9 today. Write it with the numeric cast every time.

Build number is the only reliable discriminator — value-based filtering cannot work, because a 40-second listen in milliseconds (40,000) sits inside the plausible range for radio, where an 8-hour overnight stream is real behaviour. Build numbers are monotonic, so the rule is stable indefinitely. It is baked into the guard insight above.

**The contract covers `play`, `pause` and the UI events. Two shared names are knowingly still unreconciled**, tracked separately rather than silently:

- **`stream_error`** — same name, different meaning, and *zero* overlapping property keys. Android emits it from `scheduleReconnect` on every reconnect attempt with `reason` / `attempt` / `delay_ms` / `played_for_ms` (milliseconds); iOS emits `StreamErrorEvent` only at terminal escalation boundaries, once per episode, with `player_type` / `error_type` / `error_description` / `reconnect_attempts` / `session_duration` (seconds) / `recovery_method` / `session_id`. `count(stream_error)` is not comparable across platforms and never was.
- **`error`** — Android writes the message under `description`, iOS under `error`. Android adds `error_type` (a Java class name), which collides by name with iOS's `stream_error.error_type` (a `StreamErrorType`).

Tracked as [WXYC-Android#52](https://github.com/WXYC/WXYC-Android/issues/52). Both predate this ADR. Neither is fixed here because reconciling them means changing what the Android reliability events *mean*, not just renaming a key, and that deserves its own change with its own migration boundary. Until then, treat those two names as platform-scoped and always filter by `$lib`. Android's `stream_reconnected` has no iOS counterpart at all (iOS's nearest is `stall_recovery`), so it is Android-only rather than colliding.

**`playback_heartbeat` is deliberately not ported.** iOS reconstructs listening-hours from `max(cumulative_seconds)` per `session_id`, because `pause.duration` alone under-measures: the longest listens — app swiped away, OS-killed, crashed — never fire a `pause` and contribute zero. Android has the same survivorship bias and no heartbeat, so **Android sessions are absent from every listening-hours figure**. This is currently correct rather than merely tolerable: those insights are all `$lib = 'posthog-ios'`-scoped, so Android contributes nothing rather than something wrong. Port it when Android MAU crosses ~100, or when a cross-platform listening-hours question is actually asked.

**Continuity breaks at this release** for `app_launch`, `playcut_detail_view_presented`, `streaming_link_tapped` and `external_link_tapped`, and for the shape of `play` / `pause`. Accepted given the population. Marked with a project annotation placed when the release reaches the fleet — not when it merges, because merging is not shipping: [#47](https://github.com/WXYC/WXYC-Android/pull/47) fixed the millisecond bug on 2026-08-24 and every install in the field was still emitting milliseconds a day later.

**The repo had drifted from the field.** `app/build.gradle` said `versionCode 37` / `versionName "1.2"` while the fleet reported build 38 / 1.3 — the shipped build was cut from a state never committed, and there are no tags, no releases and no fastlane. This release bumps to 39 / 1.4 in the repo. Documenting how a Play Store build gets cut is filed separately; it is the root enabler of both the drift and the unfielded fix.

## References

- [WXYC-Android#48](https://github.com/WXYC/WXYC-Android/issues/48) — the decision record and implementation scope
- [WXYC-Android#46](https://github.com/WXYC/WXYC-Android/issues/46) / [#47](https://github.com/WXYC/WXYC-Android/pull/47) — the `duration` unit and unset-start bugs
- `Shared/Playback/Sources/PlaybackCore/PlaybackSource.swift` (wxyc-ios-64) — the canonical `source` vocabulary
- `Shared/Playback/Sources/PlaybackCore/Analytics/PlaybackAnalytics.swift` (wxyc-ios-64) — the canonical event and property shapes
- `Shared/Analytics/Tests/AnalyticsTests/EventNameStabilityTests.swift` (wxyc-ios-64) — the test this repo's `AnalyticsEventNamesTest` mirrors
