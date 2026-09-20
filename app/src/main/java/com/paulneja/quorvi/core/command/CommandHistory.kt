package com.paulneja.quorvi.core.command

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "command_usage")
data class CommandUsage(
    @PrimaryKey val key: String,
    val uses: Int,
    val lastUsed: Long,
)

@Entity(tableName = "command_queries")
data class CommandQuery(
    @PrimaryKey val text: String,
    val lastUsed: Long,
)

@Dao
interface CommandDao {
    @Query("SELECT * FROM command_usage")
    suspend fun usage(): List<CommandUsage>

    @Query("INSERT INTO command_usage (key, uses, lastUsed) VALUES (:key, 1, :now) ON CONFLICT(key) DO UPDATE SET uses = uses + 1, lastUsed = :now")
    suspend fun used(key: String, now: Long)

    @Query("INSERT OR REPLACE INTO command_queries (text, lastUsed) VALUES (:text, :now)")
    suspend fun remember(text: String, now: Long)

    @Query("SELECT * FROM command_queries ORDER BY lastUsed DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CommandQuery>

    @Query("DELETE FROM command_queries")
    suspend fun clearQueries()

    @Query("SELECT * FROM command_usage WHERE key LIKE 'app:%' ORDER BY uses DESC, lastUsed DESC LIMIT :limit")
    fun observeTopApps(limit: Int): kotlinx.coroutines.flow.Flow<List<CommandUsage>>
}
