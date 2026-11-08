package org.greedycat.intelligence.domain

import java.time.Instant
import kotlin.math.abs

enum class Abstention { EMPTY_HISTORY, INSUFFICIENT_HISTORY, UNUSABLE_EVIDENCE, UNVERIFIED_TARGET }

object PredictionPolicy {
    const val MIN_SAMPLES = 30 // A research guard, not a statistically validated threshold.
    fun abstention(samples: Int, usableEvidence: Boolean, verifiedTarget: Boolean): Abstention? {
        require(samples >= 0)
        return when {
            !usableEvidence -> Abstention.UNUSABLE_EVIDENCE
            samples == 0 -> Abstention.EMPTY_HISTORY
            samples < MIN_SAMPLES -> Abstention.INSUFFICIENT_HISTORY
            !verifiedTarget -> Abstention.UNVERIFIED_TARGET
            else -> null
        }
    }

    fun validateWindow(start: String, issued: String, expires: String, elapsedMs: Long) {
        listOf(start, issued, expires).forEach(DraftValidator::requireTimestamp)
        val wallMs = Instant.parse(issued).toEpochMilli() - Instant.parse(start).toEpochMilli()
        require(elapsedMs >= 0 && wallMs >= 0 && abs(wallMs - elapsedMs) <= 1_000) { "Device clock changed during analysis. Retry after checking its time." }
        require(start <= issued && issued < expires) { "The checkpoint deadline passed before locking." }
    }
}
