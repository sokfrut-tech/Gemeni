package com.jarvis.breeno.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.breeno.assistant.service.JarvisPythonWorkerProbeService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Proves only Android process identity and permission isolation. This does NOT prove
 * Python/Chaquopy compatibility, file-system isolation, or complete sandbox security.
 */
@RunWith(AndroidJUnit4::class)
class JarvisIsolatedWorkerProbeTest {
    @Test
    fun isolatedWorkerHasDifferentUidAndNoAppNetworkOrMicrophonePermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val response = CountDownLatch(1)
        val remote = AtomicReference<Messenger?>()
        val result = AtomicReference<Bundle?>()

        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == JarvisPythonWorkerProbeService.MSG_PROBE_RESULT) {
                result.set(message.data)
                response.countDown()
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: android.os.IBinder) {
                remote.set(Messenger(binder))
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                remote.set(null)
            }
        }

        val bound = context.bindService(
            Intent(context, JarvisPythonWorkerProbeService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        assertTrue("isolated worker service did not bind", bound)
        try {
            assertTrue("isolated worker did not connect", connected.await(10, TimeUnit.SECONDS))
            remote.get()!!.send(Message.obtain(null, JarvisPythonWorkerProbeService.MSG_PROBE).apply {
                replyTo = replyMessenger
            })
            assertTrue("isolated worker did not return identity", response.await(10, TimeUnit.SECONDS))

            val snapshot = result.get() ?: throw AssertionError("missing worker snapshot")
            assertNotEquals(
                "worker must run under a distinct isolated UID",
                context.applicationInfo.uid,
                snapshot.getInt(JarvisPythonWorkerProbeService.KEY_UID)
            )
            assertFalse(
                "isolated worker must not inherit INTERNET permission",
                snapshot.getBoolean(JarvisPythonWorkerProbeService.KEY_HAS_INTERNET, true)
            )
            assertFalse(
                "isolated worker must not inherit RECORD_AUDIO permission",
                snapshot.getBoolean(JarvisPythonWorkerProbeService.KEY_HAS_RECORD_AUDIO, true)
            )
            assertTrue(snapshot.getString(JarvisPythonWorkerProbeService.KEY_PACKAGE_NAME).isNullOrBlank().not())
        } finally {
            context.unbindService(connection)
        }
    }
}
