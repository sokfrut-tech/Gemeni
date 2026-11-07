package com.jarvis.breeno.assistant.core

/** Minimal provider-facing contract; implementations must never execute Android tools. */
interface JarvisTextGenerationRuntime {
    val isModelLoaded: Boolean
    fun generateForPlanning(prompt: String, maxTokens: Int): String
}
