package org.greedycat.intelligence.domain

import org.junit.Assert.*
import org.junit.Test

class PredictionPolicyTest {
    @Test fun emptySparseAndUnusableDatasetsAbstain() {
        assertEquals(Abstention.EMPTY_HISTORY, PredictionPolicy.abstention(0, true, true))
        assertEquals(Abstention.INSUFFICIENT_HISTORY, PredictionPolicy.abstention(29, true, true))
        assertEquals(Abstention.UNUSABLE_EVIDENCE, PredictionPolicy.abstention(100, false, true))
    }
    @Test fun unverifiedTargetNeverBecomesAnEstimateRegardlessOfSampleSize() {
        listOf(0,1,29,30,1000).forEach { assertNotNull(PredictionPolicy.abstention(it, true, false)) }
        assertEquals(Abstention.UNVERIFIED_TARGET, PredictionPolicy.abstention(30, true, false))
        assertNull(PredictionPolicy.abstention(30, true, true))
    }
    @Test fun expiredAndChangedClocksAreRejected() {
        val start = "2030-01-01T00:00:00.000Z"
        val expires = "2030-01-01T00:00:10.000Z"
        assertThrows(IllegalArgumentException::class.java) { PredictionPolicy.validateWindow(start, expires, expires, 10000) }
        assertThrows(IllegalArgumentException::class.java) { PredictionPolicy.validateWindow(start, "2030-01-01T00:00:08.000Z", expires, 1000) }
        assertThrows(IllegalArgumentException::class.java) { PredictionPolicy.validateWindow(start, "2029-12-31T23:59:59.000Z", expires, 1000) }
    }
    @Test fun monotonicAndUtcDurationMustAgree() {
        PredictionPolicy.validateWindow("2030-01-01T00:00:00.000Z", "2030-01-01T00:00:01.000Z", "2030-01-01T00:00:10.000Z", 1000)
        assertThrows(IllegalArgumentException::class.java) { PredictionPolicy.validateWindow("2030-01-01T00:00:00.000Z", "2030-01-01T00:00:01.000Z", "2030-01-01T00:00:10.000Z", -1) }
    }
}
