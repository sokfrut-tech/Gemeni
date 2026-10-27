package com.jarvis.breeno.assistant
import android.app.Application
import com.jarvis.breeno.assistant.core.JarvisCore
class JarvisApp: Application(){ lateinit var core:JarvisCore; override fun onCreate(){super.onCreate();core=JarvisCore(this)} }
