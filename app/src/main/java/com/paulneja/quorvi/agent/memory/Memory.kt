package com.paulneja.quorvi.agent.memory

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

enum class MemoryKind { PREFERENCE, EPISODIC, PROCEDURAL, PROJECT, FACT }

enum class Verification { STATED, OBSERVED, HYPOTHESIS, SUMMARY }

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val kind: String,
    val scope: String = "user",
    val source: String,
    val confidence: Double = 1.0,
    val verification: String,
    val sensitive: Boolean = false,
    val pinned: Boolean = false,
    val reviewed: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null,
)

@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFts(val text: String)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY pinned DESC, updatedAt DESC")
    fun observe(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories")
    suspend fun all(): List<MemoryEntity>

    @Upsert
    suspend fun put(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "SELECT memories.* FROM memories JOIN memories_fts ON memories.rowid = memories_fts.rowid " +
            "WHERE memories_fts MATCH :query AND memories.reviewed = 1 AND (memories.expiresAt IS NULL OR memories.expiresAt > :now) " +
            "ORDER BY memories.pinned DESC, memories.confidence DESC, memories.updatedAt DESC LIMIT :limit",
    )
    suspend fun search(query: String, now: Long, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE pinned = 1 AND reviewed = 1 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun pinned(limit: Int): List<MemoryEntity>
}

object SecretFilter {
    private val patterns = listOf(
        Regex("gh[opusr]_[A-Za-z0-9]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("\\bsk-[A-Za-z0-9_-]{16,}"),
        Regex("\\bAKIA[0-9A-Z]{16}\\b"),
        Regex("\\bxox[abposr]-[A-Za-z0-9-]{10,}"),
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
        Regex("(?i)\\b(password|passwd|contraseña|token|secret|api[_ -]?key)\\s*[:=]\\s*\\S{4,}"),
        Regex("(?i)\\b(otp|código|code|pin)\\D{0,12}\\b\\d{6}\\b"),
        Regex("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"),
    )

    fun containsSecret(text: String): Boolean = patterns.any { it.containsMatchIn(text) }
}

object FtsQuery {
    fun of(text: String): String? {
        val words = Regex("[\\p{L}\\p{N}]{3,}").findAll(text.lowercase()).map { it.value }.distinct().take(12).toList()
        return if (words.isEmpty()) null else words.joinToString(" OR ") { "$it*" }
    }
}

@Singleton
class MemoryStore @Inject constructor(private val dao: MemoryDao) {
    val all = dao.observe()

    suspend fun remember(text: String, kind: MemoryKind, source: String, verification: Verification, reviewed: Boolean, confidence: Double = 1.0, now: Long = System.currentTimeMillis()): MemoryEntity {
        val clean = text.trim().take(2_000)
        require(clean.isNotEmpty()) { "Empty memory" }
        require(!SecretFilter.containsSecret(clean)) { "Memories never store passwords, tokens, keys or one-time codes" }
        val memory = MemoryEntity(UUID.randomUUID().toString(), clean, kind.name, source = source, confidence = confidence.coerceIn(0.0, 1.0), verification = verification.name, reviewed = reviewed, createdAt = now, updatedAt = now)
        dao.put(memory)
        return memory
    }

    suspend fun update(memory: MemoryEntity) {
        require(!SecretFilter.containsSecret(memory.text)) { "Memories never store passwords, tokens, keys or one-time codes" }
        dao.put(memory.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun forget(id: String) = dao.delete(id)

    suspend fun search(text: String, limit: Int = 6, now: Long = System.currentTimeMillis()): List<MemoryEntity> {
        val query = FtsQuery.of(text) ?: return emptyList()
        return runCatching { dao.search(query, now, limit) }.getOrDefault(emptyList())
    }

    suspend fun contextFor(goal: String): List<MemoryEntity> = (dao.pinned(3) + search(goal, 5)).distinctBy { it.id }.take(6)

    suspend fun export(): List<MemoryEntity> = dao.all()
}
