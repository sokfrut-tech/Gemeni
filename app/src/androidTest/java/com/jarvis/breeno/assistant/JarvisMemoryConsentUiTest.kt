package com.jarvis.breeno.assistant

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jarvis.breeno.assistant.memory.JarvisMemoryRepository
import com.jarvis.breeno.assistant.ui.JarvisMemoryActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisMemoryConsentUiTest {
    @Test
    fun memoryIsNotStoredUntilUserConfirmsExactText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = JarvisMemoryRepository(context)
        repository.clearScopeAfterUserConfirmation("Jarvis")

        val device = UiDevice.getInstance(instrumentation)
        val intent = Intent(context, JarvisMemoryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        assertTrue("Memory screen did not open", device.wait(Until.hasObject(By.text("Локальная память")), 10_000))
        val editor = device.wait(Until.findObject(By.text("Что запомнить")), 5_000)
        assertNotNull("Memory editor is missing", editor)
        editor!!.click()
        editor.setText("Только локально")

        val save = device.wait(Until.findObject(By.text("Сохранить")), 5_000)
        assertNotNull("Save action is missing", save)
        save!!.click()

        assertTrue(
            "Confirmation dialog did not appear",
            device.wait(Until.hasObject(By.textContains("Сохранить именно этот текст")), 5_000)
        )
        assertTrue("Memory was stored before consent", repository.list(scope = "Jarvis").isEmpty())

        val confirm = device.wait(Until.findObject(By.text("Подтверждаю")), 5_000)
        assertNotNull("Explicit confirmation action is missing", confirm)
        confirm!!.click()

        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && repository.list(scope = "Jarvis").isEmpty()) {
            Thread.sleep(100)
        }
        val saved = repository.list(scope = "Jarvis")
        assertEquals(1, saved.size)
        assertEquals("Только локально", saved.single().text)
    }
}
