package com.paulneja.quorvi.agent.models

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.paulneja.quorvi.connections.github.GitHubSession
import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.security.SecretVault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

enum class ProviderKind { COPILOT, BRIDGE, OPENAI_COMPATIBLE, OPENAI_RESPONSES, ANTHROPIC }

enum class ModelRole { QUICK, PLAN, VISION }

@Entity(tableName = "ai_providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val roles: String = "",
    val createdAt: Long,
)

@Dao
interface ProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY createdAt")
    fun observe(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM ai_providers ORDER BY createdAt")
    suspend fun all(): List<ProviderEntity>

    @Upsert
    suspend fun upsert(provider: ProviderEntity)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun delete(id: String)
}

@Singleton
class ModelProviders @Inject constructor(
    private val dao: ProviderDao,
    private val vault: SecretVault,
    private val http: Http,
    private val github: GitHubSession,
) {
    val providers: Flow<List<ProviderEntity>> = dao.observe()

    suspend fun save(provider: ProviderEntity, secret: String?) {
        if (provider.baseUrl.isNotBlank()) {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.paulneja.quorvi.core.net.NetGuard.secureEnough(provider.baseUrl) }
            require(ok) { "Use https for endpoints on the internet; plain http only works on your local network" }
        }
        if (secret != null) vault.put(SecretVault.provider(provider.id), secret)
        dao.upsert(provider)
    }

    suspend fun remove(id: String) {
        dao.delete(id)
        vault.remove(SecretVault.provider(id))
    }

    fun hasSecret(id: String): Boolean = vault.has(SecretVault.provider(id))

    suspend fun listModels(id: String): List<AiModelInfo> {
        val provider = dao.all().firstOrNull { it.id == id } ?: return emptyList()
        return client(provider)?.listModels().orEmpty().distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
    }

    suspend fun setModel(id: String, model: String) {
        val provider = dao.all().firstOrNull { it.id == id } ?: return
        dao.upsert(provider.copy(model = model))
    }

    suspend fun client(provider: ProviderEntity): AiClient? = when (ProviderKind.valueOf(provider.kind)) {
        ProviderKind.COPILOT -> github.tokens.valid()?.let { CopilotClient(http, provider.baseUrl.ifBlank { CopilotParsing.DEFAULT_API }, it) }
        ProviderKind.BRIDGE -> {
            val token = github.tokens.valid()
            val code = vault.get(SecretVault.provider(provider.id))
            if (token == null || code == null) null else BridgeClient(http, provider.baseUrl, code, token)
        }
        ProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleClient(http, provider.baseUrl, vault.get(SecretVault.provider(provider.id)))
        ProviderKind.OPENAI_RESPONSES -> ResponsesClient(http, provider.baseUrl, vault.get(SecretVault.provider(provider.id)))
        ProviderKind.ANTHROPIC -> AnthropicClient(http, provider.baseUrl.ifBlank { AnthropicClient.DEFAULT_URL }, vault.get(SecretVault.provider(provider.id)))
    }

    suspend fun byId(id: String): Pair<ProviderEntity, AiClient>? {
        val provider = dao.all().firstOrNull { it.id == id } ?: return null
        return client(provider)?.let { provider to it }
    }

    suspend fun forRole(role: ModelRole): Pair<ProviderEntity, AiClient>? {
        val all = dao.all()
        val pick = all.firstOrNull { role.name in it.roles.split(',') } ?: all.firstOrNull() ?: return null
        return client(pick)?.let { pick to it }
    }

    suspend fun copilotStatus(): CopilotStatus? {
        val token = github.tokens.valid() ?: return null
        val json: JsonObject = http.getJson("https://api.github.com/copilot_internal/user", mapOf("Authorization" to "Bearer $token", "Accept" to "application/json")).jsonObject
        return CopilotParsing.status(json)
    }

    suspend fun bridgeHealth(url: String): String = BridgeClient(http, url, "", "").health()
}
