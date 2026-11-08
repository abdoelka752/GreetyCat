package org.greedycat.intelligence.connectivity

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.greedycat.intelligence.data.*

data class SyncSummary(val archived: Int, val acknowledged: Int, val conflicts: Int, val missing: Int) {
    val pending get() = archived - acknowledged - conflicts
}

class SyncResponse(val acknowledged: Boolean, val code: String? = null, val retryable: Boolean = false)

internal fun validateSyncHttpStatus(code: Int, status: String?) {
    require(if (code == 200) status in setOf("STORED", "REPLAY") else code in setOf(409, 422) && status == "CONFLICT")
}

/** A bounded, explicit foreground drain. Pending work survives process/network loss. */
class ReviewSync(private val db: ResearchDatabase, private val sender: suspend (PairingCredential, String, SyncEvent) -> SyncResponse = { c, s, e -> PairingClient().sendReview(c, s, e) }) {
    private val mutex = Mutex()
    private val dao = db.syncDao()
    private fun peer(c: PairingCredential) = SyncOutbox.sha(c.fingerprint + ":" + c.pairingId)
    suspend fun summary(c: PairingCredential): SyncSummary = mutex.withLock { summaryUnsafe(c) }
    private suspend fun summaryUnsafe(c: PairingCredential) = SyncSummary(dao.count(), dao.count(peer(c), "ACKED"), dao.count(peer(c), "CONFLICT"), dao.missingCount())
    suspend fun drain(c: PairingCredential): SyncSummary = mutex.withLock {
        PairingProtocol.validate(c)
        db.withTransaction { dao.missing().forEach { SyncOutbox.enqueue(db, it) } }
        val source = dao.identity()?.sourceId ?: return@withLock summaryUnsafe(c)
        val destination = peer(c)
        var sent = 0
        val stopAt = System.nanoTime() + 30_000_000_000L
        for (event in dao.pending(destination)) {
            if (sent >= 25 || System.nanoTime() >= stopAt) break
            val previous = dao.receipt(destination, event.eventId)
            val now = System.currentTimeMillis()
            if (previous != null && previous.nextAttemptAt > now && previous.nextAttemptAt <= now + 300000) continue
            val attempts = ((previous?.attempts ?: 0).coerceAtMost(1000000)) + 1
            sent++
            val result = try { sender(c, source, event) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (rejected: PairingRejected) {
                dao.receipt(SyncReceipt(destination, event.eventId, "RETRY", attempts, 0, "AUTH_REQUIRED"))
                throw rejected
            }
            catch (_: Exception) {
                dao.receipt(SyncReceipt(destination, event.eventId, "RETRY", attempts, System.currentTimeMillis() + backoff(attempts), "CONNECTION_UNVERIFIED"))
                break
            }
            dao.receipt(SyncReceipt(destination, event.eventId,
                if (result.acknowledged) "ACKED" else if (result.retryable) "RETRY" else "CONFLICT",
                attempts, if (result.retryable) System.currentTimeMillis() + backoff(attempts) else 0, result.code))
        }
        summaryUnsafe(c)
    }
    companion object { fun backoff(attempt: Int): Long = (5000L shl (attempt.coerceIn(1, 7) - 1)).coerceAtMost(300000) }
}
