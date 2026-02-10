package com.example.helmetcompanion

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode

class NavigationFragment : Fragment(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var btnSearch: Button

    /* =======================
       Places Autocomplete Launcher
       ======================= */
    private val autocompleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode == Activity.RESULT_OK && result.data != null) {

                val place = Autocomplete.getPlaceFromIntent(result.data!!)
                val latLng = place.latLng ?: return@registerForActivityResult

                map.clear()
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        }

    /* =======================
       Fragment lifecycle
       ======================= */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_navigation, container, false)

        btnSearch = view.findViewById(R.id.btnSearch)

        // Init Places SDK once
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), "AIzaSyBDJc-L-wKVFyNTlIqmp02GgU0zMvMylK8")
        }

        btnSearch.setOnClickListener {
            openSearch()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment =
            childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /* =======================
       Google Map ready
       ======================= */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true

        // Default location (India)
        val defaultLocation = LatLng(28.6139, 77.2090)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 5f))
    }

    /* =======================
       Places Search
       ======================= */
    private fun openSearch() {

        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG
        )

        val intent = Autocomplete.IntentBuilder(
            AutocompleteActivityMode.FULLSCREEN,
            fields
        ).build(requireContext())

        autocompleteLauncher.launch(intent)
    }
}
