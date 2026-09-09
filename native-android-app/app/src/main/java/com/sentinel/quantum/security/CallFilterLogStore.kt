package com.sentinel.quantum.security

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import java.util.concurrent.Executors

/** A bounded Room history containing decisions and a device-bound HMAC, never a phone number. */
class CallFilterLogStore private constructor(
    private val dao: CallFilterDecisionDao,
    private val fingerprinter: CallNumberFingerprinter = CallNumberFingerprinter()
) {
    fun recordAsync(decision: CallRuleEngine.Decision, now: Long = System.currentTimeMillis()) {
        WRITER.execute {
            runCatching {
                dao.insert(
                    CallFilterDecisionEntity(
                        occurredAtMs = now,
                        action = decision.action.name,
                        reason = sanitize(decision.reason),
                        source = decision.source.name,
                        numberFingerprint = decision.normalizedNumber?.let(fingerprinter::fingerprint)
                    )
                )
                dao.prune(MAX_HISTORY_ENTRIES)
            }
        }
    }

    suspend fun history(): List<CallFilterDecisionEntity> = dao.latest(MAX_HISTORY_ENTRIES)

    suspend fun clear() = dao.clear()

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\r\\n\\u0000|]"), " ").take(MAX_REASON_LENGTH)

    companion object {
        const val MAX_HISTORY_ENTRIES = 500
        private const val MAX_REASON_LENGTH = 128
        private val WRITER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-call-history").apply { isDaemon = true }
        }

        fun get(context: Context): CallFilterLogStore = CallFilterLogStore(
            SentinelLocalDatabase.get(context.applicationContext).callFilterDecisionDao()
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
    val numberFingerprint: String?
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

@Database(entities = [CallFilterDecisionEntity::class], version = 1, exportSchema = true)
abstract class SentinelLocalDatabase : RoomDatabase() {
    abstract fun callFilterDecisionDao(): CallFilterDecisionDao

    companion object {
        @Volatile private var instance: SentinelLocalDatabase? = null

        fun get(context: Context): SentinelLocalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder<SentinelLocalDatabase>(
                context.applicationContext,
                "sentinel-local.db"
            ).setDriver(AndroidSQLiteDriver()).build().also { instance = it }
        }
    }
}
