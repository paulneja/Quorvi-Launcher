package com.paulneja.quorvi.connections

import com.paulneja.quorvi.connections.github.PendingParse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPendingTest {
    private val involved = Json.parseToJsonElement(
        """{"items":[
            {"number":4,"title":"Crash al abrir","repository_url":"https://api.github.com/repos/me/app","updated_at":"2026-10-10T10:00:00Z","html_url":"https://github.com/me/app/issues/4"},
            {"number":9,"title":"Nueva pantalla","repository_url":"https://api.github.com/repos/me/app","updated_at":"2026-10-11T09:00:00Z","html_url":"https://github.com/me/app/pull/9","pull_request":{}}
        ]}""",
    )
    private val reviews = Json.parseToJsonElement(
        """{"items":[{"number":9,"title":"Nueva pantalla","repository_url":"https://api.github.com/repos/me/app","updated_at":"2026-10-11T09:00:00Z","html_url":"https://github.com/me/app/pull/9","pull_request":{}}]}""",
    )

    @Test
    fun readsIssuesAndPullRequests() {
        val items = PendingParse.items(involved)
        assertEquals(listOf("me/app#4", "me/app#9"), items.map { it.key })
        assertEquals(listOf(false, true), items.map { it.pr })
    }

    @Test
    fun reviewRequestsComeFirstAndAreNotRepeated() {
        val merged = PendingParse.merge(PendingParse.items(reviews, review = true), PendingParse.items(involved))
        assertEquals(listOf("me/app#9", "me/app#4"), merged.map { it.key })
        assertTrue(merged.first().review)
    }
}
