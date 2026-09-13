package com.paulneja.quorvi.launcher

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.launcher.home.Container
import com.paulneja.quorvi.launcher.home.ItemType
import com.paulneja.quorvi.launcher.home.QuorviDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun v1LayoutSurvivesTheMoveToCellsAndSpans() {
        val name = "migration-1-2.db"
        context.deleteDatabase(name)
        createV1(name) { db ->
            db.execSQL("INSERT INTO home_items (id, container, page, cell, appKey) VALUES (1, 0, 0, 0, 'a/A#0')")
            db.execSQL("INSERT INTO home_items (id, container, page, cell, appKey) VALUES (2, 0, 1, 6, 'b/B#0')")
            db.execSQL("INSERT INTO home_items (id, container, page, cell, appKey) VALUES (3, 1, 0, 3, 'c/C#0')")
        }

        val db = Room.databaseBuilder(context, QuorviDatabase::class.java, name)
            .addMigrations(*QuorviDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val items = runBlocking { db.home().all() }.associateBy { it.id }
        val usage = runBlocking { db.commands().usage() }
        val pages = runBlocking { db.home().pages() }
        db.close()
        assertEquals(0, usage.size)
        assertEquals(0, pages.size)
        assertEquals(null, items.getValue(1).config)

        assertEquals(3, items.size)
        assertEquals(CellRect(0, 0), items.getValue(1).rect)
        assertEquals(1, items.getValue(2).page)
        assertEquals(CellRect(2, 1), items.getValue(2).rect)
        assertEquals(Container.DOCK, items.getValue(3).container)
        assertEquals(CellRect(3, 0), items.getValue(3).rect)
        assertEquals(ItemType.APP, items.getValue(3).type)
        assertEquals("c/C#0", items.getValue(3).appKey)
    }

    private fun createV1(name: String, seed: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `home_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `container` INTEGER NOT NULL, `page` INTEGER NOT NULL, `cell` INTEGER NOT NULL, `appKey` TEXT NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_home_items_container_page_cell` ON `home_items` (`container`, `page`, `cell`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2bdc710465a59f48da090e50a7ddcfb2')")
                    seed(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply { writableDatabase; close() }
    }
}
