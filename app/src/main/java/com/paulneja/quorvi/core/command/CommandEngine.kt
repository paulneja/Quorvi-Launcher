package com.paulneja.quorvi.core.command

import android.Manifest
import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.paulneja.quorvi.R
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.launcher.apps.AppCatalog
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.AppSearch
import com.paulneja.quorvi.launcher.apps.AppShortcut
import com.paulneja.quorvi.launcher.apps.Profile
import com.paulneja.quorvi.launcher.apps.Shortcuts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class CommandEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: AppCatalog,
    private val shortcuts: Shortcuts,
    private val settings: QuorviSettings,
    private val dao: CommandDao,
    private val capsules: com.paulneja.quorvi.agent.capsules.CapsuleDao,
    scope: CoroutineScope,
) {
    @Volatile private var shortcutIndex: List<Pair<AppEntry, AppShortcut>> = emptyList()
    @Volatile private var usage: Map<String, CommandUsage> = emptyMap()
    private val spanish: Boolean get() = context.resources.configuration.locales[0].language == "es"

    init {
        scope.launch {
            catalog.apps.filterNotNull().collect { apps ->
                usage = dao.usage().associateBy { it.key }
                shortcutIndex = apps.filter { it.profile == Profile.MAIN }.flatMap { app -> shortcuts.forApp(app, limit = 8).map { app to it } }
            }
        }
    }

    suspend fun recentQueries(limit: Int = 6): List<String> = dao.recent(limit).map { it.text }

    suspend fun clearHistory() = dao.clearQueries()

    suspend fun record(result: CommandResult, query: String) {
        val now = System.currentTimeMillis()
        dao.used(result.key, now)
        if (query.isNotBlank()) dao.remember(query.trim().take(80), now)
        usage = dao.usage().associateBy { it.key }
    }

    suspend fun recordLaunch(app: com.paulneja.quorvi.launcher.apps.AppEntry) {
        dao.used("app:" + app.key.encode(), System.currentTimeMillis())
        usage = dao.usage().associateBy { it.key }
    }

    suspend fun suggestions(): List<CommandResult> {
        val apps = catalog.apps.filterNotNull().first()
        val now = System.currentTimeMillis()
        val ranked = usage.values.sortedByDescending { it.uses * 2 + if (now - it.lastUsed < 86_400_000) 10 else 0 }.map { it.key }
        return Suggestions.rank(ranked, apps, settings.hiddenApps.first(), settings.unsuggested.first(), limit = 8, fill = false)
            .map { app -> CommandResult("app:" + app.key.encode(), CommandKind.APP, app.label, null, 0, CommandAction.LaunchApp(app), app = app) }
    }

    suspend fun quick(query: String): List<CommandResult> = withContext(Dispatchers.Default) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val hidden = settings.hiddenApps.first()
        val apps = catalog.apps.filterNotNull().first().filter { it.profile != Profile.PRIVATE && it.key.encode() !in hidden }
        buildList {
            apps.forEach { app ->
                val base = AppSearch.score(app.label, q)
                if (base > 0) {
                    val key = "app:" + app.key.encode()
                    add(CommandResult(key, CommandKind.APP, app.label, if (app.work) context.getString(R.string.work_badge) else null, boost(key, base + 10, now), CommandAction.LaunchApp(app), app = app))
                }
            }
            shortcutIndex.forEach { (app, s) ->
                val base = AppSearch.score(s.label, q)
                if (base >= 60) {
                    val key = "sc:" + app.key.packageName + "/" + s.info.id
                    add(CommandResult(key, CommandKind.SHORTCUT, s.label, app.label, boost(key, base - 10, now), CommandAction.LaunchShortcut(s), app = app, image = s.icon))
                }
            }
            nexoActions(q).forEach { add(it) }

            capsules.observe().first().filter { it.enabled }.forEach { c ->
                val base = AppSearch.score(c.name, q)
                if (base > 0) add(CommandResult("capsule:" + c.id, CommandKind.QUORVI, c.name, context.getString(com.paulneja.quorvi.R.string.command_capsule), boost("capsule:" + c.id, base + 5, now), CommandAction.Capsule(c.id), glyph = CommandGlyph.AGENT))
            }
            SettingsCatalog.entries.forEach { entry ->
                val title = if (spanish) entry.es else entry.en
                val base = maxOf(AppSearch.score(title, q), entry.keywords.maxOf { AppSearch.score(it, q) }.let { if (it >= 80) 70 else if (it >= 60) 50 else 0 })
                if (base > 0) {
                    val key = "set:" + entry.action
                    add(CommandResult(key, CommandKind.SETTING, title, context.getString(R.string.command_android_settings), boost(key, base - 5, now), CommandAction.Start(Intent(entry.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), glyph = CommandGlyph.SETTINGS))
                }
            }
            Calculator.evaluate(q)?.let { value ->
                val text = Calculator.format(value)
                add(CommandResult("calc", CommandKind.CALC, "= $text", q, 120, CommandAction.Copy(text), glyph = CommandGlyph.CALC))
            }
            Units.convert(q)?.let { (value, unit) ->
                val text = Calculator.format(value) + " " + unit
                add(CommandResult("unit", CommandKind.CALC, text, q, 120, CommandAction.Copy(text), glyph = CommandGlyph.CALC))
            }
            agent(q)?.let { add(it) }
            web(q)?.let { add(it) }
        }.let { CommandRanking.merge(it) }
    }

    suspend fun slow(query: String): List<CommandResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        buildList {
            addAll(contacts(q))
            addAll(media(q))
        }
    }

    private fun boost(key: String, base: Int, now: Long): Int = usage[key].let { CommandRanking.boost(base, it?.uses ?: 0, it?.lastUsed, now) }

    private fun nexoActions(q: String): List<CommandResult> {
        val items = listOf(
            Triple(QuorviAction.EDIT, R.string.command_edit_home, listOf("editar", "edit", "modo edicion", "personalizar")),
            Triple(QuorviAction.STUDIO, R.string.menu_studio, listOf("studio", "apariencia", "tema", "theme", "acento", "iconos")),
            Triple(QuorviAction.WALLPAPER, R.string.menu_wallpaper, listOf("fondo", "wallpaper", "fondo de pantalla")),
            Triple(QuorviAction.WIDGETS, R.string.menu_widgets, listOf("widget", "widgets")),
            Triple(QuorviAction.HIDDEN_APPS, R.string.studio_hidden, listOf("ocultas", "hidden", "apps ocultas")),
            Triple(QuorviAction.CONTROL, R.string.control_title, listOf("control", "controles", "panel", "linterna", "volumen", "brillo", "notificaciones", "no molestar", "musica")),
            Triple(QuorviAction.ACCOUNTS, R.string.accounts_title, listOf("cuenta", "cuentas", "login", "iniciar sesion", "sesion", "github", "copilot", "conectar", "servicios", "home assistant", "gitlab", "proveedor", "modelo", "ia")),
            Triple(QuorviAction.AGENTS, R.string.ops_agents, listOf("agente", "agentes", "agent", "tareas", "runs", "capsule", "capsulas")),
            Triple(QuorviAction.SHELL, R.string.shell_title, listOf("terminal", "shell", "consola", "bash", "nexo shell", "linux")),
            Triple(QuorviAction.CAPABILITIES, R.string.cap_title, listOf("capacidades", "root", "shizuku", "adb", "pantalla", "accesibilidad", "permisos", "privilegios", "control de pantalla")),
            Triple(QuorviAction.OPERATIONS, R.string.ops_title, listOf("operations", "operaciones", "ci", "actions", "runs", "workflows", "builds")),
        )
        return items.mapNotNull { (id, title, keywords) ->
            val label = context.getString(title)
            val base = maxOf(AppSearch.score(label, q), keywords.maxOf { AppSearch.score(it, q) })
            if (base < 60) null else CommandResult("nexo:" + id.name, CommandKind.QUORVI, label, "Quorvi", base, CommandAction.Quorvi(id), glyph = CommandGlyph.QUORVI)
        }
    }

    private fun agent(q: String): CommandResult? {
        val words = q.split(Regex("\\s+")).count { it.isNotBlank() }
        if (words < 3) return null
        val kind = com.paulneja.quorvi.agent.engine.Classifier.classify(com.paulneja.quorvi.agent.engine.RunSpec(q)).kind
        val subtitle = when (kind) {
            com.paulneja.quorvi.agent.engine.GoalKind.DOCUMENT -> context.getString(R.string.command_agent_document)
            com.paulneja.quorvi.agent.engine.GoalKind.CI_DIAGNOSIS -> context.getString(R.string.command_agent_ci)
            com.paulneja.quorvi.agent.engine.GoalKind.GENERAL -> context.getString(R.string.command_agent_general)
        }
        val score = if (words >= 5 || kind != com.paulneja.quorvi.agent.engine.GoalKind.GENERAL) 70 else 10
        return CommandResult("agent", CommandKind.AGENT, context.getString(R.string.command_agent_title), subtitle, score, CommandAction.Agent(q), glyph = CommandGlyph.AGENT)
    }

    private fun web(q: String): CommandResult? {
        val looksLikeUrl = !q.contains(' ') && Regex("""^(https?://)?[\w-]+(\.[\w-]+)+(/\S*)?$""").matches(q)
        return if (looksLikeUrl) {
            val url = if (q.startsWith("http")) q else "https://$q"
            CommandResult("url", CommandKind.WEB, url, context.getString(R.string.command_open_link), 90, CommandAction.Start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), glyph = CommandGlyph.LINK)
        } else {
            val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val intent = if (search.resolveActivity(context.packageManager) != null) search
                else Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            CommandResult("web", CommandKind.WEB, context.getString(R.string.command_search_web, q), null, 5, CommandAction.Start(intent), glyph = CommandGlyph.WEB)
        }
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun contacts(q: String): List<CommandResult> {
        if (q.any { it.isDigit() } && q.count { it.isLetter() } == 0) return emptyList()
        if (!granted(Manifest.permission.READ_CONTACTS)) {
            return if (q.length >= 3 && q.all { it.isLetter() || it == ' ' }) {
                listOf(CommandResult("perm:contacts", CommandKind.PERMISSION, context.getString(R.string.command_grant_contacts), null, 1, CommandAction.Grant(Manifest.permission.READ_CONTACTS), glyph = CommandGlyph.LOCK))
            } else emptyList()
        }
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
        val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext() && size < 5) {
                        val id = c.getLong(0)
                        val lookup = c.getString(1)
                        val name = c.getString(2) ?: continue
                        val open = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(id, lookup)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        add(CommandResult("contact:$lookup", CommandKind.CONTACT, name, context.getString(R.string.command_contact), AppSearch.score(name, q).coerceAtLeast(20), CommandAction.Start(open), glyph = CommandGlyph.CONTACT))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun media(q: String): List<CommandResult> {
        val normalized = AppSearch.normalize(q)
        val screenshots = listOf("captura", "capturas", "screenshot", "screenshots").any { normalized.contains(it) }
        if (!screenshots) return emptyList()
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        if (!granted(permission)) {
            return listOf(CommandResult("perm:media", CommandKind.PERMISSION, context.getString(R.string.command_grant_media), null, 30, CommandAction.Grant(permission), glyph = CommandGlyph.LOCK))
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val day = when {
            normalized.contains("ayer") || normalized.contains("yesterday") -> today.minusDays(1)
            normalized.contains("hoy") || normalized.contains("today") -> today
            else -> null
        }
        val selection = StringBuilder("(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?)")
        val args = mutableListOf("%Screenshot%", "%Screenshot%")
        if (day != null) {
            selection.append(" AND ${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?")
            args += day.atStartOfDay(zone).toEpochSecond().toString()
            args += day.plusDays(1).atStartOfDay(zone).toEpochSecond().toString()
        }
        return runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                selection.toString(), args.toTypedArray(),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext() && size < 6) {
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                        val open = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        add(CommandResult("media:$uri", CommandKind.MEDIA, c.getString(1) ?: uri.lastPathSegment.orEmpty(), context.getString(R.string.command_screenshot), 60, CommandAction.Start(open), glyph = CommandGlyph.MEDIA))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
