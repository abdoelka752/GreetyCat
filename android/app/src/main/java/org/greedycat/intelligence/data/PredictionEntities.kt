package org.greedycat.intelligence.data

import androidx.room.*

/** Complete immutable local record; snapshot copies survive source corrections/deletion. */
@Entity(tableName = "locked_predictions", indices = [Index(value = ["roundId", "source"], unique = true),
    Index(value = ["contextId", "mode", "roundNumber", "source"], unique = true)])
data class LockedPrediction(
    @PrimaryKey val id: String,
    val roundId: String,
    val contextId: String,
    val mode: String,
    val provenance: String,
    val roundNumber: String,
    val source: String,
    val modelVersion: String,
    val datasetId: String,
    val datasetSha256: String,
    val snapshotJson: String,
    val trainingCutoff: String?,
    val featuresCutoff: String,
    val issuedAt: String,
    val expiresAt: String,
    val horizonSeconds: Int,
    val status: String,
    val abstentionReason: String,
    val sampleCount: Int,
    val dataQuality: String,
    val probabilitiesJson: String?,
    val skillStatus: String,
    val targetVerification: String,
    val attestedUnknown: Boolean,
)

data class EligibleObservation(
    val roundId: String, val reviewId: String, val revision: Int, val value: String,
    val observedAt: String, val confirmedAt: String, val evidenceId: String, val videoOffsetMs: Long?, val reviewerId: String,
)

@Dao
interface PredictionDao {
    @Query("""SELECT r.id AS roundId, v.id AS reviewId, v.revision, v.value, v.observedAt, v.confirmedAt, v.evidenceId, v.videoOffsetMs, v.reviewerId
        FROM rounds r JOIN outcome_reviews v ON v.roundId=r.id
        WHERE r.contextId=:contextId AND r.mode=:mode AND r.provenance=:provenance
        AND v.confirmedAt < :cutoff AND v.observedAt < :cutoff
        AND v.revision=(SELECT MAX(x.revision) FROM outcome_reviews x WHERE x.roundId=r.id AND x.confirmedAt < :cutoff)
        ORDER BY v.confirmedAt, r.id LIMIT 1001""")
    suspend fun eligible(contextId: String, mode: String, provenance: String, cutoff: String): List<EligibleObservation>
    @Query("SELECT * FROM locked_predictions ORDER BY issuedAt DESC, id LIMIT 100")
    fun history(): kotlinx.coroutines.flow.Flow<List<LockedPrediction>>
    @Query("SELECT * FROM locked_predictions WHERE roundId=:id AND source='ANDROID'") suspend fun forRound(id: String): LockedPrediction?
    @Query("SELECT * FROM locked_predictions WHERE contextId=:context AND mode=:mode AND roundNumber=:number AND source='ANDROID' LIMIT 1")
    suspend fun naturalPrediction(context: String, mode: String, number: String): LockedPrediction?
    @Query("SELECT MAX(issuedAt) FROM locked_predictions") suspend fun latestIssue(): String?
    @Insert suspend fun insert(value: LockedPrediction)
}
