package com.example.helmetcompanion

import android.location.Location

class ManeuverTriggerEngine {

    private var currentIndex = 0
    private val triggeredIds = mutableSetOf<String>()

    fun reset() {
        currentIndex = 0
        triggeredIds.clear()
    }

    fun onRouteStarted() {
        reset()
    }

    fun onLocationUpdate(location: Location, session: RouteSession): RouteManeuver? {
        if (session.maneuvers.isEmpty() || currentIndex >= session.maneuvers.size) {
            return null
        }

        while (currentIndex < session.maneuvers.size) {
            val maneuver = session.maneuvers[currentIndex]
            val distanceToTurn = distanceMeters(
                location.latitude,
                location.longitude,
                maneuver.triggerPoint.latitude,
                maneuver.triggerPoint.longitude
            )

            if (distanceToTurn <= maneuver.triggerDistanceMeters && triggeredIds.add(maneuver.id)) {
                return maneuver
            }

            val hasMovedPastTrigger = triggeredIds.contains(maneuver.id) && distanceToTurn > 30f
            val hasArrivedAtTrigger = distanceToTurn <= 5f
            if (hasMovedPastTrigger || hasArrivedAtTrigger) {
                currentIndex += 1
                continue
            }

            break
        }
        return null
    }

    private fun distanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, result)
        return result[0]
    }
}
