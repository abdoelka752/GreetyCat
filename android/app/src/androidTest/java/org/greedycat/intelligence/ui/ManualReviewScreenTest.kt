package org.greedycat.intelligence.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.greedycat.intelligence.data.*
import org.greedycat.intelligence.domain.Provenance
import org.greedycat.intelligence.ingestion.ReferenceImporter
import org.greedycat.intelligence.MainActivity
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ManualReviewScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var database: ResearchDatabase
    private lateinit var root: File
    @After fun cleanup() { if(::database.isInitialized) database.close(); if(::root.isInitialized) root.deleteRecursively() }

    @Test fun syntheticImageCanBeReviewedWithExplicitAttestationAndAppearsInHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.cacheDir, "test-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = root }
        database = Room.inMemoryDatabaseBuilder(context, ResearchDatabase::class.java).build()
        val importer = ReferenceImporter(isolated, Provenance.SYNTHETIC)
        val repository = ReviewRepository(database, Provenance.SYNTHETIC, importer)
        val file = repository.register(importer.importStream(ByteArrayInputStream(ReferenceReviewTest.syntheticPng())))
        val round = UUID.randomUUID().toString()
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { ResearchTheme { ManualReviewScreen(repository, file.id, round) {} } }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Selected reference image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Basic").performScrollTo().performClick()
        compose.onNodeWithText("Observed round number").performScrollTo().performTextInput("100")
        compose.onNodeWithText("Choose observed outcome").performScrollTo().performClick()
        compose.onNodeWithText("CORN").performClick()
        compose.onNodeWithText("Observed UTC time").performScrollTo().performTextInput("2026-01-01T12:00:00.000Z")
        compose.onNodeWithText("What evidence confirms this result?").performScrollTo().performTextInput("Explicit synthetic test image")
        compose.onNodeWithText("Confirm and save").performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Confirm and save").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Human-confirmed review saved. Previous revisions were retained.").fetchSemanticsNodes().isNotEmpty() }
        val history = repository.history.first().single()
        assertEquals("CORN", history.reviews.single().value)
        assertEquals(Provenance.SYNTHETIC, history.round.provenance)
    }
}
