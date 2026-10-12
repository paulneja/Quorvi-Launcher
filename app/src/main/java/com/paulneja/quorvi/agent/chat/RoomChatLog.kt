package com.paulneja.quorvi.agent.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.paulneja.quorvi.agent.engine.ChatLog
import com.paulneja.quorvi.agent.models.AiPart
import com.paulneja.quorvi.ops.OpsDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class RoomChatLog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ChatDao,
    private val ops: OpsDao,
) : ChatLog {
    override suspend fun history(chatId: String): List<ChatMessageEntity> = dao.messages(chatId)

    override suspend fun begin(chatId: String, runId: String): String {
        val id = UUID.randomUUID().toString()
        dao.upsertMessage(ChatMessageEntity(id, chatId, ChatRole.ASSISTANT, "", ChatStatus.STREAMING, runId, System.currentTimeMillis()))
        return id
    }

    override suspend fun reopen(chatId: String, runId: String): Pair<String, String>? {
        val last = dao.messages(chatId).lastOrNull { it.role == ChatRole.ASSISTANT && it.runId == runId } ?: return null
        dao.setText(last.id, last.text, ChatStatus.STREAMING)
        return last.id to last.text
    }

    override suspend fun stream(messageId: String, text: String) = dao.setText(messageId, text, ChatStatus.STREAMING)

    override suspend fun finish(messageId: String, text: String, status: String, files: List<ChatAttachment>) {
        dao.finish(messageId, text, status, ChatAttachment.encode(files))
        dao.message(messageId)?.let { dao.touch(it.chatId, System.currentTimeMillis()) }
    }

    override suspend fun task(chatId: String, runId: String, goal: String) {
        dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), chatId, ChatRole.TASK, goal.take(500), ChatStatus.DONE, runId, System.currentTimeMillis()))
    }

    override suspend fun search(query: String, excludeChat: String, limit: Int): List<String> {
        val words = query.split(Regex("\\s+")).filter { it.length > 2 }.ifEmpty { listOf(query.trim()) }.filter { it.isNotBlank() }
        val hits = words.flatMap { dao.search(it, excludeChat, limit) }.distinctBy { it.id }.take(limit)
        val titles = hits.map { it.chatId }.distinct().associateWith { dao.chat(it)?.title.orEmpty() }
        return hits.map { m -> "- [${titles[m.chatId]}] ${m.role}: ${m.text.replace('\n', ' ').take(400)}" }
    }

    override suspend fun saveImage(bytes: ByteArray, prompt: String): ChatAttachment = withContext(Dispatchers.IO) {
        val ext = if (bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) "jpg" else "png"
        val mime = if (ext == "jpg") "image/jpeg" else "image/png"
        val name = "nexo-" + System.currentTimeMillis() + "." + ext
        val file = File(ChatFiles.dir(context), name).apply { writeBytes(bytes) }
        val uri = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.DESCRIPTION, prompt.take(250))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Quorvi")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also { u ->
                context.contentResolver.openOutputStream(u)?.use { it.write(bytes) }
            }?.toString()
        }.getOrNull()
        ChatAttachment(file.absolutePath, name, mime, bytes.size.toLong(), uri)
    }

    override suspend fun attach(file: ChatAttachment): AiPart? = withContext(Dispatchers.IO) {
        val f = File(file.path).takeIf { it.isFile } ?: return@withContext null
        when {
            file.image -> ChatFiles.downscaled(f)?.let { AiPart.Image(Base64.encodeToString(it, Base64.NO_WRAP), "image/jpeg") }
            ChatLogic.isText(file.mime, file.name) && f.length() <= ChatLogic.TEXT_LIMIT * 2 ->
                AiPart.Text("File ${file.name}:\n```\n" + f.readText().take(ChatLogic.TEXT_LIMIT) + "\n```")
            else -> null
        }
    }

    override suspend fun chatInfo(chatId: String): ChatEntity? = dao.chat(chatId)

    override suspend fun setRepo(chatId: String, repo: String) = dao.setRepo(chatId, repo)

    override suspend fun proposal(chatId: String, runId: String, draftJson: String) {
        dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), chatId, ChatRole.PR, draftJson, ChatStatus.DONE, runId, System.currentTimeMillis()))
    }

    override suspend fun offerUltra(chatId: String, runId: String, reason: String) {
        if (dao.messages(chatId).any { it.role == ChatRole.OFFER && it.status == ChatStatus.DONE }) return
        dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), chatId, ChatRole.OFFER, reason, ChatStatus.DONE, runId, System.currentTimeMillis()))
    }

    override suspend fun planDraft(chatId: String, runId: String, plan: String) {
        dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), chatId, ChatRole.PLAN, plan, ChatStatus.DONE, runId, System.currentTimeMillis()))
    }

    override suspend fun capsuleDraft(chatId: String, runId: String, capsuleJson: String) {
        dao.upsertMessage(ChatMessageEntity(UUID.randomUUID().toString(), chatId, ChatRole.CAPSULE, capsuleJson, ChatStatus.DONE, runId, System.currentTimeMillis()))
    }

    override suspend fun taskState(runId: String): String? = ops.run(runId)?.let { r -> r.state.lowercase() + (r.error?.let { ", $it" } ?: "") }
}

object ChatFiles {
    private const val MAX_SIDE = 1568

    fun dir(context: Context): File = File(context.filesDir, "chat").apply { mkdirs() }

    fun downscaled(file: File): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val sized = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        return ByteArrayOutputStream().use { out -> sized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
    }
}
