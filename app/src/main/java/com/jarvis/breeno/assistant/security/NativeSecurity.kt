package com.jarvis.breeno.assistant.security
object NativeSecurity { init { try { System.loadLibrary("jarvis_native") } catch (_:Throwable) {} } external fun sha256(data:ByteArray):ByteArray }
