package com.senk.bus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.senk.bus.data.dao.RouteDao
import com.senk.bus.data.dao.ScheduleDao
import com.senk.bus.data.entity.Route
import com.senk.bus.data.entity.Schedule

/**
 * Room database singleton. Schema version is 1; a future schema change must bump the version and
 * add a [androidx.room.migration.Migration] here.
 */
@Database(entities = [Route::class, Schedule::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun routeDao(): RouteDao

    abstract fun scheduleDao(): ScheduleDao

    companion object {
        private const val DATABASE_NAME = "bus_database"

        @Volatile
        private var instance: AppDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
        }
    }
}
