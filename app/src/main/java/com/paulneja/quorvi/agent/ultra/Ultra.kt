package com.paulneja.quorvi.agent.ultra

import com.paulneja.quorvi.agent.orchestra.AgentConfigDao
import com.paulneja.quorvi.agent.orchestra.AgentConfigEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class CommitStyle { CONVENTIONAL, SIMPLE, CUSTOM }

enum class CommitLanguage { ES, EN }

@Serializable
data class UltraConfig(
    val commitStyle: CommitStyle = CommitStyle.CONVENTIONAL,
    val commitLanguage: CommitLanguage = CommitLanguage.ES,
    val customStyle: String = "",
    val branchPrefix: String = "nexo/",
    val draftPullRequests: Boolean = true,
    val allowCommands: Boolean = true,

    val allowedCoauthors: List<String> = emptyList(),
    val instructions: String = "",
) {
    fun clamped() = copy(branchPrefix = branchPrefix.filter { it.isLetterOrDigit() || it in "/-_." }.take(40), customStyle = customStyle.take(2000), instructions = instructions.take(4000))

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(raw: String?): UltraConfig = raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }?.clamped() ?: UltraConfig()
        fun encode(c: UltraConfig): String = json.encodeToString(serializer(), c.clamped())
    }
}

@Singleton
class UltraStore @Inject constructor(private val dao: AgentConfigDao) {
    val config: Flow<UltraConfig> = dao.observe(KEY).map { UltraConfig.decode(it) }

    suspend fun current(): UltraConfig = UltraConfig.decode(dao.get(KEY))

    suspend fun update(change: (UltraConfig) -> UltraConfig) = dao.put(AgentConfigEntity(KEY, UltraConfig.encode(change(current()))))

    private companion object {
        const val KEY = "ultra"
    }
}

object CommitPolicy {
    private val trailer = Regex("(?im)^\\s*(co-authored-by|co-author|coauthored-by|signed-off-by|helped-by|assisted-by)\\s*:\\s*(.*)$")
    private val attribution = Regex("(?i)(generated (with|by) (claude|chatgpt|copilot|nexo|ai|an ai)|🤖|\\bai[- ]generated\\b|\\[bot\\]@users\\.noreply)")

    data class Violation(val line: String, val reason: String)

    fun violations(text: String, allowed: List<String> = emptyList()): List<Violation> = text.lines().mapNotNull { line ->
        val m = trailer.find(line)
        when {
            m != null -> m.groupValues[2].trim().takeIf { who -> allowed.none { it.equals(who, ignoreCase = true) } }?.let { Violation(line.trim(), "co-author") }
            attribution.containsMatchIn(line) -> Violation(line.trim(), "attribution")
            else -> null
        }
    }

    fun check(texts: List<String>, allowed: List<String> = emptyList()): List<Violation> = texts.flatMap { violations(it, allowed) }.distinct()

    fun blockedCommand(command: String): String? {
        val c = command.replace(Regex("\\s+"), " ").lowercase()
        return when {
            Regex("\\bgit( -c [^ ]+)* push\\b").containsMatchIn(c) -> "git push is not allowed: leave the changes uncommitted and call pr_propose."
            Regex("\\bgit( -c [^ ]+)* commit\\b").containsMatchIn(c) -> "git commit is not allowed: Nexo writes the commit when the user approves the pull request. Leave the changes uncommitted."
            Regex("\\bgit( -c [^ ]+)* (merge|rebase|reset --hard|filter-branch|replace)\\b").containsMatchIn(c) -> "That git command rewrites history and is not allowed here."
            Regex("\\bgh (pr|repo|release|api)\\b").containsMatchIn(c) -> "The gh CLI is not allowed: GitHub actions go through Nexo's pull request flow."
            Regex("\\b(credential|git config [^ ]*(user|credential))").containsMatchIn(c) -> "Changing git identity or credentials is not allowed."
            else -> null
        }
    }

    fun styleRule(config: UltraConfig): String {
        val language = if (config.commitLanguage == CommitLanguage.ES) "Spanish" else "English"
        val style = when (config.commitStyle) {
            CommitStyle.CONVENTIONAL -> "Conventional Commits (feat:, fix:, refactor:, docs:, test:, chore:), imperative, at most 72 characters in the subject"
            CommitStyle.SIMPLE -> "one short imperative sentence, at most 72 characters"
            CommitStyle.CUSTOM -> config.customStyle.ifBlank { "the user's usual style" }
        }
        return "Commit messages and PR titles: $style, written in $language."
    }
}
