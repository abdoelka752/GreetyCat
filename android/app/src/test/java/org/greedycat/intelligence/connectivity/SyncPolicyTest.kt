package org.greedycat.intelligence.connectivity

import org.greedycat.intelligence.data.SyncOutbox
import org.junit.Assert.*
import org.junit.Test

class SyncPolicyTest {
    @Test fun httpErrorsCannotBecomeAcknowledgmentsThroughAContradictoryBody() {
        for ((code, status) in listOf(200 to "STORED", 200 to "REPLAY", 409 to "CONFLICT", 422 to "CONFLICT")) validateSyncHttpStatus(code, status)
        for ((code, status) in listOf(409 to "STORED", 422 to "REPLAY", 500 to "STORED", 200 to "CONFLICT", 200 to null))
            assertThrows(IllegalArgumentException::class.java) { validateSyncHttpStatus(code, status) }
    }
    @Test fun backoffIsBoundedAcrossRestartAttemptsAndBadCounters() {
        assertEquals(5000L, ReviewSync.backoff(1))
        assertEquals(10000L, ReviewSync.backoff(2))
        assertEquals(300000L, ReviewSync.backoff(Int.MAX_VALUE))
        assertEquals(5000L, ReviewSync.backoff(-1))
    }
    @Test fun checksumsCoverExactUtf8Bytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SyncOutbox.sha("abc"))
        assertNotEquals(SyncOutbox.sha("{\"value\":1}"), SyncOutbox.sha("{ \"value\":1}"))
    }
}
