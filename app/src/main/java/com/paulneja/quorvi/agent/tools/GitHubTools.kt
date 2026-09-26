package com.paulneja.quorvi.agent.tools

import com.paulneja.quorvi.connections.github.GitHubApi
import java.net.URLEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun githubTools(api: GitHubApi): List<Tool> = listOf(
    GhTool("github.me", "The signed-in user's profile and stats: repos (public and private), followers, plan, and recent activity.", Risk.NETWORK, schema { }) { _ ->
        val me = api.read("/user")
        val login = me.s("login")
        val events = api.read("/users/$login/events?per_page=20") as? JsonArray
        buildString {
            append("@").append(login).append(" (").append(me.s("name") ?: "").append(")\n")
            append("Public repos: ").append(me.s("public_repos")).append(" · private: ").append(me.s("total_private_repos") ?: "?")
            append(" · followers: ").append(me.s("followers")).append(" · following: ").append(me.s("following")).append('\n')
            me.s("plan", "name")?.let { append("Plan: ").append(it).append('\n') }
            append("Member since ").append(me.s("created_at")?.take(10)).append("\n\nRecent activity:\n")
            events?.take(20)?.forEach { e -> append("- ").append(e.s("created_at")?.take(10)).append(' ').append(e.s("type")).append(" in ").append(e.s("repo", "name")).append('\n') }
        }
    },
    GhTool("github.repos", "List the user's repositories (including private and organization ones), most recently pushed first.", Risk.NETWORK, schema { string("filter", "Optional text to match in the name", optional = true) }) { a ->
        val f = a.stringOrNull("filter")?.lowercase()
        api.repos(3).filter { f == null || it.fullName.lowercase().contains(f) }.take(80).joinToString("\n") { r -> r.fullName + if (r.private) " · private" else "" }.ifBlank { "No repositories." }
    },
    GhTool("github.repo", "Details of one repository (owner/name): description, stars, forks, open issues, default branch, languages and topics.", Risk.NETWORK, schema { string("repo", "owner/name") }) { a ->
        val repo = a.string("repo")
        val r = api.read("/repos/$repo")
        val langs = (api.read("/repos/$repo/languages") as? JsonObject)?.keys?.joinToString().orEmpty()
        "${r.s("full_name")}${if (r.s("private") == "true") " (private)" else ""}\n${r.s("description").orEmpty()}\n" +
            "Stars ${r.s("stargazers_count")} · forks ${r.s("forks_count")} · open issues ${r.s("open_issues_count")} · default branch ${r.s("default_branch")}\n" +
            "Languages: $langs\nPushed ${r.s("pushed_at")?.take(10)} · created ${r.s("created_at")?.take(10)}"
    },
    GhTool("github.file", "Read a file, or list a folder, of a repository at a branch, tag or commit.", Risk.NETWORK, schema {
        string("repo", "owner/name"); string("path", "Path inside the repo, empty for the root", optional = true); string("ref", "Branch, tag or commit", optional = true)
    }) { a ->
        val path = a.stringOrNull("path").orEmpty().trim('/')
        val ref = a.stringOrNull("ref")?.let { "?ref=" + enc(it) }.orEmpty()
        when (val c = api.read("/repos/${a.string("repo")}/contents/${path.split('/').joinToString("/") { enc(it) }}$ref")) {
            is JsonArray -> c.joinToString("\n") { e -> (if (e.s("type") == "dir") "📁 " else "") + e.s("path") }
            else -> {
                val raw = c.s("content")?.replace("\n", "") ?: return@GhTool "Not a text file."
                String(java.util.Base64.getDecoder().decode(raw), Charsets.UTF_8).take(60_000)
            }
        }
    },
    GhTool("github.commits", "Recent commits of a repository (optionally of a branch or touching a path).", Risk.NETWORK, schema {
        string("repo", "owner/name"); string("branch", "Branch or sha", optional = true); string("path", "Only commits touching this path", optional = true)
    }) { a ->
        val q = buildList { add("per_page=25"); a.stringOrNull("branch")?.let { add("sha=" + enc(it)) }; a.stringOrNull("path")?.let { add("path=" + enc(it)) } }.joinToString("&")
        (api.read("/repos/${a.string("repo")}/commits?$q") as? JsonArray)?.joinToString("\n") { c ->
            "${c.s("sha")?.take(7)} ${c.s("commit", "author", "date")?.take(10)} @${c.s("author", "login") ?: c.s("commit", "author", "name")}: ${c.s("commit", "message")?.lineSequence()?.firstOrNull()}"
        }.orEmpty().ifBlank { "No commits." }
    },
    GhTool("github.issues", "List issues of a repository.", Risk.NETWORK, schema { string("repo", "owner/name"); string("state", "open, closed or all", optional = true) }) { a -> api.issuesText(a.string("repo"), a.stringOrNull("state") ?: "open") },
    GhTool("github.issue", "Read one issue or pull request conversation with its comments.", Risk.NETWORK, schema { string("repo", "owner/name"); string("number", "Number") }) { a -> api.issueText(a.string("repo"), a.string("number").trim('#').toInt()) },
    GhTool("github.pulls", "List pull requests of a repository.", Risk.NETWORK, schema { string("repo", "owner/name"); string("state", "open, closed or all", optional = true) }) { a -> api.pullsText(a.string("repo"), a.stringOrNull("state") ?: "open") },
    GhTool("github.pull", "One pull request: state, description, checks and the files it changes (with additions and deletions).", Risk.NETWORK, schema { string("repo", "owner/name"); string("number", "PR number") }) { a ->
        val repo = a.string("repo"); val n = a.string("number").trim('#')
        val p = api.read("/repos/$repo/pulls/$n")
        val files = api.read("/repos/$repo/pulls/$n/files?per_page=100") as? JsonArray
        "#$n ${p.s("title")} [${p.s("state")}${if (p.s("merged") == "true") ", merged" else ""}] ${p.s("head", "ref")} → ${p.s("base", "ref")} by @${p.s("user", "login")}\n\n${p.s("body").orEmpty().take(6000)}\n\nFiles:\n" +
            files?.joinToString("\n") { f -> "${f.s("filename")} +${f.s("additions")} -${f.s("deletions")}" }.orEmpty()
    },
    GhTool("github.actions", "Recent GitHub Actions runs of a repository with their result.", Risk.NETWORK, schema { string("repo", "owner/name") }) { a ->
        api.runs(a.string("repo"), 15).joinToString("\n") { r -> "${r.name} #${r.runNumber} ${r.status}/${r.conclusion ?: "-"} on ${r.branch} (${r.createdAt?.take(10)})" }.ifBlank { "No runs." }
    },
    GhTool("github.search", "Search GitHub. kind=code searches code (add repo:owner/name or user:login to the query), kind=issues searches issues and PRs.", Risk.NETWORK, schema {
        string("query", "GitHub search query"); string("kind", "code or issues")
    }) { a ->
        val kind = if (a.stringOrNull("kind") == "code") "code" else "issues"
        val items = (api.read("/search/$kind?per_page=20&q=" + enc(a.string("query"))) as? JsonObject)?.get("items") as? JsonArray
        items?.joinToString("\n") { i -> if (kind == "code") "${i.s("repository", "full_name")}: ${i.s("path")}" else "${i.s("repository_url")?.substringAfter("repos/")} #${i.s("number")} ${i.s("title")} [${i.s("state")}]" }.orEmpty().ifBlank { "Nothing found." }
    },
    GhTool("github.notifications", "The user's unread GitHub notifications.", Risk.NETWORK, schema { }) { _ ->
        (api.read("/notifications?per_page=30") as? JsonArray)?.joinToString("\n") { n -> "${n.s("repository", "full_name")}: ${n.s("subject", "type")} ${n.s("subject", "title")} (${n.s("reason")})" }.orEmpty().ifBlank { "No unread notifications." }
    },
    GhTool("github.comment", "Post a comment on an issue or pull request AS THE USER. The user approves every comment before it is posted.", Risk.EXTERNAL, schema {
        string("repo", "owner/name"); string("number", "Issue or PR number"); string("body", "The comment, in Markdown")
    }) { a ->
        val r = api.write("/repos/${a.string("repo")}/issues/${a.string("number").trim('#')}/comments", buildJsonObject { put("body", a.string("body")) })
        "Comment posted: ${r.s("html_url")}"
    },
    GhTool("github.issue_create", "Open a new issue AS THE USER. The user approves it before it is created.", Risk.EXTERNAL, schema {
        string("repo", "owner/name"); string("title", "Issue title"); string("body", "Issue description in Markdown")
    }) { a ->
        val r = api.write("/repos/${a.string("repo")}/issues", buildJsonObject { put("title", a.string("title")); put("body", a.string("body")) })
        "Issue #${r.s("number")} created: ${r.s("html_url")}"
    },
)

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

private fun JsonElement.s(vararg path: String): String? {
    var cur: JsonElement = this
    for (p in path) cur = (cur as? JsonObject)?.get(p) ?: return null
    return (cur as? JsonPrimitive)?.takeIf { cur !is JsonNull }?.content
}

private class GhTool(
    override val name: String,
    override val description: String,
    override val risk: Risk,
    override val parameters: JsonObject,
    private val block: suspend (JsonObject) -> String,
) : Tool {
    override val idempotent = risk != Risk.EXTERNAL

    override suspend fun invoke(args: JsonObject, context: ToolContext): ToolResult = runCatching { ToolResult.ok(block(args)) }
        .getOrElse { ToolResult.failed("$name: ${it.message ?: it::class.simpleName}") }
}
