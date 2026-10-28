package com.jarvis.breeno.assistant

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class JarvisRuntimeSmokeTest {
    private lateinit var device: UiDevice
    private val packageName = "com.jarvis.breeno.assistant"

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am force-stop $packageName")
    }

    @Test
    fun launcherShowsCurrentJarvisUi() {
        launchJarvis()

        val visible = device.wait(Until.hasObject(By.text("ДЖАРВИС")), 10_000)
        if (!visible) saveFailureScreenshot("jarvis-launcher-failure")
        assertTrue("Current Jarvis UI did not appear", visible)
        assertTrue("Quick-command card missing", device.wait(Until.hasObject(By.text("Быстрые команды")), 3_000))
        assertTrue("Navigator action missing", device.wait(Until.hasObject(By.text("Навигатор")), 3_000))
        assertTrue("Search action missing", device.wait(Until.hasObject(By.text("Поиск")), 3_000))
        assertTrue("Phone action missing", device.wait(Until.hasObject(By.text("Телефон")), 3_000))
        assertTrue("Microphone action missing", device.wait(Until.hasObject(By.desc("Микрофон")), 3_000))
    }

    @Test
    fun nativeSphereViewIsAttached() {
        launchJarvis()
        val sphere = device.wait(Until.findObject(By.desc("JarvisSphere")), 10_000)
        if (sphere == null) saveFailureScreenshot("jarvis-sphere-failure")
        assertNotNull("Native GLSurfaceView is not attached to the UI", sphere)
    }

    @Test
    fun microphoneButtonChangesAssistantState() {
        launchJarvis()
        val mic = device.wait(Until.findObject(By.desc("Микрофон")), 10_000)
        assertNotNull("Microphone button is missing", mic)
        mic!!.click()
        assertTrue("Listening state text did not appear", device.wait(Until.hasObject(By.textContains("Слушаю")), 3_000))
    }

    private fun launchJarvis() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        assertNotNull("Launcher activity is missing", launchIntent)
        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }

    private fun saveFailureScreenshot(name: String) {
        val dir = File("/sdcard/Download/jarvis-test")
        device.executeShellCommand("mkdir -p ${dir.absolutePath}")
        device.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "$name.png").outputStream())
    }
}
