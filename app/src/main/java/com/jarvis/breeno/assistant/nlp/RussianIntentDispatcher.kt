package com.jarvis.breeno.assistant.nlp
class RussianIntentDispatcher { fun dispatch(text:String):String = when { text.contains("найди",true)||text.contains("поиск",true)->"SEARCH:$text"; text.contains("открой",true)->"OPEN:$text"; else->"CHAT:$text" } }
