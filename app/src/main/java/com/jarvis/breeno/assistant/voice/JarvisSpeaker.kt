package com.jarvis.breeno.assistant.voice
import android.speech.tts.TextToSpeech
import android.content.Context
import java.util.Locale
class JarvisSpeaker(context:Context){private val tts=TextToSpeech(context){ }; fun speak(text:String){tts.language=Locale("ru","RU");tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"jarvis")}}
