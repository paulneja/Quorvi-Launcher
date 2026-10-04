package com.paulneja.quorvi.ui.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.paulneja.quorvi.R
import com.paulneja.quorvi.ui.design.Glyphs

enum class SettingsModule(@StringRes val title: Int, @StringRes val description: Int, val glyph: ImageVector, val keywords: List<String>) {
    HOME(R.string.mod_home, R.string.mod_home_desc, Glyphs.Home, listOf("inicio", "home", "apariencia", "vidrio", "glass", "material", "movimiento", "animacion", "iconos", "reloj", "clock", "paneles", "grilla", "fondo", "wallpaper", "tema", "color", "acento", "orientacion")),
    APPS(R.string.mod_apps, R.string.mod_apps_desc, Glyphs.Apps, listOf("apps", "ocultas", "hidden", "sugerencias", "sugeridas", "cajon", "drawer")),
    AI(R.string.mod_ai, R.string.mod_ai_desc, Glyphs.Sparkle, listOf("ia", "ai", "modelos", "models", "proveedor", "copilot", "openai", "anthropic", "claude", "bridge", "orquesta", "memoria", "memory", "voz", "voice", "asistente", "assistant", "gemini", "hablar")),
    AGENTS(R.string.mod_agents, R.string.mod_agents_desc, Glyphs.Shield, listOf("agentes", "agents", "permisos", "politica", "policy", "autonomia", "capsulas", "capsules", "auditoria", "aprobaciones")),
    GITHUB(R.string.mod_github, R.string.mod_github_desc, Glyphs.Git, listOf("github", "gitlab", "repos", "repositorios", "ci", "ultra", "commits", "pr", "coautor", "copilot")),
    SERVERS(R.string.mod_servers, R.string.mod_servers_desc, Glyphs.Server, listOf("servidores", "servers", "ssh", "docker", "http", "https", "tcp", "hosts", "home assistant", "worker", "discord")),
    SYSTEM(R.string.mod_system, R.string.mod_system_desc, Glyphs.Chip, listOf("sistema", "root", "shizuku", "adb", "control de pantalla", "accesibilidad", "hooks", "privilegios")),
    TERMINAL(R.string.mod_terminal, R.string.mod_terminal_desc, Glyphs.Terminal, listOf("terminal", "shell", "bash", "linux", "unix", "almacenamiento", "sdcard")),
    BACKUP(R.string.mod_backup, R.string.mod_backup_desc, Glyphs.Retry, listOf("respaldo", "backup", "exportar", "importar", "escena", "restaurar", "app de inicio", "version", "labs"));

    companion object {
        fun of(key: String?): SettingsModule? = entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}
