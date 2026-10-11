package com.paulneja.quorvi.core.backup

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.os.SystemClock
import com.paulneja.quorvi.core.security.CredentialRef
import com.paulneja.quorvi.core.security.SecretVault
import com.paulneja.quorvi.launcher.home.QuorviDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class BackupPart(val tables: List<String>, val secretPrefixes: List<String>) {
    HOME(listOf("home_items", "home_pages", "command_usage", "command_queries"), emptyList()),
    AI(listOf("ai_providers", "agent_config"), listOf("provider_key_", "bridge_pairing_code")),
    CHATS(listOf("chats", "chat_messages", "memories"), emptyList()),
    AGENTS(listOf("capsules", "policy_settings", "policy_grants", "policy_approvals", "policy_audit", "runs", "run_steps", "run_events", "artifacts"), emptyList()),
    CONNECTIONS(
        listOf("hosts", "docker_containers", "http_widgets", "services", "ha_entities", "watched_repos", "ci_executions", "ci_jobs"),
        listOf("github_oauth", "http_auth_", "service_token_", "ssh_key_", "ssh_password_"),
    ),
}

@Serializable
data class BackupManifest(
    val format: Int,
    val app: String,
    val appId: String,
    val dbVersion: Int,
    val exportedAt: Long,
    val parts: List<String>,
)

