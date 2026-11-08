package org.greedycat.intelligence.connectivity

import android.content.Context
import android.content.ContextWrapper
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.greedycat.intelligence.data.*
import org.greedycat.intelligence.domain.*
import org.greedycat.intelligence.ingestion.ReferenceImporter
import org.junit.*
import org.junit.Assert.*

class ReviewSyncTest {
    private lateinit var context: Context
    private lateinit var db: ResearchDatabase
    private lateinit var reviews: ReviewRepository
    private lateinit var file: ReferenceFile
    private lateinit var root: File
    private val name = "test-sync-${UUID.randomUUID()}.db"
    private val credential = PairingCredential("https://127.0.0.1:8767", "a".repeat(64), UUID.randomUUID().toString(), "x".repeat(43), 1000.0, 2000.0)
    @Before fun setup(): Unit = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "test-sync-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = root }
        db = ResearchDatabase.open(context, name)
        val importer = ReferenceImporter(isolated, Provenance.SYNTHETIC)
        reviews = ReviewRepository(db, Provenance.SYNTHETIC, importer)
        file = reviews.register(importer.importStream(ByteArrayInputStream(ReferenceReviewTest.syntheticPng())))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    private suspend fun review(number: String = "100"): OutcomeReview = reviews.confirm(
        ManualReview(UUID.randomUUID().toString(), file.id, GameMode.BASIC, number, OutcomeValue.CORN,
            "2026-01-01T12:00:00.000Z", null, "Synthetic sync test, not gameplay", null), reviews.identity().contextId)

    @Test fun reviewAndCorrectionQueueAtomicallyAndSurviveDemoClearing(): Unit = runBlocking {
        val first = review()
        val second = reviews.confirm(ManualReview(first.roundId, file.id, GameMode.BASIC, "100", OutcomeValue.TOMATO,
            first.observedAt, null, "Synthetic correction for sync test", first.id), reviews.identity().contextId)
        assertEquals(2, db.syncDao().count())
        val event = db.syncDao().event(first.id)!!
        assertEquals(SyncOutbox.sha(event.payload), event.sha256)
        assertTrue(db.syncDao().event(second.id)!!.payload.contains(first.id))
        ResearchRepository(db, Provenance.SYNTHETIC).clearDemo()
        assertEquals(2, db.syncDao().count())
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET payload='changed'")
        }
        assertThrows(android.database.sqlite.SQLiteException::class.java) { db.openHelper.writableDatabase.execSQL("DELETE FROM sync_outbox") }
    }
    @Test fun lostAckAndReopenKeepOneRemoteEventAndEventuallyAcknowledge(): Unit = runBlocking {
        val first = review()
        val remote = mutableSetOf<String>()
        ReviewSync(db) { _, _, event -> remote.add(event.eventId); throw java.io.IOException("Synthetic lost ACK") }.drain(credential)
        val source = db.syncDao().identity()!!.sourceId
        val destination = SyncOutbox.sha(credential.fingerprint + ":" + credential.pairingId)
        val pending = db.syncDao().receipt(destination, first.id)!!
        assertEquals("RETRY", pending.status)
        db.close()
        db = ResearchDatabase.open(context, name)
        assertEquals(source, db.syncDao().identity()!!.sourceId)
        db.syncDao().receipt(pending.copy(nextAttemptAt = 0))
        val summary = ReviewSync(db) { _, _, event -> remote.add(event.eventId); SyncResponse(true) }.drain(credential)
        assertEquals(1, remote.size); assertEquals(1, summary.acknowledged); assertEquals(0, summary.pending)
    }
    @Test fun cancellationAfterRemoteWriteKeepsEventPending(): Unit = runBlocking {
        val first = review()
        var cancelled = false
        try { ReviewSync(db) { _, _, _ -> throw kotlinx.coroutines.CancellationException("Synthetic interruption") }.drain(credential) }
        catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertTrue(cancelled); assertNotNull(db.syncDao().event(first.id))
        assertEquals(1, ReviewSync(db) { _, _, _ -> SyncResponse(true) }.drain(credential).acknowledged)
    }
    @Test fun legacyReviewBackfillAndPerPairingReceiptsAreIndependent(): Unit = runBlocking {
        val first = review()
        // Add an explicit pre-outbox-style revision through the DAO for backfill.
        val legacy = first.copy(id = UUID.randomUUID().toString(), revision = 2, previousId = first.id, value = "FISH")
        db.reviewDao().insertReview(legacy)
        assertEquals(1, db.syncDao().missingCount())
        val sync = ReviewSync(db) { _, _, _ -> SyncResponse(true) }
        assertEquals(2, sync.drain(credential).acknowledged)
        val other = PairingCredential(credential.address, "b".repeat(64), UUID.randomUUID().toString(), "y".repeat(43), 1000.0, 2000.0)
        assertEquals(0, sync.summary(other).acknowledged)
        assertEquals(2, sync.drain(other).acknowledged)
        assertEquals(2, db.syncDao().count())
    }
    @Test fun conflictIsDurableAndNeverMarkedAcknowledged(): Unit = runBlocking {
        review()
        val sync = ReviewSync(db) { _, _, _ -> SyncResponse(false, "EVENT_CONFLICT", false) }
        val summary = sync.drain(credential)
        assertEquals(1, summary.conflicts); assertEquals(0, summary.acknowledged)
        var contacted = false
        ReviewSync(db) { _, _, _ -> contacted = true; SyncResponse(true) }.drain(credential)
        assertFalse(contacted)
        assertEquals(1, db.syncDao().count())
    }
    @Test fun actionSendsAtMostTwentyFiveAndRetryCooldownIsRespected(): Unit = runBlocking {
        repeat(26) { review((100 + it).toString()) }
        val sync = ReviewSync(db) { _, _, _ -> SyncResponse(true) }
        assertEquals(25, sync.drain(credential).acknowledged)
        assertEquals(26, sync.drain(credential).acknowledged)
        val other = PairingCredential(credential.address, credential.fingerprint, UUID.randomUUID().toString(), credential.token, 1000.0, 2000.0)
        var attempts = 0
        val failed = ReviewSync(db) { _, _, _ -> attempts++; throw java.io.IOException("Synthetic offline PC") }
        failed.drain(other)
        val destination = SyncOutbox.sha(other.fingerprint + ":" + other.pairingId)
        val head = db.syncDao().pending(destination).first()
        assertTrue(db.syncDao().receipt(destination, head.eventId)!!.nextAttemptAt > System.currentTimeMillis())
        // The cooling event itself is not retried; later events may progress.
        val seen = mutableListOf<String>()
        ReviewSync(db) { _, _, event -> seen.add(event.eventId); SyncResponse(true) }.drain(other)
        assertFalse(head.eventId in seen)
        assertEquals(1, attempts)
    }
    @Test fun archiveCapacityDoesNotLoseOrRejectLocalReview(): Unit = runBlocking {
        db.withTransaction {
            repeat(SyncOutbox.CAPACITY) { db.syncDao().enqueue(SyncEvent(eventId = "synthetic-capacity-$it", payload = "Explicit negative capacity fixture", sha256 = "0".repeat(64))) }
        }
        val first = review()
        assertEquals(SyncOutbox.CAPACITY, db.syncDao().count())
        assertNull(db.syncDao().event(first.id)); assertEquals(1, db.syncDao().missingCount())
        assertEquals(first.id, db.reviewDao().revisions(first.roundId).single().id)
    }
}
