package com.paulneja.quorvi.agent.policy

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.paulneja.quorvi.agent.tools.Risk
import com.paulneja.quorvi.agent.tools.Tool
import com.paulneja.quorvi.agent.tools.stringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

enum class Category { WORKSPACE, SHELL, SSH, NETWORK, EXPORT, DEVICES, SCREEN, PRIVILEGED, PUBLISH, UNUSUAL }

enum class Rule { ALLOW, ASK, DENY }

data class PolicyConfig(
    val mode: AutonomyMode = AutonomyMode.AUTO,
    val rules: Map<Category, Rule> = DEFAULT_RULES,
    val minBattery: Int = 15,
    val networkOnlyUnmetered: Boolean = false,
    val relaxShell: Boolean = false,
    val relaxNetwork: Boolean = false,

    val freeWeb: Boolean = false,
) {
    fun rule(category: Category): Rule = rules[category] ?: DEFAULT_RULES.getValue(category)

    companion object {
        val DEFAULT_RULES = mapOf(
            Category.WORKSPACE to Rule.ALLOW,
            Category.SHELL to Rule.ALLOW,
            Category.SSH to Rule.ALLOW,
            Category.NETWORK to Rule.ALLOW,
            Category.EXPORT to Rule.ALLOW,
            Category.DEVICES to Rule.ASK,

            Category.SCREEN to Rule.ASK,
            Category.PRIVILEGED to Rule.ASK,
            Category.PUBLISH to Rule.ASK,
            Category.UNUSUAL to Rule.ASK,
        )

        fun from(settings: Map<String, String>) = PolicyConfig(
            mode = settings["mode"]?.let { runCatching { AutonomyMode.valueOf(it) }.getOrNull() } ?: AutonomyMode.AUTO,
            rules = Category.entries.associateWith { c -> settings["rule.${c.name}"]?.let { runCatching { Rule.valueOf(it) }.getOrNull() } ?: DEFAULT_RULES.getValue(c) },
            minBattery = settings["minBattery"]?.toIntOrNull() ?: 15,
            networkOnlyUnmetered = settings["unmetered"] == "true",
            relaxShell = settings["relaxShell"] == "true",
            relaxNetwork = settings["relaxNetwork"] == "true",
            freeWeb = settings["freeWeb"] == "true",
        )
    }
}

data class DeviceState(val battery: Int, val charging: Boolean, val unmetered: Boolean)

fun interface DeviceContext {
    fun state(): DeviceState
}

class AndroidDeviceContext @Inject constructor(@ApplicationContext private val context: Context) : DeviceContext {
    override fun state(): DeviceState {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 100
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        return DeviceState(level, plugged, caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true)
    }
}

data class GateDecision(val verdict: Verdict, val reason: String, val action: String, val resource: String, val approvalId: String? = null)

interface PolicyGate {
    suspend fun check(runId: String, tool: Tool, args: JsonObject): GateDecision
}

interface PolicyStore {
    suspend fun config(): PolicyConfig
    suspend fun grants(action: String, runId: String, now: Long): List<GrantEntity>
    suspend fun consume(grant: GrantEntity)
    suspend fun requestApproval(approval: ApprovalEntity)
    suspend fun audit(entry: AuditEntity)
}

class RoomPolicyStore @Inject constructor(private val dao: PolicyDao) : PolicyStore {
    private var writes = 0

    override suspend fun config() = PolicyConfig.from(dao.settings().associate { it.key to it.value })
    override suspend fun grants(action: String, runId: String, now: Long) = dao.grantsFor(action, runId, now)
    override suspend fun consume(grant: GrantEntity) = dao.consume(grant.id)
    override suspend fun requestApproval(approval: ApprovalEntity) = dao.putApproval(approval)

    override suspend fun audit(entry: AuditEntity) {
        dao.audit(entry)
        if (++writes % 200 == 0) dao.rotateAudit(5_000)
    }
}

