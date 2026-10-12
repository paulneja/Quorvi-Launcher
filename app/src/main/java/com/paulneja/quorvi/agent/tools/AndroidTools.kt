package com.paulneja.quorvi.agent.tools

import com.paulneja.quorvi.control.PowerHook
import com.paulneja.quorvi.control.PowerHooks
import com.paulneja.quorvi.control.PrivBackend
import com.paulneja.quorvi.control.PrivilegedShells
import com.paulneja.quorvi.control.RootLevel
import com.paulneja.quorvi.control.ScreenControlBroker
import com.paulneja.quorvi.control.ScreenResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class AndroidInspect(private val broker: ScreenControlBroker) : Tool {
    override val name = "android.inspect_ui"
    override val description = "Read the current screen as a list of elements (role, text, id, clickable/editable/scroll). Field contents are hidden on sensitive screens."
    override val risk = Risk.READ
    override val idempotent = true
    override val parameters = schema {}

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult {
        val snap = broker.inspect() ?: return ToolResult.failed("Screen control is off or no window is available", ToolStatus.UNAUTHORIZED)
        return ToolResult.ok(snap.describe())
    }
}

class AndroidAct(private val broker: ScreenControlBroker) : Tool {
    override val name = "android.act"
    override val description = "Act on the screen: click/set_text on a target (its id or visible text), or scroll_forward/scroll_back/back/home/recents/notifications. A human touch pauses the agent."
    override val risk = Risk.WRITE
    override val idempotent = false
    override val parameters = schema {
        string("action", "click, set_text, scroll_forward, scroll_back, back, home, recents or notifications")
        string("target", "the element id or its visible text (for click/set_text)", optional = true)
        string("text", "text to type (for set_text)", optional = true)
    }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult {
        if (!broker.acquire()) return ToolResult(ToolStatus.CANCELLED, "The screen is being used by you; the agent stepped back.")
        val result = broker.act(args.string("action"), args.stringOrNull("target"), args.stringOrNull("text"))
        return when (result) {
            is ScreenResult.Ok -> {
                kotlinx.coroutines.delay(500)
                val after = broker.inspect()?.describe(40)
                ToolResult.ok(result.message + (after?.let { "\n--- screen now ---\n$it" } ?: ""))
            }
            is ScreenResult.Unavailable -> ToolResult.failed(result.reason)
            ScreenResult.Preempted -> ToolResult(ToolStatus.CANCELLED, "You touched the screen; the agent released it.")
        }
    }
}

class AndroidLaunch(private val broker: ScreenControlBroker) : Tool {
    override val name = "android.launch"
    override val description = "Open an app by package name."
    override val risk = Risk.WRITE
    override val idempotent = true
    override val parameters = schema { string("package", "package name, e.g. com.android.settings") }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult = when (val r = broker.launch(args.string("package"))) {
        is ScreenResult.Ok -> ToolResult.ok(r.message)
        is ScreenResult.Unavailable -> ToolResult.failed(r.reason)
        ScreenResult.Preempted -> ToolResult(ToolStatus.CANCELLED, "preempted")
    }
}

class AndroidListApps(private val broker: ScreenControlBroker) : Tool {
    override val name = "android.list_apps"
    override val description = "List installed launchable apps as name and package."
    override val risk = Risk.READ
    override val idempotent = true
    override val parameters = schema { string("query", "optional filter", optional = true) }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult {
        val q = args.stringOrNull("query")?.lowercase()
        val apps = broker.apps().filter { q == null || it.second.lowercase().contains(q) || it.first.contains(q) }
        if (apps.isEmpty()) return ToolResult.failed("Screen control is off or no apps matched", ToolStatus.UNAUTHORIZED)
        return ToolResult.ok(apps.take(200).joinToString("\n") { "${it.second} — ${it.first}" })
    }
}

class PrivShell(private val shells: PrivilegedShells, private val backends: Set<PrivBackend>) : Tool {
    override val name = "android.priv_shell"
    override val description = "Run a shell command with system privileges through " + backends.joinToString { it.name.lowercase() } + ". Root supports levels read<control<exec<modify<experimental."
    override val risk = Risk.EXTERNAL
    override val idempotent = false
    override val parameters = schema {
        string("command", "shell command")
        string("backend", "root, shizuku or adb_tcp", optional = true)
        string("level", "for root: read, control, exec, modify or experimental", optional = true)
        integer("timeoutSec", "default 120", optional = true)
    }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult {
        val backend = args.stringOrNull("backend")?.let { runCatching { PrivBackend.valueOf(it.uppercase()) }.getOrNull() } ?: backends.first()
        if (backend !in backends) return ToolResult.failed("$backend is not enabled", ToolStatus.UNAUTHORIZED)
        val level = args.stringOrNull("level")?.let { runCatching { RootLevel.valueOf(it.uppercase()) }.getOrNull() } ?: RootLevel.EXEC
        context.emit("exec", "${backend.name.lowercase()}: ${args.string("command").take(200)}", null)
        return runPriv(backend, args.string("command"), level, (args.intOrNull("timeoutSec") ?: 120).toLong(), shells)
    }
}

