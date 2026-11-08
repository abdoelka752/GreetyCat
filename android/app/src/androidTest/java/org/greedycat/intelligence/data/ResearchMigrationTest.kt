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
}
