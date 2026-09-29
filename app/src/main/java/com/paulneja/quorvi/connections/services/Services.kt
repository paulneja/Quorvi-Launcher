package com.paulneja.quorvi.connections.services

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.paulneja.quorvi.core.security.CredentialRef
import kotlinx.coroutines.flow.Flow

enum class ServiceKind { HOME_ASSISTANT, GITLAB, WORKER, DISCORD }

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val baseUrl: String,
    val extra: String = "{}",
    val createdAt: Long,
    val status: String? = null,
    val statusAt: Long? = null,
)

@Entity(tableName = "ha_entities", primaryKeys = ["serviceId", "entityId"])
data class HaEntity(
    val serviceId: String,
    val entityId: String,
    val name: String,
    val state: String,
    val unit: String? = null,
    val favorite: Boolean = false,
    val updatedAt: Long,
)

@Dao
interface ServicesDao {
    @Query("SELECT * FROM services ORDER BY name")
    fun observe(): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE kind = :kind ORDER BY name")
    suspend fun ofKind(kind: String): List<ServiceEntity>

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun get(id: String): ServiceEntity?

    @Upsert
    suspend fun put(service: ServiceEntity)

    @Query("UPDATE services SET status = :status, statusAt = :at WHERE id = :id")
    suspend fun setStatus(id: String, status: String?, at: Long)

    @Query("DELETE FROM services WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM ha_entities WHERE serviceId = :serviceId ORDER BY favorite DESC, name")
    fun observeEntities(serviceId: String): Flow<List<HaEntity>>

    @Query("SELECT * FROM ha_entities WHERE favorite = 1 ORDER BY name")
    fun observeFavorites(): Flow<List<HaEntity>>

    @Query("SELECT * FROM ha_entities WHERE serviceId = :serviceId")
    suspend fun entities(serviceId: String): List<HaEntity>

    @Upsert
    suspend fun putEntities(entities: List<HaEntity>)

    @Query("UPDATE ha_entities SET state = :state, updatedAt = :at WHERE serviceId = :serviceId AND entityId = :entityId")
    suspend fun setState(serviceId: String, entityId: String, state: String, at: Long)

    @Query("UPDATE ha_entities SET favorite = :favorite WHERE serviceId = :serviceId AND entityId = :entityId")
    suspend fun setFavorite(serviceId: String, entityId: String, favorite: Boolean)

    @Query("DELETE FROM ha_entities WHERE serviceId = :serviceId")
    suspend fun clearEntities(serviceId: String)
}

fun tokenRef(serviceId: String) = CredentialRef("service_token_$serviceId")
