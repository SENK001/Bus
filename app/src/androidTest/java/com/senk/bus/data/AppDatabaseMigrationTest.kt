package com.senk.bus.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_deduplicatesScheduleTimes() {
        val dbName = "migration-test"
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS routes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    origin TEXT NOT NULL,
                    destination TEXT NOT NULL,
                    isFavorite INTEGER NOT NULL,
                    isDefault INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    routeId INTEGER NOT NULL,
                    departureTime TEXT NOT NULL,
                    FOREIGN KEY(routeId) REFERENCES routes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX IF NOT EXISTS index_schedules_routeId ON schedules(routeId)")
            execSQL(
                "INSERT INTO routes(id, name, origin, destination, isFavorite, isDefault) " +
                    "VALUES (1, 'A', 'O', 'D', 0, 0)",
            )
            execSQL("INSERT INTO schedules(id, routeId, departureTime) VALUES (1, 1, '08:30')")
            execSQL("INSERT INTO schedules(id, routeId, departureTime) VALUES (2, 1, '08:30')")
            close()
        }

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            dbName,
        ).addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val rows = migrated.scheduleDao().getSchedulesForRouteSync(1L)
                .filter { it.departureTime == "08:30" }
            assertEquals(1, rows.size)
        } finally {
            migrated.close()
        }
    }
}
