package com.jarvis.breeno.assistant

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jarvis.breeno.assistant.ui.JarvisRuntimeController
import kotlinx.coroutines.runBlocking
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
    fun decliningModelDownloadConsentDoesNotStartNetworkSetup() {
        launchJarvis()
        val mic = device.wait(Until.findObject(By.desc("Микрофон")), 10_000)
        assertNotNull("Microphone button is missing", mic)
        mic!!.click()

        val consent = device.wait(Until.findObject(By.text("Загрузить русскую модель?")), 10_000)
        assertNotNull("Explicit model-download consent dialog is missing", consent)
        assertNotNull("Consent dialog must offer a clear decline action", device.wait(Until.findObject(By.text("Не сейчас")), 5_000))
        device.findObject(By.text("Не сейчас")).click()

        assertTrue(
            "Consent dialog should close after declining",
            device.wait(Until.gone(By.text("Загрузить русскую модель?")), 5_000)
        )
        assertTrue(
            "Declining consent should not start model network setup",
            device.wait(Until.hasObject(By.textContains("Сетевых запросов не выполнялось")), 5_000)
        )
    }

    @Test
    fun runtimePrepareRejectsMissingModelWithoutDownloading() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = JarvisRuntimeController(context)
        try {
            val failure = runCatching { runtime.prepare {} }.exceptionOrNull()
            assertNotNull("Runtime must reject preparation when ASR is not installed", failure)
            assertTrue(
                "Runtime must explain that separate model-download consent is required",
                failure!!.message.orEmpty().contains("отдельно подтвердите")
            )
            val modelFiles = File(context.filesDir, "models").walkTopDown().filter { it.isFile }.toList()
            assertTrue("prepare() must not create model files or download them without consent", modelFiles.isEmpty())
        } finally {
            runtime.close()
        }
    }

    @Test
    fun settingsOpensLocalModelPanel() {
        launchJarvis()
        val settings = device.wait(Until.findObject(By.desc("Модель и настройки")), 10_000)
        assertNotNull("Model/settings button is missing", settings)
        settings!!.click()
        assertTrue(
            "Local intelligence model panel did not open",
            device.wait(Until.hasObject(By.text("Локальный интеллект Джарвиса")), 5_000)
        )
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
