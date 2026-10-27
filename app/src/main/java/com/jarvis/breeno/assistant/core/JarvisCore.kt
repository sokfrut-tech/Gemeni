package com.jarvis.breeno.assistant.core
import android.content.Context
class JarvisCore(private val context:Context){val tools=JarvisToolRegistry();suspend fun handle(text:String)=tools.dispatch(text)}
