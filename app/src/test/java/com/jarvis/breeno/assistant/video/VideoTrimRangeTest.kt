package com.jarvis.breeno.assistant.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoTrimRangeTest {
    @Test
    fun clampsStartAndEndToSourceDuration() {
        assertEquals(VideoTrimRange(0L, 500L), VideoTrimRange.create(-100L, 500L, 10_000L))
        assertEquals(VideoTrimRange(9_500L, 10_000L), VideoTrimRange.create(9_900L, 12_000L, 10_000L))
    }

    @Test
    fun enforcesMinimumHalfSecondSelection() {
        assertEquals(VideoTrimRange(2_000L, 2_500L), VideoTrimRange.create(2_000L, 2_000L, 8_000L))
        assertNull(VideoTrimRange.create(0L, 500L, 499L))
    }

    @Test
    fun keepsValidSelectionAndDuration() {
        val selection = VideoTrimRange.create(1_250L, 4_750L, 10_000L)!!
        assertEquals(1_250L, selection.startMs)
        assertEquals(4_750L, selection.endMs)
        assertEquals(3_500L, selection.durationMs)
    }
}
