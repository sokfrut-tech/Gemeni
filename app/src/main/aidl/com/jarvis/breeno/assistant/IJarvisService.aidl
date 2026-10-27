package com.jarvis.breeno.assistant;
import com.jarvis.breeno.assistant.IJarvisCallback;
interface IJarvisService { void submitText(String text); void startListening(); void stopListening(); void registerCallback(IJarvisCallback callback); void unregisterCallback(IJarvisCallback callback); }
