package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.launcher.home.ImportEntry
import com.paulneja.quorvi.launcher.home.L3Row
import com.paulneja.quorvi.launcher.home.LauncherImport
import com.paulneja.quorvi.launcher.home.LauncherImport.DESKTOP
import com.paulneja.quorvi.launcher.home.LauncherImport.HOTSEAT
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherImportTest {
    private fun app(id: Long, container: Long, screen: Int, x: Int, y: Int, c: String, rank: Int = 0, profile: Long = 0) =
        L3Row(id, container, screen, x, y, LauncherImport.TYPE_APP, null, c, profile, rank)

    private fun folder(id: Long, screen: Int, x: Int, y: Int, title: String) =
        L3Row(id, DESKTOP, screen, x, y, LauncherImport.TYPE_FOLDER, title, null, 0, 0)

    @Test
    fun keepsReadingOrderAcrossScreensAndFolderOrder() {
        val rows = listOf(
            app(1, DESKTOP, 1, 0, 0, "b/B"),
            app(2, DESKTOP, 0, 1, 0, "a/A2"),
            app(3, DESKTOP, 0, 0, 0, "a/A1"),
            folder(10, 0, 0, 1, "Social"),
            app(11, 10, 0, 0, 0, "w/W", rank = 1),
            app(12, 10, 0, 0, 0, "t/T", rank = 0),
            app(20, HOTSEAT, 1, 1, 0, "d/D2"),
            app(21, HOTSEAT, 0, 0, 0, "d/D1"),
        )
        val plan = LauncherImport.plan(rows, setOf("a/A1", "a/A2", "b/B", "w/W", "t/T", "d/D1", "d/D2"))
        assertEquals(
            listOf(ImportEntry.App("a/A1"), ImportEntry.App("a/A2"), ImportEntry.Folder("Social", listOf("t/T", "w/W")), ImportEntry.App("b/B")),
            plan.entries,
        )
        assertEquals(listOf("d/D1", "d/D2"), plan.dock)
        assertEquals(5, plan.appCount)
    }

    @Test
    fun uninstalledAppsWorkProfileAndTinyFoldersAreHandled() {
        val rows = listOf(
            folder(10, 0, 0, 0, "Solo"),
            app(11, 10, 0, 0, 0, "x/X"),
            app(12, 10, 0, 0, 0, "gone/G"),
            folder(20, 0, 1, 0, "Empty"),
            app(30, DESKTOP, 0, 2, 0, "work/W", profile = 10),
        )
        val plan = LauncherImport.plan(rows, setOf("x/X", "work/W"))
        assertEquals(listOf(ImportEntry.App("x/X")), plan.entries)
    }

    @Test
    fun componentIsReadFromIntentUri() {
        assertEquals("com.app/com.app.Main", LauncherImport.componentOf("#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=com.app/.Main;end"))
        assertEquals("x.y/z.Act", LauncherImport.componentOf("#Intent;component=x.y/z.Act;end"))
        assertEquals(null, LauncherImport.componentOf("#Intent;action=VIEW;end"))
    }

    @Test
    fun rootScriptCopiesTheNewestDatabaseAndItsJournal() {
        val root = java.nio.file.Files.createTempDirectory("l3").toFile()
        val src = java.io.File(root, "src").apply { mkdirs() }
        val dest = java.io.File(root, "dest").apply { mkdirs() }
        java.io.File(src, "launcher_old.db").apply { writeText("old"); setLastModified(1_000) }
        java.io.File(src, "launcher.db").writeText("live")
        java.io.File(src, "launcher.db-journal").writeText("journal")
        listOf("", "-journal", "-wal", "-shm").forEach { java.io.File(dest, "l3.db$it").createNewFile() }
        val p = ProcessBuilder("sh", "-c", com.paulneja.quorvi.launcher.home.LauncherImportSource.copyScript(src.path, dest.path)).redirectErrorStream(true).start()
        assertEquals(0, p.waitFor())
        assertEquals("live", java.io.File(dest, "l3.db").readText())
        assertEquals("journal", java.io.File(dest, "l3.db-journal").readText())
        assertEquals(0L, java.io.File(dest, "l3.db-wal").length())
        val missing = ProcessBuilder("sh", "-c", com.paulneja.quorvi.launcher.home.LauncherImportSource.copyScript(java.io.File(root, "nope").path, dest.path)).start()
        assertEquals(3, missing.waitFor())
        root.deleteRecursively()
    }
}