class RootPowerHook(private val shells: PrivilegedShells) : Tool {
    override val name = "android.power_hook"
    override val description = "Run a curated root customization. Hooks: " + PowerHooks.catalog.joinToString { "${it.id} (${it.level.name.lowercase()})" }
    override val risk = Risk.EXTERNAL
    override val idempotent = false
    override val parameters = schema {
        string("hook", "hook id from the list")
        string("args", "JSON object of parameter values", optional = true)
    }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult {
        val hook: PowerHook = PowerHooks.byId(args.string("hook")) ?: return ToolResult.failed("Unknown hook")
        val params = args.stringOrNull("args")?.let { parseArgs(it) } ?: emptyMap()
        val command = runCatching { hook.render(params) }.getOrElse { return ToolResult.failed(it.message ?: "bad args") }
        context.emit("exec", "power_hook ${hook.id}: $command", null)
        return runPriv(PrivBackend.ROOT, command, hook.level, 120, shells)
    }

    private fun parseArgs(raw: String): Map<String, String> =
        runCatching { Json.parseToJsonElement(raw).jsonObject.mapValues { (it.value as? JsonPrimitive)?.content.orEmpty() } }.getOrDefault(emptyMap())
}

private suspend fun runPriv(backend: PrivBackend, command: String, level: RootLevel, timeoutSec: Long, shells: PrivilegedShells): ToolResult {
    if (!shells.available(backend)) return ToolResult.failed("$backend is not available (not granted or not present)", ToolStatus.UNAUTHORIZED)
    return runCatching { shells.exec(backend, command, level, timeoutSec) }.fold(
        { r ->
            val text = "backend=${r.backend} exit=${r.exitCode}${if (r.timedOut) " (timed out)" else ""}\n${r.stdout.takeLast(8_000)}${if (r.stderr.isNotBlank()) "\nstderr:\n${r.stderr.takeLast(3_000)}" else ""}"
            if (r.exitCode == 0 && !r.timedOut) ToolResult.ok(text) else ToolResult.failed(text)
        },
        { ToolResult.failed(it.message ?: "privileged exec failed", ToolStatus.UNAUTHORIZED) },
    )
}

class InstallApk(private val context: android.content.Context) : Tool {
    override val name = "android.install_apk"
    override val description = "Install an APK built in your workspace on this phone (through Shizuku) and optionally open it. Use this instead of adb, pm or priv_shell to install apps."
    override val risk = Risk.EXTERNAL
    override val idempotent = false
    override val parameters = schema {
        string("path", "APK path, relative to your workspace")
        boolean("open", "open the app after installing (default true)", optional = true)
    }

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val home = java.io.File(this@InstallApk.context.filesDir, "home").canonicalFile
        val raw = args.string("path")
        val file = (if (raw.startsWith("/")) java.io.File(raw) else java.io.File(java.io.File(home, context.workspace), raw)).canonicalFile
        if (!file.path.startsWith(this@InstallApk.context.filesDir.canonicalPath + "/")) return@withContext ToolResult.failed("The APK must be inside your workspace")
        if (!file.isFile) return@withContext ToolResult.failed("No APK at ${file.path}")
        val info = this@InstallApk.context.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: return@withContext ToolResult.failed("${file.name} is not a valid APK (Android could not read it)")
        val pkg = info.packageName
        if (!com.paulneja.quorvi.control.ShizukuShell.available()) {
            return@withContext ToolResult.failed("Shizuku is not running or not allowed for Quorvi, so the APK cannot be installed silently. Tell the user to start Shizuku, or export the APK to Downloads with artifact.export so they can install it by hand.", ToolStatus.UNAUTHORIZED)
        }
        context.emit("exec", "install $pkg from ${file.name}", null)
        val installed = com.paulneja.quorvi.control.ShizukuShell.pipe(arrayOf("pm", "install", "-r", "-S", file.length().toString()), file, 180)
        if (installed.exitCode != 0 || !installed.stdout.contains("Success")) {
            return@withContext ToolResult.failed("pm install failed (exit ${installed.exitCode}): ${(installed.stdout + installed.stderr).trim().take(800)}")
        }
        val open = args.boolOrNull("open") != false
        val opened = if (open) com.paulneja.quorvi.control.ShizukuShell.exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1", 30).let { it.exitCode == 0 } else false
        ToolResult(ToolStatus.SUCCESS, "Installed $pkg (version ${info.versionName ?: "?"})." + if (open) (if (opened) " Opened it." else " Could not open it automatically.") else "")
    }
}
