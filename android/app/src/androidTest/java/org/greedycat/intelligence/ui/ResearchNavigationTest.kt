package org.greedycat.intelligence.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.greedycat.intelligence.MainActivity
import org.greedycat.intelligence.ResearchApplication
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResearchNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetOnlyDemoAndAppearance() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<ResearchApplication>()
        application.demoRepository.clearDemo()
        application.realRepository.setCompactSpacing(false)
    }

    @Test fun navigationAndDemoPersistenceNeverPopulateRealHistory() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Open demo workspace").performScrollTo().performClick()
        compose.onNodeWithText("DEMO · SYNTHETIC").assertIsDisplayed()
        compose.onNodeWithText("Add sample draft").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("demoRow").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Add sample draft").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Sample draft already exists. No duplicate was added.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sample draft already exists. No duplicate was added.").assertExists()
        compose.onNodeWithText("Back to real workspace").performScrollTo().performClick()
        compose.onNodeWithText("History", useUnmergedTree = true).performClick()
        compose.onNodeWithText("No real rounds yet").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Open demo workspace").performScrollTo().performClick()
        compose.onNodeWithTag("demoRow").assertExists()
        compose.onNodeWithText("Clear demo records").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("demoRow").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun compactPreferenceSurvivesActivityRecreation() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("compactSwitch").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("compactSwitch").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.On }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("compactSwitch").assertIsOn()
    }
}
