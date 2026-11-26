package com.jarvis.breeno.assistant.memory

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class JarvisDialogueGroupingTest {
    @Test
    fun groupsSessionsIntoTodayYesterday30DaysAndEarlier() {
        val noonToday = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 26, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val oneHour = 3_600_000L
        val oneDay = 24L * oneHour
        val sessions = listOf(
            ChatSession("today", "Сегодняшний чат", noonToday - oneHour, noonToday - oneHour, 2),
            ChatSession("yesterday", "Вчерашний чат", noonToday - oneDay, noonToday - oneDay, 4),
            ChatSession("week", "Чат за месяц", noonToday - 10 * oneDay, noonToday - 10 * oneDay, 1),
            ChatSession("old", "Давний чат", noonToday - 45 * oneDay, noonToday - 45 * oneDay, 5),
        )

        val grouped = JarvisMemoryRepository.groupSessionsByBucket(sessions, nowMillis = noonToday)
        assertEquals(4, grouped.size)
        assertEquals(DialogueDateBucket.TODAY, grouped[0].first)
        assertEquals("today", grouped[0].second.single().id)
        assertEquals(DialogueDateBucket.YESTERDAY, grouped[1].first)
        assertEquals("yesterday", grouped[1].second.single().id)
        assertEquals(DialogueDateBucket.LAST_30_DAYS, grouped[2].first)
        assertEquals("week", grouped[2].second.single().id)
        assertEquals(DialogueDateBucket.EARLIER, grouped[3].first)
        assertEquals("old", grouped[3].second.single().id)
    }
}
