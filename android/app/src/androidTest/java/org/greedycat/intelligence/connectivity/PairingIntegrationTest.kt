package org.greedycat.intelligence.connectivity

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Requires the owned temporary PC server/USB forwarding fixture from tools/test_pairing_phone.py. */
class PairingIntegrationTest {
    @Test fun actualPcTrustExchangePersistenceExpiryAndRevocation(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.noBackupFilesDir, "t008-test.json")
        assumeTrue("No explicitly prepared cross-device pairing fixture.", fixture.exists())
        val json = Json.parseToJsonElement(fixture.readText()).jsonObject
        fun value(key: String) = json.getValue(key).jsonPrimitive.content
        val client = PairingClient()
        val address = value("address")
        val pin = value("fingerprint")
        var rejected = false
        try { client.pair(address, if (pin == "0".repeat(64)) "1".repeat(64) else "0".repeat(64), value("invitation")) }
        catch (_: javax.net.ssl.SSLException) { rejected = true }
        assertTrue("Wrong pin must fail before the invitation is consumed", rejected)
        rejected = false
        try { client.pair(value("wrong_host_address"), value("wrong_host_fingerprint"), value("invitation")) }
        catch (_: java.io.IOException) { rejected = true }
        assertTrue("Correct pin cannot bypass certificate hostname checks", rejected)
        val record = client.pair(address, pin, value("invitation"))
        val vault = PairingVault(context, "test-cross-device")
        try {
            vault.save(record)
            val reopened = PairingVault(context, "test-cross-device").load()!!
            client.probe(reopened)
            rejected = false
            try { client.pair(address, pin, value("invitation")) }
            catch (_: PairingRejected) { rejected = true }
            assertTrue(rejected)
            val expired = PairingProtocol.credential(json.getValue("expired").jsonObject, address, pin)
            rejected = false
            try { client.probe(expired) } catch (_: PairingRejected) { rejected = true }
            assertTrue("Server must reject expired credentials", rejected)
            client.revoke(reopened)
            rejected = false
            try { client.probe(reopened) } catch (_: PairingRejected) { rejected = true }
            assertTrue(rejected)
        } finally {
            vault.forget()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry("org.greedycat.intelligence.test-cross-device")
        }
    }
}
