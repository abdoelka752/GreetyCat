package org.greedycat.intelligence.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionEntity::class, RoundEntity::class, MissingFieldEntity::class, WorkspacePreferences::class,
    ReferenceFile::class, OutcomeReview::class, ReviewIdentity::class], version = 3, exportSchema = true)
@TypeConverters(ResearchConverters::class)
abstract class ResearchDatabase : RoomDatabase() {
    abstract fun researchDao(): ResearchDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `reference_files` (`id` TEXT NOT NULL, `sha256` TEXT NOT NULL, `mime` TEXT NOT NULL, `byteCount` INTEGER NOT NULL, `importedAt` TEXT NOT NULL, `provenance` TEXT NOT NULL, `durationMs` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reference_files_sha256` ON `reference_files` (`sha256`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `review_identity` (`id` INTEGER NOT NULL, `contextId` TEXT NOT NULL, `reviewerId` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `outcome_reviews` (`id` TEXT NOT NULL, `roundId` TEXT NOT NULL, `evidenceId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `previousId` TEXT, `value` TEXT NOT NULL, `observedAt` TEXT NOT NULL, `confirmedAt` TEXT NOT NULL, `reviewerId` TEXT NOT NULL, `videoOffsetMs` INTEGER, `reason` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`roundId`) REFERENCES `rounds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`evidenceId`) REFERENCES `reference_files`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`previousId`) REFERENCES `outcome_reviews`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outcome_reviews_roundId` ON `outcome_reviews` (`roundId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outcome_reviews_evidenceId` ON `outcome_reviews` (`evidenceId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outcome_reviews_previousId` ON `outcome_reviews` (`previousId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outcome_reviews_roundId_revision` ON `outcome_reviews` (`roundId`, `revision`)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workspace_preferences` (`id` INTEGER NOT NULL, `compactSpacing` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun open(context: Context, name: String): ResearchDatabase =
            Room.databaseBuilder(context.applicationContext, ResearchDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
