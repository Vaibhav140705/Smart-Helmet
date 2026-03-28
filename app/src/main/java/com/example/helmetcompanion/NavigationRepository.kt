package com.example.helmetcompanion

import com.google.android.gms.maps.model.LatLng
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread

class NavigationRepository {

    fun fetchRoute(
        origin: LatLng,
        destination: LatLng,
        destinationName: String,
        onResult: (Result<RouteSession>) -> Unit
    ) {
        val directionsKey = BuildConfig.DIRECTIONS_API_KEY.ifBlank { BuildConfig.MAPS_API_KEY }
        if (directionsKey.isBlank()) {
            onResult(Result.failure(IllegalStateException("DIRECTIONS_API_KEY or MAPS_API_KEY is missing from local.properties")))
            return
        }

        thread {
            try {
                val url = URL(
                    "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.latitude},${origin.longitude}&" +
                        "destination=${destination.latitude},${destination.longitude}&" +
                        "mode=driving&alternatives=false&units=metric&key=${URLEncoder.encode(directionsKey, "UTF-8")}"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000

                val stream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val response = BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.readText()
                }

                if (connection.responseCode !in 200..299) {
                    error(parseApiError(response))
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
        val status = root.optString("status", "UNKNOWN")
        if (status != "OK") {
            val apiMessage = root.optString("error_message")
            error("Directions API error: $status${if (apiMessage.isNotBlank()) " - $apiMessage" else ""}")
        }
        val routes = root.getJSONArray("routes")
        if (routes.length() == 0) {
            error("No routes returned from Google Directions API.")
        }
        val route = routes.getJSONObject(0)
        val leg = route.getJSONArray("legs").getJSONObject(0)
        val steps = leg.getJSONArray("steps")
        val routePoints = decodePolyline(route.getJSONObject("overview_polyline").getString("points"))
        val maneuvers = buildList {
            for (index in 0 until steps.length()) {
                val step = steps.getJSONObject(index)
                val instruction = stripHtml(step.optString("html_instructions"))
                val normalized = normalizeManeuver(step.optString("maneuver"), instruction)
                if (normalized == ManeuverType.UNKNOWN) {
                    continue
                }
                val startLocation = step.getJSONObject("start_location")
                add(
                    RouteManeuver(
                        id = "maneuver_$index",
                        instruction = instruction.ifBlank { normalized.name.replace("_", " ").lowercase(Locale.US) },
                        distanceMeters = step.getJSONObject("distance").optInt("value"),
                        durationText = step.getJSONObject("duration").optString("text"),
                        maneuverType = normalized,
                        triggerPoint = LatLng(
                            startLocation.getDouble("lat"),
                            startLocation.getDouble("lng")
                        )
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
            totalDistanceText = leg.getJSONObject("distance").optString("text"),
            totalDurationText = leg.getJSONObject("duration").optString("text")
        )
    }

    private fun normalizeManeuver(maneuver: String?, instruction: String): ManeuverType {
        val raw = (maneuver ?: "").lowercase(Locale.US)
        val normalizedInstruction = instruction.lowercase(Locale.US)
        return when {
            "uturn" in raw || "u-turn" in raw || "u turn" in normalizedInstruction -> ManeuverType.UTURN
            "turn-slight-left" in raw || "keep-left" in raw || "slight left" in normalizedInstruction -> ManeuverType.SLIGHT_LEFT
            "turn-slight-right" in raw || "keep-right" in raw || "slight right" in normalizedInstruction -> ManeuverType.SLIGHT_RIGHT
            "turn-left" in raw || normalizedInstruction.contains("left") -> ManeuverType.LEFT
            "turn-right" in raw || normalizedInstruction.contains("right") -> ManeuverType.RIGHT
            "straight" in raw || normalizedInstruction.contains("straight") || normalizedInstruction.contains("continue") -> ManeuverType.STRAIGHT
            normalizedInstruction.contains("destination") -> ManeuverType.ARRIVE
            else -> ManeuverType.UNKNOWN
        }
    }

    private fun stripHtml(rawInstruction: String): String {
        return rawInstruction
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun parseApiError(response: String): String {
        return runCatching {
            val root = JSONObject(response)
            val status = root.optString("status", "HTTP error")
            val message = root.optString("error_message")
            "Directions API error: $status${if (message.isNotBlank()) " - $message" else ""}"
        }.getOrElse {
            "Directions API request failed."
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
