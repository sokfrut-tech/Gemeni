package com.jarvis.breeno.assistant.core
class GeminiCloudClient(private val apiKeyProvider:()->String={""}) { suspend fun complete(prompt:String):String = if(apiKeyProvider().isBlank()) "" else "Gemini fallback: $prompt" }
