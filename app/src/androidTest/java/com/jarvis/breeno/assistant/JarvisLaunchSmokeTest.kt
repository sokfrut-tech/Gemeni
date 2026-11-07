package com.jarvis.breeno.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jarvis.breeno.assistant.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisLaunchSmokeTest {
    @Test
    fun mainScreenLaunchesAndShowsReadyState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        instrumentation.targetContext.packageManager
            .getLaunchIntentForPackage(instrumentation.targetContext.packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?.let { instrumentation.startActivitySync(it) }

        instrumentation.waitForIdleSync()

        val readyStateVisible = device.wait(
            Until.hasObject(By.textContains("Готов.")),
            15_000L
        )
        assertTrue("Jarvis main screen did not show its ready state", readyStateVisible)
    }
}
