package com.paulneja.quorvi.connections.ssh

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable
data class HostHealth(
    val kernel: String,
    val uptimeSec: Long?,
    val load1: Double?,
    val load5: Double?,
    val load15: Double?,
    val cpus: Int?,
    val memTotal: Long?,
    val memAvailable: Long?,
    val diskTotal: Long?,
    val diskFree: Long?,
    val failedUnits: List<String>,
    val docker: Boolean,
) {
    val memUsedRatio: Double? get() = if (memTotal != null && memAvailable != null && memTotal > 0) 1.0 - memAvailable.toDouble() / memTotal else null
    val diskUsedRatio: Double? get() = if (diskTotal != null && diskFree != null && diskTotal > 0) 1.0 - diskFree.toDouble() / diskTotal else null
    val loadPerCpu: Double? get() = if (load1 != null && cpus != null && cpus > 0) load1 / cpus else null
    val healthy: Boolean get() = failedUnits.isEmpty() && (memUsedRatio ?: 0.0) < 0.95 && (diskUsedRatio ?: 0.0) < 0.95 && (loadPerCpu ?: 0.0) < 2.0
}

object HostProbe {
    const val COMMAND = "uname -srm; echo ---; cat /proc/uptime; echo ---; cat /proc/loadavg; echo ---; nproc 2>/dev/null; echo ---; " +
        "free -b 2>/dev/null | awk '/^Mem:/ {print \$2, \$7}'; echo ---; df -P -B1 / 2>/dev/null | awk 'NR==2 {print \$2, \$4}'; echo ---; " +
        "systemctl --failed --no-legend --plain 2>/dev/null | awk '{print \$1}'; echo ---; command -v docker >/dev/null 2>&1 && echo yes || echo no"

    fun parse(output: String): HostHealth {
        val parts = output.split(Regex("(?m)^---\\s*$")).map { it.trim() }
        fun part(i: Int) = parts.getOrNull(i).orEmpty()
        val load = part(2).split(Regex("\\s+"))
        val mem = part(4).split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        val disk = part(5).split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        return HostHealth(
            kernel = part(0).lineSequence().firstOrNull().orEmpty(),
            uptimeSec = part(1).substringBefore(' ').toDoubleOrNull()?.toLong(),
            load1 = load.getOrNull(0)?.toDoubleOrNull(),
            load5 = load.getOrNull(1)?.toDoubleOrNull(),
            load15 = load.getOrNull(2)?.toDoubleOrNull(),
            cpus = part(3).toIntOrNull(),
            memTotal = mem.getOrNull(0),
            memAvailable = mem.getOrNull(1),
            diskTotal = disk.getOrNull(0),
            diskFree = disk.getOrNull(1),
            failedUnits = part(6).lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("0 ") },
            docker = part(7) == "yes",
        )
    }
}

@Serializable
data class DockerContainer(val id: String, val name: String, val image: String, val state: String, val status: String)

enum class DockerAction(val verb: String) { START("start"), STOP("stop"), RESTART("restart") }

object Docker {
    const val LIST = "docker ps -a --no-trunc --format '{{json .}}'"
    private val safeId = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(output: String): List<DockerContainer> = output.lineSequence().map { it.trim() }.filter { it.startsWith("{") }.mapNotNull { line ->
        val o = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@mapNotNull null
        fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val id = o.s("ID").take(64)
        if (id.isEmpty()) null else DockerContainer(id, o.s("Names"), o.s("Image"), o.s("State").ifEmpty { if (o.s("Status").startsWith("Up")) "running" else "exited" }, o.s("Status"))
    }.toList()

    fun action(action: DockerAction, id: String): String {
        require(safeId.matches(id)) { "Invalid container id" }
        return "docker ${action.verb} $id"
    }

    fun logs(id: String, tail: Int = 200): String {
        require(safeId.matches(id)) { "Invalid container id" }
        return "docker logs --tail ${tail.coerceIn(1, 2000)} $id 2>&1"
    }
}