@Singleton
class FullBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: QuorviDatabase,
    private val vault: SecretVault,
) {
    suspend fun export(password: CharArray, parts: Set<BackupPart>): ByteArray = withContext(Dispatchers.IO) {
        require(parts.isNotEmpty())

        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
        val copy = File(context.cacheDir, "backup-${System.nanoTime()}.db")
        try {
            context.getDatabasePath(DB_NAME).copyTo(copy, overwrite = true)
            SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { snap ->
                val existing = tables(snap)
                val keep = parts.flatMap { it.tables }.toSet()
                BackupPart.entries.flatMap { it.tables }.filter { it !in keep && it in existing }.forEach { snap.execSQL("DELETE FROM `$it`") }
                snap.execSQL("VACUUM")
            }
            val zip = ByteArrayOutputStream()
            ZipOutputStream(zip).use { z ->
                fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
                val manifest = BackupManifest(FORMAT, APP, context.packageName, db.openHelper.readableDatabase.version, System.currentTimeMillis(), parts.map { it.name })
                put(MANIFEST, json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                put(DATABASE, copy.readBytes())
                put(SECRETS, json.encodeToString(SECRET_MAP, secretsFor(parts)).toByteArray())
                if (BackupPart.HOME in parts) {
                    settingsFile(context).takeIf { it.isFile }?.let { put(SETTINGS, it.readBytes()) }
                    File(context.filesDir, WALLPAPER).takeIf { it.isFile }?.let { put(WALLPAPER, it.readBytes()) }
                }
                if (BackupPart.CHATS in parts) {
                    File(context.filesDir, CHAT_DIR).listFiles().orEmpty().filter { it.isFile }.forEach { put("$CHAT_DIR/${it.name}", it.readBytes()) }
                }
            }
            BackupCrypto.encrypt(zip.toByteArray(), password)
        } finally {
            copy.delete()
        }
    }

    private fun secretsFor(parts: Set<BackupPart>): Map<String, String> {
        val prefixes = BackupPart.entries.associateWith { it.secretPrefixes }
        return vault.names().mapNotNull { name ->
            val owner = prefixes.entries.firstOrNull { (_, list) -> list.any { name.startsWith(it) } }?.key ?: BackupPart.CONNECTIONS
            if (owner !in parts) null else vault.get(CredentialRef(name))?.let { name to it }
        }.toMap()
    }

    suspend fun stage(data: ByteArray, password: CharArray): BackupManifest = withContext(Dispatchers.IO) {
        val plain = BackupCrypto.decrypt(data, password)
        val dir = pendingDir(context).apply { deleteRecursively(); mkdirs() }
        var manifest: BackupManifest? = null
        ZipInputStream(ByteArrayInputStream(plain)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val target = File(dir, e.name).canonicalFile
                // Never write outside the staging folder, whatever the archive says.
                if (!target.path.startsWith(dir.canonicalPath + File.separator)) continue
                target.parentFile?.mkdirs()
                val bytes = z.readBytes()
                target.writeBytes(bytes)
                if (e.name == MANIFEST) manifest = json.decodeFromString(BackupManifest.serializer(), String(bytes))
            }
        }
        val m = manifest ?: run { dir.deleteRecursively(); throw BackupException(BackupException.Reason.NOT_A_BACKUP) }
        val current = db.openHelper.readableDatabase.version
        if (m.format > FORMAT || m.dbVersion > current) { dir.deleteRecursively(); throw BackupException(BackupException.Reason.NEWER_VERSION) }
        if (m.dbVersion < current) { dir.deleteRecursively(); throw BackupException(BackupException.Reason.INCOMPATIBLE, "backup database v${m.dbVersion}, this app v$current") }
        m
    }

    fun discardPending() { pendingDir(context).deleteRecursively() }

    companion object {
        const val DB_NAME = "quorvi.db"
        const val SETTINGS_NAME = "quorvi_settings"
        private const val APP = "quorvi"
        private const val FORMAT = 1
        private const val MANIFEST = "manifest.json"
        private const val DATABASE = "database.db"
        private const val SECRETS = "secrets.json"
        private const val SETTINGS = "settings.preferences_pb"
        private const val WALLPAPER = "wallpaper.jpg"
        private const val CHAT_DIR = "chat"
        private val json = Json { ignoreUnknownKeys = true }
        private val SECRET_MAP = MapSerializer(String.serializer(), String.serializer())

        private fun pendingDir(context: Context) = File(context.filesDir, "restore-pending")
        private fun settingsFile(context: Context) = File(context.filesDir, "datastore/$SETTINGS_NAME.preferences_pb")
        private fun tables(db: SQLiteDatabase): Set<String> =
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

        fun applyPending(context: Context) {
            val dir = pendingDir(context)
            if (!dir.isDirectory) return
            RandomAccessFile(File(context.filesDir, ".restore.lock"), "rw").use { raf ->
                raf.channel.lock().use {
                    if (!dir.isDirectory) return
                    runCatching { apply(context, dir) }.onFailure { android.util.Log.e("QuorviBackup", "restore failed", it) }
                    dir.deleteRecursively()
                }
            }
        }

        private fun apply(context: Context, dir: File) {
            val manifest = json.decodeFromString(BackupManifest.serializer(), File(dir, MANIFEST).readText())
            val parts = manifest.parts.mapNotNull { n -> BackupPart.entries.firstOrNull { it.name == n } }.toSet()
            val target = context.getDatabasePath(DB_NAME)
            val backupDb = File(dir, DATABASE)
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                backupDb.copyTo(target, overwrite = true)
            } else {
                SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { main ->
                    main.execSQL("ATTACH DATABASE ? AS bak", arrayOf(backupDb.path))
                    val here = tables(main)
                    main.beginTransaction()
                    try {
                        parts.flatMap { it.tables }.filter { it in here }.forEach { t ->
                            main.execSQL("DELETE FROM main.`$t`")
                            main.execSQL("INSERT INTO main.`$t` SELECT * FROM bak.`$t`")
                        }
                        main.setTransactionSuccessful()
                    } finally {
                        main.endTransaction()
                    }
                    main.execSQL("DETACH DATABASE bak")
                }
            }
            SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { main ->
                if (BackupPart.CHATS in parts) runCatching { main.execSQL("INSERT INTO memories_fts(memories_fts) VALUES('rebuild')") }
                if (manifest.appId != context.packageName) {
                    main.execSQL("DELETE FROM home_items WHERE type = 1")
                    main.execSQL("UPDATE chat_messages SET attachments = REPLACE(attachments, ?, ?) WHERE attachments IS NOT NULL", arrayOf("/${manifest.appId}/", "/${context.packageName}/"))
                }
            }
            File(dir, SETTINGS).takeIf { it.isFile }?.copyTo(settingsFile(context).apply { parentFile?.mkdirs() }, overwrite = true)
            File(dir, WALLPAPER).takeIf { it.isFile }?.copyTo(File(context.filesDir, WALLPAPER), overwrite = true)
            File(dir, CHAT_DIR).takeIf { it.isDirectory }?.copyRecursively(File(context.filesDir, CHAT_DIR), overwrite = true)
            File(dir, SECRETS).takeIf { it.isFile }?.let { f ->
                val vault = SecretVault(context)
                json.decodeFromString(SECRET_MAP, f.readText()).forEach { (name, value) -> vault.put(CredentialRef(name), value) }
            }
        }

        fun restart(context: Context) {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                val pending = PendingIntent.getActivity(context, 41, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
                context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 800, pending)
            }
            context.getSystemService(ActivityManager::class.java)?.runningAppProcesses?.filter { it.pid != Process.myPid() }?.forEach { Process.killProcess(it.pid) }
            Process.killProcess(Process.myPid())
        }
    }
}
