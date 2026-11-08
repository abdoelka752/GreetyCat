package org.greedycat.intelligence.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID

@Entity(tableName = "sync_identity")
data class SyncIdentity(@PrimaryKey val id: Int = 1, val sourceId: String)

/** Frozen metadata only, intentionally retained independently of demo/source deletion. */
@Entity(tableName = "sync_outbox", indices = [Index(value = ["eventId"], unique = true)])
data class SyncEvent(@PrimaryKey(autoGenerate = true) val sequence: Long = 0, val eventId: String,
    val payload: String, val sha256: String)

@Entity(tableName = "sync_receipts", primaryKeys = ["peer", "eventId"])
data class SyncReceipt(val peer: String, val eventId: String, val status: String,
    val attempts: Int, val nextAttemptAt: Long, val code: String?)

@Dao
interface SyncDao {
    @Insert suspend fun identity(identity: SyncIdentity)
    @Query("SELECT * FROM sync_identity WHERE id=1") suspend fun identity(): SyncIdentity?
    @Insert suspend fun enqueue(event: SyncEvent)
    @Query("SELECT * FROM sync_outbox WHERE eventId=:id") suspend fun event(id: String): SyncEvent?
    @Query("SELECT count(*) FROM sync_outbox") suspend fun count(): Int
    @Query("SELECT count(*) FROM outcome_reviews r WHERE NOT EXISTS(SELECT 1 FROM sync_outbox o WHERE o.eventId=r.id)") suspend fun missingCount(): Int
    @Query("SELECT r.* FROM outcome_reviews r WHERE NOT EXISTS(SELECT 1 FROM sync_outbox o WHERE o.eventId=r.id) ORDER BY r.roundId,r.revision LIMIT 100") suspend fun missing(): List<OutcomeReview>
    @Query("SELECT o.* FROM sync_outbox o LEFT JOIN sync_receipts r ON r.eventId=o.eventId AND r.peer=:peer WHERE r.status IS NULL OR r.status='RETRY' ORDER BY o.sequence LIMIT 100") suspend fun pending(peer: String): List<SyncEvent>
    @Query("SELECT * FROM sync_receipts WHERE peer=:peer AND eventId=:event") suspend fun receipt(peer: String, event: String): SyncReceipt?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun receipt(value: SyncReceipt)
    @Query("SELECT count(*) FROM sync_receipts WHERE peer=:peer AND status=:status") suspend fun count(peer: String, status: String): Int
}

object SyncOutbox {
    const val CAPACITY = 10000
    fun sha(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    suspend fun enqueue(db: ResearchDatabase, review: OutcomeReview) {
        val dao = db.syncDao()
        if (dao.event(review.id) != null || dao.count() >= CAPACITY) return
        val round = requireNotNull(db.researchDao().round(review.roundId))
        val reference = requireNotNull(db.reviewDao().reference(review.evidenceId))
        check(round.provenance.name == reference.provenance)
        val payload = buildJsonObject {
            put("review_id", review.id); put("round_id", round.id); put("context_id", round.contextId)
            put("mode", round.mode.name); put("provenance", round.provenance.name); put("round_number", requireNotNull(round.roundNumber))
            put("revision", review.revision); put("previous_id", review.previousId?.let(::JsonPrimitive) ?: JsonNull)
            put("value", review.value); put("observed_at", review.observedAt); put("confirmed_at", review.confirmedAt)
            put("reviewer_id", review.reviewerId); put("reason", review.reason)
            put("evidence", buildJsonObject {
                put("id", reference.id); put("sha256", reference.sha256); put("mime", reference.mime)
                put("bytes", reference.byteCount); put("imported_at", reference.importedAt)
                put("duration_ms", reference.durationMs?.let(::JsonPrimitive) ?: JsonNull)
                put("video_offset_ms", review.videoOffsetMs?.let(::JsonPrimitive) ?: JsonNull)
            })
        }.toString()
        check(payload.toByteArray(Charsets.UTF_8).size <= 8192)
        if (dao.identity() == null) dao.identity(SyncIdentity(sourceId = UUID.randomUUID().toString()))
        dao.enqueue(SyncEvent(eventId = review.id, payload = payload, sha256 = sha(payload)))
    }
    val migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `sync_identity` (`id` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` TEXT NOT NULL, `payload` TEXT NOT NULL, `sha256` TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_eventId` ON `sync_outbox` (`eventId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `sync_receipts` (`peer` TEXT NOT NULL, `eventId` TEXT NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `nextAttemptAt` INTEGER NOT NULL, `code` TEXT, PRIMARY KEY(`peer`, `eventId`))")
            protect(db)
        }
    }
    fun protect(db: SupportSQLiteDatabase) {
        for (table in listOf("sync_outbox", "sync_identity")) for (op in listOf("UPDATE", "DELETE")) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_no_${op.lowercase()} BEFORE $op ON $table BEGIN SELECT RAISE(ABORT, 'Sync archives are immutable'); END")
        }
    }
    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) { protect(db) }
    }
}
