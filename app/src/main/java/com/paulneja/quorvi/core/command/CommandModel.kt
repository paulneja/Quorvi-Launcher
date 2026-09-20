package com.paulneja.quorvi.core.command

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.AppShortcut

enum class CommandKind { APP, SHORTCUT, QUORVI, AGENT, SETTING, CONTACT, MEDIA, CALC, WEB, PERMISSION }

enum class CommandGlyph { APP, SETTINGS, CONTACT, MEDIA, CALC, WEB, LINK, QUORVI, LOCK, AGENT }

sealed interface CommandAction {
    data class LaunchApp(val app: AppEntry) : CommandAction
    data class LaunchShortcut(val shortcut: AppShortcut) : CommandAction
    data class Start(val intent: Intent) : CommandAction
    data class Copy(val text: String) : CommandAction
    data class Quorvi(val id: QuorviAction) : CommandAction
    data class Grant(val permission: String) : CommandAction
    data class Agent(val goal: String) : CommandAction
    data class Capsule(val id: String) : CommandAction
}

enum class QuorviAction { EDIT, STUDIO, WALLPAPER, WIDGETS, HIDDEN_APPS, OPERATIONS, CONTROL, ACCOUNTS, CAPABILITIES, AGENTS, SHELL }

data class CommandResult(
    val key: String,
    val kind: CommandKind,
    val title: String,
    val subtitle: String? = null,
    val score: Int,
    val action: CommandAction,
    val app: AppEntry? = null,
    val image: ImageBitmap? = null,
    val glyph: CommandGlyph = CommandGlyph.APP,
)

object CommandRanking {
    fun boost(base: Int, uses: Int, lastUsedMs: Long?, now: Long): Int {
        if (base <= 0) return base
        val frequency = (kotlin.math.ln(1.0 + uses) * 6).toInt()
        val recency = lastUsedMs?.let { val hours = (now - it) / 3_600_000.0; if (hours < 24) 8 else if (hours < 168) 4 else 0 } ?: 0
        return base + frequency + recency
    }

    fun merge(results: List<CommandResult>, limitPerKind: Int = 6): List<CommandResult> =
        results.groupBy { it.kind }.flatMap { (_, list) -> list.sortedByDescending { it.score }.take(limitPerKind) }
            .sortedWith(compareByDescending<CommandResult> { it.score }.thenBy { it.kind.ordinal })
}
