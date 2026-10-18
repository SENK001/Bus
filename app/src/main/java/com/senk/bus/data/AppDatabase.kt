package com.senk.bus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.senk.bus.data.dao.RouteDao
import com.senk.bus.data.dao.ScheduleDao
import com.senk.bus.data.entity.Route
import com.senk.bus.data.entity.Schedule

/**
 * Room database singleton. Schema version is 2; a future schema change must bump the version and
 * add a [androidx.room.migration.Migration] here.
 */
@Database(entities = [Route::class, Schedule::class], version = 2, exportSchema = true)
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
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS schedules_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        routeId INTEGER NOT NULL,
                        departureTime TEXT NOT NULL DEFAULT '00:00',
                        FOREIGN KEY(routeId) REFERENCES routes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO schedules_new(id, routeId, departureTime)
                    SELECT MIN(id) AS id, routeId, departureTime
                    FROM schedules
                    GROUP BY routeId, departureTime
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE schedules")
                db.execSQL("ALTER TABLE schedules_new RENAME TO schedules")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_schedules_routeId ON schedules(routeId)",
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_schedules_routeId_departureTime
                    ON schedules(routeId, departureTime)
                    """.trimIndent(),
                )
            }
        }
    }
}
