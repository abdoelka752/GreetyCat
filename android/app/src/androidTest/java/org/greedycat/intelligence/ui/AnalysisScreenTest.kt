package org.greedycat.intelligence.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.greedycat.intelligence.MainActivity
import org.greedycat.intelligence.data.*
import org.greedycat.intelligence.domain.Provenance
import org.greedycat.intelligence.ingestion.ReferenceImporter
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AnalysisScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var db: ResearchDatabase
    private lateinit var root: File
    private lateinit var model: AnalysisViewModel
    @After fun cleanup() {
        if(::model.isInitialized) model.viewModelScope.cancel()
        if(::db.isInitialized) db.close()
        if(::root.isInitialized) root.deleteRecursively()
    }
    @Test fun emptyHistoryAndAttestedManualCheckpointNeverDisplayInventedProbabilities() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.cacheDir, "test-analysis-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = root }
        db = Room.inMemoryDatabaseBuilder(context, ResearchDatabase::class.java).addCallback(PredictionSchema.callback).build()
        val reviews = ReviewRepository(db, Provenance.SYNTHETIC, ReferenceImporter(isolated, Provenance.SYNTHETIC))
        val repo = PredictionRepository(db, reviews, Provenance.SYNTHETIC)
        model = AnalysisViewModel(repo)
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { ResearchTheme { AnalysisScreen(model) {} } } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Compute historical summary").fetchSemanticsNodes().isNotEmpty() && !model.state.value.loading }
        compose.onNodeWithText("Compute historical summary").performScrollTo().performClick()
        compose.waitUntil(10000) { model.state.value.summary != null && !model.state.value.busy }
        compose.onNodeWithText("No eligible observations. The prior alone is not evidence for a prediction.").assertExists()
        compose.onNodeWithText("Current round number").performScrollTo().performTextInput("100")
        compose.onNodeWithText("Record abstention checkpoint").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("unseenTarget").performScrollTo().performClick()
        compose.onNodeWithText("Record abstention checkpoint").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("predictionRow").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("ABSTAINED · EMPTY HISTORY").assertExists()
        val saved = repo.history.first().single()
        assertNull(saved.probabilitiesJson); assertEquals("SYNTHETIC", saved.provenance)
    }
}
