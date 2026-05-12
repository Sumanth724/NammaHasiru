package com.nammahasiru.app.ui.map

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.nammahasiru.app.R
import com.nammahasiru.app.data.Plant
import com.nammahasiru.app.data.PlantStatus
import com.nammahasiru.app.databinding.FragmentMapBinding
import com.nammahasiru.app.viewmodel.PlantViewModel

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlantViewModel by activityViewModels()
    private var googleMap: GoogleMap? = null
    private var pendingPlants: List<Plant>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get or create SupportMapFragment using commitNow() (synchronous)
        var mapFrag = childFragmentManager
            .findFragmentById(R.id.map_fragment) as? SupportMapFragment
        if (mapFrag == null) {
            mapFrag = SupportMapFragment.newInstance()
            childFragmentManager.beginTransaction()
                .replace(R.id.map_fragment, mapFrag)
                .commitNow()           // synchronous — fragment attached before getMapAsync()
        }
        mapFrag.getMapAsync(this)

        // Observe plant list
        viewModel.allPlants.observe(viewLifecycleOwner) { plants ->
            val validPlants = plants.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            binding.tvMapPlantCount.text = "${validPlants.size} Plants Geo-Tagged"
            if (googleMap != null) {
                addMarkersToMap(validPlants)
            } else {
                pendingPlants = validPlants
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        Log.d("MapFragment", "onMapReady ✅")
        googleMap = map

        map.uiSettings.isZoomControlsEnabled  = true
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isMapToolbarEnabled    = true

        // Wait until map tiles and layout are fully ready before placing markers
        map.setOnMapLoadedCallback {
            Log.d("MapFragment", "Map fully loaded — drawing markers")
            pendingPlants?.let { addMarkersToMap(it) }
            pendingPlants = null
        }
    }

    private fun addMarkersToMap(plants: List<Plant>) {
        val map = googleMap ?: return
        map.clear()

        if (plants.isEmpty()) {
            // Show a default view of India when no plants are geo-tagged
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 4f))
            Log.d("MapFragment", "No valid plant locations to show")
            return
        }

        Log.d("MapFragment", "Adding ${plants.size} markers")

        val boundsBuilder = LatLngBounds.Builder()

        plants.forEach { plant ->
            val position = LatLng(plant.latitude, plant.longitude)

            val hue = when (plant.status) {
                PlantStatus.ALIVE   -> BitmapDescriptorFactory.HUE_GREEN
                PlantStatus.DEAD    -> BitmapDescriptorFactory.HUE_RED
                PlantStatus.UNKNOWN -> BitmapDescriptorFactory.HUE_YELLOW
            }
            val statusLabel = when (plant.status) {
                PlantStatus.ALIVE   -> "🌿 Alive"
                PlantStatus.DEAD    -> "💀 Dead"
                PlantStatus.UNKNOWN -> "❓ Pending"
            }

            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(plant.speciesName)
                    .snippet(statusLabel)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
            )
            boundsBuilder.include(position)

            Log.d("MapFragment", "Marker added: ${plant.speciesName} @ ${plant.latitude}, ${plant.longitude}")
        }

        // Move camera — use newLatLngZoom for single/identical points, bounds for multiple
        if (plants.size == 1) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(plants[0].latitude, plants[0].longitude), 16f
                )
            )
        } else {
            try {
                val bounds = boundsBuilder.build()
                // Use moveCamera first (instant) then animateCamera for smooth zoom
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200))
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
            } catch (e: Exception) {
                // Fallback: zoom to first plant if bounds fails
                Log.e("MapFragment", "Bounds error: ${e.message}")
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(plants[0].latitude, plants[0].longitude), 16f
                    )
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding  = null
    }
}
