package org.greedycat.intelligence.domain

import java.time.Instant
import java.util.UUID

enum class GameMode { BASIC, PROFESSIONAL, UNKNOWN }
enum class Provenance { REAL, SYNTHETIC }
enum class RoundPhase { UNKNOWN, BETTING_OPEN, BETTING_CLOSED, SPINNING, RESULT_PENDING, RESULT_CONFIRMED, ROUND_COMPLETED }
enum class MissingField { ROUND_NUMBER, MODE, START_TIME, BETTING_CLOSE, FRAMES, OUTCOME }

data class SessionRecord(
    val id: String,
    val contextId: String,
    val mode: GameMode,
    val provenance: Provenance,
    val startedAt: String,
)

/** A draft only: final outcomes/confirmation are deliberately outside T005. */
data class RoundDraft(
    val id: String,
    val sessionId: String,
    val contextId: String,
    val mode: GameMode,
    val provenance: Provenance,
    val roundNumber: String?,
    val phase: RoundPhase,
    val startedAt: String?,
    val bettingClosedAt: String?,
) {
    val missingFields: Set<MissingField> get() = buildSet {
        if (roundNumber == null) add(MissingField.ROUND_NUMBER)
        if (mode == GameMode.UNKNOWN) add(MissingField.MODE)
        if (startedAt == null) add(MissingField.START_TIME)
        if (bettingClosedAt == null) add(MissingField.BETTING_CLOSE)
        add(MissingField.OUTCOME)
        add(MissingField.FRAMES)
    }
}

object DraftValidator {
    private val uuidPattern = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    private val timePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z")

    fun requireUuid(value: String) {
        require(uuidPattern.matches(value) && UUID.fromString(value).toString() == value) { "Canonical UUID required" }
    }

    fun requireTimestamp(value: String) {
        require(timePattern.matches(value) && !value.startsWith("0000")) { "Canonical UTC milliseconds required" }
        val parsed = Instant.parse(value)
        // Instant.parse normalizes leap seconds; this contract rejects them.
        require(value.substring(17, 19).toInt() < 60) { "Leap seconds are unsupported" }
        require(value.substring(11, 13).toInt() < 24 && value.substring(14, 16).toInt() < 60) { "Canonical clock time required" }
        require(parsed.nano % 1_000_000 == 0) { "Millisecond precision required" }
    }

    fun validate(session: SessionRecord, draft: RoundDraft, allowed: Provenance) {
        listOf(session.id, session.contextId, draft.id, draft.sessionId, draft.contextId).forEach(::requireUuid)
        requireTimestamp(session.startedAt)
        draft.startedAt?.let(::requireTimestamp)
        draft.bettingClosedAt?.let(::requireTimestamp)
        require(session.id == draft.sessionId && session.contextId == draft.contextId && session.mode == draft.mode) { "Session identity mismatch" }
        require(session.provenance == allowed && draft.provenance == allowed) { "Workspace provenance mismatch" }
        require(draft.roundNumber == null || Regex("[0-9]{1,32}").matches(draft.roundNumber)) { "Digit-string round number required" }
        require(draft.phase !in setOf(RoundPhase.RESULT_CONFIRMED, RoundPhase.ROUND_COMPLETED)) { "Draft storage cannot confirm an outcome" }
        require(draft.startedAt == null || draft.bettingClosedAt == null || draft.startedAt <= draft.bettingClosedAt) { "Betting close precedes start" }
        require(draft.phase != RoundPhase.BETTING_OPEN || draft.bettingClosedAt == null) { "Open betting cannot already be closed" }
    }
}
