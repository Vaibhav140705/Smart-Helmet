package com.example.helmetcompanion

import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.helmetcompanion.databinding.FragmentNavigationBinding
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.util.Locale
import kotlin.concurrent.thread

class NavigationFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!
    private lateinit var map: GoogleMap
    private val locationRepository by lazy { AppContainer.from(requireContext()).locationRepository }
    private var locationCallback: LocationCallback? = null
    private lateinit var maneuverAdapter: ManeuverAdapter

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        maneuverAdapter = ManeuverAdapter()
        binding.listManeuvers.layoutManager = LinearLayoutManager(requireContext())
        binding.listManeuvers.adapter = maneuverAdapter

        val mapFragment =
            childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnSuggestRoute.setOnClickListener { requestRoute() }
        binding.btnClearRoute.setOnClickListener { clearRouteUi() }
        binding.btnMyLocation.setOnClickListener { refreshCurrentLocation(centerMap = true) }
        binding.editDestination.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                requestRoute()
                true
            } else {
                false
            }
        }

        refreshCurrentLocation(centerMap = false)
        observeRoute()
    }

    override fun onResume() {
        super.onResume()
        startTracking()
        refreshCurrentLocation(centerMap = false)
    }

    override fun onPause() {
        super.onPause()
        locationRepository.stopRouteTracking(locationCallback)
        locationCallback = null
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        if (locationRepository.hasLocationPermission()) {
            map.isMyLocationEnabled = true
        }
        map.setPadding(0, 320, 0, 250)

        val defaultLocation = LatLng(28.6139, 77.2090)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))
    }

    private fun observeRoute() {
        viewModel.routeMessage.observe(viewLifecycleOwner) { message ->
            binding.textRouteMessage.text = message
        }
        viewModel.routeError.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank() && isAdded) {
                binding.btnSuggestRoute.isEnabled = true
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
        viewModel.routeSession.observe(viewLifecycleOwner) { session ->
            if (!::map.isInitialized) return@observe
            map.clear()
            if (locationRepository.hasLocationPermission()) {
                map.isMyLocationEnabled = true
            }

            if (session == null) {
                binding.textRouteSummary.text = "No active route"
                binding.textNextInstruction.text = "Enter a destination to generate turn output."
                binding.textBluetoothCue.text = "ESP32 output: standby"
                binding.textNextArrow.text = "S"
                maneuverAdapter.submitList(emptyList())
                binding.btnSuggestRoute.isEnabled = true
                return@observe
            }

            binding.btnSuggestRoute.isEnabled = true
            binding.textRouteSummary.text =
                "${session.destinationName} • ${session.totalDistanceText} • ${session.totalDurationText}"
            binding.textNextInstruction.text =
                session.maneuvers.firstOrNull()?.instruction ?: "Route ready"
            binding.textNextArrow.text = session.maneuvers.firstOrNull()?.helmetCommand ?: "S"
            binding.textBluetoothCue.text =
                "ESP32 output: ${session.maneuvers.firstOrNull()?.helmetCommand ?: "S"}"
            maneuverAdapter.submitList(session.maneuvers)

            map.addPolyline(
                PolylineOptions()
                    .addAll(session.routePoints)
                    .color(requireContext().getColor(R.color.hc_map_route))
                    .width(12f)
            )
            map.addMarker(
                MarkerOptions()
                    .position(session.destinationLatLng)
                    .title(session.destinationName)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            refreshCurrentLocation(centerMap = false)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(session.destinationLatLng, 14f))
        }
    }

    private fun refreshCurrentLocation(centerMap: Boolean) {
        locationRepository.requestSingleFreshLocation { location ->
            if (!isAdded || location == null) return@requestSingleFreshLocation
            val current = LatLng(location.latitude, location.longitude)
            binding.editSource.setText("${location.latitude}, ${location.longitude}")
            if (::map.isInitialized) {
                map.addMarker(
                    MarkerOptions()
                        .position(current)
                        .title("Current location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
                if (centerMap) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 15f))
                }
            }
        }
    }

    private fun requestRoute() {
        val query = binding.editDestination.text?.toString().orEmpty().trim()
        if (query.isBlank()) {
            binding.editDestination.error = "Enter a destination"
            return
        }
        binding.editDestination.error = null
        binding.btnSuggestRoute.isEnabled = false
        binding.textRouteMessage.text = "Looking up destination and preparing the route..."

        parseLatLng(query)?.let { latLng ->
            viewModel.fetchRoute(query, latLng)
            return
        }

        thread {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocationName(query, 1)
                val destination = addresses?.firstOrNull()
                requireActivity().runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    if (destination == null) {
                        binding.btnSuggestRoute.isEnabled = true
                        Toast.makeText(
                            requireContext(),
                            "Destination not found. Try a clearer address or lat,lng.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        viewModel.fetchRoute(
                            query,
                            LatLng(destination.latitude, destination.longitude)
                        )
                    }
                }
            } catch (error: Exception) {
                requireActivity().runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    binding.btnSuggestRoute.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        error.message ?: "Unable to search destination right now.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun clearRouteUi() {
        viewModel.clearRoute()
        if (isAdded) {
            binding.editDestination.text?.clear()
            refreshCurrentLocation(centerMap = true)
        }
    }

    private fun startTracking() {
        if (locationCallback != null) return
        locationCallback = locationRepository.startRouteTracking { location ->
            val maneuver = viewModel.onRiderLocationUpdate(location)
            if (maneuver != null && isAdded) {
                binding.textNextInstruction.text = maneuver.instruction
                binding.textNextArrow.text = maneuver.helmetCommand
                binding.textBluetoothCue.text = "ESP32 output: ${maneuver.helmetCommand}"
            }
        }
    }

    private fun parseLatLng(query: String): LatLng? {
        val parts = query.split(",").map { it.trim() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        return LatLng(lat, lng)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
