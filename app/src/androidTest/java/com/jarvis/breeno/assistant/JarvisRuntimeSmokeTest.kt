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
        // Не чистим данные приложения и не останавливаем его: тест работает в том же процессе,
        // и "pm clear" убивал процесс инструментации ("Process crashed"). Запуск ниже сам
        // очищает задачу через FLAG_ACTIVITY_CLEAR_TASK.
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    // Экран по решению владельца: только шар по центру, без текста и кнопок.
    @Test
    fun launcherShowsOrbOnly() {
        launchJarvis()
        assertVisible(By.desc("JarvisSphere"), "Jarvis sphere missing")
    }

    // Нажатие на шар запускает запись. Проверяем, что шар нажимается и приложение живо
    // (на эмуляторе может появиться системный запрос разрешения на микрофон).
    @Test
    fun tappingOrbStartsListening() {
        launchJarvis()
        val orb = device.wait(Until.findObject(By.desc("JarvisSphere")), 30_000)
        assertNotNull("Jarvis sphere is missing", orb)
        orb!!.click()
        val allow = device.wait(Until.findObject(By.textContains("Allow")), 3_000)
            ?: device.wait(Until.findObject(By.textContains("Разрешить")), 3_000)
            ?: device.wait(Until.findObject(By.textContains("while using the app")), 3_000)
            ?: device.wait(Until.findObject(By.textContains("при использовании")), 3_000)
        allow?.click()
        assertTrue("App is not running after tapping the orb", device.wait(Until.hasObject(By.pkg(packageName)), 5_000))
    }

    private fun assertVisible(selector: androidx.test.uiautomator.BySelector, message: String) {
        // Эмулятор рисует сферу программно (SwiftShader), поэтому интерфейс появляется медленнее: ждём дольше.
        val visible = device.wait(Until.hasObject(selector), 30_000)
        if (!visible) {
            saveFailureScreenshot(message.replace(" ", "-"))
            logWindowHierarchy(message)
        }
        assertTrue(message, visible)
    }

    private fun launchJarvis() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        assertNotNull("Launcher activity is missing", launchIntent)
        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }

    // Пишет дерево экрана в logcat (тег JarvisTest), чтобы видеть в логе CI, что реально на экране.
    private fun logWindowHierarchy(message: String) {
        val out = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(out)
        val xml = out.toString(Charsets.UTF_8.name())
        android.util.Log.e("JarvisTest", "FAIL: $message")
        xml.chunked(3000).forEachIndexed { i, part -> android.util.Log.e("JarvisTest", "[$i] $part") }
    }

    private fun saveFailureScreenshot(name: String) {
        val dir = File("/sdcard/Download/jarvis-test")
        device.executeShellCommand("mkdir -p ${dir.absolutePath}")
        dir.mkdirs()
        device.takeScreenshot(File(dir, "$name.png"))
    }
}
