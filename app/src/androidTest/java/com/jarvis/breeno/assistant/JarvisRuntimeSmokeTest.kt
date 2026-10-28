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
        device.executeShellCommand("pm clear $packageName")
        device.executeShellCommand("am force-stop $packageName")
    }

    @Test
    fun launcherShowsFunctionalJarvisUi() {
        launchJarvis()
        assertVisible(By.text("ДЖАРВИС"), "Jarvis header missing")
        assertVisible(By.text("Рабочие команды"), "Functional command card missing")
        assertVisible(By.text("Навигатор"), "Navigator action missing")
        assertVisible(By.text("Поиск"), "Search action missing")
        assertVisible(By.text("Телефон"), "Phone action missing")
        assertVisible(By.desc("Микрофон"), "Microphone action missing")
        assertVisible(By.desc("JarvisSphere"), "Native sphere missing")
        assertVisible(By.textContains("Runtime"), "Runtime status missing")
    }

    @Test
    fun microphoneRequestsPermissionAndEntersRuntimeState() {
        launchJarvis()
        val mic = device.wait(Until.findObject(By.desc("Микрофон")), 10_000)
        assertNotNull("Microphone button is missing", mic)
        mic!!.click()
        // On the emulator this may first show the Android permission dialog.
        val allow = device.wait(Until.findObject(By.textMatches("(?i)(while using the app|only this time|разрешить|при использовании)")), 3_000)
        if (allow != null) allow.click()
        val state = device.wait(Until.hasObject(By.textContains("Микрофон активен")), 5_000) ||
            device.wait(Until.hasObject(By.textContains("ASR")), 5_000) ||
            device.wait(Until.hasObject(By.textContains("Ошибка runtime")), 5_000)
        assertTrue("Microphone action produced no runtime state", state)
    }

    @Test
    fun settingsOpensAndroidAccessibilitySettings() {
        launchJarvis()
        device.wait(Until.findObject(By.desc("Настройки")), 10_000).click()
        assertTrue("Accessibility settings did not open", device.wait(Until.hasObject(By.textContains("Accessibility")), 5_000) || device.wait(Until.hasObject(By.textContains("Специальные возможности")), 5_000))
    }

    private fun assertVisible(selector: androidx.test.uiautomator.BySelector, message: String) {
        val visible = device.wait(Until.hasObject(selector), 10_000)
        if (!visible) saveFailureScreenshot(message.replace(" ", "-"))
        assertTrue(message, visible)
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
