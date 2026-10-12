package com.paulneja.quorvi.agent.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RunSpec(
    val goal: String,
    val label: String? = null,
    val origin: String = "command",
    val ciExecutionId: String? = null,
    val mode: String = "AUTO",
    val chatId: String? = null,

    val meta: Boolean = false,

    val specialists: List<String> = emptyList(),

    val research: Boolean = false,

    val plan: Boolean = false,

    val voice: Boolean = false,

    val jobId: String? = null,
)

enum class GoalKind { DOCUMENT, CI_DIAGNOSIS, GENERAL }

data class Classification(val kind: GoalKind, val needsExport: Boolean, val needsRuntime: Boolean)

object Classifier {
    private val downloads = Regex("(?i)descargas|downloads?\\b|\\bdescarg")
    private val document = Regex("(?i)\\b(documento|document|informe|report|pdf|archivo|file|resumen|ensayo|essay|presentaci[oó]n|art[ií]culo)\\b")
    private val ci = Regex("(?i)\\b(workflow|actions|pipeline|ci|build|job)\\b")
    private val failure = Regex("(?i)fall[oóaé]|fail|rompi|broke|error|rojo|red\\b")

    fun classify(spec: RunSpec): Classification = when {
        spec.ciExecutionId != null || (ci.containsMatchIn(spec.goal) && failure.containsMatchIn(spec.goal)) -> Classification(GoalKind.CI_DIAGNOSIS, needsExport = false, needsRuntime = true)
        downloads.containsMatchIn(spec.goal) || document.containsMatchIn(spec.goal) -> Classification(GoalKind.DOCUMENT, needsExport = true, needsRuntime = true)
        else -> Classification(GoalKind.GENERAL, needsExport = false, needsRuntime = true)
    }
}

@Serializable
data class PlannedStep(val id: String, val title: String, val dependsOn: List<String> = emptyList())

@Serializable
private data class PlanBody(val steps: List<PlannedStep> = emptyList())

object PlanParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    const val MAX_STEPS = 8

    fun parse(text: String): List<PlannedStep>? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val body = runCatching { json.decodeFromString(PlanBody.serializer(), text.substring(start, end + 1)) }.getOrNull() ?: return null
        val steps = body.steps.filter { it.title.isNotBlank() }.take(MAX_STEPS)
            .mapIndexed { i, s -> s.copy(id = s.id.trim().ifEmpty { "s${i + 1}" }.take(24), title = s.title.trim().take(160)) }
        if (steps.isEmpty() || steps.map { it.id }.toSet().size != steps.size) return null
        val ids = steps.map { it.id }.toSet()
        val cleaned = steps.map { s -> s.copy(dependsOn = s.dependsOn.filter { it in ids && it != s.id }.distinct()) }
        return order(cleaned)
    }

    fun order(steps: List<PlannedStep>): List<PlannedStep>? {
        val byId = steps.associateBy { it.id }
        val done = linkedSetOf<String>()
        val remaining = steps.toMutableList()
        while (remaining.isNotEmpty()) {
            val next = remaining.firstOrNull { s -> s.dependsOn.all { it in done } } ?: return null
            done += next.id
            remaining.remove(next)
        }
        return done.map { byId.getValue(it) }
    }

    fun fallback(kind: GoalKind): List<PlannedStep> = when (kind) {
        GoalKind.DOCUMENT -> listOf(
            PlannedStep("research", "Research the topic with web sources and keep URLs"),
            PlannedStep("outline", "Write an outline", listOf("research")),
            PlannedStep("draft", "Write the full document in the workspace", listOf("outline")),
            PlannedStep("export", "Check the document and export it to Downloads", listOf("draft")),
        )
        GoalKind.CI_DIAGNOSIS -> listOf(
            PlannedStep("logs", "Read the failed jobs and their logs"),
            PlannedStep("analyze", "Identify the cause with evidence; inspect code if needed", listOf("logs")),
            PlannedStep("report", "Write report.md with diagnosis and an optional patch", listOf("analyze")),
        )
        GoalKind.GENERAL -> listOf(PlannedStep("do", "Carry out the request"))
    }
}
