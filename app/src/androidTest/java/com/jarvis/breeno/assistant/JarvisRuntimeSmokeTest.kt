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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class JarvisRuntimeSmokeTest {
    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }
    private val packageName = "com.jarvis.breeno.assistant"

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
        val mic = device.findObject(By.desc("Микрофон"))
        assertTrue("Microphone action must have a non-empty on-screen area", mic.visibleBounds.width() > 0 && mic.visibleBounds.height() > 0)
        assertVisible(By.textContains("Runtime"), "Runtime status missing")
    }

    @Test
    fun decliningModelDownloadConsentDoesNotStartNetworkSetup() {
        // This test targets model consent, so pre-grant microphone access to
        // avoid the Android runtime permission prompt taking precedence.
        device.executeShellCommand("pm grant $packageName android.permission.RECORD_AUDIO")
        launchJarvis()
        val mic = device.wait(Until.findObject(By.desc("Микрофон")), 10_000)
        assertNotNull("Microphone button is missing", mic)
        mic!!.click()

        val consent = device.wait(Until.findObject(By.text("Загрузить голосовые модели?")), 10_000)
        assertNotNull("Explicit model-download consent dialog is missing", consent)
        assertNotNull("Consent dialog must offer a clear decline action", device.wait(Until.findObject(By.text("Не сейчас")), 5_000))
        device.findObject(By.text("Не сейчас")).click()

        assertTrue(
            "Consent dialog should close after declining",
            device.wait(Until.gone(By.text("Загрузить голосовые модели?")), 5_000)
        )
        assertTrue(
            "Declining consent should not start model network setup",
            device.wait(Until.hasObject(By.textContains("Сетевых запросов не выполнялось")), 5_000)
        )
    }

    @Test
    fun denyingMicrophonePermissionShowsActionableStatus() {
        device.executeShellCommand("pm revoke $packageName android.permission.RECORD_AUDIO")
        launchJarvis()
        val mic = device.wait(Until.findObject(By.desc("Микрофон")), 10_000)
        assertNotNull("Microphone button is missing", mic)
        mic!!.click()

        // Android permission-controller labels vary by system language and
        // image. Prefer the stable framework resource id, then localized text.
        val denyButton = device.wait(
            Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_button")),
            10_000
        ) ?: device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_and_dont_ask_again_button")), 1_000)
            ?: listOf("Не разрешать", "Запретить", "Отказать", "Don't allow", "Deny")
                .firstNotNullOfOrNull { label ->
                    device.wait(Until.findObject(By.text(label)), 1_000)
                }
        assertNotNull("Android microphone permission dialog did not offer a deny action (resource id and localized labels checked)", denyButton)
        denyButton!!.click()

        assertTrue(
            "Jarvis should explain how to recover after microphone permission is denied",
            device.wait(
                Until.hasObject(By.textContains("Доступ к микрофону не предоставлен")),
                5_000
            )
        )
        val permissionState = device.executeShellCommand("pm check-permission android.permission.RECORD_AUDIO $packageName")
        assertTrue("Microphone permission must remain denied", permissionState.trim() == "denied")
    }

    @Test
    fun runtimePrepareRejectsMissingModelWithoutDownloading() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = JarvisRuntimeController(context)
        try {
            val deniedDownload = runCatching {
                runtime.downloadVoiceModelsAfterConsent(userConsent = false) {}
            }.exceptionOrNull()
            assertNotNull("Downloader must reject a missing explicit consent flag", deniedDownload)
            assertTrue(
                "Downloader must explain that explicit user consent is required",
                deniedDownload!!.message.orEmpty().contains("явное согласие")
            )

            val failure = runCatching { runtime.prepare {} }.exceptionOrNull()
            assertNotNull("Runtime must reject preparation when ASR is not installed", failure)
            assertTrue(
                "Runtime must explain that separate model-download consent is required",
                failure!!.message.orEmpty().contains("Русская ASR-модель не установлена")
            )
            val modelFiles = File(context.filesDir, "models").walkTopDown().filter { it.isFile }.toList()
            assertTrue("prepare() and denied download must not create model files or access the network", modelFiles.isEmpty())
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        device.pressHome()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        assertNotNull("Launcher activity is missing", launchIntent)
        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.startActivitySync(launchIntent)
        instrumentation.waitForIdleSync()
    }

    private fun saveFailureScreenshot(name: String) {
        val dir = File("/sdcard/Download/jarvis-test")
        device.executeShellCommand("mkdir -p ${dir.absolutePath}")
        dir.mkdirs()
        device.takeScreenshot(File(dir, "$name.png"))
        device.dumpWindowHierarchy(File(dir, "$name.xml"))
    }
}
