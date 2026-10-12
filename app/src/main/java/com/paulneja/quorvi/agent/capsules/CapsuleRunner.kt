package com.paulneja.quorvi.agent.capsules

import com.paulneja.quorvi.agent.engine.Agents
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Singleton
class CapsuleRunner @Inject constructor(private val dao: CapsuleDao, private val agents: Agents, private val chats: com.paulneja.quorvi.agent.chat.ChatDao) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(capsuleId: String, reason: String? = null): String {
        val entity = dao.get(capsuleId) ?: return ""
        val capsule = runCatching { json.decodeFromString(Capsule.serializer(), entity.json) }.getOrNull() ?: return ""
        if (capsule.ai && !agents.ai.current()) return ""
        dao.markRun(capsuleId, System.currentTimeMillis())

        if (capsule.ai) {
            val chatId = CapsuleLaunch.chatId(capsuleId)
            val now = System.currentTimeMillis()
            if (chats.chat(chatId) == null) chats.upsertChat(com.paulneja.quorvi.agent.chat.ChatEntity(chatId, "⚡ " + capsule.name, now, now))
            val text = (reason?.let { "($it)\n" } ?: "") + capsule.prompt
            chats.upsertMessage(com.paulneja.quorvi.agent.chat.ChatMessageEntity(java.util.UUID.randomUUID().toString(), chatId, com.paulneja.quorvi.agent.chat.ChatRole.USER, text, com.paulneja.quorvi.agent.chat.ChatStatus.DONE, null, now))
            chats.touch(chatId, now)
            return agents.engine.submit(agents.engine.encode(com.paulneja.quorvi.agent.engine.RunSpec(goal = capsule.prompt, origin = "chat:$chatId", chatId = chatId)))
        }
        val steps = capsule.steps.map { step -> step.tool to JsonObject(step.args) }
        return agents.engine.submitCapsule(capsuleId, capsule.name, steps)
    }
}
