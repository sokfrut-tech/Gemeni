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
    fun decliningModelDownloadConsentDoesNotStartDownload() {
        launchJarvis()
        val mic = device.wait(Until.findObject(By.desc("Микрофон")), 10_000)
        assertNotNull("Microphone button is missing", mic)
        mic!!.click()

        val consent = device.wait(Until.findObject(By.text("Загрузить русскую модель?")), 10_000)
        assertNotNull("Explicit model-download consent dialog is missing", consent)
        device.wait(Until.findObject(By.text("Не сейчас")), 5_000)!!.click()

        assertTrue(
            "Consent dialog should close without starting a model download",
            device.wait(Until.gone(By.text("Загрузить русскую модель?")), 5_000)
        )
        assertTrue(
            "Declining consent should show a no-download status",
            device.wait(Until.hasObject(By.textContains("Сетевых запросов не выполнялось")), 5_000)
        )
    }

    @Test
    fun runtimePrepareNeverDownloadsModelWithoutPriorConsent() = runBlocking {
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
            assertTrue("No model files may be created by prepare() without consent", modelFiles.isEmpty())
        } finally {
            runtime.close()
        }
    }

    @Test
    fun microphoneRequestsPermissionAndEntersRuntimeState() {
        launchJarvis()
        val mic = device.wait(Until.findObject(By.desc("Микрофон")), 10_000)
        assertNotNull("Microphone button is missing", mic)
        mic!!.click()

        val consent = device.wait(Until.findObject(By.text("Загрузить русскую модель?")), 10_000)
        assertNotNull("Model download must require explicit consent before microphone permission", consent)
        device.wait(Until.findObject(By.text("Разрешить загрузку")), 5_000)!!.click()

        // Android microphone permission is requested only after the model has been
        // explicitly approved and successfully installed.
        val allow = device.wait(Until.findObject(By.textContains("Allow")), 180_000)
            ?: device.wait(Until.findObject(By.textContains("Разрешить")), 3_000)
            ?: device.wait(Until.findObject(By.textContains("while using the app")), 3_000)
            ?: device.wait(Until.findObject(By.textContains("при использовании")), 3_000)
        if (allow != null) allow.click()

        val listening = device.wait(
            Until.hasObject(By.textContains("Микрофон активен")),
            180_000
        )
        assertTrue("Microphone permission/ASR preparation did not reach active listening", listening)
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
        dir.mkdirs()
        device.takeScreenshot(File(dir, "$name.png"))
    }
}
