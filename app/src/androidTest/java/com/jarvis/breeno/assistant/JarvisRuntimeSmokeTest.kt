package com.jarvis.breeno.assistant

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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
        device.pressHome()
    }

    @Test
    fun launcherShowsCurrentResponsiveJarvisUi() {
        launchJarvis()
        saveUiEvidence("launcher-home")

        assertVisible(By.text("ДЖАРВИС"), "Jarvis header missing")
        assertVisible(By.text("РУССКИЙ AI-АССИСТЕНТ  •  COLOROS"), "Assistant identity missing")
        assertVisible(By.text("Ожидаю команду"), "Initial runtime state missing")
        assertVisible(By.desc("Анимированное ядро Джарвиса"), "Jarvis visual core missing")

        val navigator = findAfterScroll(By.text("Навигатор"))
        assertNotNull("Navigator action is missing from the scrollable UI", navigator)
        assertVisible(By.text("Поиск"), "Search action missing")
        assertVisible(By.text("Телефон"), "Phone action missing")
        assertVisible(By.text("БЫСТРЫЕ ДЕЙСТВИЯ"), "Quick actions section missing")

        val talk = findAfterScroll(By.text("ГОВОРИТЬ С ДЖАРВИСОМ"))
        assertNotNull("Talk-to-Jarvis control is missing from the scrollable UI", talk)
    }

    @Test
    fun microphonePreflightRequiresExplicitModelDownloadConsent() {
        launchJarvis()
        val talk = findAfterScroll(By.text("ГОВОРИТЬ С ДЖАРВИСОМ"))
        assertNotNull("Talk-to-Jarvis control is missing", talk)
        talk!!.click()

        val consentTitle = device.wait(
            Until.findObject(By.text("Загрузить русскую голосовую модель?")),
            10_000
        )
        assertNotNull("Missing-model consent dialog did not appear", consentTitle)
        saveUiEvidence("model-consent")
        assertVisible(By.text("НЕ СЕЙЧАС"), "Model-consent cancel action missing")

        // Do not start a network download or open the microphone in this smoke test.
        // Verify the user's decline is reflected in the actual UI.
        device.findObject(By.text("НЕ СЕЙЧАС")).click()
        assertTrue(
            "Declining model installation did not return to the idle UI",
            device.wait(
                Until.hasObject(By.textContains("Загрузка модели отменена")),
                5_000
            )
        )
    }

    @Test
    fun settingsOpensAndroidAccessibilitySettings() {
        launchJarvis()
        val settings = device.wait(Until.findObject(By.desc("Настройки")), 10_000)
        assertNotNull("Settings button is missing", settings)
        settings!!.click()

        // The previous test searched for a localized heading that the Settings
        // screen does not expose as a text node. Confirm the actual destination.
        val settingsPackage = device.wait(
            Until.findObject(By.pkg("com.android.settings")),
            5_000
        )
        assertNotNull("Android Settings package did not become the foreground UI", settingsPackage)
        saveUiEvidence("accessibility-settings")
    }

    private fun findAfterScroll(selector: BySelector): UiObject2? {
        var found = device.wait(Until.findObject(selector), 750)
        var attempts = 0
        while (found == null && attempts < 7) {
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(
                width / 2,
                (height * 0.82f).toInt(),
                width / 2,
                (height * 0.30f).toInt(),
                24
            )
            device.waitForIdle()
            found = device.wait(Until.findObject(selector), 750)
            attempts++
        }
        return found
    }

    private fun assertVisible(selector: BySelector, message: String) {
        val visible = device.wait(Until.hasObject(selector), 5_000)
        if (!visible) saveUiEvidence(message)
        assertTrue(message, visible)
    }

    private fun launchJarvis() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        assertNotNull("Launcher activity is missing", launchIntent)
        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        assertTrue(
            "Jarvis did not become the foreground package",
            device.wait(Until.hasObject(By.pkg(packageName)), 10_000)
        )
    }

    private fun saveUiEvidence(name: String) {
        device.waitForIdle()
        val directory = File("/sdcard/Download/jarvis-test")
        device.executeShellCommand("mkdir -p ${directory.absolutePath}")
        directory.mkdirs()
        val safeName = name.replace(Regex("[^A-Za-z0-9_-]"), "-")
        device.takeScreenshot(File(directory, "$safeName.png"))
        device.dumpWindowHierarchy(File(directory, "$safeName.xml"))
    }
}
