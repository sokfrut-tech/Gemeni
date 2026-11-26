package com.jarvis.breeno.assistant.memory

import java.text.Normalizer

data class SecretCheckResult(
    val blocked: Boolean,
    val reason: String? = null,
)

/**
 * Fail-closed secret & credential guard before writing facts or exporting memory.
 * Normalizes NFKC, invisible zero-width chars, and common Cyrillic/Greek confusable homoglyphs
 * so obfuscated secrets cannot slip into persistent memory.
 */
object JarvisSecretGuard {
    private val INVISIBLE_CHARS = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF\\u00AD]")

    private val CONFUSABLE_MAP = mapOf(
        'а' to 'a', 'А' to 'A',
        'в' to 'b', 'В' to 'B',
        'е' to 'e', 'Е' to 'E',
        'ё' to 'e', 'Ё' to 'E',
        'к' to 'k', 'К' to 'K',
        'м' to 'm', 'М' to 'M',
        'н' to 'h', 'Н' to 'H',
        'о' to 'o', 'О' to 'O',
        'р' to 'p', 'Р' to 'P',
        'с' to 'c', 'С' to 'C',
        'т' to 't', 'Т' to 'T',
        'у' to 'y', 'У' to 'Y',
        'х' to 'x', 'Х' to 'X',
        'і' to 'i', 'І' to 'I',
    )

    private val API_KEY_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("""\bsk-(?:proj-|ant-|live-|test-)?[A-Za-z0-9_-]{16,}\b""") to "API-ключ (sk-...)",
        Regex("""\bAIza[0-9A-Za-z_-]{20,}\b""") to "ключ Google/Gemini (AIza...)",
        Regex("""\bgh[pousr]_[A-Za-z0-9]{20,}\b""") to "токен GitHub",
        Regex("""\bgithub_pat_[A-Za-z0-9_]{20,}\b""") to "персональный токен GitHub",
        Regex("""\bAKIA[0-9A-Z]{16}\b""") to "ключ AWS (AKIA...)",
        Regex("""\bxox[baprs]-[A-Za-z0-9-]{12,}\b""") to "токен Slack",
        Regex("""-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----""") to "закрытый приватный ключ (PEM)",
        Regex("""\bBearer\s+[A-Za-z0-9._~+/=-]{16,}\b""", RegexOption.IGNORE_CASE) to "токен авторизации Bearer",
        Regex("""\beyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b""") to "JWT-токен",
    )

    private val PASSWORD_ASSIGNMENT_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("""(?:парол[ья]|parol)\s*(?:от\s+\S+\s*)?(?:[:=\-–—]|это)\s*\S{3,}""", RegexOption.IGNORE_CASE) to "пароль",
        Regex("""\bpassword\b\s*(?:[:=\-–—]|is)\s*\S{3,}""", RegexOption.IGNORE_CASE) to "пароль (password)",
        Regex("""\b(?:pin|пин(?:-код)?)\b\s*(?:[:=\-–—]|это)\s*\d{4,12}\b""", RegexOption.IGNORE_CASE) to "PIN-код",
        Regex("""\b(?:api[_\s-]?key|secret[_\s-]?key|access[_\s-]?token|секретн(?:ый|ого)\s+ключ)\b\s*[:=\-–—]\s*\S{6,}""", RegexOption.IGNORE_CASE) to "секретный ключ",
        Regex("""(?:сид[-\s]?фраз|seed\s*phrase|мнемоник[аи])\s*[:=\-–—]\s*(?:\S+\s+){3,}\S+""", RegexOption.IGNORE_CASE) to "сид-фраза кошелька",
    )

    private val CARD_CANDIDATE_REGEX = Regex("""(?<!\d)(?:\d[ -]?){13,19}(?!\d)""")

    fun normalizeForInspection(input: String): String {
        val nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC)
        return INVISIBLE_CHARS.replace(nfkc, "")
    }

    private fun foldConfusablesForLatins(normalized: String): String {
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) {
            sb.append(CONFUSABLE_MAP[ch] ?: ch)
        }
        return sb.toString()
    }

    fun inspect(text: String): SecretCheckResult {
        if (text.isBlank()) return SecretCheckResult(blocked = false)
        val normalized = normalizeForInspection(text)
        val folded = foldConfusablesForLatins(normalized)

        for ((regex, label) in API_KEY_PATTERNS) {
            if (regex.containsMatchIn(normalized) || regex.containsMatchIn(folded)) {
                return SecretCheckResult(
                    blocked = true,
                    reason = "Обнаружен $label. SecretGuard запрещает сохранять ключи и токены в долговременную память.",
                )
            }
        }

        for ((regex, label) in PASSWORD_ASSIGNMENT_PATTERNS) {
            if (regex.containsMatchIn(normalized) || regex.containsMatchIn(folded)) {
                return SecretCheckResult(
                    blocked = true,
                    reason = "Обнаружен $label. SecretGuard не сохраняет пароли, PIN-коды и сид-фразы.",
                )
            }
        }

        val hasValidCard = CARD_CANDIDATE_REGEX.findAll(normalized).any { match ->
            val digits = match.value.filter { it.isDigit() }
            digits.length in 13..19 && passesLuhnCheck(digits)
        }
        if (hasValidCard) {
            return SecretCheckResult(
                blocked = true,
                reason = "Обнаружен номер банковской карты. SecretGuard блокирует сохранение платёжных данных.",
            )
        }

        return SecretCheckResult(blocked = false)
    }

    fun isSafeToPersist(text: String): Boolean = !inspect(text).blocked

    internal fun passesLuhnCheck(digits: String): Boolean {
        if (digits.length !in 13..19 || digits.all { it == digits[0] }) return false
        var sum = 0
        var doubleIt = false
        for (i in digits.length - 1 downTo 0) {
            val d = digits[i] - '0'
            if (d !in 0..9) return false
            var value = d
            if (doubleIt) {
                value *= 2
                if (value > 9) value -= 9
            }
            sum += value
            doubleIt = !doubleIt
        }
        return sum % 10 == 0
    }
}
