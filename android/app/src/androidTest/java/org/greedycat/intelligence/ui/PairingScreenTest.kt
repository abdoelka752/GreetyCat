package org.greedycat.intelligence.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import org.greedycat.intelligence.connectivity.PairingVault
import org.junit.Rule
import org.junit.Test

class PairingScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun historyIsSentOnlyAfterExplicitSyncConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}"
        val vault = PairingVault(context, name)
        val db = org.greedycat.intelligence.data.ResearchDatabase.open(context, "$name.db")
        val calls = java.util.concurrent.atomic.AtomicInteger()
        try {
            kotlinx.coroutines.runBlocking {
                db.syncDao().identity(org.greedycat.intelligence.data.SyncIdentity(sourceId = UUID.randomUUID().toString()))
                val payload = "{\"provenance\":\"SYNTHETIC\",\"fixture\":\"UI consent control\"}"
                db.syncDao().enqueue(org.greedycat.intelligence.data.SyncEvent(eventId = UUID.randomUUID().toString(), payload = payload,
                    sha256 = org.greedycat.intelligence.data.SyncOutbox.sha(payload)))
            }
            vault.save(org.greedycat.intelligence.connectivity.PairingCredential("https://127.0.0.1:8767", "a".repeat(64), UUID.randomUUID().toString(), "S".repeat(43), 1000.0, 2000.0))
            val sync = org.greedycat.intelligence.connectivity.ReviewSync(db) { _, _, _ -> calls.incrementAndGet(); org.greedycat.intelligence.connectivity.SyncResponse(true) }
            val model = PairingViewModel(vault, sync = sync)
            compose.setContent { MaterialThemeForPairing { PairingScreen(model) {} } }
            compose.waitUntil(5000) { !model.state.value.loading }
            compose.onNodeWithText("Sync reviewed history").performScrollTo().performClick()
            compose.onNodeWithText("Cancel").performClick()
            org.junit.Assert.assertEquals(0, calls.get())
            compose.onNodeWithText("Sync reviewed history").performScrollTo().performClick()
            compose.onNodeWithText("Sync reviews").performClick()
            compose.waitUntil(5000) { model.state.value.synchronization?.acknowledged == 1 }
            org.junit.Assert.assertEquals(1, calls.get())
        } finally {
            vault.forget(); db.close(); context.deleteDatabase("$name.db")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("org.greedycat.intelligence.$name")
        }
    }
    @Test fun consentIsRequiredAndChangingAddressResetsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}"
        val vault = PairingVault(context, name)
        val model = PairingViewModel(vault)
        try {
            compose.setContent { MaterialThemeForPairing { PairingScreen(model) {} } }
            compose.waitUntil(5000) { !model.state.value.loading }
            compose.onNodeWithTag("pairingSubmit").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("pairingFingerprint").performScrollTo().performTextInput("a".repeat(64))
            compose.onNodeWithTag("pairingInvitation").performScrollTo().performTextInput("S".repeat(43))
            compose.onNodeWithTag("pairingSubmit").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("pairingConfirmed").performScrollTo().performClick()
            compose.onNodeWithTag("pairingSubmit").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("pairingAddress").performScrollTo().performTextReplacement("https://127.0.0.1:9999")
            compose.onNodeWithTag("pairingConfirmed").assertIsOff()
            compose.onNodeWithTag("pairingSubmit").performScrollTo().assertIsNotEnabled()
        } finally {
            vault.forget()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("org.greedycat.intelligence.$name")
        }
    }
}

@androidx.compose.runtime.Composable
private fun MaterialThemeForPairing(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(content = content)
}
