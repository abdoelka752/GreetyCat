package org.greedycat.intelligence.domain

import org.junit.Assert.*
import org.junit.Test

class ReviewValidatorTest {
    private val review = ManualReview(DemoSample.round.id, DemoSample.session.id, GameMode.BASIC, "100",
        OutcomeValue.CORN, "2026-01-01T12:00:00.000Z", null, "Synthetic reference confirmation", null)
    private val now = "2026-01-01T12:01:00.000Z"
    @Test fun validImageAndVideoRequireExplicitTimeAndOffset() {
        ReviewValidator.validate(review, now, null)
        ReviewValidator.validate(review.copy(videoOffsetMs = 999), now, 1000)
    }
    @Test fun unknownModeFutureTimeAndAmbiguousNumbersAreRejected() {
        listOf(review.copy(mode = GameMode.UNKNOWN), review.copy(roundNumber = "00100"),
            review.copy(roundNumber = "-1"), review.copy(observedAt = "2026-01-01T12:02:00.000Z"),
            review.copy(reason = " "), review.copy(observedAt = "not known")).forEach {
            assertThrows(Exception::class.java) { ReviewValidator.validate(it, now, null) }
        }
    }
    @Test fun offsetCannotBeMissingOutsideVideoOrAttachedToImage() {
        listOf<Long?>(null, -1, 1000).forEach { offset ->
            assertThrows(IllegalArgumentException::class.java) { ReviewValidator.validate(review.copy(videoOffsetMs = offset), now, 1000) }
        }
        assertThrows(IllegalArgumentException::class.java) { ReviewValidator.validate(review.copy(videoOffsetMs = 0), now, null) }
    }
}
