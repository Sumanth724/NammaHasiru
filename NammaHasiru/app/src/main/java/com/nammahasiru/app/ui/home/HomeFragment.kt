package com.nammahasiru.app.ui.home

import android.content.DialogInterface
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nammahasiru.app.R
import com.nammahasiru.app.ai.PlantHealthClassifier
import com.nammahasiru.app.data.Plant
import com.nammahasiru.app.data.PlantStatus
import com.nammahasiru.app.databinding.FragmentHomeBinding
import com.nammahasiru.app.viewmodel.PlantViewModel
import kotlinx.coroutines.launch
import java.io.File

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlantViewModel by activityViewModels()
    private lateinit var adapter: RecentPlantsAdapter
    private lateinit var plantClassifier: PlantHealthClassifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        plantClassifier = PlantHealthClassifier(requireContext().applicationContext)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeData()

        val navController = findNavController()

        binding.fabAddPlant.setOnClickListener {
            navController.navigate(R.id.action_home_to_addPlant)
        }

        binding.cardTotalPlants.setOnClickListener {
            val args = Bundle().apply { putString("filterType", "ALL") }
            navController.navigate(R.id.action_home_to_plantList, args)
        }
        binding.cardAlivePlants.setOnClickListener {
            val args = Bundle().apply { putString("filterType", "ALIVE") }
            navController.navigate(R.id.action_home_to_plantList, args)
        }
        binding.cardDeadPlants.setOnClickListener {
            val args = Bundle().apply { putString("filterType", "DEAD") }
            navController.navigate(R.id.action_home_to_plantList, args)
        }

        // View all recent plants
        binding.tvViewAll.setOnClickListener {
            val args = Bundle().apply { putString("filterType", "ALL") }
            navController.navigate(R.id.action_home_to_plantList, args)
        }

        // Recent plants RecyclerView
        adapter = RecentPlantsAdapter(
            onItemClick = { plant ->
                val args = Bundle().apply { putInt("plantId", plant.id.toInt()) }
                navController.navigate(R.id.plantDetailFragment, args)
            },
            onDeleteClick = { plant -> viewModel.deletePlant(plant) }
        )
        binding.rvRecentPlants.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentPlants.adapter = adapter
    }

    private fun showStatusDialog(plant: Plant) {
        val options = arrayOf("🌿 Mark as Alive", "💀 Mark as Dead", "🤖 Re-analyze with AI")
        AlertDialog.Builder(requireContext())
            .setTitle("Update: ${plant.speciesName}")
            .setItems(options) { _: DialogInterface, which: Int ->
                when (which) {
                    0 -> { viewModel.updatePlantStatus(plant, PlantStatus.ALIVE); showToast("Marked as Alive 🌿") }
                    1 -> { viewModel.updatePlantStatus(plant, PlantStatus.DEAD);  showToast("Marked as Dead 💀") }
                    2 -> reAnalyzeWithAI(plant)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reAnalyzeWithAI(plant: Plant) {
        val path = plant.photoPath
        if (path.isNullOrEmpty() || !File(path).exists()) {
            showToast("⚠️ No photo found. Delete & re-add with a photo.")
            return
        }
        showToast("🤖 Analyzing…")
        lifecycleScope.launch {
            val opts   = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = BitmapFactory.decodeFile(path, opts)
            if (bitmap == null) { showToast("⚠️ Could not read photo."); return@launch }

            val result = plantClassifier.classify(bitmap)
            viewModel.updatePlantStatus(plant, result.status)
            val emoji = if (result.status == PlantStatus.ALIVE) "🌿" else if (result.status == PlantStatus.DEAD) "💀" else "❓"
            showToast("$emoji AI result: ${result.label}")
        }
    }

    private fun showToast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

    private fun observeData() {
        viewModel.totalCount.observe(viewLifecycleOwner) { _ -> updateSurvivalRate() }
        viewModel.aliveCount.observe(viewLifecycleOwner) { _ -> updateSurvivalRate() }
        viewModel.deadCount.observe(viewLifecycleOwner)  { _ -> updateSurvivalRate() }
        viewModel.recentPlants.observe(viewLifecycleOwner) { plants ->
            adapter.submitList(plants.take(5))
        }
    }

    private fun updateSurvivalRate() {
        val b     = _binding ?: return
        val alive = viewModel.aliveCount.value ?: 0
        val dead  = viewModel.deadCount.value  ?: 0
        val total = viewModel.totalCount.value ?: 0
        b.tvAlivePlants.text  = alive.toString()
        b.tvDeadPlants.text   = dead.toString()
        b.tvTotalPlants.text  = total.toString()
        val checked = alive + dead
        val rate    = if (checked > 0) (alive * 100) / checked else 0
        b.tvSurvivalRate.text      = "$rate%"
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    override fun onDestroy()     { super.onDestroy();     plantClassifier.close() }
}
