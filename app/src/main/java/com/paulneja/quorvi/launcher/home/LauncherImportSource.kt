package com.paulneja.quorvi.launcher.home

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.paulneja.quorvi.control.RootLevel
import com.paulneja.quorvi.control.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SourceLauncher(val packageName: String, val label: String)

@Singleton
class LauncherImportSource @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.cacheDir, "l3").apply { mkdirs() }
    private val db get() = File(dir, "l3.db")

    fun installedSources(): List<SourceLauncher> = KNOWN.mapNotNull { pkg ->
        runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            SourceLauncher(pkg, context.packageManager.getApplicationLabel(info).toString())
        }.getOrNull()
    }

    fun rootAvailable(): Boolean = RootShell.available()

    suspend fun copyWithRoot(pkg: String): Result<File> = withContext(Dispatchers.IO) {
        require(pkg.matches(Regex("[A-Za-z0-9._]+"))) { "bad package" }
        dir.listFiles()?.forEach { it.delete() }

        SUFFIXES.forEach { File(dir, "l3.db$it").createNewFile() }
        val result = RootShell.exec(copyScript("/data/data/$pkg/databases", dir.absolutePath), RootLevel.READ, "", "", 20, globalNamespace = true)
        val ok = result.exitCode == 0 && db.length() > 0
        if (!ok) SUFFIXES.forEach { File(dir, "l3.db$it").delete() }
        if (ok) Result.success(db) else Result.failure(IllegalStateException(rootError(result.exitCode, result.stderr)))
    }

    private fun rootError(code: Int, stderr: String) = when (code) {
        3 -> context.getString(com.paulneja.quorvi.R.string.import_err_access)
        4 -> context.getString(com.paulneja.quorvi.R.string.import_err_no_db)
        else -> stderr.ifBlank { context.getString(com.paulneja.quorvi.R.string.import_err_root, code) }.take(200)
    }

    suspend fun copyFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
            context.contentResolver.openInputStream(uri)!!.use { input -> db.outputStream().use { input.copyTo(it) } }
            db
        }
    }

    suspend fun rows(file: File): Result<List<L3Row>> = withContext(Dispatchers.IO) {
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { sql ->
                val hasRank = sql.rawQuery("PRAGMA table_info(favorites)", null).use { c ->
                    var found = false
                    while (c.moveToNext()) if (c.getString(1) == "rank") found = true
                    found
                }
                val columns = "_id, container, screen, cellX, cellY, itemType, title, intent, profileId" + if (hasRank) ", rank" else ""
                sql.rawQuery("SELECT $columns FROM favorites", null).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                L3Row(
                                    id = c.getLong(0), container = c.getLong(1), screen = c.getInt(2), cellX = c.getInt(3), cellY = c.getInt(4),
                                    itemType = c.getInt(5), title = c.getString(6), component = LauncherImport.componentOf(c.getString(7)),
                                    profileId = c.getLong(8), rank = if (hasRank) c.getInt(9) else 0,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun copyScript(source: String, dest: String): String =
            "cd $source || exit 3; f=\$(ls -t launcher*.db 2>/dev/null | head -1); [ -n \"\$f\" ] || exit 4; " +
                "cat \"\$f\" > $dest/l3.db || exit 5; " +
                SUFFIXES.filter { it.isNotEmpty() }.joinToString(" ") { s -> "[ -f \"\$f$s\" ] && cat \"\$f$s\" > $dest/l3.db$s;" } + " exit 0"

        private val SUFFIXES = listOf("", "-journal", "-wal", "-shm")
        private val KNOWN = listOf("com.android.launcher3", "com.google.android.apps.nexuslauncher", "com.android.launcher", "app.lawnchair", "ch.deletescape.lawnchair.plah")
    }
}
