package com.paulneja.quorvi.ops

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class RunOrigin(val id: String, val origin: String)

@Entity(tableName = "runs", indices = [Index("state"), Index("updatedAt")])
data class RunEntity(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val origin: String,
    val goal: String,
    val state: String,
    val executor: String? = null,
    val model: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val error: String? = null,
    val tokensIn: Long? = null,
    val tokensOut: Long? = null,
    val costSource: String? = null,
    val attempt: Int = 1,
    val heartbeatAt: Long? = null,
    val lastCommittedStep: String? = null,
    val policy: String? = null,
)

@Entity(tableName = "run_steps", indices = [Index("runId")])
data class StepEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val taskId: String? = null,
    val position: Int,
    val title: String,
    val kind: String,
    val toolName: String? = null,
    val dependsOn: String = "",
    val state: String,
    val attempt: Int = 0,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val output: String? = null,
    val error: String? = null,
    val inputDigest: String? = null,
)

@Entity(tableName = "run_events", primaryKeys = ["runId", "sequence"])
data class EventEntity(
    val runId: String,
    val sequence: Long,
    val actor: String,
    val type: String,
    val message: String,
    val payload: String? = null,
    val timestamp: Long,
)

@Entity(tableName = "artifacts", indices = [Index("runId")])
data class ArtifactEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val name: String,
    val mime: String,
    val localUri: String,
    val sha256: String,
    val size: Long,
    val verified: Boolean,
    val exportedUri: String? = null,
    val createdAt: Long,
    val provenance: String? = null,
)

@Entity(tableName = "ci_executions", indices = [Index("repo"), Index("updatedAt")])
data class CiExecutionEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val repo: String,
    val runId: Long,
    val workflow: String,
    val title: String,
    val branch: String?,
    val event: String?,
    val status: String,
    val conclusion: String?,
    val number: Int,
    val attempt: Int,
    val createdAt: Long?,
    val updatedAt: Long?,
    val startedAt: Long?,
    val url: String,
    val actor: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "ci_jobs", indices = [Index("executionId")])
data class CiJobEntity(
    @PrimaryKey val id: String,
    val executionId: String,
    val name: String,
    val status: String?,
    val conclusion: String?,
    val startedAt: Long?,
    val completedAt: Long?,
    val steps: String,
    val url: String?,
)

@Entity(tableName = "watched_repos")
data class WatchedRepoEntity(@PrimaryKey val fullName: String, val addedAt: Long)

@Dao
interface OpsDao {
    @Query("SELECT COUNT(*) FROM runs WHERE origin = :origin AND (state IN ('QUEUED', 'PLANNING', 'RUNNING', 'PAUSING', 'RETRYING', 'VERIFYING', 'CANCELLING') OR (state = 'WAITING_USER' AND updatedAt > :waitingSince))")
    suspend fun busyRuns(origin: String, waitingSince: Long): Int

    @Query("SELECT id FROM runs WHERE origin IN (:origins)")
    suspend fun runIdsOf(origins: List<String>): List<String>

    @Query("SELECT id FROM runs WHERE origin LIKE 'chat:%' OR origin LIKE 'chat-task:%'")
    suspend fun chatRunIds(): List<String>

    @Query("SELECT id, origin FROM runs WHERE origin LIKE 'chat:%' OR origin LIKE 'chat-task:%'")
    suspend fun chatRunOrigins(): List<RunOrigin>

    @Query("DELETE FROM runs WHERE id IN (:ids)")
    suspend fun deleteRuns(ids: List<String>)

    @Query("DELETE FROM run_events WHERE runId IN (:ids)")
    suspend fun deleteEvents(ids: List<String>)

    @Query("DELETE FROM run_steps WHERE runId IN (:ids)")
    suspend fun deleteSteps(ids: List<String>)

    @Query("SELECT * FROM runs ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRuns(limit: Int = 200): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :id")
    fun observeRun(id: String): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun run(id: String): RunEntity?

    @Query("SELECT * FROM runs")
    suspend fun allRuns(): List<RunEntity>

    @Query("SELECT * FROM runs WHERE state = :state")
    suspend fun runsIn(state: String): List<RunEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(run: RunEntity)

    @Upsert
    suspend fun upsertRun(run: RunEntity)

    @Query("UPDATE runs SET state = :to, updatedAt = :now, finishedAt = :finishedAt, error = :error WHERE id = :id AND state = :from")
    suspend fun transition(id: String, from: String, to: String, now: Long, finishedAt: Long?, error: String?): Int

