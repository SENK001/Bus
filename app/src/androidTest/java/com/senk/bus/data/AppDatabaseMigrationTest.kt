package com.senk.bus.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_deduplicatesScheduleTimes() {
        val dbName = "migration-test"
        // v1 schema comes from app/schemas (asset). Duplicate times are allowed in v1.
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO routes(id, name, origin, destination, isFavorite, isDefault) " +
                    "VALUES (1, 'A', 'O', 'D', 0, 0)",
            )
            execSQL("INSERT INTO schedules(id, routeId, departureTime) VALUES (1, 1, '08:30')")
            execSQL("INSERT INTO schedules(id, routeId, departureTime) VALUES (2, 1, '08:30')")
        }

        // Runs MIGRATION_1_2 and validates the migrated schema against exported 2.json.
        val migrated = helper.runMigrationsAndValidate(
            dbName,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        )
        migrated.query(
            "SELECT COUNT(*) FROM schedules WHERE routeId = 1 AND departureTime = '08:30'",
        ).use { cursor ->
            val count = if (cursor.moveToFirst()) cursor.getInt(0) else -1
            assertEquals(1, count)
        }
    }
}
