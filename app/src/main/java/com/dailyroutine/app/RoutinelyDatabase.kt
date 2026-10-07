package com.dailyroutine.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DailyHealthMetricEntity::class],
    version = 3,
    exportSchema = false
)
abstract class RoutinelyDatabase : RoomDatabase() {
    abstract fun dailyHealthMetricDao(): DailyHealthMetricDao

    companion object {
        private const val DATABASE_NAME = "routinely.db"

        @Volatile
        private var INSTANCE: RoutinelyDatabase? = null

        fun getInstance(context: Context): RoutinelyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoutinelyDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}

