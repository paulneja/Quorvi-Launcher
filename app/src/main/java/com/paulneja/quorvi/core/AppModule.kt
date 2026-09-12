package com.paulneja.quorvi.core

import android.content.Context
import androidx.room.Room
import com.paulneja.quorvi.launcher.home.HomeDao
import com.paulneja.quorvi.launcher.home.QuorviDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): QuorviDatabase =
        Room.databaseBuilder(context, QuorviDatabase::class.java, com.paulneja.quorvi.core.backup.FullBackup.DB_NAME)
            .addMigrations(*QuorviDatabase.MIGRATIONS)
            .enableMultiInstanceInvalidation()
            .build()

    @Provides
    fun homeDao(db: QuorviDatabase): HomeDao = db.home()

    @Provides
    fun providerDao(db: QuorviDatabase): com.paulneja.quorvi.agent.models.ProviderDao = db.providers()

    @Provides
    fun opsDao(db: QuorviDatabase): com.paulneja.quorvi.ops.OpsDao = db.ops()

    @Provides
    fun capsuleDao(db: QuorviDatabase): com.paulneja.quorvi.agent.capsules.CapsuleDao = db.capsules()

    @Provides
    fun servicesDao(db: QuorviDatabase): com.paulneja.quorvi.connections.services.ServicesDao = db.services()

    @Provides
    fun memoryDao(db: QuorviDatabase): com.paulneja.quorvi.agent.memory.MemoryDao = db.memory()

    @Provides
    fun policyDao(db: QuorviDatabase): com.paulneja.quorvi.agent.policy.PolicyDao = db.policy()

    @Provides
    fun connectionsDao(db: QuorviDatabase): com.paulneja.quorvi.connections.ConnectionsDao = db.connections()

    @Provides
    fun chatDao(db: QuorviDatabase): com.paulneja.quorvi.agent.chat.ChatDao = db.chats()

    @Provides
    fun agentConfigDao(db: QuorviDatabase): com.paulneja.quorvi.agent.orchestra.AgentConfigDao = db.agentConfig()

    @Provides
    fun commandDao(db: QuorviDatabase): com.paulneja.quorvi.core.command.CommandDao = db.commands()
}
