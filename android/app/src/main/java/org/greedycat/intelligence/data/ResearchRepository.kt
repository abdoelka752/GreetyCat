package org.greedycat.intelligence.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.greedycat.intelligence.domain.*

enum class InsertResult { INSERTED, REPLAY }

class ResearchRepository(private val database: ResearchDatabase, val provenance: Provenance) {
    private val dao = database.researchDao()
    fun observeRounds(): Flow<List<StoredRound>> = dao.observeRounds(provenance)
    fun observeCompactSpacing(): Flow<Boolean> = dao.observePreferences().map { it?.compactSpacing ?: false }
    suspend fun setCompactSpacing(compact: Boolean) { dao.savePreferences(WorkspacePreferences(compactSpacing = compact)) }

    /** Atomic compare-and-insert; no replace/upsert or outcome confirmation. */
    suspend fun addDraft(session: SessionRecord, draft: RoundDraft): InsertResult {
        DraftValidator.validate(session, draft, provenance)
        val sessionRow = SessionEntity(session.id, session.contextId, session.mode, session.provenance, session.startedAt)
        val roundRow = RoundEntity(draft.id, draft.sessionId, draft.contextId, draft.mode, draft.provenance,
            draft.roundNumber, draft.phase, draft.startedAt, draft.bettingClosedAt)
        return database.withTransaction {
            val oldSession = dao.session(session.id)
            val oldRound = dao.round(draft.id)
            check(oldSession == null || oldSession == sessionRow) { "Session identifier conflict" }
            check(oldRound == null || oldRound == roundRow) { "Round identifier conflict" }
            if (oldRound != null) {
                check(dao.missing(draft.id).map { it.field }.toSet() == draft.missingFields) { "Missing-data metadata conflict" }
                InsertResult.REPLAY
            } else {
                if (oldSession == null) dao.insertSession(sessionRow)
                dao.insertRound(roundRow)
                dao.insertMissing(draft.missingFields.map { MissingFieldEntity(draft.id, it) })
                InsertResult.INSERTED
            }
        }
    }

    suspend fun clearDemo() {
        check(provenance == Provenance.SYNTHETIC) { "Only the demo workspace can be cleared here" }
        database.withTransaction { dao.deleteSessions(Provenance.SYNTHETIC) }
    }
}
