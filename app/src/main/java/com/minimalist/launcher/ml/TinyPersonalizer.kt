package com.minimalist.launcher.ml

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.BufferedReader
import kotlin.math.exp

/**
 * TinyPersonalizer: A zero-dependency On-Device ML Engine.
 * Implements Logistic Regression with Stochastic Gradient Descent (SGD).
 */
object TinyPersonalizer {
    
    private const val WEIGHTS_FILE = "tiny_ml_weights.json"
    private const val DATA_FILE = "notification_training_data.csv"
    
    private var weights = mutableMapOf<String, Float>()
    private var bias = 0.0f
    private var isTrained = false
    
    // Hyperparameters
    private const val LEARNING_RATE = 0.1f
    private const val EPOCHS = 10
    
    fun loadModel(context: Context) {
        try {
            val file = File(context.filesDir, WEIGHTS_FILE)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            bias = json.optDouble("bias", 0.0).toFloat()
            val wJson = json.getJSONObject("weights")
            
            weights.clear()
            wJson.keys().forEach { key ->
                weights[key] = wJson.getDouble(key).toFloat()
            }
            isTrained = true
            Log.d("TinyML", "Model loaded. Bias: $bias, Weights: ${weights.size}")
        } catch (e: Exception) {
            Log.e("TinyML", "Failed to load model", e)
        }
    }
    
    fun predict(packageName: String, category: String?, hour: Int): Float {
        if (!isTrained) return 0.0f // Safe default: Don't rescue until we know what we're doing
        
        // Feature Engineering (must match training)
        var score = bias
        
        // Feature: Package (One-Hot)
        score += weights.getOrDefault("pkg_$packageName", 0.0f)
        
        // Feature: Category (One-Hot)
        val cat = category ?: "null"
        score += weights.getOrDefault("cat_$cat", 0.0f)
        
        // Feature: Time Bucket (One-Hot)
        val timeBucket = when(hour) {
            in 6..9 -> "morning"
            in 10..17 -> "work"
            in 18..22 -> "evening"
            else -> "night"
        }
        score += weights.getOrDefault("time_$timeBucket", 0.0f)
        
        return sigmoid(score)
    }
    
    fun train(context: Context) {
        Log.d("TinyML", "Starting training...")
        
        // 1. Load Data
        val data = loadData(context)
        if (data.isEmpty()) {
            Log.d("TinyML", "Not enough data to train.")
            return
        }
        
        // 2. Initialize Weights
        weights.clear()
        bias = 0.0f
        
        // 3. SGD Training Loop
        repeat(EPOCHS) { epoch ->
            var totalError = 0.0f
            data.forEach { sample ->
                val prediction = predictInternal(sample.features)
                val error = sample.label - prediction
                totalError += error * error
                
                // Update Step: w = w + (learning_rate * error * input)
                bias += LEARNING_RATE * error * 1.0f 
                
                sample.features.forEach { feature ->
                    val currentWeight = weights.getOrDefault(feature, 0.0f)
                    weights[feature] = currentWeight + (LEARNING_RATE * error * 1.0f)
                }
            }
           // Log.d("TinyML", "Epoch $epoch Error: $totalError")
        }
        
        // 4. Save Model
        saveModel(context)
        isTrained = true
        Log.d("TinyML", "Training complete. Saved weights.")
    }
    
    private fun predictInternal(features: List<String>): Float {
        var score = bias
        features.forEach { score += weights.getOrDefault(it, 0.0f) }
        return sigmoid(score)
    }
    
    private fun sigmoid(x: Float): Float {
        return 1.0f / (1.0f + exp(-x))
    }
    
    data class TrainingSample(val features: List<String>, val label: Float)
    
    private fun loadData(context: Context): List<TrainingSample> {
        val samples = mutableListOf<TrainingSample>()
        val file = File(context.filesDir, DATA_FILE)
        if (!file.exists()) return samples
        
        BufferedReader(FileReader(file)).use { reader ->
            reader.readLine() // Skip header
            reader.forEachLine { line ->
                val parts = line.split(",")
                if (parts.size >= 7) {
                    try {
                        val pkg = parts[1]
                        val cat = parts[2]
                        val hour = parts[3].toIntOrNull() ?: 12
                        val labelInt = parts[6].toIntOrNull() ?: 0
                        
                        // Label: 1.0 is Positive (Open), 0.0 is Negative (Dismiss)
                        // Map: 0->0.0, 1->1.0, 2->0.3 (Batched is weak negative)
                        val label = when(labelInt) {
                            1 -> 1.0f // Open
                            0 -> 0.0f // Dismiss
                            else -> 0.2f // Batched
                        }
                        
                        val features = mutableListOf<String>()
                        features.add("pkg_$pkg")
                        features.add("cat_$cat")
                        
                         val timeBucket = when(hour) {
                            in 6..9 -> "morning"
                            in 10..17 -> "work"
                            in 18..22 -> "evening"
                            else -> "night"
                        }
                        features.add("time_$timeBucket")
                        
                        samples.add(TrainingSample(features, label))
                    } catch (e: Exception) { }
                }
            }
        }
        return samples
    }
    
    private fun saveModel(context: Context) {
        try {
            val json = JSONObject()
            json.put("bias", bias)
            
            val wJson = JSONObject()
            weights.forEach { (k, v) -> wJson.put(k, v) }
            json.put("weights", wJson)
            
            val file = File(context.filesDir, WEIGHTS_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e("TinyML", "Failed to save model", e)
        }
    }
}
