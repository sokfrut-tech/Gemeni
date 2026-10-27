package com.jarvis.breeno.assistant.core
class JarvisToolRegistry{suspend fun dispatch(text:String):String=when{ text.contains("время",true)->java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date());else->"Принял: $text"}}
