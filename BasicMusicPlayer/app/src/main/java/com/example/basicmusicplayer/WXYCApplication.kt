package com.example.basicmusicplayer

import android.app.Application
import android.util.Log

class WXYCApplication : Application() {
    
    companion object {
        private const val TAG = "WXYCApplication"
    }
    
    override fun onCreate() {
        Log.i(TAG, "WXYCApplication onCreate: Starting application initialization")
        
        try {
            super.onCreate()
            Log.d(TAG, "WXYCApplication: Super onCreate completed")
            
            // Set up global uncaught exception handler
            setupGlobalExceptionHandler()
            
            Log.i(TAG, "WXYCApplication: Application initialization completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: WXYCApplication failed to initialize", e)
            // Let the system handle this critical failure
            throw e
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