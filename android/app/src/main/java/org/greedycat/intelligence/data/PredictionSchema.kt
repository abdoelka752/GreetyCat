package org.greedycat.intelligence.data

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PredictionSchema {
    fun protect(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS locked_predictions_no_update BEFORE UPDATE ON locked_predictions BEGIN SELECT RAISE(ABORT, 'Locked predictions are immutable'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS locked_predictions_no_delete BEFORE DELETE ON locked_predictions BEGIN SELECT RAISE(ABORT, 'Locked predictions require an explicit future retention workflow'); END")
    }
    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) { protect(db) }
    }
    val migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `locked_predictions` (`id` TEXT NOT NULL, `roundId` TEXT NOT NULL, `contextId` TEXT NOT NULL, `mode` TEXT NOT NULL, `provenance` TEXT NOT NULL, `roundNumber` TEXT NOT NULL, `source` TEXT NOT NULL, `modelVersion` TEXT NOT NULL, `datasetId` TEXT NOT NULL, `datasetSha256` TEXT NOT NULL, `snapshotJson` TEXT NOT NULL, `trainingCutoff` TEXT, `featuresCutoff` TEXT NOT NULL, `issuedAt` TEXT NOT NULL, `expiresAt` TEXT NOT NULL, `horizonSeconds` INTEGER NOT NULL, `status` TEXT NOT NULL, `abstentionReason` TEXT NOT NULL, `sampleCount` INTEGER NOT NULL, `dataQuality` TEXT NOT NULL, `probabilitiesJson` TEXT, `skillStatus` TEXT NOT NULL, `targetVerification` TEXT NOT NULL, `attestedUnknown` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_locked_predictions_roundId_source` ON `locked_predictions` (`roundId`, `source`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_locked_predictions_contextId_mode_roundNumber_source` ON `locked_predictions` (`contextId`, `mode`, `roundNumber`, `source`)")
            protect(db)
        }
    }
}
