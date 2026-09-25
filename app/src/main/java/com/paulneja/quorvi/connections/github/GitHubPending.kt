package com.paulneja.quorvi.connections.github

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class PendingItem(val repo: String, val number: Int, val title: String, val pr: Boolean, val review: Boolean, val updatedAt: String, val url: String) {
    val key: String get() = "$repo#$number"
}

object PendingParse {
    fun items(search: JsonElement, review: Boolean = false): List<PendingItem> =
        ((search as? JsonObject)?.get("items") as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val repo = o.s("repository_url")?.substringAfter("/repos/") ?: return@mapNotNull null
            PendingItem(
                repo = repo,
                number = o.s("number")?.toIntOrNull() ?: return@mapNotNull null,
                title = o.s("title").orEmpty(),
                pr = o["pull_request"] != null,
                review = review,
                updatedAt = o.s("updated_at").orEmpty(),
                url = o.s("html_url").orEmpty(),
            )
        }

    fun merge(reviews: List<PendingItem>, involved: List<PendingItem>): List<PendingItem> {
        val seen = reviews.map { it.key }.toSet()
        return reviews.sortedByDescending { it.updatedAt } + involved.filter { it.key !in seen }.sortedByDescending { it.updatedAt }
    }

    private fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { this[key] !is JsonNull }?.content
}

@Singleton
class GitHubPending @Inject constructor(private val api: GitHubApi) {
    suspend fun fetch(): List<PendingItem> {
        val login = (api.read("/user") as? JsonObject)?.get("login")?.let { (it as? JsonPrimitive)?.content } ?: return emptyList()
        val reviews = PendingParse.items(api.read("/search/issues?per_page=30&sort=updated&q=" + q("is:open is:pr archived:false review-requested:$login")), review = true)
        val involved = PendingParse.items(api.read("/search/issues?per_page=40&sort=updated&q=" + q("is:open archived:false involves:$login")))
        return PendingParse.merge(reviews, involved)
    }

    private fun q(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
