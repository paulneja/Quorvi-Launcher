package com.paulneja.quorvi.agent.chat

import com.paulneja.quorvi.agent.models.AiMessage
import com.paulneja.quorvi.agent.models.AiPart
import com.paulneja.quorvi.agent.models.AiRole
import java.util.Calendar

enum class ChatAge { TODAY, YESTERDAY, WEEK, OLDER }

object ChatLogic {
    const val HISTORY = 40
    const val ATTACHED = 3
    const val TEXT_LIMIT = 60_000

    fun titleFrom(text: String): String {
        val line = text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

        val words = line.split(Regex("\\s+")).filter { it.isNotEmpty() && !(it.startsWith("@") && it.length > 1 && it.drop(1).all(Char::isLetter)) }
        val title = buildString {
            for (w in words) {
                if (length + w.length + 1 > 42) break
                if (isNotEmpty()) append(' ')
                append(w)
            }
        }.ifEmpty { line.take(42) }
        return title.trimStart('¿', '¡').trimEnd('.', ',', ';', ':', '?', '¿', '!').replaceFirstChar { it.uppercase() }
    }

    fun history(messages: List<ChatMessageEntity>, taskState: (String) -> String? = { null }, attach: (ChatAttachment) -> AiPart? = { null }): List<AiMessage> {
        val recent = messages.takeLast(HISTORY)
        val withFiles = recent.filter { it.role == ChatRole.USER && it.attachments != null }.takeLast(ATTACHED).map { it.id }.toSet()
        return recent.mapNotNull { m ->
            when (m.role) {
                ChatRole.USER -> {
                    val files = m.files
                    if (files.isEmpty()) AiMessage.user(m.text)
                    else {
                        val parts = if (m.id in withFiles) files.map { f -> attach(f) ?: AiPart.Text(describe(f)) } else files.map { AiPart.Text(describe(it)) }
                        AiMessage(AiRole.USER, listOf(AiPart.Text(m.text.ifBlank { "(attached files)" })) + parts)
                    }
                }
                ChatRole.ASSISTANT -> m.text.takeIf { it.isNotBlank() && m.status != ChatStatus.ERROR }?.let { AiMessage.assistant(it) }
                ChatRole.TASK -> AiMessage.assistant("[task \"${m.text}\" → ${m.runId?.let(taskState) ?: "submitted"}]")
                ChatRole.PR -> com.paulneja.quorvi.agent.ultra.PrDraft.decode(m.text)?.let { d ->
                    val owner = buildString {
                        if (d.excluded.isNotEmpty()) append("; the user left out ").append(d.excluded.joinToString())
                        if (d.edited.isNotEmpty()) append("; the user edited ").append(d.edited.joinToString())
                    }
                    AiMessage.assistant("[pull request draft \"${d.title}\" (${d.included.size} files$owner) → ${d.status}${d.url?.let { " $it" } ?: ""}${d.error?.let { ": $it" } ?: ""}]")
                }
                else -> null
            }
        }.dropWhile { it.role != AiRole.USER }
    }

    fun describe(f: ChatAttachment) = "[attached file: ${f.name}, ${f.mime}, ${f.size} bytes, path ${f.path}]"

    fun isText(mime: String, name: String) = mime.startsWith("text/") || mime in setOf("application/json", "application/xml", "application/javascript", "application/x-sh") ||
        name.substringAfterLast('.', "").lowercase() in setOf("md", "txt", "kt", "kts", "java", "py", "js", "ts", "json", "yml", "yaml", "toml", "xml", "html", "css", "sh", "csv", "log", "gradle", "c", "cpp", "h", "rs", "go")

    fun age(updatedAt: Long, now: Long): ChatAge {
        val day = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when {
            updatedAt >= day -> ChatAge.TODAY
            updatedAt >= day - DAY -> ChatAge.YESTERDAY
            updatedAt >= day - 6 * DAY -> ChatAge.WEEK
            else -> ChatAge.OLDER
        }
    }

    fun grouped(chats: List<ChatEntity>, now: Long): List<Pair<ChatAge, List<ChatEntity>>> =
        chats.sortedByDescending { it.updatedAt }.groupBy { age(it.updatedAt, now) }.toList().sortedBy { it.first.ordinal }

    private const val DAY = 24L * 60 * 60 * 1000
}
