package com.example.helmetcompanion

import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
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
import com.google.android.material.tabs.TabLayout
import java.util.Locale
import kotlin.concurrent.thread
import android.content.Context
import android.view.inputmethod.InputMethodManager

class NavigationFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!

    private lateinit var map: GoogleMap
    private val locationRepository by lazy { AppContainer.from(requireContext()).locationRepository }
    private var locationCallback: LocationCallback? = null
    private lateinit var maneuverAdapter: ManeuverAdapter
    private var isNavigating = false

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

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 1. Setup the Master Tab Layout to switch between the 3 Views
        binding.tabLayoutMain.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.containerMap.visibility = if (tab?.position == 0) View.VISIBLE else View.GONE
                binding.containerHeadArrows.visibility = if (tab?.position == 1) View.VISIBLE else View.GONE
                binding.containerSimulation.visibility = if (tab?.position == 2) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // 2. Setup the Simulation Buttons (Tab 3)
        binding.btnSimLeft.setOnClickListener { sendBluetoothCommand("L") }
        binding.btnSimRight.setOnClickListener { sendBluetoothCommand("R") }
        binding.btnSimStraight.setOnClickListener { sendBluetoothCommand("S") }
        binding.btnSimUturn.setOnClickListener { sendBluetoothCommand("U") }

        // Standard Routing Listeners
        binding.btnSuggestRoute.setOnClickListener { view ->
            // 1. Hide the keyboard
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            // 2. Trigger the route search
            requestRoute()
        }
        binding.btnStartRoute.setOnClickListener { enterNavigationMode() }
        binding.btnEndNavigation.setOnClickListener { exitNavigationMode() }
        binding.btnMyLocation.setOnClickListener { refreshCurrentLocation(centerMap = true) }

        // Keyboard "Search" Fix
        binding.editDestination.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                requestRoute()
                true
            } else false
        }

        refreshCurrentLocation(centerMap = false)
        observeRoute()
    }

    // Pass the manual command to your ViewModel/ESP32 here
    private fun sendBluetoothCommand(command: String) {
        Toast.makeText(requireContext(), "Command Sent: $command", Toast.LENGTH_SHORT).show()
        // Example: viewModel.sendHelmetCommand(command)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        if (locationRepository.hasLocationPermission()) {
            map.isMyLocationEnabled = true
        }
        map.setPadding(0, 200, 0, 250) // Adjusted padding for new top bar
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(28.6139, 77.2090), 10f))
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

    private fun enterNavigationMode() {
        isNavigating = true
        binding.textPlannerTitle.visibility = View.GONE
        binding.layoutPlannerControls.visibility = View.GONE
        binding.btnStartRoute.visibility = View.GONE
        binding.layoutActiveNavControls.visibility = View.VISIBLE
        binding.panelManeuver.visibility = View.VISIBLE
        toggleLocationFields(horizontal = true)
        map.setPadding(0, 180, 0, 300)
    }

    private fun exitNavigationMode() {
        isNavigating = false
        viewModel.clearRoute()
        binding.textPlannerTitle.visibility = View.VISIBLE
        binding.layoutPlannerControls.visibility = View.VISIBLE
        binding.layoutActiveNavControls.visibility = View.GONE
        binding.btnStartRoute.visibility = View.GONE
        binding.panelManeuver.visibility = View.GONE
        toggleLocationFields(horizontal = false)
        map.setPadding(0, 200, 0, 250)
        binding.editDestination.text?.clear()
        refreshCurrentLocation(centerMap = true)
    }

    private fun toggleLocationFields(horizontal: Boolean) {
        val density = resources.displayMetrics.density
        val margin14 = (14 * density).toInt()
        val margin16 = (16 * density).toInt()
        val spacing = (8 * density).toInt()

        if (horizontal) {
            binding.layoutLocations.orientation = LinearLayout.HORIZONTAL
            binding.layoutDestination.hint = "Dest"
            val sourceParams = binding.layoutSource.layoutParams as LinearLayout.LayoutParams
            sourceParams.width = 0
            sourceParams.weight = 1f
            sourceParams.marginEnd = spacing
            sourceParams.topMargin = 0
            binding.layoutSource.layoutParams = sourceParams
            val destParams = binding.layoutDestination.layoutParams as LinearLayout.LayoutParams
            destParams.width = 0
            destParams.weight = 1f
            destParams.marginStart = spacing
            destParams.topMargin = 0
            binding.layoutDestination.layoutParams = destParams
        } else {
            binding.layoutLocations.orientation = LinearLayout.VERTICAL
            binding.layoutDestination.hint = "Destination (address or lat,lng)"
            val sourceParams = binding.layoutSource.layoutParams as LinearLayout.LayoutParams
            sourceParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            sourceParams.weight = 0f
            sourceParams.marginEnd = 0
            sourceParams.topMargin = margin16
            binding.layoutSource.layoutParams = sourceParams
            val destParams = binding.layoutDestination.layoutParams as LinearLayout.LayoutParams
            destParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            destParams.weight = 0f
            destParams.marginStart = 0
            destParams.topMargin = margin14
            binding.layoutDestination.layoutParams = destParams
        }
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
                // Reset Map Tab
                binding.textRouteSummary.text = "No active route"
                binding.textNextInstruction.text = "Enter a destination to generate turn output."
                binding.textBluetoothCue.text = "ESP32 output: standby"
                binding.textNextArrow.text = "S"
                maneuverAdapter.submitList(emptyList())
                binding.btnSuggestRoute.isEnabled = true
                binding.btnStartRoute.visibility = View.GONE

                // Reset Head Arrows Tab
                binding.textGiantArrow.text = "S"
                binding.textGiantInstruction.text = "Waiting for route..."
                binding.textHeadArrowSummary.text = "No active route"
                binding.textHeadArrowStatus.text = "ESP32 output: standby"
                return@observe
            }

            binding.btnSuggestRoute.isEnabled = true
            if (!isNavigating) {
                binding.btnStartRoute.visibility = View.VISIBLE
            }

            val currentArrow = session.maneuvers.firstOrNull()?.helmetCommand ?: "S"
            val currentInst = session.maneuvers.firstOrNull()?.instruction ?: "Route ready"

            // Update Map Tab
            binding.textRouteSummary.text = "${session.destinationName} • ${session.totalDistanceText} • ${session.totalDurationText}"
            binding.textNextInstruction.text = currentInst
            binding.textNextArrow.text = currentArrow
            binding.textBluetoothCue.text = "ESP32 output: $currentArrow"
            maneuverAdapter.submitList(session.maneuvers)

            // Update Head Arrows Tab
            binding.textGiantArrow.text = currentArrow
            binding.textGiantInstruction.text = currentInst
            binding.textHeadArrowSummary.text = "Active Route"
            binding.textHeadArrowStatus.text = "ESP32 output: $currentArrow"

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

            if (isNavigating) {
                binding.textRouteMessage.text = "Route updated."
            }
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
                        Toast.makeText(requireContext(), "Destination not found.", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.fetchRoute(query, LatLng(destination.latitude, destination.longitude))
                    }
                }
            } catch (error: Exception) {
                requireActivity().runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    binding.btnSuggestRoute.isEnabled = true
                    Toast.makeText(requireContext(), error.message ?: "Unable to search destination right now.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearRouteUi() {
        exitNavigationMode()
    }

    private fun startTracking() {
        if (locationCallback != null) return
        locationCallback = locationRepository.startRouteTracking { location ->
            val maneuver = viewModel.onRiderLocationUpdate(location)
            if (maneuver != null && isAdded) {
                // Update Map View
                binding.textNextInstruction.text = maneuver.instruction
                binding.textNextArrow.text = maneuver.helmetCommand
                binding.textBluetoothCue.text = "ESP32 output: ${maneuver.helmetCommand}"

                // Update Head Arrows View
                binding.textGiantArrow.text = maneuver.helmetCommand
                binding.textGiantInstruction.text = maneuver.instruction
                binding.textHeadArrowStatus.text = "ESP32 output: ${maneuver.helmetCommand}"
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