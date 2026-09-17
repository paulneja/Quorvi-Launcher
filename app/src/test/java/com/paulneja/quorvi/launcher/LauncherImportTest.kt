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
}
