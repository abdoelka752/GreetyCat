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
