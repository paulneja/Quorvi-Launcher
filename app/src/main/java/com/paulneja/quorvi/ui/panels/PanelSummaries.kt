package com.paulneja.quorvi.ui.panels

import androidx.compose.runtime.Immutable
import com.paulneja.quorvi.launcher.live.NetState
import com.paulneja.quorvi.ops.CiExecutionEntity
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ui.operations.Status
import com.paulneja.quorvi.ui.operations.ciStatus

@Immutable
data class RunView(
    val id: String,
    val goal: String,
    val state: RunState,
    val done: Int,
    val total: Int,
    val currentStep: String?,
    val updatedAt: Long,
    val error: String?,
    val origin: String = "",
) {
    val chat: Boolean get() = origin.startsWith("chat:")
}

sealed interface AgentSummary {
    data class Running(val count: Int, val done: Int, val total: Int) : AgentSummary
    data object Idle : AgentSummary
    data object NoModel : AgentSummary
}

sealed interface GitHubSummary {
    data object SignedOut : GitHubSummary
    data object Empty : GitHubSummary

    data class NoCi(val repos: Int, val prs: Int?) : GitHubSummary
    data class Ok(val prs: Int?) : GitHubSummary
    data class Running(val prs: Int?) : GitHubSummary
    data class Failing(val count: Int, val prs: Int?) : GitHubSummary
}

sealed interface NetSummary {
    data object Wifi : NetSummary
    data class Cellular(val operator: String?) : NetSummary
    data object Airplane : NetSummary
    data object Offline : NetSummary
}

object PanelSummaries {
    fun agent(runs: List<RunView>, hasModel: Boolean): AgentSummary {
        val active = runs.filter { it.state.active }.sortedByDescending { it.updatedAt }
        return when {
            active.isNotEmpty() -> AgentSummary.Running(active.size, active.first().done, active.first().total)
            !hasModel -> AgentSummary.NoModel
            else -> AgentSummary.Idle
        }
    }

    fun latestPerRepo(runs: List<CiExecutionEntity>): List<CiExecutionEntity> =
        runs.groupBy { it.repo }.values.map { group -> group.maxBy { it.createdAt ?: it.fetchedAt } }.sortedByDescending { it.createdAt ?: it.fetchedAt }

    fun github(signedIn: Boolean, runs: List<CiExecutionEntity>, prs: Int?, watched: Int = 0): GitHubSummary {
        if (!signedIn) return GitHubSummary.SignedOut
        val latest = latestPerRepo(runs)
        if (latest.isEmpty()) return when {
            watched > 0 -> GitHubSummary.NoCi(watched, prs)
            prs != null && prs > 0 -> GitHubSummary.Ok(prs)
            else -> GitHubSummary.Empty
        }
        val statuses = latest.map { ciStatus(it.status, it.conclusion) }
        val failing = statuses.count { it == Status.FAILURE }
        return when {
            failing > 0 -> GitHubSummary.Failing(failing, prs)
            statuses.any { it == Status.RUNNING || it == Status.QUEUED } -> GitHubSummary.Running(prs)
            else -> GitHubSummary.Ok(prs)
        }
    }

    fun network(state: NetState): NetSummary = when {
        state.wifiConnected -> NetSummary.Wifi
        state.cellularActive -> NetSummary.Cellular(state.operator)
        state.airplane -> NetSummary.Airplane
        else -> NetSummary.Offline
    }
}
