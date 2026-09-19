package com.paulneja.quorvi.ui

import com.paulneja.quorvi.launcher.live.NetState
import com.paulneja.quorvi.ops.CiExecutionEntity
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ui.panels.AgentSummary
import com.paulneja.quorvi.ui.panels.GitHubSummary
import com.paulneja.quorvi.ui.panels.NetSummary
import com.paulneja.quorvi.ui.panels.PanelSummaries
import com.paulneja.quorvi.ui.panels.RunView
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSummaryTest {
    private fun run(id: String, state: RunState, done: Int = 0, total: Int = 0, updated: Long = 0) =
        RunView(id, "goal $id", state, done, total, null, updated, null)

    private fun ci(repo: String, status: String, conclusion: String?, created: Long) = CiExecutionEntity(
        id = "$repo-$created", provider = "github", repo = repo, runId = created, workflow = "build", title = "t", branch = "main",
        event = "push", status = status, conclusion = conclusion, number = 1, attempt = 1, createdAt = created, updatedAt = created,
        startedAt = created, url = "", actor = null, fetchedAt = created,
    )

    @Test
    fun agentShowsRunningProgressOfTheNewestActiveRun() {
        val summary = PanelSummaries.agent(listOf(run("a", RunState.COMPLETED, 3, 3, 5), run("b", RunState.RUNNING, 2, 5, 9), run("c", RunState.PLANNING, 0, 0, 7)), hasModel = true)
        assertEquals(AgentSummary.Running(2, 2, 5), summary)
    }

    @Test
    fun agentAsksForAModelOnlyWhenNothingRuns() {
        assertEquals(AgentSummary.NoModel, PanelSummaries.agent(emptyList(), hasModel = false))
        assertEquals(AgentSummary.Idle, PanelSummaries.agent(listOf(run("a", RunState.FAILED)), hasModel = true))
    }

    @Test
    fun githubCountsOnlyTheLatestRunPerRepo() {
        val runs = listOf(
            ci("o/nexo", "completed", "failure", 1),
            ci("o/nexo", "completed", "success", 2),
            ci("o/dev", "completed", "failure", 3),
            ci("o/worker", "in_progress", null, 4),
        )
        assertEquals(GitHubSummary.Failing(1, prs = 2), PanelSummaries.github(signedIn = true, runs, prs = 2))
        assertEquals(GitHubSummary.Ok(prs = null), PanelSummaries.github(true, listOf(ci("o/a", "completed", "success", 1)), prs = null))
        assertEquals(GitHubSummary.SignedOut, PanelSummaries.github(false, runs, prs = 2))
        assertEquals(GitHubSummary.Empty, PanelSummaries.github(true, emptyList(), prs = null))
        assertEquals(GitHubSummary.NoCi(1, null), PanelSummaries.github(true, emptyList(), prs = null, watched = 1))
    }

    @Test
    fun networkPrefersWifiThenCellularThenOffline() {
        val base = NetState(false, false, false, null, false, null, false, null, null, null, "Personal")
        assertEquals(NetSummary.Wifi, PanelSummaries.network(base.copy(wifiEnabled = true, wifiConnected = true)))
        assertEquals(NetSummary.Cellular("Personal"), PanelSummaries.network(base.copy(cellularActive = true)))
        assertEquals(NetSummary.Airplane, PanelSummaries.network(base.copy(airplane = true)))
        assertEquals(NetSummary.Offline, PanelSummaries.network(base))
    }
}
