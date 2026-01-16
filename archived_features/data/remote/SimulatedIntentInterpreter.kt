package com.minimalist.launcher.data.remote

import android.os.SystemClock
import android.util.Log
import com.minimalist.launcher.domain.gateway.IntentInterpreter
import com.minimalist.launcher.domain.model.IntentPrediction
import com.minimalist.launcher.domain.model.IntentType
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * SimulatedIntentInterpreter: Simulation Engine.
 * 
 * Used for Phase A verification to test:
 * 1. Architecture integration
 * 2. Caching logic
 * 3. Timeout fallback
 * 4. Safety mechanisms
 */
class SimulatedIntentInterpreter : IntentInterpreter {
    
    companion object {
        const val TAG = "SimulatedLLM"
        
        // Toggle this to test fail-open logic
        var shouldFail = false
    }

    override suspend fun classify(appPackage: String, title: String, text: String): IntentPrediction {
        Log.d(TAG, "Inferencing... [$appPackage] $title")
        
        // Simulate Inference Latency (50ms - 150ms)
        val latency = Random.nextLong(50, 150)
        delay(latency)
        
        if (shouldFail) {
            throw RuntimeException("Simulated Inference Failure")
        }

        // Hardcoded Patterns for Simulation
        val content = "$title $text".lowercase()
        
        // STATE Patterns
        if (content.contains("storage") || 
            content.contains("backup") || 
            content.contains("update") ||
            content.contains("wifi") ||
            content.contains("connected") ||
            content.contains("running in the background")) {
            return IntentPrediction(IntentType.STATE, 0.95f)
        }
        
        // TASK Patterns
        if (content.contains("bill") || 
            content.contains("due") || 
            content.contains("verify") ||
            content.contains("payment") ||
            content.contains("check in")) {
            return IntentPrediction(IntentType.TASK, 0.90f)
        }
        
        // MESSAGE Patterns
        if (content.contains("sent you") || 
            content.contains("reply") || 
            content.contains("message")) {
            return IntentPrediction(IntentType.MESSAGE, 0.85f)
        }
        
        // Default to MESSAGE with low confidence if unknown (closest to 'General')
        return IntentPrediction(IntentType.MESSAGE, 0.40f)
    }
}
