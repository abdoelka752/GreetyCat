package org.greedycat.intelligence.connectivity

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PairingVaultTest {
    @Test fun encryptedCredentialSurvivesReopenAndForget() {
        withVault { context, name, vault ->
            val record = example()
            assertNull(vault.load())
            vault.save(record)
            val bytes = File(context.noBackupFilesDir, "$name.enc").readBytes()
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(record.token))
            assertEquals(record.token, PairingVault(context, name).load()!!.token)
            val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .getKey("org.greedycat.intelligence.$name", null)
            assertNull(key.encoded)
            vault.forget()
            assertNull(vault.load())
        }
    }
    @Test fun tamperingFailsClosedAndPreservesFile() {
        withVault { context, name, vault ->
            vault.save(example())
            val file = File(context.noBackupFilesDir, "$name.enc")
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertThrows(Exception::class.java) { vault.load() }
            assertTrue(file.exists())
            vault.forget()
        }
    }
    private fun example() = PairingCredential("https://127.0.0.1:8767", "a".repeat(64), UUID.randomUUID().toString(), "S".repeat(43), 1000.0, 2000.0)
    private fun withVault(action: (android.content.Context, String, PairingVault) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}"
        val vault = PairingVault(context, name)
        try { action(context, name, vault) }
        finally {
            vault.forget()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("org.greedycat.intelligence.$name")
        }
    }
}
