package org.greedycat.intelligence.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.greedycat.intelligence.domain.*
import org.greedycat.intelligence.ingestion.ReferenceImporter
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PredictionRepositoryTest {
    private lateinit var db: ResearchDatabase
    private lateinit var root: File
    private lateinit var reviews: ReviewRepository
    private lateinit var repo: PredictionRepository
    private lateinit var file: ReferenceFile
    private lateinit var identity: ReviewIdentity
    private lateinit var importer: ReferenceImporter
    private val clock = FakeClock()
    class FakeClock : PredictionClock {
        var wall = "2030-01-01T00:00:00.000Z"
        override fun now() = wall
        override fun elapsedMs() = 0L
    }
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.cacheDir, "test-prediction-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = root }
        importer = ReferenceImporter(isolated, Provenance.SYNTHETIC)
        db = Room.inMemoryDatabaseBuilder(context, ResearchDatabase::class.java).addCallback(PredictionSchema.callback).build()
        reviews = ReviewRepository(db, Provenance.SYNTHETIC, importer)
        repo = PredictionRepository(db, reviews, Provenance.SYNTHETIC, clock)
        file = reviews.register(importer.importStream(ByteArrayInputStream(ReferenceReviewTest.syntheticPng())))
        identity = reviews.identity()
    }
    @After fun cleanup() { db.close(); root.deleteRecursively() }
    private suspend fun checkpoint(number: String = "100", id: String = UUID.randomUUID().toString()) =
        repo.recordManual(id, identity.contextId, GameMode.BASIC, number, 30, true)

    private suspend fun seed(number: String = "1", mode: GameMode = GameMode.BASIC, context: String = identity.contextId): OutcomeReview {
        val session = SessionRecord(UUID.randomUUID().toString(), context, mode, Provenance.SYNTHETIC, "2029-12-31T23:59:00.000Z")
        val round = RoundDraft(UUID.randomUUID().toString(), session.id, context, mode, Provenance.SYNTHETIC, number,
            RoundPhase.RESULT_PENDING, session.startedAt, null)
        ResearchRepository(db, Provenance.SYNTHETIC).addDraft(session, round)
        return OutcomeReview(UUID.randomUUID().toString(), round.id, file.id, 1, null, "CORN", session.startedAt,
            "2029-12-31T23:59:01.000Z", identity.reviewerId, null, "Explicit synthetic chronological fixture").also { db.reviewDao().insertReview(it) }
    }

    @Test fun emptyCheckpointAbstainsAndReplayCannotChangeContent() = runBlocking {
        val saved = checkpoint()
        assertEquals("ABSTAINED", saved.status); assertEquals("EMPTY_HISTORY", saved.abstentionReason)
        assertNull(saved.probabilitiesJson); assertEquals("NO_PROVEN_EDGE", saved.skillStatus)
        assertEquals("MANUAL_UNVERIFIED", saved.targetVerification); assertTrue(saved.attestedUnknown)
        val draft = requireNotNull(db.researchDao().round(saved.roundId))
        assertEquals(RoundPhase.UNKNOWN, draft.phase); assertNull(draft.startedAt)
        clock.wall = "2030-01-01T00:02:00.000Z"
        assertEquals(saved, checkpoint(id = saved.roundId))
        var rejected = false
        try { checkpoint("101", saved.roundId) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected); assertEquals(1, repo.history.first().size)
    }

    @Test fun importedOrAlreadyKnownRoundCannotReceiveRetrospectiveCheckpoint() = runBlocking {
        val old = seed("100")
        var rejected = 0
        try { checkpoint() } catch (_: IllegalStateException) { rejected++ }
        try { checkpoint(id = old.roundId) } catch (_: IllegalStateException) { rejected++ }
        assertEquals(2, rejected); assertTrue(repo.history.first().isEmpty())
    }

    @Test fun correctionAsOfCutoffAndModeContextFilteringPreventLeakage() = runBlocking {
        val old = seed()
        seed("2", GameMode.PROFESSIONAL)
        seed("3", context = UUID.randomUUID().toString())
        val future = old.copy(id = UUID.randomUUID().toString(), revision = 2, previousId = old.id, value = "TOMATO", confirmedAt = "2030-01-01T00:00:01.000Z")
        db.reviewDao().insertReview(future)
        val before = repo.research(GameMode.BASIC)
        assertEquals(listOf(old.id), before.members.map { it.reviewId })
        assertEquals(1, before.estimate?.categories?.first()?.count)
        val saved = checkpoint()
        clock.wall = "2030-01-01T00:00:02.000Z"
        val after = repo.research(GameMode.BASIC)
        assertEquals(listOf(future.id), after.members.map { it.reviewId })
        assertEquals(0, after.estimate?.categories?.first()?.count)
        assertEquals(saved, db.predictionDao().forRound(saved.roundId))
        assertTrue(saved.snapshotJson.contains(old.id)); assertFalse(saved.snapshotJson.contains(future.id))
    }

    @Test fun sqliteTriggersRejectUpdateAndDeletionOfSnapshot() = runBlocking {
        val saved = checkpoint()
        val database = db.openHelper.writableDatabase
        assertThrows(android.database.sqlite.SQLiteException::class.java) { database.execSQL("UPDATE locked_predictions SET status='ESTIMATE'") }
        assertThrows(android.database.sqlite.SQLiteException::class.java) { database.execSQL("DELETE FROM locked_predictions") }
        assertEquals(saved, db.predictionDao().forRound(saved.roundId))
    }

    @Test fun laterTargetOutcomeOnlyEntersSubsequentDataset() = runBlocking {
        val saved = checkpoint()
        val result = OutcomeReview(UUID.randomUUID().toString(), saved.roundId, file.id, 1, null, "PIZZA",
            "2030-01-01T00:00:01.000Z", "2030-01-01T00:00:02.000Z", identity.reviewerId, null, "Synthetic outcome after the checkpoint")
        db.reviewDao().insertReview(result)
        clock.wall = "2030-01-01T00:00:03.000Z"
        val next = checkpoint("101")
        assertEquals(0, saved.sampleCount); assertEquals(1, next.sampleCount)
        assertFalse(saved.snapshotJson.contains(result.id)); assertTrue(next.snapshotJson.contains(result.id))
        assertEquals(saved, db.predictionDao().forRound(saved.roundId))
    }

    @Test fun mutatedEvidenceFailsClosedAndCannotIssueProbabilities() = runBlocking {
        seed(); importer.file(file).appendBytes(byteArrayOf(0))
        val summary = repo.research(GameMode.BASIC)
        assertNotNull(summary.error); assertNull(summary.estimate)
        val saved = checkpoint()
        assertEquals("UNUSABLE_EVIDENCE", saved.abstentionReason); assertNull(saved.probabilitiesJson)
    }

    @Test fun datasetCapAbstainsInsteadOfSilentlyTruncating() = runBlocking {
        db.withTransaction { repeat(1001) { seed((it+1).toString()) } }
        val summary = repo.research(GameMode.BASIC)
        assertNotNull(summary.error); assertNull(summary.estimate); assertTrue(summary.members.isEmpty())
    }

    @Test fun persistedSnapshotAndProtectionSurviveCloseAndReopen(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-prediction-${UUID.randomUUID()}.db"
        db.close(); db = ResearchDatabase.open(context, name)
        try {
            reviews = ReviewRepository(db, Provenance.SYNTHETIC, importer)
            identity = reviews.identity(); repo = PredictionRepository(db, reviews, Provenance.SYNTHETIC, clock)
            val saved = checkpoint()
            db.close(); db = ResearchDatabase.open(context, name)
            assertEquals(saved, db.predictionDao().forRound(saved.roundId))
            assertThrows(android.database.sqlite.SQLiteException::class.java) { db.openHelper.writableDatabase.execSQL("DELETE FROM locked_predictions") }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun backwardsClockAndChangedContextRejectNewRecords() = runBlocking {
        checkpoint()
        clock.wall = "2029-12-31T23:59:59.000Z"
        var rejected = 0
        try { checkpoint("101") } catch (_: IllegalArgumentException) { rejected++ }
        clock.wall = "2030-01-01T00:00:01.000Z"
        reviews.newContext()
        try { checkpoint("102") } catch (_: IllegalStateException) { rejected++ }
        assertEquals(2, rejected); assertEquals(1, repo.history.first().size)
    }

    @Test fun demoClearRetainsLedgerAndNumberReuseRequiresNewContext() = runBlocking {
        val saved = checkpoint()
        ResearchRepository(db, Provenance.SYNTHETIC).clearDemo()
        assertNull(db.researchDao().round(saved.roundId))
        assertEquals(saved, repo.history.first().single())
        var rejected = false
        try { checkpoint() } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        identity = reviews.newContext()
        val distinct = checkpoint()
        assertNotEquals(saved.contextId, distinct.contextId)
        assertEquals(2, repo.history.first().size)
    }
}
