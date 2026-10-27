package com.jarvis.breeno.assistant.memory
import android.content.Context
class JarvisMemoryRepository(context:Context){ private val p=context.getSharedPreferences("jarvis_memory",Context.MODE_PRIVATE); fun put(k:String,v:String)=p.edit().putString(k,v).apply(); fun get(k:String)=p.getString(k,null) }
