package org.greedycat.intelligence.data

import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.greedycat.intelligence.domain.*
import org.greedycat.intelligence.ingestion.ReferenceImporter
import java.util.UUID

class ReviewRepository(
    private val database: ResearchDatabase,
    val provenance: Provenance,
    val files: ReferenceImporter,
) {
    private val dao = database.reviewDao()
    private val imports = Mutex()
    val references = dao.references()
    val history = dao.history()
    suspend fun reference(id: String) = dao.reference(id)
    suspend fun roundHistory(id: String) = dao.roundHistory(id)

    suspend fun importUri(uri: Uri): ReferenceFile = imports.withLock { register(files.importUri(uri)) }

    suspend fun register(reference: ReferenceFile): ReferenceFile {
        require(reference.provenance == provenance.name) { "Workspace provenance mismatch." }
        DraftValidator.requireUuid(reference.id)
        DraftValidator.requireTimestamp(reference.importedAt)
        require(reference.byteCount in 1..ReferenceImporter.MAX_BYTES)
        require(reference.mime in setOf("image/png", "image/jpeg", "image/webp", "video/mp4", "video/webm"))
        require(if(reference.mime.startsWith("image/")) reference.durationMs == null else reference.durationMs != null && reference.durationMs in 1..7_200_000)
        files.verify(reference)
        return database.withTransaction {
            dao.referenceByHash(reference.sha256)?.also {
                require(it.mime == reference.mime && it.byteCount == reference.byteCount && it.durationMs == reference.durationMs)
            } ?: reference.also { dao.insertReference(it) }
        }
    }

    suspend fun identity(): ReviewIdentity = database.withTransaction {
        dao.identity() ?: ReviewIdentity(contextId = UUID.randomUUID().toString(), reviewerId = UUID.randomUUID().toString()).also { dao.insertIdentity(it) }
    }

    suspend fun newContext(): ReviewIdentity = database.withTransaction {
        val identity = identity()
        val updated = identity.copy(contextId = UUID.randomUUID().toString())
        dao.changeContext(updated.contextId)
        updated
    }

    suspend fun confirm(input: ManualReview, expectedContextId: String): OutcomeReview {
        val reference = requireNotNull(dao.reference(input.evidenceId)) { "Reference is unavailable." }
        require(reference.provenance == provenance.name) { "Workspace provenance mismatch." }
        files.verify(reference)
        val confirmedAt = utcNow()
        ReviewValidator.validate(input, confirmedAt, reference.durationMs)
        return database.withTransaction {
            val identity = identity()
            val oldRound = database.researchDao().round(input.roundId)
            val previous = dao.revisions(input.roundId).firstOrNull()
            check(previous?.id == input.expectedPreviousId) { "This review changed. Reopen it before confirming." }
            if (oldRound == null) {
                check(identity.contextId == expectedContextId) { "Context changed. Reopen the review." }
                val sessionId = UUID.randomUUID().toString()
                ResearchRepository(database, provenance).addDraft(
                    SessionRecord(sessionId, identity.contextId, input.mode, provenance, input.observedAt),
                    RoundDraft(input.roundId, sessionId, identity.contextId, input.mode, provenance,
                        input.roundNumber, RoundPhase.RESULT_PENDING, input.observedAt, null),
                )
            } else {
                require(oldRound.provenance == provenance && oldRound.contextId == expectedContextId &&
                    oldRound.mode == input.mode && oldRound.roundNumber == input.roundNumber) { "Round identity cannot change during a correction." }
            }
            previous?.let { require(confirmedAt >= it.confirmedAt) { "Device clock moved backwards. Check its time." } }
            val result = OutcomeReview(UUID.randomUUID().toString(), input.roundId, reference.id,
                (previous?.revision ?: 0) + 1, previous?.id, input.outcome.name, input.observedAt,
                confirmedAt, identity.reviewerId, input.videoOffsetMs, input.reason.trim())
            dao.insertReview(result)
            SyncOutbox.enqueue(database, result)
            result
        }
    }
}
