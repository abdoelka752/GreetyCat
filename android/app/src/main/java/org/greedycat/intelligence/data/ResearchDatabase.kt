package org.greedycat.intelligence.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionEntity::class, RoundEntity::class, MissingFieldEntity::class, WorkspacePreferences::class], version = 2, exportSchema = true)
@TypeConverters(ResearchConverters::class)
abstract class ResearchDatabase : RoomDatabase() {
    abstract fun researchDao(): ResearchDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workspace_preferences` (`id` INTEGER NOT NULL, `compactSpacing` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun open(context: Context, name: String): ResearchDatabase =
            Room.databaseBuilder(context.applicationContext, ResearchDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2).build()
    }
}
