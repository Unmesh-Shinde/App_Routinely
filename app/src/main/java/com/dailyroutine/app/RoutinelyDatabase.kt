package com.dailyroutine.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyHealthMetricEntity::class],
    version = 4,
    exportSchema = false
)
abstract class RoutinelyDatabase : RoomDatabase() {
    abstract fun dailyHealthMetricDao(): DailyHealthMetricDao

    companion object {
        private const val DATABASE_NAME = "routinely.db"

        /** Add provenance without discarding the 180-day history already stored in Room. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val metrics = listOf("steps", "sleepHours", "activeCalories", "totalCalories", "heartPoints", "distanceKm", "weightKg")
                metrics.forEach { property ->
                    db.execSQL("ALTER TABLE daily_health_metrics ADD COLUMN ${property}SyncStatus TEXT NOT NULL DEFAULT 'UNKNOWN'")
                    db.execSQL("ALTER TABLE daily_health_metrics ADD COLUMN ${property}Source TEXT")
                    db.execSQL(
                        "UPDATE daily_health_metrics SET ${property}SyncStatus = 'SYNCED', " +
                            "${property}Source = 'legacy_room' WHERE $property IS NOT NULL"
                    )
                }
            }
        }

        @Volatile
        private var INSTANCE: RoutinelyDatabase? = null

        fun getInstance(context: Context): RoutinelyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoutinelyDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_3_4)
                .build().also { INSTANCE = it }
            }
        }
    }
}

