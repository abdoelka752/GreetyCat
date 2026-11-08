package org.greedycat.intelligence.domain

/** Explicit fixed synthetic draft; never interpreted as an observed outcome. */
object DemoSample {
    val session = SessionRecord(
        "00000000-0000-4000-8000-000000000003",
        "00000000-0000-4000-8000-000000000002",
        GameMode.BASIC, Provenance.SYNTHETIC, "2026-01-01T12:00:00.000Z",
    )
    val round = RoundDraft(
        "00000000-0000-4000-8000-000000000001", session.id, session.contextId,
        GameMode.BASIC, Provenance.SYNTHETIC, "100", RoundPhase.BETTING_OPEN,
        "2026-01-01T12:00:00.000Z", null,
    )
}
