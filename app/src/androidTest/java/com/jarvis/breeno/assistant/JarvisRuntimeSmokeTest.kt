package com.jarvis.breeno.assistant

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.jarvis.breeno.assistant.core.LlamaNativeBridge
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
    fun launcherShowsCoreFirstJarvisUi() {
        launchJarvis()
        saveUiEvidence("launcher-home")

        assertVisible(By.desc("Интерактивное энергетическое ядро Джарвиса"), "Interactive Jarvis core missing")
        assertVisible(By.text("Я РЯДОМ"), "Idle assistant state missing")
        assertVisible(By.text("КОСНИТЕСЬ ЯДРА, ЧТОБЫ ГОВОРИТЬ"), "Core activation hint missing")
        assertTrue("Legacy oversized quick-action section should be removed",
            device.wait(Until.hasObject(By.text("БЫСТРЫЕ ДЕЙСТВИЯ")), 500).not())
    }

    @Test
    fun tappingCoreShowsExplicitModelDownloadConsent() {
        launchJarvis()
        val core = device.wait(
            Until.findObject(By.desc("Интерактивное энергетическое ядро Джарвиса")),
            10_000
        )
        assertNotNull("Interactive Jarvis core is missing", core)
        core!!.click()

        val consentTitle = device.wait(
            Until.findObject(By.text("Загрузить русскую голосовую модель?")),
            10_000
        )
        assertNotNull("Missing-model consent dialog did not appear after tapping the core", consentTitle)
        saveUiEvidence("model-consent")
        assertVisible(By.text("НЕ СЕЙЧАС"), "Model-consent cancel action missing")

        // Do not download weights or open the microphone in the smoke test.
        device.findObject(By.text("НЕ СЕЙЧАС")).click()
        assertTrue(
            "Declining model installation did not return to the idle UI",
            device.wait(Until.hasObject(By.textContains("ЗАГРУЗКА МОДЕЛИ ОТМЕНЕНА")), 5_000)
        )
    }

    @Test
    fun settingsOpensAndroidAccessibilitySettings() {
        launchJarvis()
        val settings = device.wait(Until.findObject(By.desc("Настройки")), 10_000)
        assertNotNull("Settings button is missing", settings)
        settings!!.click()

        assertVisible(By.text("Настройки"), "Jarvis Settings screen did not open")
        val accessibilityItem = findAfterScroll(By.text("Спецвозможности Android"))
        assertNotNull("Android accessibility row missing in Jarvis Settings", accessibilityItem)
        accessibilityItem!!.click()

        val settingsPackage = device.wait(
            Until.findObject(By.pkg("com.android.settings")),
            5_000
        )
        assertNotNull("Android Settings package did not become the foreground UI", settingsPackage)
        saveUiEvidence("accessibility-settings")
    }

    @Test
    fun nativeLlamaLibraryLoadsAndInitializesCleanly() {
        assertTrue(
            "libjarvis_llama.so failed to load on device/emulator: ${LlamaNativeBridge.loadError}",
            LlamaNativeBridge.isNativeLibraryLoaded
        )
        LlamaNativeBridge.nativeBackendInit()
    }

    @Test
    fun minimizeAndResumeDoesNotFreezeOnWhiteScreen() {
        launchJarvis()
        assertVisible(By.desc("Интерактивное энергетическое ядро Джарвиса"), "Orb missing before minimize")
        assertVisible(By.text("Я РЯДОМ"), "Idle state missing before minimize")

        device.pressHome()
        device.wait(Until.gone(By.pkg(packageName)), 5_000)

        // Relaunch WITHOUT CLEAR_TASK to simulate resuming the minimized app.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resumeIntent = context.packageManager.getLaunchIntentForPackage(packageName)!!
        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(resumeIntent)
        assertTrue("Jarvis did not resume to foreground", device.wait(Until.hasObject(By.pkg(packageName)), 10_000))

        assertVisible(By.desc("Интерактивное энергетическое ядро Джарвиса"), "Orb missing after resume (white screen regression)")
        assertVisible(By.text("Я РЯДОМ"), "Idle state missing after resume")
        saveUiEvidence("resumed-home")
    }

    @Test
    fun textInputAnswersDeterministicQueryLive() {
        launchJarvis()
        val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
        assertNotNull("Text query input field missing on Core tab", input)
        input!!.text = "Сколько времени"

        val sendBtn = device.wait(Until.findObject(By.desc("Отправить")), 5_000)
        assertNotNull("Send button missing", sendBtn)
        sendBtn!!.click()

        assertTrue(
            "Jarvis did not answer time query in the dialogue feed",
            device.wait(Until.hasObject(By.textContains("Сейчас ")), 8_000)
        )
        saveUiEvidence("text-query-answer")
    }

    @Test
    fun modelsAndStudioTabsSwitchCleanly() {
        launchJarvis()

        val menuBtn = device.wait(Until.findObject(By.desc("Меню возможностей")), 5_000)
        assertNotNull("Drawer menu button missing", menuBtn)
        menuBtn!!.click()

        assertVisible(By.text("Jarvis Ai"), "Drawer header missing")
        val studioItem = device.wait(Until.findObject(By.text("Студия ИИ (Фото и Видео)")), 5_000)
        assertNotNull("Studio drawer item missing", studioItem)
        studioItem!!.click()
        assertVisible(By.text("СТУДИЯ ИИ — КАРТИНКИ И ВИДЕО"), "Studio screen did not open")

        val menuFromStudio = device.wait(Until.findObject(By.desc("Меню возможностей")), 5_000)
        assertNotNull("Drawer button missing on Studio header", menuFromStudio)
        menuFromStudio!!.click()

        val memoryItem = device.wait(Until.findObject(By.text("Память Джарвиса")), 5_000)
        assertNotNull("Memory drawer item missing", memoryItem)
        memoryItem!!.click()
        assertVisible(By.text("ПАМЯТЬ ДЖАРВИСА"), "Memory screen did not open")

        val settingsBtn = device.wait(Until.findObject(By.desc("Настройки")), 5_000)
        assertNotNull("Settings gear button missing", settingsBtn)
        settingsBtn!!.click()
        assertVisible(By.text("Настройки"), "Settings screen did not open")

        val modelsGroup = findAfterScroll(By.text("Локальные модели и Hugging Face GGUF"))
        assertNotNull("Collapsed models group missing in Settings", modelsGroup)
        modelsGroup!!.click()

        val modelsLink = findAfterScroll(By.text("Каталог Dolphin / Hermes / Stheno / Qwen"))
        assertNotNull("Catalog sub-link missing after expanding models group", modelsLink)
        modelsLink!!.click()
        assertVisible(By.text("МОДЕЛИ И ГОЛОС ДЖАРВИСА"), "Models screen did not open from Settings")

        val menuFromModels = device.wait(Until.findObject(By.desc("Меню возможностей")), 5_000)
        assertNotNull("Drawer button missing on Models header", menuFromModels)
        menuFromModels!!.click()
        val homeItem = device.wait(Until.findObject(By.text("Ядро и чат")), 5_000)
        assertNotNull("Home drawer item missing", homeItem)
        homeItem!!.click()
        assertVisible(By.text("Я РЯДОМ"), "Did not return cleanly to Core Home screen")
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
