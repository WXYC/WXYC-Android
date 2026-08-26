package org.wxyc.wxycapp.analytics

/**
 * Assembles the property map shared by the `play` and `pause` events.
 *
 * The shape is dictated by iOS, which writes the same properties on the same events in
 * the same PostHog project — see `PlaybackStartedEvent` / `PlaybackStoppedEvent` in
 * `Shared/Playback/Sources/PlaybackCore/Analytics/PlaybackAnalytics.swift`:
 *
 * - `source` — the surface, from the shared [PlaybackSource] vocabulary.
 * - `reason` — free text; reuses iOS's wording where the same thing happens on both.
 * - `duration` — **seconds**, on `pause` only.
 * - `session_id` — the per-listen identifier, pairing a `pause` with its `play`.
 *
 * Absent values are omitted rather than defaulted. A substituted duration is averaged
 * in as though it were a real listen; an omitted one reads as a pre-instrumentation
 * row and is skipped by aggregates.
 */
object PlaybackEventProperties {

    fun build(
        attribution: PlaybackAttribution,
        durationSeconds: Double?,
        sessionId: String?
    ): Map<String, Any> {
        val properties = mutableMapOf<String, Any>("source" to attribution.source.value)
        attribution.reason?.let { properties["reason"] = it }
        durationSeconds?.let { properties["duration"] = it }
        sessionId?.let { properties["session_id"] = it }
        return properties
    }
}
