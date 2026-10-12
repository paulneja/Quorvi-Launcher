package com.paulneja.quorvi.agent.chat

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = ChatMode.CHAT) val mode: String = ChatMode.CHAT,
    val repo: String? = null,
) {
    val ultra: Boolean get() = mode == ChatMode.ULTRA
}

object ChatMode {
    const val CHAT = "chat"
    const val ULTRA = "ultra"
}

object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TASK = "task"
    const val PR = "pr"

    const val OFFER = "offer"

    const val CAPSULE = "capsule"

    const val PLAN = "plan"
}

object ChatStatus {
    const val DONE = "done"
    const val STREAMING = "streaming"
    const val ERROR = "error"
    const val STOPPED = "stopped"
    const val ACCEPTED = "accepted"
    const val DISMISSED = "dismissed"
}

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chatId")],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String,
    val text: String,
    val status: String = ChatStatus.DONE,
    val runId: String? = null,
    val createdAt: Long,
    val attachments: String? = null,
) {
    val files: List<ChatAttachment> get() = ChatAttachment.decode(attachments)
}

@Serializable
data class ChatAttachment(val path: String, val name: String, val mime: String, val size: Long, val uri: String? = null) {
    val image: Boolean get() = mime.startsWith("image/")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val list = ListSerializer(serializer())
        fun decode(raw: String?): List<ChatAttachment> = raw?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() }.orEmpty()
        fun encode(files: List<ChatAttachment>): String? = files.takeIf { it.isNotEmpty() }?.let { json.encodeToString(list, it) }
    }
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT chatId, text FROM chat_messages WHERE role IN ('user', 'assistant') ORDER BY createdAt DESC LIMIT 4000")
    suspend fun searchable(): List<ChatText>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observeChat(id: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAt, rowid")
    fun observeMessages(chatId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAt, rowid")
    suspend fun messages(chatId: String): List<ChatMessageEntity>

    @Upsert
    suspend fun upsertChat(chat: ChatEntity)

    @Upsert
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET text = :text, status = :status WHERE id = :id")
    suspend fun setText(id: String, text: String, status: String)

    @Query("UPDATE chat_messages SET text = :text, status = :status, attachments = :attachments WHERE id = :id")
    suspend fun finish(id: String, text: String, status: String, attachments: String?)

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun message(id: String): ChatMessageEntity?

    @Query("SELECT m.* FROM chat_messages m WHERE m.chatId != :exclude AND m.role IN ('user','assistant') AND m.text LIKE '%' || :query || '%' ORDER BY m.createdAt DESC LIMIT :limit")
    suspend fun search(query: String, exclude: String, limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun chat(id: String): ChatEntity?

    @Query("SELECT attachments FROM chat_messages WHERE chatId = :chatId AND attachments IS NOT NULL")
    suspend fun attachmentsOf(chatId: String): List<String>

    @Query("SELECT attachments FROM chat_messages WHERE attachments IS NOT NULL")
    suspend fun allAttachments(): List<String>

    @Query("UPDATE chats SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("UPDATE chats SET repo = :repo WHERE id = :id")
    suspend fun setRepo(id: String, repo: String?)

    @Query("SELECT id FROM chats")
    suspend fun chatIds(): List<String>

    @Query("UPDATE chats SET mode = :mode WHERE id = :id")
    suspend fun setMode(id: String, mode: String)

    @Query("UPDATE chats SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE chat_messages SET status = '${ChatStatus.STOPPED}' WHERE status = '${ChatStatus.STREAMING}'")
    suspend fun closeDangling()

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteChat(id: String)

    @Transaction
    @Query("DELETE FROM chats")
    suspend fun deleteAll()
}

data class ChatText(val chatId: String, val text: String)
