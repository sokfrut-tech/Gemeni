package com.jarvis.breeno.assistant

import android.content.Intent
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
    fun microphoneRequestsPermissionWithoutStartingNetworkModelSetup() {
        launchJarvis()
        device.executeShellCommand("pm revoke $packageName android.permission.RECORD_AUDIO")

        val mic = device.wait(Until.findObject(By.desc("Микрофон")), 10_000)
        assertNotNull("Microphone button is missing", mic)
        mic!!.click()

        // This is a deterministic permission-flow smoke test. Granting permission
        // here would start first-run ASR downloads and make emulator CI network-dependent.
        val deny = device.wait(Until.findObject(By.textContains("Don't allow")), 5_000)
            ?: device.wait(Until.findObject(By.textContains("Не разрешать")), 5_000)
            ?: device.wait(Until.findObject(By.textContains("Deny")), 3_000)
            ?: device.wait(Until.findObject(By.textContains("Запретить")), 3_000)
        assertNotNull("Android microphone permission dialog did not appear", deny)
        deny!!.click()

        assertTrue(
            "Jarvis did not return to its main screen after microphone permission was denied",
            device.wait(Until.hasObject(By.text("ДЖАРВИС")), 5_000)
        )
    }

    @Test
    fun settingsOpensAndroidAccessibilitySettings() {
        launchJarvis()
        device.wait(Until.findObject(By.desc("Модель и настройки")), 10_000)
            ?.click()
            ?: throw AssertionError("Model/settings button is missing")
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
        dir.mkdirs()
        device.takeScreenshot(File(dir, "$name.png"))
    }
}
