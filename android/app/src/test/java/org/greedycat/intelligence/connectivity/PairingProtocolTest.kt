package org.greedycat.intelligence.connectivity

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PairingProtocolTest {
    @Test fun privateAddressesAreCanonicalAndHaveNoDnsOrUrlEscape() {
        assertEquals("https://192.168.1.2:8767", PairingProtocol.address(" https://192.168.1.2:8767/ "))
        for (input in listOf("https://10.0.0.1", "https://172.16.1.2", "https://172.31.1.2", "https://127.0.0.1:8767"))
            assertTrue(PairingProtocol.address(input).startsWith("https://"))
        for (input in listOf("http://127.0.0.1:8767", "https://example.com", "https://8.8.8.8", "https://169.254.1.2",
            "https://172.32.1.2", "https://192.168.001.1", "https://127.1", "https://2130706433", "https://[::1]",
            "https://user@127.0.0.1", "https://127.0.0.1/x", "https://127.0.0.1?q=secret", "https://127.0.0.1#x",
            "https://127.0.0.1:0", "https://127.0.0.1:65536"))
            assertThrows(Exception::class.java) { PairingProtocol.address(input) }
    }
    @Test fun fingerprintAndSecretsAreExact() {
        assertEquals("ab".repeat(32), PairingProtocol.pin("AB".repeat(32)))
        for (pin in listOf("", "a".repeat(63), "a".repeat(65), "g".repeat(64)))
            assertThrows(IllegalArgumentException::class.java) { PairingProtocol.pin(pin) }
        assertTrue(PairingProtocol.secret.matches("A".repeat(43)))
        assertFalse(PairingProtocol.secret.matches("A".repeat(43) + "\n"))
    }
    @Test fun credentialsAreVersionedAndRejectChangedShapeAndTimes() {
        val good = buildJsonObject {
            put("schema_version", "pairing-1"); put("pairing_id", "a3d33419-0e82-43b1-8f1d-6eabed1afbe7")
            put("issued_at", 1000); put("expires_at", 2000); put("token", "x".repeat(43))
        }
        val record = PairingProtocol.credential(good, "https://127.0.0.1:8767", "a".repeat(64))
        assertFalse(record.toString().contains(record.token))
        for ((key, value) in listOf("schema_version" to JsonPrimitive("other"), "pairing_id" to JsonPrimitive("bad"),
            "expires_at" to JsonPrimitive(999), "expires_at" to JsonPrimitive(999999), "token" to JsonPrimitive("short"))) {
            assertThrows(Exception::class.java) {
                PairingProtocol.credential(JsonObject(good + (key to value)), record.address, record.fingerprint)
            }
        }
        assertThrows(Exception::class.java) {
            PairingProtocol.credential(JsonObject(good + ("extra" to JsonPrimitive(true))), record.address, record.fingerprint)
        }
    }
    @Test fun boundedReadsRejectLargeBodies() {
        assertEquals(4096, ByteArray(4096).inputStream().pairingBytes().size)
        assertThrows(IllegalArgumentException::class.java) { ByteArray(4097).inputStream().pairingBytes() }
    }
}
