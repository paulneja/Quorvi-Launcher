package com.paulneja.quorvi.agent.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.paulneja.quorvi.agent.engine.RunSpec
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ops.OpsDao
import com.paulneja.quorvi.ops.RunState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ChatDao,
    private val ops: OpsDao,
    private val core: CoreClient,
    private val capsules: com.paulneja.quorvi.agent.capsules.CapsuleDao,
) {
    val chats: Flow<List<ChatEntity>> = dao.observeChats()

    fun chat(id: String): Flow<ChatEntity?> = dao.observeChat(id)

    fun messages(id: String): Flow<List<ChatMessageEntity>> = dao.observeMessages(id)

    suspend fun send(chatId: String?, text: String, uris: List<Uri>, mode: String = ChatMode.CHAT, repo: String? = null, voice: Boolean = false, title: String? = null): String? {
        val files = uris.mapNotNull { importFile(it) }
        if (text.isBlank() && files.isEmpty()) return chatId
        val now = System.currentTimeMillis()
        val id = chatId ?: UUID.randomUUID().toString().also {
            dao.upsertChat(ChatEntity(it, title ?: ChatLogic.titleFrom(text.ifBlank { files.first().name }), now, now, mode, repo))
        }
        dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), id, ChatRole.USER, text.trim(), ChatStatus.DONE, null, now, ChatAttachment.encode(files)))
        dao.touch(id, now)
        ready()
        val waiting = pendingQuestion(id)
        if (waiting != null) {
            core.answerRun(waiting, text.trim())
            return id
        }
        val goal = text.trim().ifBlank { files.joinToString { it.name } }
        val mentions = Mentions.parse(text)
        if (mentions.ultra && dao.chat(id)?.ultra != true) offer(id, context.getString(com.paulneja.quorvi.R.string.ultra_offer_asked))
        val spec = json.encodeToString(RunSpec.serializer(), spec(goal, id, mentions).copy(voice = voice))
        if (core.submitRun(spec) == null) {
            dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), id, ChatRole.ASSISTANT, "", ChatStatus.ERROR, null, System.currentTimeMillis()))
        }
        return id
    }

    suspend fun retry(chatId: String) {
        val all = dao.messages(chatId)
        val lastUser = all.lastOrNull { it.role == ChatRole.USER } ?: return
        all.filter { it.createdAt >= lastUser.createdAt && it.role == ChatRole.ASSISTANT && it.status != ChatStatus.STREAMING }.forEach { dao.finish(it.id, it.text, ChatStatus.STOPPED, it.attachments) }
        val spec = json.encodeToString(RunSpec.serializer(), spec(lastUser.text.ifBlank { "(attached files)" }, chatId, Mentions.parse(lastUser.text)))
        ready()
        core.submitRun(spec)
    }

    private fun spec(goal: String, chatId: String, mentions: Mentions) =
        RunSpec(goal = goal, origin = "chat:$chatId", chatId = chatId, meta = mentions.meta, specialists = mentions.specialists.map { it.name }, research = mentions.research, plan = mentions.plan)

    private suspend fun offer(chatId: String, reason: String) {
        if (dao.messages(chatId).any { it.role == ChatRole.OFFER && it.status == ChatStatus.DONE }) return
        dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), chatId, ChatRole.OFFER, reason, ChatStatus.DONE, null, System.currentTimeMillis()))
    }

    suspend fun acceptUltra(messageId: String) {
        val m = dao.message(messageId) ?: return
        dao.setText(m.id, m.text, ChatStatus.ACCEPTED)
        if (dao.chat(m.chatId)?.ultra == true) return
        dao.setMode(m.chatId, ChatMode.ULTRA)
        dao.touch(m.chatId, System.currentTimeMillis())

        val lastUser = dao.messages(m.chatId).lastOrNull { it.role == ChatRole.USER } ?: return
        ready()
        core.submitRun(json.encodeToString(RunSpec.serializer(), spec(lastUser.text.ifBlank { "(attached files)" }, m.chatId, Mentions.parse(lastUser.text))))
    }

    suspend fun dismiss(messageId: String) {
        val m = dao.message(messageId) ?: return
        dao.setText(m.id, m.text, ChatStatus.DISMISSED)
    }

    suspend fun approvePlan(messageId: String) {
        val m = dao.message(messageId) ?: return
        dao.setText(m.id, m.text, ChatStatus.ACCEPTED)
        val now = System.currentTimeMillis()
        dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), m.chatId, ChatRole.USER, context.getString(com.paulneja.quorvi.R.string.plan_approved_message), ChatStatus.DONE, null, now))
        dao.touch(m.chatId, now)
        ready()
        core.submitRun(json.encodeToString(RunSpec.serializer(), RunSpec(goal = "The user approved this plan. Carry it out now, step by step, and report when done.\n\n${m.text}", origin = "chat:${m.chatId}", chatId = m.chatId)))
    }

    suspend fun changePlan(messageId: String, comment: String) {
        val m = dao.message(messageId) ?: return
        dao.setText(m.id, m.text, ChatStatus.DISMISSED)
        val now = System.currentTimeMillis()
        val note = comment.trim()
        dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), m.chatId, ChatRole.USER, "/plan $note", ChatStatus.DONE, null, now))
        dao.touch(m.chatId, now)
        ready()
        core.submitRun(json.encodeToString(RunSpec.serializer(), RunSpec(goal = "Revise your plan with this feedback from the user: $note", origin = "chat:${m.chatId}", chatId = m.chatId, plan = true)))
    }

    suspend fun saveCapsule(messageId: String): String? {
        val m = dao.message(messageId) ?: return null
        val capsule = runCatching { json.decodeFromString(com.paulneja.quorvi.agent.capsules.Capsule.serializer(), m.text) }.getOrNull() ?: return null
        val id = UUID.randomUUID().toString()
        capsules.put(com.paulneja.quorvi.agent.capsules.CapsuleEntity(id, capsule.name, capsule.icon, m.text, createdAt = System.currentTimeMillis()))
        dao.setText(m.id, m.text, ChatStatus.ACCEPTED)
        return id
    }

    suspend fun stop(chatId: String) {
        activeRuns(chatId).forEach { core.cancelRun(it) }
    }

    suspend fun activeRuns(chatId: String): List<String> =
        dao.messages(chatId).mapNotNull { it.runId }.distinct().filter { id -> ops.run(id)?.state?.let { s -> runCatching { !RunState.valueOf(s).terminal }.getOrDefault(false) } == true }

    suspend fun setRepo(chatId: String, repo: String?) = dao.setRepo(chatId, repo)

    suspend fun rename(chatId: String, title: String) = dao.rename(chatId, title.trim().take(80).ifBlank { return })

    suspend fun delete(chatId: String) {
        stop(chatId)
        val files = dao.attachmentsOf(chatId).flatMap { ChatAttachment.decode(it) }
        dao.deleteChat(chatId)

        forgetRuns(ops.runIdsOf(listOf("chat:$chatId", "chat-task:$chatId")))
        withContext(Dispatchers.IO) { files.forEach { runCatching { File(it.path).delete() } } }
    }

    suspend fun pruneOrphanRuns() {
        val alive = dao.chatIds().toSet()
        forgetRuns(ops.chatRunOrigins().filter { it.origin.substringAfter(':') !in alive }.map { it.id })
    }

    private suspend fun forgetRuns(ids: List<String>) {
        if (ids.isEmpty()) return
        ids.chunked(500).forEach { chunk ->
            ops.deleteEvents(chunk)
            ops.deleteSteps(chunk)
            ops.deleteRuns(chunk)
        }
    }

    suspend fun deleteAll() {
        val files = dao.allAttachments().flatMap { ChatAttachment.decode(it) }
        dao.deleteAll()
        forgetRuns(ops.chatRunIds())
        withContext(Dispatchers.IO) { files.forEach { runCatching { File(it.path).delete() } } }
    }

    private suspend fun ready() {
        withTimeoutOrNull(6_000) { core.connected.filterNotNull().first() }
    }

    private suspend fun pendingQuestion(chatId: String): String? {
        val runId = dao.messages(chatId).lastOrNull { it.role == ChatRole.ASSISTANT && it.runId != null }?.runId ?: return null
        val run = ops.run(runId) ?: return null
        if (run.state != RunState.WAITING_USER.name) return null
        val question = ops.lastEvent(runId, "question") ?: return null
        val approval = ops.lastEvent(runId, "approval")
        return runId.takeIf { approval == null || question.sequence > approval.sequence }
    }

    private suspend fun importFile(uri: Uri): ChatAttachment? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var name = "file"
            var size = 0L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            if (size > MAX_FILE) return@runCatching null
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val target = File(ChatFiles.dir(context), UUID.randomUUID().toString().take(8) + "-" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
            ChatAttachment(target.absolutePath, name, mime, target.length())
        }.getOrNull()
    }

    private companion object {
        val json = Json { encodeDefaults = false }
        const val MAX_FILE = 25L * 1024 * 1024
    }
}
