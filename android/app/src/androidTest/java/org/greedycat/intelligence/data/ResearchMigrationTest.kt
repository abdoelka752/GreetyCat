package org.greedycat.intelligence.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.greedycat.intelligence.domain.DemoSample

@RunWith(AndroidJUnit4::class)
class ResearchMigrationTest {
    private val name = "test-migration-v1-v2.db"
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), ResearchDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @After fun deleteTestDatabase() { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }

    @Test fun versionFourMigratesWithoutDeletingPriorRecordsOrLedgerGuards() {
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO workspace_preferences VALUES (1,1)")
            PredictionSchema.protect(this)
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, SyncOutbox.migration).use { migrated ->
            migrated.query("SELECT compactSpacing FROM workspace_preferences").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            migrated.query("SELECT count(*) FROM sync_outbox").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            migrated.query("SELECT count(*) FROM sqlite_master WHERE type='trigger'").use { assertTrue(it.moveToFirst()); assertEquals(6, it.getInt(0)) }
        }
    }

    @Test fun versionOneRecordsAndMissingFlagsSurvivePreferenceMigration() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO capture_sessions VALUES (?,?,?,?,?)", arrayOf(DemoSample.session.id, DemoSample.session.contextId, "BASIC", "SYNTHETIC", DemoSample.session.startedAt))
            execSQL("INSERT INTO rounds VALUES (?,?,?,?,?,?,?,?,?)", arrayOf(DemoSample.round.id, DemoSample.session.id, DemoSample.session.contextId, "BASIC", "SYNTHETIC", "100", "BETTING_OPEN", DemoSample.round.startedAt, null))
            DemoSample.round.missingFields.forEach { field -> execSQL("INSERT INTO round_missing_fields VALUES (?,?)", arrayOf(DemoSample.round.id, field.name)) }
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, ResearchDatabase.MIGRATION_1_2).use { migrated ->
            migrated.query("SELECT roundNumber, provenance FROM rounds").use {
                assertTrue(it.moveToFirst()); assertEquals("100", it.getString(0)); assertEquals("SYNTHETIC", it.getString(1))
            }
            migrated.query("SELECT COUNT(*) FROM round_missing_fields").use { assertTrue(it.moveToFirst()); assertEquals(3, it.getInt(0)) }
            migrated.execSQL("INSERT INTO workspace_preferences VALUES (1, 1)")
            migrated.query("SELECT compactSpacing FROM workspace_preferences WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        }
    }

    @Test fun versionTwoPreferencesAndDraftsSurviveReviewMigration() {
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO workspace_preferences VALUES (1, 1)")
            execSQL("INSERT INTO capture_sessions VALUES (?,?,?,?,?)", arrayOf(DemoSample.session.id, DemoSample.session.contextId, "BASIC", "SYNTHETIC", DemoSample.session.startedAt))
            execSQL("INSERT INTO rounds VALUES (?,?,?,?,?,?,?,?,?)", arrayOf(DemoSample.round.id, DemoSample.session.id, DemoSample.session.contextId, "BASIC", "SYNTHETIC", "100", "BETTING_OPEN", DemoSample.round.startedAt, null))
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, ResearchDatabase.MIGRATION_2_3).use { migrated ->
            migrated.query("SELECT compactSpacing FROM workspace_preferences").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            migrated.query("SELECT roundNumber FROM rounds").use { assertTrue(it.moveToFirst()); assertEquals("100", it.getString(0)) }
            migrated.query("SELECT COUNT(*) FROM outcome_reviews").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun versionThreeReviewsSurvivePredictionLedgerMigration() {
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO workspace_preferences VALUES (1, 1)")
            execSQL("INSERT INTO review_identity VALUES (1, ?, ?)", arrayOf(DemoSample.session.contextId, DemoSample.session.id))
            execSQL("INSERT INTO capture_sessions VALUES (?,?,?,?,?)", arrayOf(DemoSample.session.id, DemoSample.session.contextId, "BASIC", "SYNTHETIC", DemoSample.session.startedAt))
            execSQL("INSERT INTO rounds VALUES (?,?,?,?,?,?,?,?,?)", arrayOf(DemoSample.round.id, DemoSample.session.id, DemoSample.session.contextId, "BASIC", "SYNTHETIC", "100", "RESULT_PENDING", DemoSample.round.startedAt, null))
            execSQL("INSERT INTO reference_files VALUES (?,?,?,?,?,?,?)", arrayOf<Any?>(DemoSample.round.id, "a".repeat(64), "image/png", 1, DemoSample.session.startedAt, "SYNTHETIC", null))
            execSQL("INSERT INTO outcome_reviews VALUES (?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(DemoSample.session.id, DemoSample.round.id, DemoSample.round.id, 1, null, "CORN", DemoSample.round.startedAt, "2026-01-01T12:00:20.000Z", DemoSample.session.id, null, "Synthetic migration fixture"))
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, PredictionSchema.migration).use { migrated ->
            migrated.query("SELECT contextId FROM review_identity").use { assertTrue(it.moveToFirst()); assertEquals(DemoSample.session.contextId, it.getString(0)) }
            migrated.query("SELECT compactSpacing FROM workspace_preferences").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            migrated.query("SELECT COUNT(*) FROM locked_predictions").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            migrated.query("SELECT value, reason FROM outcome_reviews").use { assertTrue(it.moveToFirst()); assertEquals("CORN", it.getString(0)); assertEquals("Synthetic migration fixture", it.getString(1)) }
            migrated.query("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'locked_predictions_no_%'").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        }
    }
}
