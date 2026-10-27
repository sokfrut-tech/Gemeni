package com.jarvis.breeno.assistant.updater
import android.content.Context
class ModelDownloadManager(private val context:Context){fun modelDir()=context.filesDir.resolve("models").also{it.mkdirs()}}
