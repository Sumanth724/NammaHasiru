package com.nammahasiru.app.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.nammahasiru.app.R
import com.nammahasiru.app.data.Plant
import com.nammahasiru.app.data.PlantDatabase
import com.nammahasiru.app.data.PlantStatus
import com.nammahasiru.app.viewmodel.PlantViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PlantDetailFragment : Fragment() {

    private val viewModel: PlantViewModel by activityViewModels()

    private lateinit var plantClassifier: com.nammahasiru.app.ai.PlantHealthClassifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        plantClassifier = com.nammahasiru.app.ai.PlantHealthClassifier(requireContext().applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plant_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        // Get plant ID passed from the list
        val plantId = arguments?.getInt("plantId", -1) ?: -1
        if (plantId == -1) {
            Toast.makeText(requireContext(), "Plant not found", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        // Observe the specific plant instead of just a one-time fetch
        viewModel.allPlants.observe(viewLifecycleOwner) { plants ->
            val plant = plants.find { it.id.toInt() == plantId }
            if (plant != null) {
                val authPrefs = requireContext().getSharedPreferences("namma_hasiru_auth", Context.MODE_PRIVATE)
                val username = authPrefs.getString("logged_in_user", "default") ?: "default"
                val profilePrefs = requireContext().getSharedPreferences("profile_$username", Context.MODE_PRIVATE)
                val defaultName = authPrefs.getString("user_${username}_name", username) ?: username
                val displayName = profilePrefs.getString("name", defaultName) ?: defaultName
                
                bindPlant(view, plant, displayName)
            } else {
                // If plant is deleted, navigate up
                findNavController().navigateUp()
            }
        }
    }

    private fun bindPlant(view: View, plant: Plant, plantedBy: String) {
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Plant name
        view.findViewById<TextView>(R.id.tv_plant_name).text = plant.speciesName

        // Notes / species subtitle
        val notesView = view.findViewById<TextView>(R.id.tv_plant_notes)
        if (plant.notes.isNotEmpty()) {
            notesView.text = plant.notes
            notesView.visibility = View.VISIBLE
        } else {
            notesView.visibility = View.GONE
        }

        // Status badge
        val badge = view.findViewById<TextView>(R.id.tv_status_badge)
        when (plant.status) {
            PlantStatus.ALIVE -> {
                badge.text = "Alive"
                badge.setTextColor(0xFF004D40.toInt())
                badge.setBackgroundResource(R.drawable.bg_badge_active)
            }
            PlantStatus.DEAD -> {
                badge.text = "Lost"
                badge.setTextColor(0xFFD32F2F.toInt())
                badge.setBackgroundResource(R.drawable.bg_badge_inactive)
            }
            PlantStatus.UNKNOWN -> {
                badge.text = "Pending"
                badge.setTextColor(0xFF757575.toInt())
                badge.setBackgroundResource(R.drawable.bg_badge_inactive)
            }
        }

        // Planted date
        view.findViewById<TextView>(R.id.tv_planted_date).text = fmt.format(Date(plant.plantedDate))

        // Location
        val locText = if (plant.locationName.isNotEmpty()) plant.locationName
                      else "%.4f, %.4f".format(plant.latitude, plant.longitude)
        view.findViewById<TextView>(R.id.tv_location).text = locText

        // Planted by (logged-in user's display name)
        view.findViewById<TextView>(R.id.tv_planted_by).text = plantedBy

        // 90-day check (plantedDate + 90 days)
        val ninetyDayMillis = plant.plantedDate + (90L * 24 * 60 * 60 * 1000)
        view.findViewById<TextView>(R.id.tv_90_day_check).text = fmt.format(Date(ninetyDayMillis))

        // Plant photo
        val photoView = view.findViewById<ImageView>(R.id.iv_plant_photo)
        val placeholder = view.findViewById<ImageView>(R.id.iv_plant_placeholder)
        val photoPath = plant.photoPath
        if (!photoPath.isNullOrEmpty() && File(photoPath).exists()) {
            photoView.visibility = View.VISIBLE
            placeholder.visibility = View.GONE
            Glide.with(this)
                .load(File(photoPath))
                .centerCrop()
                .into(photoView)
        } else {
            photoView.visibility = View.GONE
            placeholder.visibility = View.VISIBLE
        }

        // Survival bars — show 1 filled if ALIVE, none if DEAD/UNKNOWN (simple indicator)
        val bars = listOf(
            view.findViewById<View>(R.id.bar1),
            view.findViewById<View>(R.id.bar2),
            view.findViewById<View>(R.id.bar3),
            view.findViewById<View>(R.id.bar4),
            view.findViewById<View>(R.id.bar5)
        )
        val filledCount = when (plant.status) {
            PlantStatus.ALIVE    -> (plant.healthConfidence / 20).coerceIn(1, 5)
            PlantStatus.DEAD     -> 0
            PlantStatus.UNKNOWN  -> 1
        }
        bars.forEachIndexed { i, barView ->
            barView.setBackgroundResource(
                if (i < filledCount) R.drawable.bg_progress_filled
                else R.drawable.bg_progress_empty
            )
        }
        val pct = if (plant.status == PlantStatus.ALIVE) "100% healthy" else if (plant.status == PlantStatus.DEAD) "0% - marked lost" else "Pending check"
        view.findViewById<TextView>(R.id.tv_survival_label).text = "$filledCount/5 completed • $pct"

        // Mark as lost button / Update Status
        view.findViewById<Button>(R.id.btn_mark_lost).setOnClickListener {
            showStatusDialog(plant)
        }

        // View on map button (basic)
        view.findViewById<Button>(R.id.btn_view_map).setOnClickListener {
            Toast.makeText(requireContext(), "Opening map for ${plant.speciesName}…", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.mapFragment)
        }

        // Edit button
        view.findViewById<ImageView>(R.id.btn_edit_plant).setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_plant, null)
            val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_edit_name)
            val etNotes = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_edit_notes)
            
            etName.setText(plant.speciesName)
            etNotes.setText(plant.notes)

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Edit Details")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val updatedName = etName.text.toString().trim()
                    val updatedNotes = etNotes.text.toString().trim()
                    if (updatedName.isNotEmpty()) {
                        viewModel.updatePlant(plant.copy(speciesName = updatedName, notes = updatedNotes))
                        Toast.makeText(requireContext(), "Plant updated", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Delete button
        view.findViewById<ImageView>(R.id.btn_delete_plant).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Plant")
                .setMessage("Are you sure you want to delete this plant?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deletePlant(plant)
                    Toast.makeText(requireContext(), "Plant deleted", Toast.LENGTH_SHORT).show()
                    // Fragment will pop back automatically because of observer!
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showStatusDialog(plant: Plant) {
        val options = arrayOf("🌿 Mark as Alive", "💀 Mark as Dead", "🤖 Re-analyze with AI")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Update Status")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmMarkAlive(plant)
                    1 -> { viewModel.updatePlantStatus(plant, PlantStatus.DEAD);  Toast.makeText(requireContext(), "Marked as Dead 💀", Toast.LENGTH_SHORT).show() }
                    2 -> reAnalyzeWithAI(plant)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * If the plant has no valid photo, show a confirmation dialog warning the user
     * that no image shows a plant before marking it as alive.
     */
    private fun confirmMarkAlive(plant: Plant) {
        val photoPath = plant.photoPath
        val hasPhoto = !photoPath.isNullOrEmpty() && File(photoPath).exists()
        if (!hasPhoto) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Confirm Planting")
                .setMessage(
                    "It looks like the image doesn't show a plant, but we've marked it as alive.\n\n" +
                    "Could you confirm if you really planted something here?"
                )
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, I planted it") { _, _ ->
                    viewModel.updatePlantStatus(plant, PlantStatus.ALIVE)
                    Toast.makeText(requireContext(), "Marked as Alive 🌿", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("No, cancel", null)
                .show()
        } else {
            viewModel.updatePlantStatus(plant, PlantStatus.ALIVE)
            Toast.makeText(requireContext(), "Marked as Alive 🌿", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reAnalyzeWithAI(plant: Plant) {
        val path = plant.photoPath
        if (path.isNullOrEmpty() || !File(path).exists()) {
            Toast.makeText(requireContext(), "⚠️ No photo found. Please add a photo first.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(requireContext(), "🤖 Analyzing image...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val opts   = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = android.graphics.BitmapFactory.decodeFile(path, opts)
            if (bitmap == null) {
                Toast.makeText(requireContext(), "⚠️ Could not read photo.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Two-stage: presence gate first, then health classification
            val result = com.nammahasiru.app.ai.PlantHealthAnalyzer.analyze(bitmap)

            if (result.notAPlant) {
                // Image does not contain a plant — do NOT update status
                Toast.makeText(
                    requireContext(),
                    "🚫 ${result.message}",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            // Valid plant image — update status
            viewModel.updatePlantStatus(plant, result.status)
            val emoji   = when (result.status) { PlantStatus.ALIVE -> "🌿"; PlantStatus.DEAD -> "💀"; else -> "❓" }
            val confStr = if (result.confidence > 0) " • ${result.confidence}% confidence" else ""
            Toast.makeText(requireContext(), "$emoji AI: ${result.message}$confStr", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        plantClassifier.close()
    }
}
