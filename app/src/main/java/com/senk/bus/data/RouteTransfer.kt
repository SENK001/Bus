package com.senk.bus.data

import com.senk.bus.data.entity.Route
import com.senk.bus.data.entity.Schedule
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

data class RouteTransferItem(
    val name: String,
    val origin: String,
    val destination: String,
    val isFavorite: Boolean,
    val isDefault: Boolean,
    val departureTimes: List<String>,
)

data class RouteTransferParseResult(
    val routes: List<RouteTransferItem>,
    val skippedEntries: Int,
)

object RouteTransfer {
    const val EXPORT_VERSION: Int = 2

    fun buildExportBase64(
        routes: List<Route>,
        schedulesByRoute: Map<Long, List<Schedule>>,
    ): String {
        val routesArray = JSONArray()
        for (route in routes) {
            val routeObject = JSONObject()
            routeObject.put("name", route.name)
            routeObject.put("origin", route.origin)
            routeObject.put("destination", route.destination)
            routeObject.put("isFavorite", route.isFavorite)
            routeObject.put("isDefault", route.isDefault)

            val schedulesArray = JSONArray()
            for (schedule in schedulesByRoute[route.id].orEmpty()) {
                val scheduleObject = JSONObject()
                scheduleObject.put("departureTime", schedule.departureTime)
                schedulesArray.put(scheduleObject)
            }
            routeObject.put("schedules", schedulesArray)
            routesArray.put(routeObject)
        }

        val payload = JSONObject()
        payload.put("version", EXPORT_VERSION)
        payload.put("routes", routesArray)
        return Base64.getEncoder().encodeToString(payload.toString().toByteArray(StandardCharsets.UTF_8))
    }

    fun parseImportBase64(base64Payload: String): RouteTransferParseResult {
        val decoded = Base64.getMimeDecoder().decode(base64Payload)
        val json = String(decoded, StandardCharsets.UTF_8)
        val data = JSONObject(json)
        val routesArray = data.optJSONArray("routes")
            ?: throw JSONException("Import payload has no routes array")

        val routes = ArrayList<RouteTransferItem>()
        var skippedEntries = 0
        for (i in 0 until routesArray.length()) {
            val routeObject = routesArray.optJSONObject(i)
            if (routeObject == null) {
                skippedEntries += 1
                continue
            }
            val name = readText(routeObject, "name")
            val origin = readText(routeObject, "origin")
            val destination = readText(routeObject, "destination")
            if (name.isEmpty() || origin.isEmpty() || destination.isEmpty()) {
                skippedEntries += 1
                continue
            }

            val departureTimes = ArrayList<String>()
            val schedulesArray = routeObject.optJSONArray("schedules")
            if (schedulesArray != null) {
                for (j in 0 until schedulesArray.length()) {
                    val departureTime = readDepartureTime(schedulesArray.optJSONObject(j))
                    if (departureTime == null) {
                        skippedEntries += 1
                    } else {
                        departureTimes.add(departureTime)
                    }
                }
            }

            routes.add(
                RouteTransferItem(
                    name = name,
                    origin = origin,
                    destination = destination,
                    isFavorite = routeObject.optBoolean("isFavorite", false),
                    isDefault = routeObject.optBoolean("isDefault", false),
                    departureTimes = departureTimes,
                ),
            )
        }
        return RouteTransferParseResult(routes = routes, skippedEntries = skippedEntries)
    }

    private fun readText(obj: JSONObject?, key: String): String {
        if (obj == null) {
            return ""
        }
        val value = obj.opt(key)
        return if (value is String) value.trim() else ""
    }

    private fun readDepartureTime(obj: JSONObject?): String? {
        val departureTime = readText(obj, "departureTime")
        return if (NextDeparture.isValid(departureTime)) departureTime else null
    }
}
