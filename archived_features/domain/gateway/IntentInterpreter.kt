package com.minimalist.launcher.domain.gateway

import com.minimalist.launcher.domain.model.IntentPrediction

/**
 * IntentInterpreter: Gateway to the Local LLM (or Simulation).
 * 
 * CONTRACT:
 * 1. No side effects.
 * 2. No blocking I/O on the caller thread.
 * 3. Deterministic output (for the same input).
 */
interface IntentInterpreter {
    
    /**
     * Classify the intent of a notification.
     * 
     * @param appPackage The package name of the source app.
     * @param title The notification title.
     * @param text The notification text.
     * @return IntentPrediction containing the type and confidence.
     */
    suspend fun classify(
        appPackage: String, 
        title: String, 
        text: String
    ): IntentPrediction
}
