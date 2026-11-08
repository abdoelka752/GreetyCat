package org.greedycat.intelligence.data

import android.os.SystemClock
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.greedycat.intelligence.domain.*
import org.greedycat.intelligence.statistics.*
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.util.UUID

interface PredictionClock { fun now(): String; fun elapsedMs(): Long }
object AndroidPredictionClock : PredictionClock {
    override fun now() = utcNow()
    override fun elapsedMs() = SystemClock.elapsedRealtime()
}

data class ResearchSnapshot(val contextId: String, val mode: GameMode, val cutoff: String,
    val estimate: ResearchEstimate?, val error: String?, val members: List<EligibleObservation>,
    val json: String, val sha256: String, val trainingCutoff: String?)

class PredictionRepository(
    private val database: ResearchDatabase,
    private val reviews: ReviewRepository,
    val provenance: Provenance,
    private val clock: PredictionClock = AndroidPredictionClock,
) {
    init { require(reviews.provenance == provenance) { "Repository workspace provenance mismatch." } }
    private val dao = database.predictionDao()
    val history = dao.history()
    suspend fun contextId() = reviews.identity().contextId

    suspend fun research(mode: GameMode): ResearchSnapshot = snapshot(reviews.identity().contextId, mode, clock.now())

    internal suspend fun snapshot(context: String, mode: GameMode, cutoff: String): ResearchSnapshot {
        DraftValidator.requireUuid(context); DraftValidator.requireTimestamp(cutoff)
        require(mode != GameMode.UNKNOWN)
        val rows = dao.eligible(context, mode.name, provenance.name, cutoff)
        var problem: String? = if (rows.size > MAX_ROWS) "More than 1,000 eligible rounds. Analysis was not truncated; no summary was issued." else null
        val references = mutableMapOf<String, ReferenceFile>()
        if (problem == null) {
            try {
                for (row in rows) {
                    require(row.observedAt <= row.confirmedAt) { "Observation time follows confirmation time." }
                    if(row.evidenceId !in references) {
                        val file = requireNotNull(database.reviewDao().reference(row.evidenceId)) { "Evidence metadata is missing." }
                        require(file.provenance == provenance.name && file.importedAt < cutoff) { "Evidence provenance or import time is inconsistent." }
                        references[file.id] = file
                    }
                    require(requireNotNull(references[row.evidenceId]).importedAt <= row.confirmedAt) { "Evidence import follows confirmation time." }
                }
                require(references.values.sumOf { it.byteCount } <= MAX_EVIDENCE_BYTES) { "Evidence exceeds the 256 MiB verification budget." }
                for (file in references.values) reviews.files.verify(file)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { problem = if(e is IllegalArgumentException) e.message else "Evidence could not be verified. No summary was issued." }
        }
        val selected = if(problem == null) rows else emptyList()
        val estimate = if(problem == null) DirichletEstimator.estimate(selected.groupingBy { OutcomeValue.valueOf(it.value) }.eachCount()) else null
        val payload = buildJsonObject {
            put("schema_version", "local-research-1"); put("model_version", DirichletEstimator.MODEL_VERSION)
            put("context_id", context); put("mode", mode.name); put("provenance", provenance.name)
            put("features_cutoff", cutoff); put("alpha_per_category", DirichletEstimator.ALPHA)
            put("complete", problem == null); put("error", problem?.let(::JsonPrimitive) ?: JsonNull)
            putJsonArray("members") {
                selected.forEach { row -> addJsonObject {
                    put("round_id", row.roundId); put("review_id", row.reviewId); put("revision", row.revision)
                    put("value", row.value); put("observed_at", row.observedAt); put("confirmed_at", row.confirmedAt)
                    put("evidence_id", row.evidenceId); put("sha256", requireNotNull(references[row.evidenceId]).sha256)
                    put("video_offset_ms", row.videoOffsetMs?.let(::JsonPrimitive) ?: JsonNull); put("reviewer_id", row.reviewerId)
                } }
            }
            estimate?.let { stats ->
                put("sample_count", stats.sampleCount); put("normalized_predictive_entropy", stats.normalizedPredictiveEntropy)
                putJsonArray("categories") { stats.categories.forEach { row -> addJsonObject {
                    put("value", row.outcome.name); put("count", row.count)
                    put("empirical", row.empirical?.let(::JsonPrimitive) ?: JsonNull)
                    put("posterior_mean", row.posteriorMean); put("parameter_sd", row.parameterSd)
                } } }
            }
        }.toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return ResearchSnapshot(context, mode, cutoff, estimate, problem, selected, payload, hash, selected.maxOfOrNull { it.confirmedAt })
    }

    /** Manual acquisition is unverified: the immutable record must ABSTAIN. No backdated issue-time API. */
    suspend fun recordManual(roundId: String, context: String, mode: GameMode, number: String, horizonSeconds: Int, attested: Boolean): LockedPrediction {
        DraftValidator.requireUuid(roundId); DraftValidator.requireUuid(context)
        require(attested) { "Confirm that this is a current round whose result you do not know." }
        require(mode != GameMode.UNKNOWN)
        require(Regex("0|[1-9][0-9]{0,31}").matches(number)) { "Enter a canonical digit-only round number." }
        require(horizonSeconds in 5..120) { "Choose a local checkpoint window from 5 to 120 seconds." }
        fun replay(value: LockedPrediction): LockedPrediction {
            check(value.contextId == context && value.mode == mode.name && value.roundNumber == number && value.provenance == provenance.name && value.horizonSeconds == horizonSeconds) { "Checkpoint identifier conflict." }
            return value
        }
        dao.forRound(roundId)?.let { return replay(it) }
        check(dao.naturalPrediction(context, mode.name, number) == null) { "A checkpoint already exists for this round/context. Start a new context only for a distinct history." }
        val started = clock.now(); val elapsed = clock.elapsedMs()
        DraftValidator.requireTimestamp(started)
        dao.latestIssue()?.let { require(started >= it) { "Device clock moved backwards. Check its time." } }
        check(database.researchDao().round(roundId) == null && database.researchDao().naturalRound(context, mode.name, number) == null) { "This round already exists. Historical/imported rounds cannot receive a prospective checkpoint." }
        val snapshot = snapshot(context, mode, started)
        val issued = clock.now()
        val expires = DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.parse(started).plusSeconds(horizonSeconds.toLong()))
        PredictionPolicy.validateWindow(started, issued, expires, clock.elapsedMs()-elapsed)
        val samples = snapshot.estimate?.sampleCount ?: 0
        val reason = requireNotNull(PredictionPolicy.abstention(samples, snapshot.error == null, verifiedTarget = false))
        return database.withTransaction {
            dao.forRound(roundId)?.let { return@withTransaction replay(it) }
            check(dao.naturalPrediction(context, mode.name, number) == null) { "A checkpoint already exists for this round/context." }
            check(reviews.identity().contextId == context) { "Workspace context changed. Refresh before recording." }
            dao.latestIssue()?.let { require(issued >= it) { "Device clock moved backwards." } }
            check(database.researchDao().round(roundId) == null && database.researchDao().naturalRound(context, mode.name, number) == null) { "The target round became known before locking." }
            check(dao.eligible(context, mode.name, provenance.name, started) == snapshot.members || snapshot.error != null) { "Historical data changed during analysis. Refresh and retry." }
            // Check again inside the transaction; waiting for its lock cannot extend the deadline.
            val lockedAt = clock.now()
            PredictionPolicy.validateWindow(started, lockedAt, expires, clock.elapsedMs()-elapsed)
            val sessionId = UUID.randomUUID().toString()
            ResearchRepository(database, provenance).addDraft(
                SessionRecord(sessionId, context, mode, provenance, started),
                // The checkpoint clock does not establish the game's phase or round start.
                RoundDraft(roundId, sessionId, context, mode, provenance, number, RoundPhase.UNKNOWN, null, null),
            )
            val value = LockedPrediction(UUID.randomUUID().toString(), roundId, context, mode.name, provenance.name,
                number, "ANDROID", DirichletEstimator.MODEL_VERSION, UUID.randomUUID().toString(), snapshot.sha256,
                snapshot.json, snapshot.trainingCutoff, started, lockedAt, expires, horizonSeconds, "ABSTAINED", reason.name,
                samples, if(samples == 0) "UNKNOWN" else "LOW", null, "NO_PROVEN_EDGE", "MANUAL_UNVERIFIED", true)
            dao.insert(value)
            value
        }
    }

    companion object { const val MAX_ROWS = 1000; const val MAX_EVIDENCE_BYTES = 256L*1024*1024 }
}
