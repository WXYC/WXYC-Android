package org.wxyc.wxycapp

import android.app.Application
import android.util.Log

import dagger.hilt.android.HiltAndroidApp
import org.wxyc.wxycapp.analytics.AnalyticsEvents
import org.wxyc.wxycapp.analytics.PostHogManager

@HiltAndroidApp
class WXYCApplication : Application() {
    
    companion object {
        private const val TAG = "WXYCApplication"
    }
    
    override fun onCreate() {
        Log.i(TAG, "WXYCApplication onCreate: Starting application initialization")
        
        try {
            super.onCreate()
            Log.d(TAG, "WXYCApplication: Super onCreate completed")
            
            // Initialize PostHog Analytics
            initializeAnalytics()
            
            // Set up global uncaught exception handler
            setupGlobalExceptionHandler()
            
            Log.i(TAG, "WXYCApplication: Application initialization completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: WXYCApplication failed to initialize", e)
            // Let the system handle this critical failure
            throw e
        }
    }
    
    private fun initializeAnalytics() {
        try {
            // Initialize PostHog with API key from BuildConfig
            PostHogManager.initialize(
                context = this,
                apiKey = BuildConfig.POSTHOG_API_KEY
            )
            
            // Register build variant as super property
            val buildVariant = if (BuildConfig.DEBUG) "Debug" else "Release"
            PostHogManager.registerSuperProperties(
                mapOf("Build Configuration" to buildVariant)
            )
            
            // Track app launch event
            PostHogManager.capture(AnalyticsEvents.APP_LAUNCH)
            
            Log.i(TAG, "Analytics initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize analytics", e)
            // Don't throw - analytics failure shouldn't crash the app
        }
    }
    
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "=== GLOBAL UNCAUGHT EXCEPTION ===")
            Log.e(TAG, "Thread: ${thread.name}")
            Log.e(TAG, "Exception: ${exception.javaClass.simpleName}")
            Log.e(TAG, "Message: ${exception.message}")
            Log.e(TAG, "Stack trace:")
            
            // Log the full stack trace
            exception.stackTrace.forEach { element ->
                Log.e(TAG, "  at $element")
            }
            
            // Log any caused by exceptions
            var cause = exception.cause
            while (cause != null) {
                Log.e(TAG, "Caused by: ${cause.javaClass.simpleName}: ${cause.message}")
                cause.stackTrace.forEach { element ->
                    Log.e(TAG, "  at $element")
                }
                cause = cause.cause
            }
            
            Log.e(TAG, "=== END UNCAUGHT EXCEPTION ===")
            
            // Call the original handler to maintain normal crash behavior
            defaultHandler?.uncaughtException(thread, exception)
        }
        
        Log.d(TAG, "Global exception handler configured")
    }
} 