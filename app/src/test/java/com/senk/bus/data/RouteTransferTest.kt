package com.senk.bus.data

import com.senk.bus.data.entity.Route
import com.senk.bus.data.entity.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RouteTransferTest {

    @Test
    fun parseImportBase64_filtersInvalidEntriesAndCountsSkips() {
        val payload = """
            {
              "version": 2,
              "routes": [
                {
                  "name": "A",
                  "origin": "O",
                  "destination": "D",
                  "isFavorite": true,
                  "isDefault": false,
                  "schedules": [{"departureTime":"08:30"}, {"departureTime":"99:99"}]
                },
                {
                  "name": " ",
                  "origin": "O",
                  "destination": "D",
                  "schedules": [{"departureTime":"07:00"}]
                },
                {
                  "name": "B",
                  "origin": "O2",
                  "destination": "D2",
                  "isDefault": true,
                  "schedules": [{"departureTime":"17:00"}]
                }
              ]
            }
        """.trimIndent()

        val base64 = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))

        val parsed = RouteTransfer.parseImportBase64(base64)
        assertEquals(2, parsed.routes.size)
        assertEquals(2, parsed.skippedEntries)

        val first = parsed.routes[0]
        assertEquals("A", first.name)
        assertTrue(first.isFavorite)
        assertEquals(listOf("08:30"), first.departureTimes)

        val second = parsed.routes[1]
        assertEquals("B", second.name)
        assertTrue(second.isDefault)
    }

    @Test
    fun buildExportBase64_containsVersionAndAllSchedules() {
        val route = Route().apply {
            id = 3L
            name = "R"
            origin = "X"
            destination = "Y"
            isFavorite = true
            isDefault = false
        }
        val schedules = listOf(
            Schedule().apply {
                routeId = 3L
                departureTime = "08:00"
            },
            Schedule().apply {
                routeId = 3L
                departureTime = "18:00"
            },
        )

        val base64 = RouteTransfer.buildExportBase64(listOf(route), mapOf(3L to schedules))
        val parsed = RouteTransfer.parseImportBase64(base64)

        assertEquals(1, parsed.routes.size)
        assertEquals(0, parsed.skippedEntries)
        assertEquals(listOf("08:00", "18:00"), parsed.routes[0].departureTimes)
        assertFalse(parsed.routes[0].isDefault)
        assertTrue(parsed.routes[0].isFavorite)
    }
}
