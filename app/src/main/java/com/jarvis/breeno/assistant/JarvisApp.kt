package com.jarvis.breeno.assistant

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.jarvis.breeno.assistant.core.JarvisCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadTaskState(
    val active: Boolean = false,
    val label: String = "",
    val percent: Int = 0,
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * Application-scoped download coordinator so switching tabs or minimizing the app
 * never aborts ongoing ASR, Piper TTS, VAD, or GGUF model downloads.
 */
class ModelDownloadCoordinator(
    private val appScope: CoroutineScope,
    private val onCompleted: () -> Unit,
) {
    private val _state = MutableStateFlow(DownloadTaskState())
    val state: StateFlow<DownloadTaskState> = _state.asStateFlow()

    @Volatile private var activeJob: Job? = null

    fun start(
        initialLabel: String,
        task: suspend (updateProgress: (label: String?, percent: Int) -> Unit) -> Unit,
    ) {
        if (activeJob?.isActive == true) {
            _state.value = _state.value.copy(
                message = "Дождитесь завершения или отмените текущую загрузку",
                isError = true,
            )
            return
        }
        _state.value = DownloadTaskState(active = true, label = initialLabel, percent = 0)
        activeJob = appScope.launch {
            try {
                task { label, pct ->
                    _state.value = _state.value.copy(
                        active = true,
                        label = label ?: _state.value.label,
                        percent = pct.coerceIn(0, 100),
                    )
                }
                _state.value = DownloadTaskState(
                    active = false,
                    label = "",
                    percent = 100,
                    message = "Готово: $initialLabel",
                    isError = false,
                )
                onCompleted()
            } catch (_: CancellationException) {
                _state.value = DownloadTaskState(
                    active = false,
                    message = "Загрузка отменена",
                    isError = false,
                )
            } catch (err: Throwable) {
                _state.value = DownloadTaskState(
                    active = false,
                    message = "Ошибка загрузки: ${err.message ?: err.javaClass.simpleName}",
                    isError = true,
                )
            } finally {
                activeJob = null
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
    }

    fun clearMessage() {
        if (!_state.value.active) {
            _state.value = _state.value.copy(message = null, isError = false)
        }
    }
}

class JarvisApp : Application() {
    lateinit var core: JarvisCore
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var downloads: ModelDownloadCoordinator
        private set

    @Volatile var onDownloadStateUpdated: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        core = JarvisCore(this)
        downloads = ModelDownloadCoordinator(appScope) {
            onDownloadStateUpdated?.invoke()
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                isInForeground = startedActivities > 0
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                isInForeground = startedActivities > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) {
                isInForeground = true
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        @Volatile var isInForeground: Boolean = false
            private set
    }
}
