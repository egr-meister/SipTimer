package com.egrmeister.siptimer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [WaterEntryEntity::class, GoalChangeEntity::class, ReminderSkipEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun waterEntryDao(): WaterEntryDao
    abstract fun goalChangeDao(): GoalChangeDao
    abstract fun reminderSkipDao(): ReminderSkipDao

    companion object {
        const val VERSION = 1
        const val NAME = "siptimer.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // No destructive fallback: an unknown upgrade path must fail loudly in testing.
                .build()
    }
}

/**
 * Schema migrations. Schemas are exported to app/schemas (Room Gradle plugin) and committed.
 * Version 1 is the initial schema; every future version adds an explicit Migration here, e.g.
 *
 *   val MIGRATION_1_2 = object : Migration(1, 2) {
 *       override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE ...") }
 *   }
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
