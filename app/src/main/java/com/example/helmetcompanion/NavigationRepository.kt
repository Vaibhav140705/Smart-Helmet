package com.example.helmetcompanion

import org.json.JSONObject
import com.google.android.gms.maps.model.LatLng
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class NavigationRepository {

    fun fetchRoute(
        origin: LatLng,
        destination: LatLng,
        destinationName: String,
        onResult: (Result<RouteSession>) -> Unit
    ) {
        thread {
            try {
                val url = URL(
                    "https://router.project-osrm.org/route/v1/driving/" +
                            "${origin.longitude},${origin.latitude};" +
                            "${destination.longitude},${destination.latitude}" +
                            "?overview=full&geometries=polyline&steps=true"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "com.example.helmetcompanion")

                val stream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

                if (connection.responseCode !in 200..299) {
                    error("OSRM request failed with code ${connection.responseCode}")
                }
                val routeSession = parseRoute(response, destination, destinationName)
                onResult(Result.success(routeSession))
            } catch (error: Exception) {
                onResult(Result.failure(error))
            }
        }
    }

    private fun parseRoute(
        response: String,
        destinationLatLng: LatLng,
        destinationName: String
    ): RouteSession {
        val root = JSONObject(response)
        val code = root.optString("code", "UNKNOWN")
        if (code != "Ok") {
            error("OSRM error: $code - ${root.optString("message")}")
        }

        val routes = root.getJSONArray("routes")
        if (routes.length() == 0) error("No routes returned from OSRM.")

        val route = routes.getJSONObject(0)
        val legs = route.getJSONArray("legs")
        val leg = legs.getJSONObject(0)
        val steps = leg.getJSONArray("steps")

        val routePoints = decodePolyline(route.getString("geometry"))

        val totalDistanceMeters = route.getDouble("distance").toInt()
        val totalDurationSeconds = route.getDouble("duration").toInt()
        val totalDistanceText = formatDistance(totalDistanceMeters)
        val totalDurationText = formatDuration(totalDurationSeconds)

        val maneuvers = buildList {
            for (index in 0 until steps.length()) {
                val step = steps.getJSONObject(index)
                val maneuver = step.getJSONObject("maneuver")
                val type = maneuver.optString("type", "")
                val modifier = maneuver.optString("modifier", "")
                val instruction = buildInstruction(type, modifier)
                val normalized = normalizeManeuver(type, modifier)

                if (normalized == ManeuverType.UNKNOWN) continue

                val location = maneuver.getJSONArray("location")
                // OSRM returns coordinates as [longitude, latitude]
                val triggerPoint = LatLng(
                    location.getDouble(1),
                    location.getDouble(0)
                )

                add(
                    RouteManeuver(
                        id = "maneuver_$index",
                        instruction = instruction,
                        distanceMeters = step.getDouble("distance").toInt(),
                        durationText = formatDuration(step.getDouble("duration").toInt()),
                        maneuverType = normalized,
                        triggerPoint = triggerPoint
                    )
                )
            }
            add(
                RouteManeuver(
                    id = "maneuver_arrive",
                    instruction = "Arrive at destination",
                    distanceMeters = 0,
                    durationText = "",
                    maneuverType = ManeuverType.ARRIVE,
                    triggerPoint = destinationLatLng,
                    triggerDistanceMeters = 10f
                )
            )
        }

        return RouteSession(
            destinationName = destinationName,
            destinationLatLng = destinationLatLng,
            routePoints = routePoints,
            maneuvers = maneuvers,
            totalDistanceText = totalDistanceText,
            totalDurationText = totalDurationText
        )
    }

    private fun buildInstruction(type: String, modifier: String): String {
        val mod = modifier.replace("-", " ")
        return when (type) {
            "depart" -> "Head ${mod.ifBlank { "forward" }}"
            "turn" -> "Turn $mod".trim()
            "continue" -> "Continue ${mod.ifBlank { "straight" }}"
            "merge" -> "Merge ${mod.ifBlank { "" }}".trim()
            "on ramp" -> "Take the ramp ${mod.ifBlank { "" }}".trim()
            "off ramp" -> "Take the exit ${mod.ifBlank { "" }}".trim()
            "fork" -> "Keep ${mod.ifBlank { "" }} at the fork".trim()
            "end of road" -> "Turn $mod at the end of the road".trim()
            "roundabout", "rotary" -> "Enter the roundabout"
            "exit roundabout", "exit rotary" -> "Exit the roundabout"
            "arrive" -> "Arrive at destination"
            else -> "$type $mod".trim().replaceFirstChar { it.uppercase() }
        }
    }

    private fun normalizeManeuver(type: String, modifier: String): ManeuverType {
        val t = type.lowercase(Locale.US)
        val m = modifier.lowercase(Locale.US)
        return when {
            "arrive" in t -> ManeuverType.ARRIVE
            "uturn" in m || "u-turn" in m -> ManeuverType.UTURN
            "slight left" in m || "sharp left" in m -> ManeuverType.SLIGHT_LEFT
            "slight right" in m || "sharp right" in m -> ManeuverType.SLIGHT_RIGHT
            "left" in m -> ManeuverType.LEFT
            "right" in m -> ManeuverType.RIGHT
            "straight" in m || "continue" in t || "depart" in t -> ManeuverType.STRAIGHT
            "roundabout" in t || "rotary" in t -> ManeuverType.STRAIGHT
            else -> ManeuverType.UNKNOWN
        }
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) {
            String.format(Locale.US, "%.1f km", meters / 1000.0)
        } else {
            "$meters m"
        }
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            "${hours}h ${mins}min"
        } else {
            "${minutes} min"
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val polyline = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dLat

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dLng

            polyline.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return polyline
    }
}