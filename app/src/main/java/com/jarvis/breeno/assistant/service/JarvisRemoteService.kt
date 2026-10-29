package com.jarvis.breeno.assistant.service
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.jarvis.breeno.assistant.IJarvisCallback
import com.jarvis.breeno.assistant.IJarvisService
class JarvisRemoteService:Service(){ private val callbacks=mutableSetOf<IJarvisCallback>();private val router by lazy{com.jarvis.breeno.assistant.core.TextCommandRouter(this)}; private val binder=object:IJarvisService.Stub(){override fun submitText(text:String){val reply=router.handle(text);callbacks.forEach{it.onText(reply)}} override fun startListening(){} override fun stopListening(){} override fun registerCallback(c:IJarvisCallback){callbacks.add(c)} override fun unregisterCallback(c:IJarvisCallback){callbacks.remove(c)}}; override fun onBind(i:Intent?):IBinder=binder }
