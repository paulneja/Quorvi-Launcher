package com.paulneja.quorvi.agent.policy

import com.paulneja.quorvi.agent.tools.Risk
import com.paulneja.quorvi.agent.tools.Tool
import com.paulneja.quorvi.agent.tools.stringOrNull
import com.paulneja.quorvi.agent.tools.strings
import java.net.InetAddress
import java.net.URI
import kotlinx.serialization.json.JsonObject

enum class AutonomyMode { MANUAL, AUTO, BYPASS }

enum class Verdict { ALLOW, DENY, REQUIRE_CONFIRMATION, WAIT_FOR_CONTEXT }

data class Decision(val verdict: Verdict, val reason: String, val publish: Boolean = false)

const val PUBLISH_REASON_GIT = "git push needs an explicit publishing permission"
const val PUBLISH_REASON_GH = "gh write operations need an explicit publishing permission"

fun interface HostResolver {
    fun resolve(host: String): List<InetAddress>
}

class PolicyEngine(
    private val mode: AutonomyMode = AutonomyMode.AUTO,
    private val resolver: HostResolver = HostResolver { InetAddress.getAllByName(it).toList() },
    private val relaxShell: Boolean = false,
    private val relaxNetwork: Boolean = false,
) {
    fun evaluate(tool: Tool, args: JsonObject): Decision {
        if (mode == AutonomyMode.MANUAL && tool.risk != Risk.READ) return Decision(Verdict.REQUIRE_CONFIRMATION, "Manual mode: ${tool.name} needs approval")
        return when (tool.name) {
            "shell.exec", "android.shell" -> shell(runCatching { args.strings("argv") }.getOrElse { return Decision(Verdict.DENY, it.message.orEmpty()) })
            "web.fetch" -> network(args.stringOrNull("url").orEmpty())

            "job.start" -> shell(listOf("bash", "-c", args.stringOrNull("command").orEmpty()))
            "ssh.exec", "remote.run" -> shell(listOf("sh", "-c", args.stringOrNull("command").orEmpty()))
            "ha.call_service", "android.screenshot", "android.launch", "android.list_apps", "android.read_notifications" -> Decision(Verdict.ALLOW, "the category rule decides")
            "android.priv_shell", "android.root" -> Decision(Verdict.ALLOW, "privileged action; the category rule decides")
            "discord.post" -> Decision(Verdict.DENY, "Posting to Discord needs an explicit publishing permission", publish = true)
            else -> if (tool.risk == Risk.EXTERNAL) Decision(Verdict.REQUIRE_CONFIRMATION, "${tool.name} acts outside the device") else Decision(Verdict.ALLOW, "within the run workspace")
        }
    }

    fun shell(argv: List<String>): Decision {
        if (argv.isEmpty()) return Decision(Verdict.DENY, "Empty command")
        val words = argv.flatMap { it.split(Regex("\\s+")) }.filter { it.isNotEmpty() }
        val program = words.first().substringAfterLast('/')
        if (!relaxShell && program in setOf("su", "sudo", "tsu")) return Decision(Verdict.DENY, "Privilege escalation is not available to agents")
        for (i in words.indices) {
            val w = words[i].substringAfterLast('/')
            val next = words.getOrNull(i + 1)
            if (w == "git" && words.drop(i + 1).take(4).any { it == "push" }) return if (relaxShell) Decision(Verdict.ALLOW, "publishing allowed by settings") else Decision(Verdict.DENY, PUBLISH_REASON_GIT, publish = true)
            if (w == "gh" && next in setOf("pr", "release", "repo", "workflow", "run") && words.drop(i + 2).take(2).any { it in setOf("create", "merge", "delete", "edit", "rerun", "cancel") })
                return if (relaxShell) Decision(Verdict.ALLOW, "publishing allowed by settings") else Decision(Verdict.DENY, PUBLISH_REASON_GH, publish = true)
            if (!relaxShell && w == "rm" && words.drop(i + 1).any { it.startsWith("/") || it == "~" || it.startsWith("~/") || it == ".." })
                return Decision(Verdict.DENY, "Deleting outside the run workspace is not allowed")
        }
        return Decision(Verdict.ALLOW, "command runs inside the Quorvi runtime")
    }

    fun network(url: String): Decision {
        val uri = runCatching { URI(url) }.getOrNull() ?: return Decision(Verdict.DENY, "Invalid URL")
        if (uri.scheme !in setOf("http", "https")) return Decision(Verdict.DENY, "Only http and https are allowed")
        val host = uri.host?.lowercase() ?: return Decision(Verdict.DENY, "URL without host")
        if (relaxNetwork) return Decision(Verdict.ALLOW, "local network allowed by settings")
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan")) return Decision(Verdict.DENY, "Local network targets are not allowed")
        val addresses = runCatching { resolver.resolve(host) }.getOrElse { return Decision(Verdict.WAIT_FOR_CONTEXT, "Could not resolve $host") }
        if (addresses.isEmpty()) return Decision(Verdict.WAIT_FOR_CONTEXT, "Could not resolve $host")
        if (addresses.any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress || isUniqueLocal(it) || isMetadata(it) })
            return Decision(Verdict.DENY, "$host resolves to a private or local address")
        return Decision(Verdict.ALLOW, "public web address")
    }

    private fun isUniqueLocal(address: InetAddress): Boolean = address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc

    private fun isMetadata(address: InetAddress): Boolean = address.hostAddress == "169.254.169.254" || address.hostAddress?.startsWith("100.100.100.200") == true
}