class PolicyService(
    private val store: PolicyStore,
    private val device: DeviceContext,
    private val engineFor: (PolicyConfig) -> PolicyEngine = { PolicyEngine(it.mode, relaxShell = it.relaxShell, relaxNetwork = it.relaxNetwork || it.freeWeb) },
    private val clock: () -> Long = System::currentTimeMillis,
) : PolicyGate {
    override suspend fun check(runId: String, tool: Tool, args: JsonObject): GateDecision {
        val config = store.config()
        val base = engineFor(config).evaluate(tool, args)
        val resource = resourceOf(tool, args)
        val odd = Watch.unusual(tool.name, resource)
        val category = when {
            base.publish -> Category.PUBLISH
            Watch.usesRoot(tool.name, resource) -> Category.PRIVILEGED
            odd != null -> Category.UNUSUAL
            else -> categoryOf(tool)
        }
        val action = when (category) {
            Category.PUBLISH -> "publish"
            Category.PRIVILEGED -> ROOT
            Category.UNUSUAL -> UNUSUAL
            else -> tool.name
        }
        val decision = decide(runId, tool, base, category, action, resource, config, odd)
        store.audit(AuditEntity(at = clock(), subject = "run:$runId", action = action, resource = resource.take(300), verdict = decision.verdict.name, reason = decision.reason.take(300), runId = runId))
        return decision
    }

    private suspend fun decide(runId: String, tool: Tool, base: Decision, category: Category, action: String, resource: String, config: PolicyConfig, odd: String?): GateDecision {
        if (base.verdict == Verdict.DENY && !base.publish) return GateDecision(Verdict.DENY, base.reason, action, resource)
        if (base.verdict == Verdict.WAIT_FOR_CONTEXT) return GateDecision(Verdict.WAIT_FOR_CONTEXT, base.reason, action, resource)

        var rule = config.rule(category)

        if (category == Category.PRIVILEGED || category == Category.UNUSUAL) {
            rule = if (rule == Rule.DENY) Rule.DENY else Rule.ASK
        } else if (category == Category.NETWORK && config.freeWeb) {
            rule = Rule.ALLOW
        } else if (category != Category.WORKSPACE && tool.risk != Risk.READ) {
            rule = when (config.mode) {
                AutonomyMode.MANUAL -> if (rule == Rule.DENY) Rule.DENY else Rule.ASK
                AutonomyMode.BYPASS -> if (rule == Rule.ASK && category != Category.PUBLISH) Rule.ALLOW else rule
                AutonomyMode.AUTO -> rule
            }
        }
        if (rule == Rule.DENY) return GateDecision(Verdict.DENY, "Policy denies ${category.name.lowercase()} actions", action, resource)
        if (rule == Rule.ASK) {
            val grant = store.grants(action, runId, clock()).firstOrNull { g ->
                if (category == Category.PRIVILEGED) g.usesLeft == 1 && g.resource == resource else g.resource == "*" || resource.startsWith(g.resource)
            }
            if (grant == null) {
                val reason = when {
                    base.publish -> base.reason
                    category == Category.PRIVILEGED -> "Runs as root: every root command is approved one by one."
                    odd != null -> odd
                    else -> "Policy asks before ${category.name.lowercase()} actions"
                }
                val approval = ApprovalEntity(UUID.randomUUID().toString(), runId, action, resource, summaryOf(tool, resource), reason, clock())
                store.requestApproval(approval)
                return GateDecision(Verdict.REQUIRE_CONFIRMATION, approval.reason, action, resource, approval.id)
            }
            store.consume(grant)
        }

        if (tool.risk != Risk.READ) {
            val state = device.state()
            if (!state.charging && state.battery < config.minBattery) return GateDecision(Verdict.WAIT_FOR_CONTEXT, "battery at ${state.battery}% (minimum ${config.minBattery}%)", action, resource)
            if (category == Category.NETWORK && config.networkOnlyUnmetered && !state.unmetered) return GateDecision(Verdict.WAIT_FOR_CONTEXT, "waiting for an unmetered network", action, resource)
        }
        return GateDecision(Verdict.ALLOW, base.reason, action, resource)
    }

    companion object {
        const val ROOT = "root"
        const val UNUSUAL = "unusual"

        fun categoryOf(tool: Tool): Category = when (tool.name) {
            "android.priv_shell", "android.root", "android.power_hook" -> Category.PRIVILEGED
            "android.inspect_ui", "android.act", "android.screenshot", "android.launch", "android.list_apps", "android.read_notifications", "android.shell" -> Category.SCREEN
            else -> when (tool.name.substringBefore('.')) {
                "fs" -> Category.WORKSPACE
                "shell" -> Category.SHELL
                "ssh", "remote" -> Category.SSH
                "ha" -> Category.DEVICES
                "web", "ci" -> Category.NETWORK
                "artifact" -> Category.EXPORT
                else -> if (tool.risk == Risk.EXTERNAL) Category.PUBLISH else Category.WORKSPACE
            }
        }

        fun resourceOf(tool: Tool, args: JsonObject): String = when (tool.name) {
            "shell.exec", "android.shell" -> args["argv"].toString()
            "android.priv_shell", "android.root" -> "${args.stringOrNull("level") ?: "exec"}: ${args.stringOrNull("command")}"
            "android.act" -> "${args.stringOrNull("action")} ${args.stringOrNull("target").orEmpty()}"
            "android.launch" -> args.stringOrNull("package").orEmpty()
            "ssh.exec" -> "${args.stringOrNull("host")}: ${args.stringOrNull("command")}"
            "remote.run" -> "${args.stringOrNull("worker")}: ${args.stringOrNull("command")}"
            "ha.call_service" -> "${args.stringOrNull("entity_id")} ${args.stringOrNull("service")}"
            "discord.post" -> "${args.stringOrNull("channel")}: ${args.stringOrNull("message")}"
            "web.fetch" -> args.stringOrNull("url").orEmpty()
            "artifact.export" -> args.stringOrNull("name").orEmpty()
            else -> args.toString()
        }

        private fun summaryOf(tool: Tool, resource: String) = "${tool.name}: ${resource.take(200)}"
    }
}

@Singleton
class Approvals @Inject constructor(private val dao: PolicyDao) {
    suspend fun approve(id: String, requested: ApprovalScope, now: Long = System.currentTimeMillis()): ApprovalEntity? {
        val approval = dao.approval(id) ?: return null

        val scope = if (approval.action == PolicyService.ROOT) ApprovalScope.ONCE else requested
        dao.decide(id, "APPROVED", now)
        dao.putGrant(
            GrantEntity(
                id = UUID.randomUUID().toString(),
                subject = if (scope == ApprovalScope.HOUR) "agent" else approval.runId,
                action = approval.action,
                resource = if (scope == ApprovalScope.ONCE) approval.resource else "*",
                createdAt = now,
                expiresAt = if (scope == ApprovalScope.HOUR) now + (if (approval.action == PolicyService.UNUSUAL) SESSION_MS else 3_600_000) else null,
                usesLeft = if (scope == ApprovalScope.ONCE) 1 else null,
                reason = approval.summary,
            ),
        )
        return approval
    }

    suspend fun deny(id: String, now: Long = System.currentTimeMillis()): ApprovalEntity? {
        val approval = dao.approval(id) ?: return null
        dao.decide(id, "DENIED", now)
        return approval
    }
}

enum class ApprovalScope { ONCE, RUN, HOUR }

private const val SESSION_MS = 6 * 3_600_000L
