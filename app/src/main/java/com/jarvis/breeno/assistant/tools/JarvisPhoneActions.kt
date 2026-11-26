package com.jarvis.breeno.assistant.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.util.Locale
import kotlin.math.pow

enum class SettingsSection(
    val id: String,
    val titleRu: String,
    val androidAction: String,
) {
    WIFI("wifi", "Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
    BLUETOOTH("bluetooth", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
    SOUND("sound", "Звук", Settings.ACTION_SOUND_SETTINGS),
    DISPLAY("display", "Экран", Settings.ACTION_DISPLAY_SETTINGS),
    BATTERY("battery", "Батарея", Settings.ACTION_BATTERY_SAVER_SETTINGS),
    APP("app", "Настройки Jarvis Ai", Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
    MAIN("main", "Настройки устройства", Settings.ACTION_SETTINGS),
}

sealed interface PhoneActionIntent {
    data class SetTimer(val seconds: Int, val label: String) : PhoneActionIntent
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : PhoneActionIntent
    data class ToggleFlashlight(val enabled: Boolean) : PhoneActionIntent
    data class OpenSettings(val section: SettingsSection) : PhoneActionIntent
    data class OpenUrl(val url: String) : PhoneActionIntent
    data class ShareText(val text: String) : PhoneActionIntent
    data class Calculate(val expression: String, val formattedResult: String) : PhoneActionIntent
}

/**
 * Pure deterministic Android action parser + safe arithmetic parser (no reflection/eval)
 * ported from OGAM AssistantToolExecutor & adapted natively for Android intents and Russian speech.
 */
object JarvisPhoneActions {
    private val RU_LOCALE = Locale("ru", "RU")

    private val TIMER_REGEX = Regex(
        """^(?:(?:джарвис|пожалуйста|поставь|установи|заведи|засеки|включи)[,\s]+)*(?:таймер|обратный\s+отсч[её]т)\s*(?:на\s*)?(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val HOUR_PART = Regex("""(\d+)\s*(?:ч(?:ас(?:а|ов)?)?|h)\b""", RegexOption.IGNORE_CASE)
    private val MIN_PART = Regex("""(\d+)\s*(?:мин(?:ут[ауы]?)?|m)\b""", RegexOption.IGNORE_CASE)
    private val SEC_PART = Regex("""(\d+)\s*(?:сек(?:унд[ауы]?)?|s)\b""", RegexOption.IGNORE_CASE)

    private val ALARM_REGEX = Regex(
        """^(?:(?:джарвис|пожалуйста|поставь|установи|заведи)[,\s]+)*(?:будильник(?:\s+на)?|разбуди(?:\s+меня)?(?:\s+в)?)\s*(\d{1,2})\s*[:.\sч]\s*(\d{2})(?:\s+(.+))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val ALARM_HOUR_ONLY_REGEX = Regex(
        """^(?:(?:джарвис|пожалуйста|поставь|установи|заведи)[,\s]+)*(?:будильник(?:\s+на)?|разбуди(?:\s+меня)?(?:\s+в)?)\s*(\d{1,2})\s*(?:час(?:а|ов)?|утра|вечера)?(?:\s+(.+))?$""",
        RegexOption.IGNORE_CASE,
    )

    private val URL_EXTRACT_REGEX = Regex("""(https?://[^\s<>"']+|[a-zA-Z0-9.-]+\.(?:ru|com|org|net|io|ai|dev|edu|gov)(?:/[^\s<>"']*)?)""", RegexOption.IGNORE_CASE)

    private val PERCENT_OF_REGEX = Regex(
        """(\d+(?:[.,]\d+)?)\s*%\s*(?:от|of)\s*(\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    fun parseCommand(rawText: String): PhoneActionIntent? {
        val clean = rawText.trim()
        if (clean.isEmpty()) return null
        val lower = clean.lowercase(RU_LOCALE)

        // 1. Flashlight
        if (lower.contains("фонарик") || lower.contains("вспышк")) {
            val turnOff = lower.contains("выкл") || lower.contains("погас") || lower.contains("откл")
            val turnOn = lower.contains("вкл") || lower.contains("зажг") || lower == "фонарик"
            if (turnOff) return PhoneActionIntent.ToggleFlashlight(enabled = false)
            if (turnOn) return PhoneActionIntent.ToggleFlashlight(enabled = true)
        }

        // 2. Timer
        TIMER_REGEX.find(clean)?.let { m ->
            val rest = m.groupValues[1].trim()
            val h = HOUR_PART.find(rest)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val min = MIN_PART.find(rest)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val sec = SEC_PART.find(rest)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            var totalSeconds = h * 3600 + min * 60 + sec
            if (totalSeconds == 0) {
                val bareNumber = Regex("""^(\d{1,4})(?:\s|$)""").find(rest)?.groupValues?.get(1)?.toIntOrNull()
                if (bareNumber != null && bareNumber > 0) {
                    totalSeconds = bareNumber * 60
                }
            }
            if (totalSeconds in 1..86_400) {
                val strippedLabel = rest
                    .replace(HOUR_PART, "")
                    .replace(MIN_PART, "")
                    .replace(SEC_PART, "")
                    .replace(Regex("""^\d+\s*"""), "")
                    .trim()
                    .trim('-', ',', '.', ':')
                    .ifBlank { "Таймер Jarvis Ai" }
                return PhoneActionIntent.SetTimer(totalSeconds, strippedLabel.take(60))
            }
        }

        // 3. Alarm
        ALARM_REGEX.find(clean)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull()
            val minute = m.groupValues[2].toIntOrNull()
            val label = m.groupValues[3].trim().ifBlank { "Будильник Jarvis Ai" }
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                return PhoneActionIntent.SetAlarm(hour, minute, label.take(60))
            }
        }
        ALARM_HOUR_ONLY_REGEX.find(clean)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull()
            val label = m.groupValues[2].trim().ifBlank { "Будильник Jarvis Ai" }
            if (hour != null && hour in 0..23) {
                return PhoneActionIntent.SetAlarm(hour, 0, label.take(60))
            }
        }

        // 4. Safe System Settings (strict 7-section allowlist)
        if (lower.contains("настройк") &&
            !lower.contains("джарвис") &&
            !lower.contains("ии") &&
            !lower.contains("модел")
        ) {
            val section = when {
                lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("вайф") || lower.contains("вай-ф") -> SettingsSection.WIFI
                lower.contains("bluetooth") || lower.contains("блют") -> SettingsSection.BLUETOOTH
                lower.contains("звук") || lower.contains("громк") -> SettingsSection.SOUND
                lower.contains("экран") || lower.contains("ярк") || lower.contains("диспл") -> SettingsSection.DISPLAY
                lower.contains("батар") || lower.contains("энерг") || lower.contains("аккум") -> SettingsSection.BATTERY
                lower.contains("приложен") -> SettingsSection.APP
                else -> SettingsSection.MAIN
            }
            return PhoneActionIntent.OpenSettings(section)
        }

        // 5. Explicit Safe URL Launch (http/https only)
        if (lower.startsWith("открой сайт") || lower.startsWith("открой ссылк") || lower.startsWith("перейди на ") || lower.startsWith("открой url")) {
            val rawCandidate = clean.replaceFirst(
                Regex("""^(?:открой\s+(?:сайт|ссылку|url)|перейди\s+на)\s*""", RegexOption.IGNORE_CASE),
                "",
            ).trim()
            val urlMatch = URL_EXTRACT_REGEX.find(rawCandidate)?.value ?: rawCandidate
            val normalized = validateSafeHttpUrl(urlMatch)
            if (normalized != null) {
                return PhoneActionIntent.OpenUrl(normalized)
            }
        }

        // 6. Share text
        if (lower.startsWith("поделись ") || lower.startsWith("отправь текст ")) {
            val body = clean.replaceFirst(
                Regex("""^(?:поделись(?:\s+текстом)?|отправь\s+текст)\s*:?\s*""", RegexOption.IGNORE_CASE),
                "",
            ).trim()
            if (body.isNotEmpty()) {
                return PhoneActionIntent.ShareText(body)
            }
        }

        // 7. Safe Arithmetic Calculator
        val mathCandidate = extractMathCandidate(clean)
        if (mathCandidate != null) {
            val evaluated = evaluateMathExpression(mathCandidate)
            if (evaluated != null) {
                return PhoneActionIntent.Calculate(
                    expression = mathCandidate,
                    formattedResult = formatCalculationResult(evaluated),
                )
            }
        }

        return null
    }

    fun validateSafeHttpUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.isISOControl() }) return null
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("intent:") ||
            lower.startsWith("data:") ||
            lower.startsWith("tel:")
        ) {
            return null
        }
        val candidate = if (lower.startsWith("http://") || lower.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return runCatching {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host
            if ((scheme == "http" || scheme == "https") && !host.isNullOrBlank() && host.contains('.')) {
                uri.toASCIIString()
            } else {
                null
            }
        }.getOrNull()
    }

    private fun extractMathCandidate(raw: String): String? {
        PERCENT_OF_REGEX.find(raw)?.let { m ->
            val pct = m.groupValues[1].replace(',', '.')
            val base = m.groupValues[2].replace(',', '.')
            return "($pct / 100) * $base"
        }

        val stripped = raw
            .replaceFirst(
                Regex("""^(?:джарвис[,\s]*)?(?:посчитай|вычисли|реши|сколько\s+будет|калькулятор)\s*:?\s*""", RegexOption.IGNORE_CASE),
                "",
            )
            .replace("плюс", "+")
            .replace("минус", "-")
            .replace("умножить на", "*")
            .replace("разделить на", "/")
            .replace("поделить на", "/")
            .replace('×', '*')
            .replace('÷', '/')
            .replace(',', '.')
            .trim()
            .trimEnd('?', '=', ' ')

        if (stripped.isEmpty()) return null
        val hasOperator = stripped.any { it in setOf('+', '-', '*', '/', '%', '^') }
        val hasDigit = stripped.any { it.isDigit() }
        val allowedOnly = stripped.all { it.isDigit() || it in setOf('.', '+', '-', '*', '/', '%', '^', '(', ')', ' ') }
        return if (hasOperator && hasDigit && allowedOnly) stripped else null
    }

    /**
     * Pure recursive-descent arithmetic evaluator (no JavaScript/BeanShell/reflection).
     * Supports +, -, *, /, %, ^, unary +/- and nested parentheses.
     */
    fun evaluateMathExpression(expr: String): Double? {
        val normalized = expr
            .replace('×', '*')
            .replace('÷', '/')
            .replace(',', '.')
            .trim()
        if (normalized.isEmpty()) return null

        val parser = object {
            var pos = 0

            fun skipWhitespace() {
                while (pos < normalized.length && normalized[pos].isWhitespace()) pos++
            }

            fun parseExpression(): Double {
                var value = parseTerm()
                while (true) {
                    skipWhitespace()
                    if (pos < normalized.length && (normalized[pos] == '+' || normalized[pos] == '-')) {
                        val op = normalized[pos++]
                        val rhs = parseTerm()
                        value = if (op == '+') value + rhs else value - rhs
                    } else {
                        return value
                    }
                }
            }

            fun parseTerm(): Double {
                var value = parsePower()
                while (true) {
                    skipWhitespace()
                    if (pos < normalized.length && (normalized[pos] == '*' || normalized[pos] == '/' || normalized[pos] == '%')) {
                        val op = normalized[pos++]
                        val rhs = parsePower()
                        when (op) {
                            '*' -> value *= rhs
                            '/' -> {
                                require(rhs != 0.0) { "Деление на ноль" }
                                value /= rhs
                            }
                            '%' -> {
                                require(rhs != 0.0) { "Деление по модулю на ноль" }
                                value %= rhs
                            }
                        }
                    } else {
                        return value
                    }
                }
            }

            fun parsePower(): Double {
                val base = parseUnary()
                skipWhitespace()
                return if (pos < normalized.length && normalized[pos] == '^') {
                    pos++
                    val exp = parsePower()
                    base.pow(exp)
                } else {
                    base
                }
            }

            fun parseUnary(): Double {
                skipWhitespace()
                if (pos < normalized.length && normalized[pos] == '+') {
                    pos++
                    return parseUnary()
                }
                if (pos < normalized.length && normalized[pos] == '-') {
                    pos++
                    return -parseUnary()
                }
                return parsePrimary()
            }

            fun parsePrimary(): Double {
                skipWhitespace()
                require(pos < normalized.length) { "Неожиданный конец выражения" }
                if (normalized[pos] == '(') {
                    pos++
                    val inner = parseExpression()
                    skipWhitespace()
                    require(pos < normalized.length && normalized[pos] == ')') { "Незакрытая скобка" }
                    pos++
                    return inner
                }
                val start = pos
                var dots = 0
                while (pos < normalized.length && (normalized[pos].isDigit() || normalized[pos] == '.')) {
                    if (normalized[pos] == '.') {
                        dots++
                        require(dots <= 1) { "Некорректное число" }
                    }
                    pos++
                }
                require(pos > start) { "Ожидалось число" }
                return normalized.substring(start, pos).toDouble()
            }
        }

        return runCatching {
            val result = parser.parseExpression()
            parser.skipWhitespace()
            if (parser.pos != normalized.length || result.isNaN() || result.isInfinite()) null else result
        }.getOrNull()
    }

    fun formatCalculationResult(value: Double): String {
        val bd = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }

    fun formatDurationRu(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add("$h ч")
            if (m > 0) add("$m мин")
            if (s > 0 || isEmpty()) add("$s с")
        }.joinToString(" ")
    }
}

class JarvisPhoneActionExecutor(private val context: Context) {
    private val appContext = context.applicationContext

    fun execute(action: PhoneActionIntent): Result<String> = runCatching {
        when (action) {
            is PhoneActionIntent.Calculate -> {
                "Результат: ${action.expression} = ${action.formattedResult}"
            }

            is PhoneActionIntent.SetTimer -> {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                "Открываю таймер на ${JarvisPhoneActions.formatDurationRu(action.seconds)} (${action.label})."
            }

            is PhoneActionIntent.SetAlarm -> {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                val hh = "%02d".format(action.hour)
                val mm = "%02d".format(action.minute)
                "Открываю будильник на $hh:$mm (${action.label})."
            }

            is PhoneActionIntent.ToggleFlashlight -> {
                val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    ?: error("Сервис камеры недоступен на этом устройстве")
                val cameraId = manager.cameraIdList.firstOrNull { id ->
                    val chars = manager.getCameraCharacteristics(id)
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                    hasFlash && (lensFacing == CameraCharacteristics.LENS_FACING_BACK || manager.cameraIdList.size == 1)
                } ?: error("Вспышка не найдена на устройстве")
                manager.setTorchMode(cameraId, action.enabled)
                if (action.enabled) "Фонарик включён." else "Фонарик выключен."
            }

            is PhoneActionIntent.OpenSettings -> {
                val intent = Intent(action.section.androidAction).apply {
                    if (action.section == SettingsSection.APP) {
                        data = Uri.fromParts("package", appContext.packageName, null)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                "Открываю раздел настроек «${action.section.titleRu}»."
            }

            is PhoneActionIntent.OpenUrl -> {
                val safe = JarvisPhoneActions.validateSafeHttpUrl(action.url)
                    ?: error("Разрешены только безопасные ссылки http:// и https://")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safe)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                "Открываю сайт: $safe"
            }

            is PhoneActionIntent.ShareText -> {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, action.text)
                }
                val chooser = Intent.createChooser(send, "Поделиться через").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(chooser)
                "Открываю меню отправки текста."
            }
        }
    }
}
