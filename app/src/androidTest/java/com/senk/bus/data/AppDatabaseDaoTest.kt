package com.senk.bus.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.senk.bus.data.entity.Route
import com.senk.bus.data.entity.Schedule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun setDefault_keepsSingleDefaultRoute() {
        val routeA = Route().apply {
            name = "A"
            origin = "O1"
            destination = "D1"
        }
        val routeB = Route().apply {
            name = "B"
            origin = "O2"
            destination = "D2"
        }
        routeA.id = db.routeDao().insert(routeA)
        routeB.id = db.routeDao().insert(routeB)

        db.routeDao().setDefault(routeA.id)
        db.routeDao().setDefault(routeB.id)

        val all = db.routeDao().getAllRoutesSync()
        val defaults = all.filter { it.isDefault }
        assertEquals(1, defaults.size)
        assertEquals(routeB.id, defaults[0].id)
    }

    @Test
    fun deleteRoute_cascadesSchedules() {
        val route = Route().apply {
            name = "A"
            origin = "O"
            destination = "D"
        }
        route.id = db.routeDao().insert(route)
        db.scheduleDao().insert(
            Schedule().apply {
                routeId = route.id
                departureTime = "08:30"
            },
        )
        db.scheduleDao().insert(
            Schedule().apply {
                routeId = route.id
                departureTime = "12:00"
            },
        )

        db.routeDao().delete(route)

        val schedules = db.scheduleDao().getSchedulesForRouteSync(route.id)
        assertTrue(schedules.isEmpty())
    }

    @Test
    fun scheduleUniqueIndex_blocksDuplicateTimePerRoute() {
        val route = Route().apply {
            name = "A"
            origin = "O"
            destination = "D"
        }
        route.id = db.routeDao().insert(route)

        db.scheduleDao().insert(
            Schedule().apply {
                routeId = route.id
                departureTime = "09:00"
            },
        )

        var duplicateRejected = false
        try {
            db.scheduleDao().insert(
                Schedule().apply {
                    routeId = route.id
                    departureTime = "09:00"
                },
            )
        } catch (_: Exception) {
            duplicateRejected = true
        }
        assertTrue(duplicateRejected)
    }
}
