package com.jarvis.breeno.assistant.core

/** Pure Russian command parser; deliberately contains no Android side effects. */
sealed interface JarvisCommand {
    data class OpenApp(val name: String) : JarvisCommand
    data class Search(val query: String) : JarvisCommand
    data class Navigate(val destination: String) : JarvisCommand
    data class Dial(val number: String) : JarvisCommand
    data object TellTime : JarvisCommand
    data class Unsupported(val original: String) : JarvisCommand
    data object Empty : JarvisCommand
}

object JarvisCommandParser {
    fun parse(rawText: String): JarvisCommand {
        val text = rawText.trim()
        if (text.isEmpty()) return JarvisCommand.Empty

        Regex("^(?:джарвис[, ]*)?(?:открой|запусти)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return JarvisCommand.OpenApp(it.groupValues[1].trim()) }

        Regex("^(?:джарвис[, ]*)?(?:найди|поищи|поиск)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return JarvisCommand.Search(it.groupValues[1].trim()) }

        Regex("^(?:джарвис[, ]*)?(?:построй маршрут|маршрут|навигатор)\\s*(?:до|в)?\\s*(.*)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return JarvisCommand.Navigate(it.groupValues[1].trim()) }

        Regex("^(?:джарвис[, ]*)?(?:позвони|набери)\\s*(.*)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return JarvisCommand.Dial(it.groupValues[1].trim()) }

        if (text.contains("который час", true) || text.contains("сколько времени", true)) {
            return JarvisCommand.TellTime
        }
        return JarvisCommand.Unsupported(text)
    }
}
