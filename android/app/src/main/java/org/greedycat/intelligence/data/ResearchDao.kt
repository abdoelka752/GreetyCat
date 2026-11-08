package org.greedycat.intelligence.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.greedycat.intelligence.domain.Provenance

@Dao
interface ResearchDao {
    @Insert suspend fun insertSession(value: SessionEntity)
    @Insert suspend fun insertRound(value: RoundEntity)
    @Insert suspend fun insertMissing(values: List<MissingFieldEntity>)
    @Query("SELECT * FROM capture_sessions WHERE id = :id") suspend fun session(id: String): SessionEntity?
    @Query("SELECT * FROM rounds WHERE id = :id") suspend fun round(id: String): RoundEntity?
    @Query("SELECT * FROM round_missing_fields WHERE roundId = :id") suspend fun missing(id: String): List<MissingFieldEntity>
    @Transaction
    @Query("SELECT * FROM rounds WHERE provenance = :provenance ORDER BY startedAt DESC, id ASC LIMIT 100")
    fun observeRounds(provenance: Provenance): Flow<List<StoredRound>>
    @Query("DELETE FROM capture_sessions WHERE provenance = :provenance") suspend fun deleteSessions(provenance: Provenance)
    @Query("SELECT * FROM workspace_preferences WHERE id = 1") fun observePreferences(): Flow<WorkspacePreferences?>
    @Upsert suspend fun savePreferences(value: WorkspacePreferences)
}
