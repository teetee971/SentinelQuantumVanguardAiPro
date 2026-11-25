package com.sentinel.quantum.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

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
        private const val MAX_PENDING_WRITES = 128
        private val WRITER = BoundedPostResponseExecutor.create(
            threadName = "sentinel-call-history",
            queueCapacity = MAX_PENDING_WRITES
        )

        fun get(context: Context): CallFilterLogStore {
            return CallFilterLogStore(
                SentinelLocalDatabase.get(context.applicationContext).callFilterDecisionDao()
            )
        }
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
            instance ?: openPreserving(context.applicationContext).also { instance = it }
        }

        private fun openPreserving(context: Context): SentinelLocalDatabase {
            val file = context.getDatabasePath("sentinel-local.db")
            val present = file.isFile
            val probed = if (present) RoomMasterIdentity.read(file) else null
            val allowed = SentinelLocalDatabasePreservation.allowOpen(
                filePresent = present,
                identityHash = probed?.identityHash,
                userVersion = probed?.userVersion ?: -1,
                destructiveFallbackRequested = false
            )
            SentinelLocalDatabasePreservation.assertNonDestructive(!allowed)
            return Room.databaseBuilder<SentinelLocalDatabase>(
                context,
                "sentinel-local.db"
            ).setDriver(AndroidSQLiteDriver()).build()
        }
    }
}

/**
 * Read-only view of an existing Room file. A failed read returns null so the open
 * is refused. This object never writes, checkpoints, or removes the file.
 */
internal object RoomMasterIdentity {
    data class Probe(val identityHash: String?, val userVersion: Int)

    fun read(file: java.io.File): Probe? {
        if (!file.isFile) return null
        return runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { database ->
                val identity = database.rawQuery(
                    "SELECT identity_hash FROM room_master_table WHERE id = 42",
                    null
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else cursor.getString(0)
                }
                Probe(identity, database.version)
            }
        }.getOrNull()
    }
}
