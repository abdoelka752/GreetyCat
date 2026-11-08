package org.greedycat.intelligence.data

import androidx.room.*
import org.greedycat.intelligence.domain.*

@Entity(tableName = "capture_sessions", indices = [Index(value = ["id", "contextId", "mode", "provenance"], unique = true)])
data class SessionEntity(
    @PrimaryKey val id: String,
    val contextId: String,
    val mode: GameMode,
    val provenance: Provenance,
    val startedAt: String,
)

@Entity(
    tableName = "rounds",
    foreignKeys = [ForeignKey(entity = SessionEntity::class,
        parentColumns = ["id", "contextId", "mode", "provenance"],
        childColumns = ["sessionId", "contextId", "mode", "provenance"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sessionId", "contextId", "mode", "provenance"]),
        Index(value = ["contextId", "mode", "roundNumber"], unique = true)],
)
data class RoundEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val contextId: String,
    val mode: GameMode,
    val provenance: Provenance,
    val roundNumber: String?,
    val phase: RoundPhase,
    val startedAt: String?,
    val bettingClosedAt: String?,
)

@Entity(tableName = "round_missing_fields", primaryKeys = ["roundId", "field"],
    foreignKeys = [ForeignKey(entity = RoundEntity::class, parentColumns = ["id"], childColumns = ["roundId"], onDelete = ForeignKey.CASCADE)])
data class MissingFieldEntity(val roundId: String, val field: MissingField)

@Entity(tableName = "workspace_preferences")
data class WorkspacePreferences(@PrimaryKey val id: Int = 1, val compactSpacing: Boolean = false)

data class StoredRound(
    @Embedded val round: RoundEntity,
    @Relation(parentColumn = "id", entityColumn = "roundId") val missing: List<MissingFieldEntity>,
)

class ResearchConverters {
    @TypeConverter fun mode(value: String) = GameMode.valueOf(value)
    @TypeConverter fun mode(value: GameMode) = value.name
    @TypeConverter fun provenance(value: String) = Provenance.valueOf(value)
    @TypeConverter fun provenance(value: Provenance) = value.name
    @TypeConverter fun phase(value: String) = RoundPhase.valueOf(value)
    @TypeConverter fun phase(value: RoundPhase) = value.name
    @TypeConverter fun field(value: String) = MissingField.valueOf(value)
    @TypeConverter fun field(value: MissingField) = value.name
}
