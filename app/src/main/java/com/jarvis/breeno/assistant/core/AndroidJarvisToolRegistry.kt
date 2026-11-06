package com.jarvis.breeno.assistant.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.jarvis.breeno.assistant.core.ai.ProposalRisk
import com.jarvis.breeno.assistant.core.ai.ToolProposalCodec
import java.util.Locale

enum class AssistantState {
    IDLE, LISTENING, TRANSCRIBING, UNDERSTANDING, EXECUTING, SPEAKING,
    WAITING_FOR_APPROVAL, ERROR
}

enum class ToolRisk { SAFE, CONFIRM, BLOCK }

sealed interface ToolResult {
    data class Success(val message: String) : ToolResult
    data class NeedsConfirmation(val action: String, val description: String) : ToolResult
    data class Failure(val message: String) : ToolResult
}

/**
 * Android-native command gateway. All external actions pass through this
 * registry; an assistant/model never receives a Context or unrestricted APIs.
 */
class AndroidJarvisToolRegistry(private val context: Context) {

    fun executeUtterance(rawText: String): ToolResult = when (val command = JarvisCommandParser.parse(rawText)) {
        JarvisCommand.Empty -> ToolResult.Failure("Не расслышал команду")
        is JarvisCommand.OpenApp -> openApp(command.name)
        is JarvisCommand.Search -> webSearch(command.query)
        is JarvisCommand.Navigate -> navigate(command.destination)
        is JarvisCommand.Dial -> openDialer(command.number)
        JarvisCommand.TellTime -> {
            val time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
            ToolResult.Success("Сейчас $time")
        }
        is JarvisCommand.Unsupported -> ToolResult.Failure("Команда пока не подключена. Распознано: ${command.original}")
    }

    /**
     * Validates untrusted model output and executes only SAFE proposals.
     * CONFIRM proposals are returned to the UI without launching anything.
     */
    fun executeProposalJson(json: String, confirmed: Boolean = false): ToolResult {
        val validated = ToolProposalCodec.validate(json)
            ?: return ToolResult.Failure("Предложение ИИ отклонено: неверный формат или неизвестная команда")

        return when (validated.risk) {
            ProposalRisk.BLOCK -> ToolResult.Failure("Действие заблокировано политикой безопасности")
            ProposalRisk.CONFIRM -> {
                if (!confirmed) {
                    val description = when (validated.proposal.tool) {
                        "open_dialer" -> "Открыть набор номера ${validated.proposal.arguments["number"].orEmpty()}? Сам звонок без вашего действия не выполняется."
                        else -> "Требуется подтверждение перед выполнением действия"
                    }
                    ToolResult.NeedsConfirmation(validated.proposal.tool, description)
                } else {
                    when (validated.proposal.tool) {
                        "open_dialer" -> openDialer(validated.proposal.arguments.getValue("number"))
                        else -> ToolResult.Failure("Это действие нельзя подтвердить через текущий шлюз")
                    }
                }
            }
            ProposalRisk.SAFE -> when (validated.proposal.tool) {
                "open_app" -> openApp(validated.proposal.arguments.getValue("name"))
                "web_search" -> webSearch(validated.proposal.arguments.getValue("query"))
                "navigate" -> navigate(validated.proposal.arguments.getValue("destination"))
                "tell_time" -> {
                    val time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
                    ToolResult.Success("Сейчас $time")
                }
                else -> ToolResult.Failure("Команда не поддерживается шлюзом Android")
            }
        }
    }

    private fun openApp(query: String): ToolResult {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return ToolResult.Failure("Назови приложение")
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = context.packageManager.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolve ->
                val label = resolve.loadLabel(context.packageManager)?.toString() ?: return@mapNotNull null
                Triple(label, resolve.activityInfo.packageName, resolve.activityInfo.name)
            }
            .filter { it.first.contains(normalized, ignoreCase = true) || it.second.contains(normalized, ignoreCase = true) }

        val exact = candidates.filter { it.first.equals(query, ignoreCase = true) || it.second.equals(query, ignoreCase = true) }
        val selected = (if (exact.isNotEmpty()) exact else candidates).distinctBy { it.second }
        if (selected.size > 1) {
            return ToolResult.Failure("Нашёл несколько приложений по запросу «$query». Уточни название.")
        }
        val app = selected.singleOrNull()
            ?: return ToolResult.Failure("Не нашёл установленное приложение «$query»")
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.second)
            ?: return ToolResult.Failure("У приложения «${app.first}» нет доступной команды запуска")
        return launch(launchIntent, "Открываю ${app.first}")
    }

    private fun webSearch(query: String): ToolResult {
        if (query.length > 240) return ToolResult.Failure("Слишком длинный поисковый запрос")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        return launch(intent, "Открываю поиск: $query")
    }

    private fun navigate(destination: String): ToolResult {
        val uri = if (destination.isBlank()) Uri.parse("geo:0,0?q=")
        else Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return launch(intent, if (destination.isBlank()) "Открываю навигацию" else "Строю маршрут до $destination")
    }

    /** Opens the dialer only; the user confirms the call in the phone app. */
    private fun openDialer(number: String): ToolResult {
        val digits = number.filter { it.isDigit() || it == '+' }
        if (digits.length > 20) return ToolResult.Failure("Номер выглядит некорректно")
        val uri = if (digits.isBlank()) Uri.parse("tel:") else Uri.parse("tel:$digits")
        return launch(Intent(Intent.ACTION_DIAL, uri), "Открываю набор номера. Звонок не выполнялся — подтверди его в приложении телефона.")
    }

    private fun launch(intent: Intent, success: String): ToolResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (intent.resolveActivity(context.packageManager) == null) {
                ToolResult.Failure("На телефоне нет приложения для выполнения этой команды")
            } else {
                context.startActivity(intent)
                ToolResult.Success(success)
            }
        } catch (error: SecurityException) {
            ToolResult.Failure("Android запретил действие: ${error.message ?: "нет разрешения"}")
        } catch (error: Exception) {
            ToolResult.Failure("Не удалось выполнить команду: ${error.message ?: error.javaClass.simpleName}")
        }
    }
}
