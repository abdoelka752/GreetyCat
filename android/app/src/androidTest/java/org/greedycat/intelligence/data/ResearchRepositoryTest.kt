package org.greedycat.intelligence.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.greedycat.intelligence.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ResearchRepositoryTest {
    private lateinit var database: ResearchDatabase
    private lateinit var repository: ResearchRepository

    @Before fun create() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ResearchDatabase::class.java).build()
        repository = ResearchRepository(database, Provenance.SYNTHETIC)
    }
    @After fun close() { database.close() }

    @Test fun emptyHistoryThenAtomicInsertAndReplay() = runBlocking {
        assertTrue(repository.observeRounds().first().isEmpty())
        assertEquals(InsertResult.INSERTED, repository.addDraft(DemoSample.session, DemoSample.round))
        assertEquals(InsertResult.REPLAY, repository.addDraft(DemoSample.session, DemoSample.round))
        val row = repository.observeRounds().first().single()
        assertEquals(DemoSample.round.id, row.round.id)
        assertEquals(DemoSample.round.missingFields, row.missing.map { it.field }.toSet())
    }

    @Test fun changedIdentifierContentCannotOverwriteExistingDraft() = runBlocking {
        repository.addDraft(DemoSample.session, DemoSample.round)
        var rejected = false
        try { repository.addDraft(DemoSample.session, DemoSample.round.copy(roundNumber = "101")) }
        catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertEquals("100", repository.observeRounds().first().single().round.roundNumber)
    }

    @Test fun duplicateNaturalRoundRollsBackNewSession() = runBlocking {
        repository.addDraft(DemoSample.session, DemoSample.round)
        val session = DemoSample.session.copy(id = UUID.randomUUID().toString())
        var rejected = false
        try { repository.addDraft(session, DemoSample.round.copy(id = UUID.randomUUID().toString(), sessionId = session.id)) }
        catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
        assertTrue(rejected)
        assertNull(database.researchDao().session(session.id))
        assertEquals(1, repository.observeRounds().first().size)
    }

    @Test fun numberCanRepeatInAnotherModeOrResetContext() = runBlocking {
        repository.addDraft(DemoSample.session, DemoSample.round)
        val professional = DemoSample.session.copy(id = UUID.randomUUID().toString(), mode = GameMode.PROFESSIONAL)
        repository.addDraft(professional, DemoSample.round.copy(id = UUID.randomUUID().toString(), sessionId = professional.id, mode = professional.mode))
        val reset = DemoSample.session.copy(id = UUID.randomUUID().toString(), contextId = UUID.randomUUID().toString())
        repository.addDraft(reset, DemoSample.round.copy(id = UUID.randomUUID().toString(), sessionId = reset.id, contextId = reset.contextId))
        assertEquals(3, repository.observeRounds().first().size)
    }

    @Test fun unknownRoundNumbersDoNotInventNaturalKeys() = runBlocking {
        repeat(2) { repository.addDraft(DemoSample.session, DemoSample.round.copy(id = UUID.randomUUID().toString(), roundNumber = null)) }
        assertEquals(2, repository.observeRounds().first().size)
        assertTrue(repository.observeRounds().first().all { it.missing.any { flag -> flag.field == MissingField.ROUND_NUMBER } })
    }

    @Test fun databaseForeignKeyRejectsMismatchedSessionMetadata() = runBlocking {
        repository.addDraft(DemoSample.session, DemoSample.round)
        val mismatched = repository.observeRounds().first().single().round.copy(id = UUID.randomUUID().toString(), mode = GameMode.PROFESSIONAL)
        var rejected = false
        try { database.researchDao().insertRound(mismatched) }
        catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun deletingDemoCascadesMissingFlagsAndCannotClearRealWorkspace() = runBlocking {
        repository.addDraft(DemoSample.session, DemoSample.round)
        repository.clearDemo()
        assertTrue(repository.observeRounds().first().isEmpty())
        assertTrue(database.researchDao().missing(DemoSample.round.id).isEmpty())
        var rejected = false
        try { ResearchRepository(database, Provenance.REAL).clearDemo() }
        catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun separateFilesPersistAcrossReopenAndRejectWrongProvenance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val realName = "test-real-${UUID.randomUUID()}.db"
        val demoName = "test-demo-${UUID.randomUUID()}.db"
        var real = ResearchDatabase.open(context, realName)
        var demo = ResearchDatabase.open(context, demoName)
        try {
            val realRepo = ResearchRepository(real, Provenance.REAL)
            var rejected = false
            try { realRepo.addDraft(DemoSample.session, DemoSample.round) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            ResearchRepository(demo, Provenance.SYNTHETIC).addDraft(DemoSample.session, DemoSample.round)
            realRepo.setCompactSpacing(true)
            real.close(); demo.close()
            real = ResearchDatabase.open(context, realName)
            demo = ResearchDatabase.open(context, demoName)
            assertTrue(ResearchRepository(real, Provenance.REAL).observeRounds().first().isEmpty())
            assertTrue(ResearchRepository(real, Provenance.REAL).observeCompactSpacing().first())
            assertEquals(1, ResearchRepository(demo, Provenance.SYNTHETIC).observeRounds().first().size)
        } finally {
            real.close(); demo.close()
            context.deleteDatabase(realName); context.deleteDatabase(demoName)
        }
    }
}
