package com.paulneja.quorvi.agent.policy

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "policy_settings")
data class PolicySettingEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "policy_grants", indices = [Index("action")])
data class GrantEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val action: String,
    val resource: String = "*",
    val createdAt: Long,
    val expiresAt: Long? = null,
    val usesLeft: Int? = null,
    val reason: String? = null,
)

@Entity(tableName = "policy_approvals", indices = [Index("runId"), Index("status")])
data class ApprovalEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val action: String,
    val resource: String,
    val summary: String,
    val reason: String,
    val createdAt: Long,
    val status: String = "PENDING",
    val decidedAt: Long? = null,
)

@Entity(tableName = "policy_audit", indices = [Index("at")])
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val subject: String,
    val action: String,
    val resource: String,
    val verdict: String,
    val reason: String,
    val runId: String? = null,
)

@Dao
interface PolicyDao {
    @Query("SELECT * FROM policy_settings")
    fun observeSettings(): Flow<List<PolicySettingEntity>>

    @Query("SELECT * FROM policy_settings")
    suspend fun settings(): List<PolicySettingEntity>

    @Upsert
    suspend fun putSetting(setting: PolicySettingEntity)

    @Query("SELECT * FROM policy_grants WHERE (expiresAt IS NULL OR expiresAt > :now) AND (usesLeft IS NULL OR usesLeft > 0) ORDER BY createdAt DESC")
    fun observeGrants(now: Long): Flow<List<GrantEntity>>

    @Query("SELECT * FROM policy_grants WHERE action = :action AND (subject = :runId OR subject = 'agent') AND (expiresAt IS NULL OR expiresAt > :now) AND (usesLeft IS NULL OR usesLeft > 0)")
    suspend fun grantsFor(action: String, runId: String, now: Long): List<GrantEntity>

    @Upsert
    suspend fun putGrant(grant: GrantEntity)

    @Query("UPDATE policy_grants SET usesLeft = usesLeft - 1 WHERE id = :id AND usesLeft IS NOT NULL")
    suspend fun consume(id: String)

    @Query("DELETE FROM policy_grants WHERE id = :id")
    suspend fun revoke(id: String)

    @Query("DELETE FROM policy_grants WHERE (expiresAt IS NOT NULL AND expiresAt <= :now) OR (usesLeft IS NOT NULL AND usesLeft <= 0)")
    suspend fun pruneGrants(now: Long)

    @Upsert
    suspend fun putApproval(approval: ApprovalEntity)

    @Query("SELECT * FROM policy_approvals WHERE runId = :runId AND status = 'PENDING' ORDER BY createdAt DESC LIMIT 1")
    fun observePending(runId: String): Flow<ApprovalEntity?>

    @Query("SELECT * FROM policy_approvals WHERE id = :id")
    suspend fun approval(id: String): ApprovalEntity?

    @Query("UPDATE policy_approvals SET status = :status, decidedAt = :at WHERE id = :id")
    suspend fun decide(id: String, status: String, at: Long)

    @Insert
    suspend fun audit(entry: AuditEntity)

    @Query("SELECT * FROM policy_audit ORDER BY id DESC LIMIT :limit")
    fun observeAudit(limit: Int = 300): Flow<List<AuditEntity>>

    @Query("DELETE FROM policy_audit WHERE id <= (SELECT COALESCE(MAX(id), 0) - :keep FROM policy_audit)")
    suspend fun rotateAudit(keep: Int)
}
