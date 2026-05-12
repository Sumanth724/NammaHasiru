package com.nammahasiru.app.ui.addplant

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.nammahasiru.app.R
import com.nammahasiru.app.ai.PlantHealthClassifier
import com.nammahasiru.app.data.Plant
import com.nammahasiru.app.data.PlantStatus
import com.nammahasiru.app.databinding.FragmentAddPlantBinding
import com.nammahasiru.app.viewmodel.PlantViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddPlantFragment : Fragment() {

    private var _binding: FragmentAddPlantBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlantViewModel by activityViewModels()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentPhotoPath: String? = null
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var photoUri: Uri? = null
    private var locationCallback: LocationCallback? = null

    private lateinit var plantClassifier: PlantHealthClassifier
    private var aiStatus: PlantStatus = PlantStatus.UNKNOWN
    /** True when AI detected the uploaded image is not a plant (wall, selfie, etc.) */
    private var imageIsNotPlant: Boolean = false

    // ── Camera launcher ───────────────────────────────────────────────────────
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                currentPhotoPath?.let { path ->
                    showPhotoPreview(path)
                    imageIsNotPlant = false
                    analyzePhotoWithAI(path)
                }
            }
        }

    // ── Gallery launcher ──────────────────────────────────────────────────────
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                // Copy gallery image into app-private storage so we have a stable path
                val destFile = createImageFile()
                try {
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                    currentPhotoPath = destFile.absolutePath
                    showPhotoPreview(destFile.absolutePath)
                    imageIsNotPlant = false
                    analyzePhotoWithAI(destFile.absolutePath)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Could not load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // ── Permission launchers ──────────────────────────────────────────────────
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true) fetchLocation()
            else Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_LONG).show()
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        plantClassifier = PlantHealthClassifier(requireContext().applicationContext)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddPlantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Back button in branded header
        view.findViewById<ImageView>(R.id.btn_back_add).setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnCapture.setOnClickListener    { checkCameraAndCapture() }
        binding.btnGallery.setOnClickListener    { galleryLauncher.launch("image/*") }
        binding.btnGetLocation.setOnClickListener { checkLocationAndFetch() }
        binding.btnSavePlant.setOnClickListener  { savePlant() }

        // Tap the photo preview to retake
        binding.ivPlantPhoto.setOnClickListener {
            showPickerOptions()
        }
    }

    // ── Photo preview helper ──────────────────────────────────────────────────

    private fun showPhotoPreview(path: String) {
        val b = _binding ?: return
        Glide.with(this)
            .load(File(path))
            .centerCrop()
            .into(b.ivPlantPhoto)
        b.ivPlantPhoto.visibility   = View.VISIBLE   // show the photo
        b.tvPhotoHint.visibility    = View.GONE       // hide placeholder
        b.tvRetakeBadge.visibility  = View.VISIBLE    // show "Change" badge
    }

    private fun showPickerOptions() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Change photo")
            .setItems(arrayOf("📷 Take new photo", "🖼️ Choose from gallery")) { _, which ->
                if (which == 0) checkCameraAndCapture() else galleryLauncher.launch("image/*")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── AI Analysis ───────────────────────────────────────────────────────────

    private fun analyzePhotoWithAI(imagePath: String) {
        binding.cardAiResult.visibility    = View.VISIBLE
        binding.pbAiLoading.visibility     = View.VISIBLE
        binding.tvAiStatusBadge.visibility = View.GONE
        binding.tvAiConfidence.visibility  = View.GONE
        binding.tvAiMessage.text           = "🤖 Analyzing image..."

        lifecycleScope.launch {
            val opts   = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = BitmapFactory.decodeFile(imagePath, opts)
            if (bitmap == null) {
                showAiResult(PlantStatus.UNKNOWN, "⚠️ Could not read photo. Please try again.", 0, notAPlant = false)
                return@launch
            }
            // Two-stage: presence gate first, then health classification
            val result  = com.nammahasiru.app.ai.PlantHealthAnalyzer.analyze(bitmap)
            aiStatus        = result.status
            imageIsNotPlant = result.notAPlant
            showAiResult(result.status, result.message, result.confidence, result.notAPlant)
        }
    }

    private fun showAiResult(status: PlantStatus, message: String, confidence: Int, notAPlant: Boolean) {
        val b = _binding ?: return
        b.pbAiLoading.visibility     = View.GONE
        b.tvAiStatusBadge.visibility = View.VISIBLE
        b.tvAiConfidence.visibility  = View.GONE

        if (notAPlant) {
            b.tvAiStatusBadge.text = "❌  NOT A PLANT"
            b.tvAiStatusBadge.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_dead))
            b.tvAiMessage.text = message
            return
        }

        val (badgeText, badgeColorRes) = when (status) {
            PlantStatus.ALIVE   -> {
                val confSuffix = if (confidence > 0) "  •  $confidence%" else ""
                Pair("🌿  ALIVE$confSuffix", R.color.status_alive)
            }
            PlantStatus.DEAD    -> {
                val confSuffix = if (confidence > 0) "  •  $confidence%" else ""
                Pair("💀  DEAD$confSuffix",  R.color.status_dead)
            }
            PlantStatus.UNKNOWN -> Pair("❓  UNKNOWN", R.color.status_unknown)
        }

        b.tvAiStatusBadge.text = badgeText
        b.tvAiStatusBadge.setBackgroundColor(ContextCompat.getColor(requireContext(), badgeColorRes))
        b.tvAiMessage.text = message
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private fun checkCameraAndCapture() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val photoFile = createImageFile()
        photoUri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", photoFile
        )
        takePictureLauncher.launch(photoUri)
    }

    private fun createImageFile(): File {
        val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("PLANT_${ts}_", ".jpg", dir).also { currentPhotoPath = it.absolutePath }
    }

    // ── Location ──────────────────────────────────────────────────────────────

    private fun checkLocationAndFetch() {
        val fine   = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) fetchLocation()
        else locationPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @Suppress("MissingPermission")
    private fun fetchLocation() {
        binding.btnGetLocation.isEnabled = false
        binding.tvLocationStatus.text    = "📡 Fetching GPS location..."
        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc -> if (loc != null) applyLocation(loc.latitude, loc.longitude) else fetchLastKnown() }
            .addOnFailureListener { fetchLastKnown() }
    }

    @Suppress("MissingPermission")
    private fun fetchLastKnown() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { loc -> if (loc != null) applyLocation(loc.latitude, loc.longitude) else requestLiveUpdate() }
            .addOnFailureListener { requestLiveUpdate() }
    }

    @Suppress("MissingPermission")
    private fun requestLiveUpdate() {
        binding.tvLocationStatus.text = "📡 Acquiring GPS fix..."
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMaxUpdates(1).setWaitForAccurateLocation(false).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { applyLocation(it.latitude, it.longitude) } ?: onLocationFailed()
                fusedLocationClient.removeLocationUpdates(this)
                locationCallback = null
            }
        }
        fusedLocationClient.requestLocationUpdates(req, locationCallback!!, requireActivity().mainLooper)
    }

    private fun applyLocation(lat: Double, lng: Double) {
        currentLatitude  = lat; currentLongitude = lng
        binding.btnGetLocation.isEnabled = true
        binding.tvLocationStatus.text    = "📍 %.5f, %.5f".format(lat, lng)
        binding.tvLocationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_700))
    }

    private fun onLocationFailed() {
        binding.btnGetLocation.isEnabled = true
        binding.tvLocationStatus.text    = "⚠️ Could not get location. Try again outdoors."
        binding.tvLocationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_dead))
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun savePlant() {
        val speciesName = binding.etSpeciesName.text.toString().trim()
        if (speciesName.isEmpty()) { binding.tilSpeciesName.error = "Please enter species name"; return }
        if (currentLatitude == 0.0 && currentLongitude == 0.0) {
            Toast.makeText(requireContext(), "Please capture GPS location first", Toast.LENGTH_SHORT).show()
            return
        }
        // Block saving if the uploaded photo was rejected by the plant detector
        if (imageIsNotPlant) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("❌ Invalid Plant Image")
                .setMessage(
                    "It looks like this image does not contain a plant.\n\n" +
                    "Please retake the photo with a clear view of the plant you planted."
                )
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Retake Photo") { _, _ -> checkCameraAndCapture() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val plant = Plant(
            speciesName = speciesName,
            latitude    = currentLatitude,
            longitude   = currentLongitude,
            photoPath   = currentPhotoPath,
            notes       = binding.etNotes.text.toString().trim(),
            status      = aiStatus
        )
        viewModel.insertPlant(plant)

        val args = Bundle().apply {
            putString("speciesName", speciesName)
            putFloat("latitude", currentLatitude.toFloat())
            putFloat("longitude", currentLongitude.toFloat())
        }
        findNavController().navigate(R.id.action_addPlant_to_success, args)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        plantClassifier.close()
    }
}
