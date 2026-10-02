package com.paulneja.quorvi.launcher.home

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val GLASS_KIND = "glass"

object ItemType {
    const val APP = 0
    const val WIDGET = 1
    const val FOLDER = 2
    const val SHORTCUT = 3
    const val CLOCK = 4
    const val NEXO_CARD = 5
}

object Container {
    const val PAGE = 0
    const val DOCK = 1
    const val FOLDER = 2
}

@Entity(
    tableName = "home_items",
    indices = [Index(value = ["container", "page"]), Index(value = ["parentId"])],
)
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val container: Int,
    val page: Int = 0,
    val cellX: Int = 0,
    val cellY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val appKey: String? = null,
    val widgetId: Int? = null,
    val shortcutId: String? = null,
    val parentId: Long? = null,
    val title: String? = null,
    val locked: Boolean = false,
    val rank: Int = 0,
    val config: String? = null,
) {
    val rect: CellRect get() = CellRect(cellX, cellY, spanX, spanY)

    val backdrop: Boolean get() = type == ItemType.NEXO_CARD && title == GLASS_KIND
}

@Dao
interface HomeDao {
    @Query("SELECT * FROM home_items ORDER BY container, page, cellY, cellX, rank")
    fun observe(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items")
    suspend fun all(): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE id = :id")
    suspend fun get(id: Long): HomeItemEntity?

    @Insert
    suspend fun insert(item: HomeItemEntity): Long

    @Update
    suspend fun update(item: HomeItemEntity)

    @Update
    suspend fun updateAll(items: List<HomeItemEntity>)

    @Query("DELETE FROM home_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM home_items WHERE parentId = :parentId")
    suspend fun deleteChildren(parentId: Long)

    @Query("SELECT * FROM home_items WHERE appKey LIKE :packageName || '/%' AND appKey LIKE '%#' || :userSerial")
    suspend fun byPackage(packageName: String, userSerial: Long): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE type = 3 AND appKey LIKE :packageName || '/%'")
    suspend fun shortcutsOf(packageName: String): List<HomeItemEntity>

    @Query("DELETE FROM home_items")
    suspend fun clear()

    @Query("SELECT * FROM home_pages ORDER BY page")
    fun observePages(): Flow<List<HomePageEntity>>

    @Query("SELECT * FROM home_pages ORDER BY page")
    suspend fun pages(): List<HomePageEntity>

    @androidx.room.Upsert
    suspend fun upsertPages(pages: List<HomePageEntity>)

    @Query("DELETE FROM home_pages")
    suspend fun clearPages()

    @Query("UPDATE home_items SET config = :config WHERE id = :id")
    suspend fun setConfig(id: Long, config: String?)
}

@Database(
    entities = [
        HomeItemEntity::class,
        com.paulneja.quorvi.core.command.CommandUsage::class,
        com.paulneja.quorvi.core.command.CommandQuery::class,
        com.paulneja.quorvi.ops.RunEntity::class,
        com.paulneja.quorvi.ops.StepEntity::class,
        com.paulneja.quorvi.ops.EventEntity::class,
        com.paulneja.quorvi.ops.ArtifactEntity::class,
        com.paulneja.quorvi.ops.CiExecutionEntity::class,
        com.paulneja.quorvi.ops.CiJobEntity::class,
        com.paulneja.quorvi.ops.WatchedRepoEntity::class,
        com.paulneja.quorvi.agent.models.ProviderEntity::class,
        com.paulneja.quorvi.connections.HostEntity::class,
        com.paulneja.quorvi.connections.ContainerEntity::class,
        com.paulneja.quorvi.connections.HttpWidgetEntity::class,
        com.paulneja.quorvi.agent.policy.PolicySettingEntity::class,
        com.paulneja.quorvi.agent.policy.GrantEntity::class,
        com.paulneja.quorvi.agent.policy.ApprovalEntity::class,
        com.paulneja.quorvi.agent.policy.AuditEntity::class,
        com.paulneja.quorvi.agent.memory.MemoryEntity::class,
        com.paulneja.quorvi.agent.memory.MemoryFts::class,
        com.paulneja.quorvi.connections.services.ServiceEntity::class,
        com.paulneja.quorvi.connections.services.HaEntity::class,
    ],
    version = 10,
    exportSchema = true,
    autoMigrations = [androidx.room.AutoMigration(from = 3, to = 4), androidx.room.AutoMigration(from = 4, to = 5), androidx.room.AutoMigration(from = 5, to = 6), androidx.room.AutoMigration(from = 6, to = 7), androidx.room.AutoMigration(from = 7, to = 8), androidx.room.AutoMigration(from = 8, to = 9), androidx.room.AutoMigration(from = 9, to = 10)],
)
abstract class QuorviDatabase : RoomDatabase() {
    abstract fun home(): HomeDao

    abstract fun commands(): com.paulneja.quorvi.core.command.CommandDao

    abstract fun ops(): com.paulneja.quorvi.ops.OpsDao

    abstract fun providers(): com.paulneja.quorvi.agent.models.ProviderDao

    abstract fun connections(): com.paulneja.quorvi.connections.ConnectionsDao

    abstract fun policy(): com.paulneja.quorvi.agent.policy.PolicyDao

    abstract fun memory(): com.paulneja.quorvi.agent.memory.MemoryDao

    abstract fun services(): com.paulneja.quorvi.connections.services.ServicesDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_HOME_ITEMS_V2.replace("home_items", "home_items_v2"))
                db.execSQL(
                    """
                    INSERT INTO home_items_v2 (id, type, container, page, cellX, cellY, spanX, spanY, appKey, widgetId, shortcutId, parentId, title, locked, rank)
                    SELECT id, ${ItemType.APP}, container, page,
                        CASE WHEN container = ${Container.DOCK} THEN cell ELSE cell % 4 END,
                        CASE WHEN container = ${Container.DOCK} THEN 0 ELSE cell / 4 END,
                        1, 1, appKey, NULL, NULL, NULL, NULL, 0, 0
                    FROM home_items
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE home_items")
                db.execSQL("ALTER TABLE home_items_v2 RENAME TO home_items")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_items_container_page` ON `home_items` (`container`, `page`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_items_parentId` ON `home_items` (`parentId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `command_usage` (`key` TEXT NOT NULL, `uses` INTEGER NOT NULL, `lastUsed` INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `command_queries` (`text` TEXT NOT NULL, `lastUsed` INTEGER NOT NULL, PRIMARY KEY(`text`))")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        private const val CREATE_HOME_ITEMS_V2 = "CREATE TABLE IF NOT EXISTS `home_items` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `container` INTEGER NOT NULL, " +
            "`page` INTEGER NOT NULL, `cellX` INTEGER NOT NULL, `cellY` INTEGER NOT NULL, `spanX` INTEGER NOT NULL, " +
            "`spanY` INTEGER NOT NULL, `appKey` TEXT, `widgetId` INTEGER, `shortcutId` TEXT, `parentId` INTEGER, " +
            "`title` TEXT, `locked` INTEGER NOT NULL, `rank` INTEGER NOT NULL)"
    }
}
