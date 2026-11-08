package org.greedycat.intelligence.domain

import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

enum class OutcomeValue { CORN, TOMATO, CARROT, PEPPER, SHRIMP, COW, FISH, CHICKEN, SALAD, PIZZA }

data class ManualReview(
    val roundId: String,
    val evidenceId: String,
    val mode: GameMode,
    val roundNumber: String,
    val outcome: OutcomeValue,
    val observedAt: String,
    val videoOffsetMs: Long?,
    val reason: String,
    val expectedPreviousId: String?,
)

fun utcNow(): String = DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.now())

object ReviewValidator {
    fun validate(review: ManualReview, confirmedAt: String, durationMs: Long?) {
        DraftValidator.requireUuid(review.roundId)
        DraftValidator.requireUuid(review.evidenceId)
        review.expectedPreviousId?.let(DraftValidator::requireUuid)
        require(review.mode != GameMode.UNKNOWN) { "Choose the observed game mode." }
        require(Regex("[0-9]{1,32}").matches(review.roundNumber)) { "Enter a digit-only round number (up to 32 digits)." }
        require(review.roundNumber == "0" || !review.roundNumber.startsWith("0")) { "Remove leading zeros from the round number." }
        DraftValidator.requireTimestamp(review.observedAt)
        DraftValidator.requireTimestamp(confirmedAt)
        require(review.observedAt <= confirmedAt) { "Observation time cannot be in the future." }
        require(review.reason.trim().length in 3..256) { "Describe the evidence or correction in 3–256 characters." }
        if (durationMs == null) require(review.videoOffsetMs == null) { "Images have no video offset." }
        else require(review.videoOffsetMs != null && review.videoOffsetMs in 0 until durationMs) { "Choose a timestamp within the video." }
    }
}
