package com.jarvis.breeno.assistant

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class JarvisLaunchSmokeTest {
    @Test
    fun mainScreenLaunchesAndShowsReadyState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val launchIntent = instrumentation.targetContext.packageManager
            .getLaunchIntentForPackage(instrumentation.targetContext.packageName)

        assertNotNull("Jarvis launcher activity is missing", launchIntent)
        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            instrumentation.startActivitySync(launchIntent)
            instrumentation.waitForIdleSync()

            val headerVisible = device.wait(
                Until.hasObject(By.text("ДЖАРВИС")),
                30_000L
            )
            assertTrue("Jarvis main screen did not appear", headerVisible)

            val readyStateVisible = device.wait(
                Until.hasObject(By.text("Я рядом")),
                15_000L
            )
            if (!readyStateVisible) {
                val screenshotDir = File("/sdcard/Download/jarvis-test")
                device.executeShellCommand("mkdir -p ${screenshotDir.absolutePath}")
                device.takeScreenshot(File(screenshotDir, "launch-ready-state-missing.png"))
            }
            assertTrue("Jarvis did not show its idle ready state", readyStateVisible)
        } finally {
            // Close the launched Activity before the next instrumentation test.
            device.pressBack()
            instrumentation.waitForIdleSync()
        }
    }
}
