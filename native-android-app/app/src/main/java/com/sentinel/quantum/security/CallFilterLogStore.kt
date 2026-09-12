package com.sentinel.quantum.security

import android.content.Context
import androidx.room.*
import java.util.concurrent.Executors

class CallFilterLogStore private constructor(private val dao: CallFilterDecisionDao) {
    fun recordAsync(actionName: String, reason: String, sourceName: String) {
        WRITER.execute {
            runCatching {
                dao.insert(
                    CallFilterDecisionEntity(
                        occurredAtMs = System.currentTimeMillis(),
                        action = actionName,
                        reason = reason,
                        source = sourceName,
                        numberFingerprint = ""
                    )
                )
                dao.prune(MAX_HISTORY_ENTRIES)
            }
        }
    }

    suspend fun history(): List<CallFilterDecisionEntity> = dao.latest(MAX_HISTORY_ENTRIES)
    suspend fun clear() = dao.clear()

    companion object {
        const val MAX_HISTORY_ENTRIES = 500
        private val WRITER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-call-history").apply { isDaemon = true }
        }
        fun get(context: Context): CallFilterLogStore = CallFilterLogStore(
            SentinelRoomDatabase.get(context.applicationContext).callFilterDecisionDao()
        )
    }
}

@Entity(tableName = "call_filter_decisions")
data class CallFilterDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAtMs: Long,
    val action: String,
    val reason: String,
    val source: String,
    val numberFingerprint: String? = ""
)

@Dao
interface CallFilterDecisionDao {
    @Insert
    fun insert(entry: CallFilterDecisionEntity)
    @Query("SELECT * FROM call_filter_decisions ORDER BY occurredAtMs DESC, id DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<CallFilterDecisionEntity>
    @Query("DELETE FROM call_filter_decisions WHERE id NOT IN (SELECT id FROM call_filter_decisions ORDER BY occurredAtMs DESC, id DESC LIMIT :limit)")
    fun prune(limit: Int)
    @Query("DELETE FROM call_filter_decisions")
    suspend fun clear()
}

@Database(entities = [CallFilterDecisionEntity::class], version = 2, exportSchema = false)
abstract class SentinelRoomDatabase : RoomDatabase() {
    abstract fun callFilterDecisionDao(): CallFilterDecisionDao
    companion object {
        @Volatile private var INSTANCE: SentinelRoomDatabase? = null
        fun get(context: Context): SentinelRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SentinelRoomDatabase::class.java,
                    "sentinel_local_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
