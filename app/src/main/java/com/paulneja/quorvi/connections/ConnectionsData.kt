package com.paulneja.quorvi.connections

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val auth: String,
    val hostKeyType: String? = null,
    val hostKeyFingerprint: String? = null,
    val agentAccess: Boolean = false,
    val createdAt: Long,
    val healthJson: String? = null,
    val healthAt: Long? = null,
    val healthError: String? = null,
)

@Entity(tableName = "docker_containers", primaryKeys = ["hostId", "id"])
data class ContainerEntity(
    val hostId: String,
    val id: String,
    val name: String,
    val image: String,
    val state: String,
    val status: String,
    val fetchedAt: Long,
)

@Entity(tableName = "http_widgets")
data class HttpWidgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val method: String = "GET",
    val url: String,
    val headersJson: String = "{}",
    val body: String? = null,
    val jsonPath: String? = null,
    val display: String = "TEXT",
    val unit: String? = null,
    val okWhen: String? = null,
    val refreshSec: Int = 300,
    val allowLan: Boolean = false,
    val value: String? = null,
    val history: String = "",
    val fetchedAt: Long? = null,
    val error: String? = null,
    val createdAt: Long,
)

@Dao
interface ConnectionsDao {
    @Query("SELECT * FROM hosts ORDER BY name")
    fun observeHosts(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts ORDER BY name")
    suspend fun hosts(): List<HostEntity>

    @Query("SELECT * FROM hosts WHERE id = :id")
    fun observeHost(id: String): Flow<HostEntity?>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun host(id: String): HostEntity?

    @Upsert
    suspend fun upsertHost(host: HostEntity)

    @Query("UPDATE hosts SET healthJson = :json, healthAt = :at, healthError = :error WHERE id = :id")
    suspend fun setHealth(id: String, json: String?, at: Long, error: String?)

    @Query("UPDATE hosts SET hostKeyType = :type, hostKeyFingerprint = :fingerprint WHERE id = :id")
    suspend fun trust(id: String, type: String, fingerprint: String)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun deleteHost(id: String)

    @Query("SELECT * FROM docker_containers WHERE hostId = :hostId ORDER BY name")
    fun observeContainers(hostId: String): Flow<List<ContainerEntity>>

    @Query("SELECT * FROM docker_containers ORDER BY name")
    fun observeAllContainers(): Flow<List<ContainerEntity>>

    @Query("DELETE FROM docker_containers WHERE hostId = :hostId")
    suspend fun clearContainers(hostId: String)

    @Upsert
    suspend fun upsertContainers(containers: List<ContainerEntity>)

    @Transaction
    suspend fun replaceContainers(hostId: String, containers: List<ContainerEntity>) {
        clearContainers(hostId)
        upsertContainers(containers)
    }

    @Query("SELECT * FROM http_widgets ORDER BY name")
    fun observeHttp(): Flow<List<HttpWidgetEntity>>

    @Query("SELECT * FROM http_widgets ORDER BY name")
    suspend fun httpWidgets(): List<HttpWidgetEntity>

    @Query("SELECT * FROM http_widgets WHERE id = :id")
    suspend fun httpWidget(id: String): HttpWidgetEntity?

    @Upsert
    suspend fun upsertHttp(widget: HttpWidgetEntity)

    @Query("UPDATE http_widgets SET value = :value, history = :history, fetchedAt = :at, error = NULL WHERE id = :id")
    suspend fun setHttpValue(id: String, value: String, history: String, at: Long)

    @Query("UPDATE http_widgets SET error = :error, fetchedAt = :at WHERE id = :id")
    suspend fun setHttpError(id: String, error: String, at: Long)

    @Query("DELETE FROM http_widgets WHERE id = :id")
    suspend fun deleteHttp(id: String)
}
