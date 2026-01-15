package com.minimalist.launcher.data.remote

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import com.google.mediapipe.tasks.core.BaseOptions
import com.minimalist.launcher.domain.gateway.IntentInterpreter
import com.minimalist.launcher.domain.model.IntentPrediction
import com.minimalist.launcher.domain.model.IntentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaPipeIntentInterpreter: Dual-Engine Real Inference.
 * 
 * Supports:
 * 1. Generative LLM (GenAI): Best for zero-shot reasoning (e.g., Gemma 2B).
 * 2. Text Classifier (BERT): Best for size (<100MB).
 * 
 * Logic:
 * - Checks specifically for `bert_classifier.tflite` first (Fastest/Smallest).
 * - Checks for `model.bin` (LLM) second.
 * - Falls back to Simulation if neither exists.
 */
class MediaPipeIntentInterpreter(private val context: Context) : IntentInterpreter {

    companion object {
        private const val TAG = "MediaPipeIntent"
        
        // Paths (Accessible via adb push /data/local/tmp/llm/)
        private const val MODEL_DIR = "/data/local/tmp/llm/"
        private const val CLASSIFIER_FILE = "bert_classifier.tflite"
        private const val LLM_FILE = "model.bin" // Gemma/Phi-3
        
        // Sim Fallback
        private val simDelegate = SimulatedIntentInterpreter()
    }

    private var llmInference: LlmInference? = null
    private var textClassifier: TextClassifier? = null
    
    private val isInitialized = AtomicBoolean(false)
    private var useAhocSimulation = false

    init {
        // Lazy init on first use or background
        // We do it in classify() mostly, or we can trigger it now if we want to warm up.
        // Let's keep it lazy to improve startup time.
    }
    
    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    override suspend fun classify(appPackage: String, title: String, text: String): IntentPrediction {
        ensureInitialized()
        
        if (useAhocSimulation) {
            Log.w(TAG, "No model found. Using Simulation.")
            return simDelegate.classify(appPackage, title, text)
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // 1. Try BERT Classifier
                textClassifier?.let { classifier ->
                    // Format generic input
                    val input = "$title $text" 
                    val results = classifier.classify(input)
                    
                    // Map result (Assuming label "0" -> MESSAGE, "1" -> STATE, "2" -> TASK)
                    // Or string labels if model has metadata.
                    // For broad safety, let's assume specific string labels.
                    
                    val topResult = results.classificationResult().classifications().firstOrNull()?.categories()?.maxByOrNull { it.score() }
                    
                    if (topResult != null) {
                        val type = parseIntentType(topResult.categoryName())
                        return@withContext IntentPrediction(type, topResult.score())
                    }
                }
                
                // 2. Try Generative LLM
                llmInference?.let { llm ->
                    val prompt = buildPrompt(title, text)
                    val response = llm.generateResponse(prompt)
                    
                    // Parse LLM Output
                    val type = parseIntentType(response)
                    // LLM doesn't give confidence score easily in this API usually.
                    return@withContext IntentPrediction(type, 1.0f)
                }
                
                // Fallback (Shouldn't happen if init set correct flags)
                simDelegate.classify(appPackage, title, text)
                
            } catch (e: Exception) {
                Log.e(TAG, "Inference Limit/Crash: ${e.message}")
                throw e
            }
        }
    }

    // ------------------------------------------------------------------------
    // Initialization Logic
    // ------------------------------------------------------------------------

    private suspend fun ensureInitialized() {
        if (isInitialized.get()) return
        
        withContext(Dispatchers.IO) {
            synchronized(this) {
                if (isInitialized.get()) return@synchronized
                
                val classifierFile = File(MODEL_DIR, CLASSIFIER_FILE)
                val llmFile = File(MODEL_DIR, LLM_FILE)
                
                if (classifierFile.exists()) {
                    Log.d(TAG, "Found BERT Classifier: ${classifierFile.path}")
                    initializeClassifier(classifierFile)
                } else if (llmFile.exists()) {
                    Log.d(TAG, "Found Generative LLM: ${llmFile.path}")
                    initializeLlm(llmFile)
                } else {
                    Log.w(TAG, "No models found in $MODEL_DIR. Fallback to Simulation.")
                    useAhocSimulation = true
                }
                
                isInitialized.set(true)
            }
        }
    }
    
    private fun initializeClassifier(file: File) {
        try {
            val options = TextClassifier.TextClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(file.path).build())
                .build()
            textClassifier = TextClassifier.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load BERT", e)
            useAhocSimulation = true
        }
    }
    
    private fun initializeLlm(file: File) {
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.path)
                .setMaxTokens(10) // We only need one word
                .setTopK(1)       // Deterministic
                .setTemperature(0.0f)
                .setRandomSeed(42)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LLM", e)
            useAhocSimulation = true
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private fun buildPrompt(title: String, text: String): String {
        // Template for simple 4-bit models (Phi-3, Gemma)
        return """
            Classify intent: MESSAGE, STATE, or TASK.
            
            Title: "$title"
            Text: "$text"
            
            Intent:
        """.trimIndent()
    }
    
    private fun parseIntentType(raw: String): IntentType {
        val normalized = raw.trim().uppercase()
        Log.d(TAG, "Model Predicted Raw: '$normalized'") // Debugging
        
        return when {
            normalized.contains("MESSAGE") -> IntentType.MESSAGE
            normalized.contains("TASK") -> IntentType.TASK
            normalized.contains("STATE") -> IntentType.STATE
            normalized.contains("PROMO") -> IntentType.PROMO
            normalized.contains("OFFER") -> IntentType.PROMO
            normalized.contains("SOCIAL") -> IntentType.SOCIAL
            normalized.contains("SPAM") -> IntentType.PROMO // Treat spam as promo/unimportant
            else -> {
                Log.w(TAG, "Unknown label '$normalized', defaulting to MESSAGE (Important)")
                IntentType.MESSAGE 
            }
        }
    }
}
