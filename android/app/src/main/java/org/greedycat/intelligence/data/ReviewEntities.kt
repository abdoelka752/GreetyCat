package org.greedycat.intelligence.data

import androidx.room.*

@Entity(tableName = "reference_files", indices = [Index(value = ["sha256"], unique = true)])
data class ReferenceFile(
    @PrimaryKey val id: String,
    val sha256: String,
    val mime: String,
    val byteCount: Long,
    val importedAt: String,
    val provenance: String,
    val durationMs: Long?,
)

@Entity(tableName = "review_identity")
data class ReviewIdentity(@PrimaryKey val id: Int = 1, val contextId: String, val reviewerId: String)

/** Append-only human revisions. The draft itself is never rewritten. */
@Entity(tableName = "outcome_reviews", foreignKeys = [
    ForeignKey(entity = RoundEntity::class, parentColumns = ["id"], childColumns = ["roundId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ReferenceFile::class, parentColumns = ["id"], childColumns = ["evidenceId"]),
    ForeignKey(entity = OutcomeReview::class, parentColumns = ["id"], childColumns = ["previousId"]),
], indices = [Index("roundId"), Index("evidenceId"), Index(value = ["previousId"], unique = true),
    Index(value = ["roundId", "revision"], unique = true)])
data class OutcomeReview(
    @PrimaryKey val id: String,
    val roundId: String,
    val evidenceId: String,
    val revision: Int,
    val previousId: String?,
    val value: String,
    val observedAt: String,
    val confirmedAt: String,
    val reviewerId: String,
    val videoOffsetMs: Long?,
    val reason: String,
)

data class ReviewHistory(
    @Embedded val round: RoundEntity,
    @Relation(parentColumn = "id", entityColumn = "roundId") val reviews: List<OutcomeReview>,
)

@Dao
interface ReviewDao {
    @Insert suspend fun insertReference(value: ReferenceFile)
    @Query("SELECT * FROM reference_files WHERE sha256 = :hash") suspend fun referenceByHash(hash: String): ReferenceFile?
    @Query("SELECT * FROM reference_files WHERE id = :id") suspend fun reference(id: String): ReferenceFile?
    @Query("SELECT * FROM reference_files ORDER BY importedAt DESC, id LIMIT 100") fun references(): kotlinx.coroutines.flow.Flow<List<ReferenceFile>>
    @Insert suspend fun insertReview(value: OutcomeReview)
    @Query("SELECT * FROM outcome_reviews WHERE roundId = :id ORDER BY revision DESC") suspend fun revisions(id: String): List<OutcomeReview>
    @Transaction
    @Query("SELECT * FROM rounds ORDER BY startedAt DESC, id LIMIT 100") fun history(): kotlinx.coroutines.flow.Flow<List<ReviewHistory>>
    @Transaction
    @Query("SELECT * FROM rounds WHERE id=:id") suspend fun roundHistory(id: String): ReviewHistory?
    @Query("SELECT * FROM review_identity WHERE id=1") suspend fun identity(): ReviewIdentity?
    @Insert suspend fun insertIdentity(value: ReviewIdentity)
    @Query("UPDATE review_identity SET contextId=:contextId WHERE id=1") suspend fun changeContext(contextId: String)
}
