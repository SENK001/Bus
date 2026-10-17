package com.senk.bus.ui

import androidx.lifecycle.ViewModel

class AddEditRouteViewModel : ViewModel() {
    var routeId: Long = NO_ID
    var initializedFromDatabase: Boolean = false

    var name: String = ""
    var origin: String = ""
    var destination: String = ""

    fun resetForRoute(newRouteId: Long) {
        if (routeId == newRouteId) {
            return
        }
        routeId = newRouteId
        initializedFromDatabase = false
        name = ""
        origin = ""
        destination = ""
    }

    companion object {
        private const val NO_ID = -1L
    }
}
