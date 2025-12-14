package org.wxyc.wxycapp.analytics

import android.content.Context
import android.util.Log
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * Singleton manager for PostHog analytics.
 * Provides initialization and convenience methods for event tracking.
 */
object PostHogManager {
    private const val TAG = "PostHogManager"
    private var isInitialized = false
    
    /**
     * Initialize PostHog with API key.
     * Uses the SDK's default host (https://us.i.posthog.com)
     * Should be called from Application.onCreate()
     */
    fun initialize(context: Context, apiKey: String) {
        if (isInitialized) {
            Log.w(TAG, "PostHog already initialized")
            return
        }
        
        if (apiKey.isBlank()) {
            Log.e(TAG, "PostHog API key is blank - analytics will not be tracked")
            return
        }
        
        try {
            val config = PostHogAndroidConfig(apiKey = apiKey)
            
            PostHogAndroid.setup(context, config)
            isInitialized = true
            Log.i(TAG, "PostHog initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PostHog", e)
        }
    }
    
    /**
     * Register super properties that will be included with all events.
     */
    fun registerSuperProperties(properties: Map<String, Any>) {
        if (!isInitialized) {
            Log.w(TAG, "PostHog not initialized - cannot register super properties")
            return
        }
        
        try {
            properties.forEach { (key, value) ->
                PostHog.register(key, value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register super properties", e)
        }
    }
    
    /**
     * Track a simple event.
     */
    fun capture(event: String) {
        capture(event, emptyMap())
    }
    
    /**
     * Track an event with properties.
     */
    fun capture(event: String, properties: Map<String, Any>) {
        if (!isInitialized) {
            Log.w(TAG, "PostHog not initialized - event not tracked: $event")
            return
        }
        
        try {
            if (properties.isEmpty()) {
                PostHog.capture(event = event)
            } else {
                PostHog.capture(event = event, properties = properties)
            }
            Log.d(TAG, "Event captured: $event")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture event: $event", e)
        }
    }
    
    /**
     * Track a playback play event.
     */
    fun capturePlay(source: String, reason: String) {
        capture(
            AnalyticsEvents.PLAYBACK_PLAY,
            mapOf(
                "source" to source,
                "reason" to reason
            )
        )
    }
    
    /**
     * Track a playback pause event.
     */
    fun capturePause(source: String, duration: Long, reason: String = "") {
        val properties = mutableMapOf<String, Any>(
            "source" to source,
            "duration" to duration
        )
        if (reason.isNotBlank()) {
            properties["reason"] = reason
        }
        capture(AnalyticsEvents.PLAYBACK_PAUSE, properties)
    }
    
    /**
     * Track an error event.
     */
    fun captureError(
        error: Throwable,
        context: String,
        additionalData: Map<String, String> = emptyMap()
    ) {
        val properties = mutableMapOf<String, Any>(
            "description" to (error.message ?: error.toString()),
            "context" to context,
            "error_type" to error.javaClass.simpleName
        )
        properties.putAll(additionalData)
        
        capture(AnalyticsEvents.ERROR, properties)
    }
    
    /**
     * Track an error event with a custom error message.
     */
    fun captureError(
        errorMessage: String,
        context: String,
        additionalData: Map<String, String> = emptyMap()
    ) {
        val properties = mutableMapOf<String, Any>(
            "description" to errorMessage,
            "context" to context
        )
        properties.putAll(additionalData)
        
        capture(AnalyticsEvents.ERROR, properties)
    }
}
