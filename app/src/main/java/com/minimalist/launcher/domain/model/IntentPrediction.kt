package com.minimalist.launcher.domain.model

/**
 * IntentPrediction: The output of the Intent Interpreter.
 * 
 * @param type The classified intent (MESSAGE, STATE, TASK).
 * @param confidence Advisory confidence score (0.0 - 1.0). 
 *                   NOT used for delivery control, only for logging/heuristics.
 */
data class IntentPrediction(
    val type: IntentType,
    val confidence: Float
)
