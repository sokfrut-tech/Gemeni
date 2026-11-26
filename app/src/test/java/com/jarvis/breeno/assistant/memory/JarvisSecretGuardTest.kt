package com.jarvis.breeno.assistant.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisSecretGuardTest {
    @Test
    fun blocksApiKeysTokensAndPemKeys() {
        assertTrue(JarvisSecretGuard.inspect("Мой ключ OpenAI sk-proj-abc1234567890XYZdef").blocked)
        assertTrue(JarvisSecretGuard.inspect("Gemini key AIzaSyD83kLmNoPqRsTuVwXyZ1234567").blocked)
        assertTrue(JarvisSecretGuard.inspect("GitHub ghp_1234567890abcdefghijklmnopqr").blocked)
        assertTrue(JarvisSecretGuard.inspect("Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig12345678").blocked)
        assertTrue(JarvisSecretGuard.inspect("-----BEGIN RSA PRIVATE KEY-----\nMIIE...").blocked)
    }

    @Test
    fun blocksPasswordsPinsAndLuhnValidCards() {
        assertTrue(JarvisSecretGuard.inspect("Запомни пароль от почты: MySecret#99").blocked)
        assertTrue(JarvisSecretGuard.inspect("password = hunter2xyz").blocked)
        assertTrue(JarvisSecretGuard.inspect("пин-код = 4829").blocked)
        // Standard Luhn test Visa (4111-1111-1111-1111)
        assertTrue(JarvisSecretGuard.inspect("карта 4111 1111 1111 1111").blocked)
        assertFalse(JarvisSecretGuard.inspect("артикул 4111 1111 1111 1112").blocked)
    }

    @Test
    fun blocksInvisibleCharsAndCyrillicHomoglyphObfuscation() {
        // Cyrillic 'р'/'а'/'о' mixed into "password" + zero-width space in token
        assertTrue(JarvisSecretGuard.inspect("р\u200Bassword: TopSecret777").blocked)
        assertTrue(JarvisSecretGuard.inspect("s\u200Bk-live-1234567890abcdef123456").blocked)
    }

    @Test
    fun allowsNormalUserFacts() {
        assertFalse(JarvisSecretGuard.inspect("Я живу в Алматы и люблю кофе без сахара").blocked)
        assertFalse(JarvisSecretGuard.inspect("Мой телефон OPPO Find X6 Pro с 12 ГБ ОЗУ").blocked)
        assertTrue(JarvisSecretGuard.isSafeToPersist("Встреча с врачом в пятницу в 10:30"))
    }
}