    @Query("UPDATE runs SET heartbeatAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun heartbeat(id: String, now: Long)

    @Query("UPDATE runs SET tokensIn = COALESCE(tokensIn, 0) + :tokensIn, tokensOut = COALESCE(tokensOut, 0) + :tokensOut, costSource = :source WHERE id = :id")
    suspend fun addUsage(id: String, tokensIn: Long, tokensOut: Long, source: String)

    @Query("SELECT DISTINCT runId FROM run_steps")
    fun observeRunsWithSteps(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("UPDATE runs SET state = 'COMPLETED', finishedAt = :now, updatedAt = :now WHERE origin = :origin AND id != :keep AND state IN ('WAITING_USER', 'WAITING_CAPABILITY', 'INTERRUPTED', 'PAUSED')")
    suspend fun settleWaiting(origin: String, keep: String, now: Long)

    @Query("UPDATE runs SET lastCommittedStep = :step, heartbeatAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun commitStep(id: String, step: String, now: Long)

    @Query("UPDATE runs SET executor = :executor, model = :model, startedAt = COALESCE(startedAt, :now), updatedAt = :now WHERE id = :id")
    suspend fun markStarted(id: String, executor: String, model: String, now: Long)

    @Query("UPDATE runs SET attempt = attempt + 1, updatedAt = :now WHERE id = :id")
    suspend fun bumpAttempt(id: String, now: Long)

    @Query("SELECT * FROM run_steps WHERE runId = :runId ORDER BY position")
    fun observeSteps(runId: String): Flow<List<StepEntity>>

    @Query("SELECT * FROM run_steps WHERE runId = :runId ORDER BY position")
    suspend fun steps(runId: String): List<StepEntity>

    @Query("SELECT * FROM run_steps WHERE state = 'RUNNING'")
    suspend fun runningSteps(): List<StepEntity>

    @Upsert
    suspend fun upsertStep(step: StepEntity)

    @Query("SELECT * FROM run_events WHERE runId = :runId ORDER BY sequence")
    fun observeEvents(runId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM run_events WHERE runId = :runId AND sequence > :after ORDER BY sequence")
    suspend fun eventsAfter(runId: String, after: Long): List<EventEntity>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM run_events WHERE runId = :runId")
    suspend fun lastSequence(runId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: EventEntity)

    @Transaction
    suspend fun append(runId: String, actor: String, type: String, message: String, payload: String?, timestamp: Long): Long {
        val next = lastSequence(runId) + 1
        insertEvent(EventEntity(runId, next, actor, type, message, payload, timestamp))
        return next
    }

    @Query("SELECT * FROM artifacts WHERE runId = :runId ORDER BY createdAt")
    fun observeArtifacts(runId: String): Flow<List<ArtifactEntity>>

    @Upsert
    suspend fun upsertArtifact(artifact: ArtifactEntity)

    @Query("SELECT * FROM artifacts WHERE runId = :runId ORDER BY createdAt")
    suspend fun artifacts(runId: String): List<ArtifactEntity>

    @Query("SELECT * FROM run_events WHERE runId = :runId AND type = :type ORDER BY sequence DESC LIMIT 1")
    suspend fun lastEvent(runId: String, type: String): EventEntity?

    @Query("SELECT * FROM ci_executions ORDER BY COALESCE(updatedAt, createdAt) DESC LIMIT :limit")
    fun observeCi(limit: Int = 100): Flow<List<CiExecutionEntity>>

    @Query("SELECT * FROM ci_executions WHERE id = :id")
    fun observeCiExecution(id: String): Flow<CiExecutionEntity?>

    @Query("SELECT * FROM ci_executions WHERE status != 'completed'")
    suspend fun activeCi(): List<CiExecutionEntity>

    @Upsert
    suspend fun upsertCi(executions: List<CiExecutionEntity>)

    @Query("SELECT * FROM ci_jobs WHERE executionId = :executionId ORDER BY name")
    fun observeCiJobs(executionId: String): Flow<List<CiJobEntity>>

    @Query("DELETE FROM ci_jobs WHERE executionId = :executionId")
    suspend fun clearCiJobs(executionId: String)

    @Upsert
    suspend fun upsertCiJobs(jobs: List<CiJobEntity>)

    @Query("SELECT * FROM watched_repos ORDER BY fullName")
    fun observeWatched(): Flow<List<WatchedRepoEntity>>

    @Query("SELECT * FROM watched_repos")
    suspend fun watched(): List<WatchedRepoEntity>

    @Upsert
    suspend fun watch(repo: WatchedRepoEntity)

    @Query("DELETE FROM watched_repos WHERE fullName = :fullName")
    suspend fun unwatch(fullName: String)

    @Query("DELETE FROM ci_executions WHERE repo = :fullName")
    suspend fun forgetCi(fullName: String)
}
