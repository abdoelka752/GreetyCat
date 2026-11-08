package org.greedycat.intelligence.domain

import org.junit.Assert.*
import org.junit.Test

class DraftValidatorTest {
    @Test fun syntheticFixtureHasExplicitMissingEvidenceAndNoOutcome() {
        DraftValidator.validate(DemoSample.session, DemoSample.round, Provenance.SYNTHETIC)
        assertEquals(setOf(MissingField.BETTING_CLOSE, MissingField.OUTCOME, MissingField.FRAMES), DemoSample.round.missingFields)
    }

    @Test fun syntheticDataCannotEnterRealWorkspace() {
        assertThrows(IllegalArgumentException::class.java) {
            DraftValidator.validate(DemoSample.session, DemoSample.round, Provenance.REAL)
        }
    }

    @Test fun allIdentityDimensionsMustMatchSession() {
        val rounds = listOf(
            DemoSample.round.copy(sessionId = "00000000-0000-4000-8000-000000000099"),
            DemoSample.round.copy(contextId = "00000000-0000-4000-8000-000000000099"),
            DemoSample.round.copy(mode = GameMode.PROFESSIONAL),
            DemoSample.round.copy(provenance = Provenance.REAL),
        )
        rounds.forEach { round -> assertThrows(IllegalArgumentException::class.java) {
            DraftValidator.validate(DemoSample.session, round, Provenance.SYNTHETIC)
        } }
    }

    @Test fun invalidOrNoncanonicalTimestampsAreRejected() {
        listOf("2026-02-30T12:00:00.000Z", "2026-01-01T24:00:00.000Z", "2026-01-01T12:00:60.000Z",
            "2026-01-01T12:00:00Z", "2026-01-01T12:00:00.000+00:00", "0000-01-01T12:00:00.000Z").forEach { value ->
            assertThrows(Exception::class.java) { DraftValidator.requireTimestamp(value) }
        }
    }

    @Test fun nullValuesRemainMissingRatherThanInventingRoundData() {
        val session = DemoSample.session.copy(mode = GameMode.UNKNOWN)
        val round = DemoSample.round.copy(mode = GameMode.UNKNOWN, roundNumber = null, startedAt = null)
        DraftValidator.validate(session, round, Provenance.SYNTHETIC)
        assertTrue(round.missingFields.containsAll(setOf(MissingField.ROUND_NUMBER, MissingField.START_TIME, MissingField.MODE)))
    }

    @Test fun noDraftCanBecomeAConfirmedResult() {
        listOf(RoundPhase.RESULT_CONFIRMED, RoundPhase.ROUND_COMPLETED).forEach { phase ->
            assertThrows(IllegalArgumentException::class.java) {
                DraftValidator.validate(DemoSample.session, DemoSample.round.copy(phase = phase), Provenance.SYNTHETIC)
            }
        }
    }

    @Test fun closedBettingCannotHaveAnEarlierTimestampOrRemainOpen() {
        listOf(
            DemoSample.round.copy(phase = RoundPhase.BETTING_CLOSED, bettingClosedAt = "2025-12-31T23:59:00.000Z"),
            DemoSample.round.copy(bettingClosedAt = "2026-01-01T12:00:20.000Z"),
        ).forEach { round -> assertThrows(IllegalArgumentException::class.java) {
            DraftValidator.validate(DemoSample.session, round, Provenance.SYNTHETIC)
        } }
    }

    @Test fun identifiersAndRoundNumbersStayCanonical() {
        listOf("1", "not-a-uuid", "00000000-0000-4000-8000-00000000000G").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { DraftValidator.requireUuid(id) }
        }
        listOf("", "-1", "100.5", "1".repeat(33)).forEach { number ->
            assertThrows(IllegalArgumentException::class.java) {
                DraftValidator.validate(DemoSample.session, DemoSample.round.copy(roundNumber = number), Provenance.SYNTHETIC)
            }
        }
    }
}
