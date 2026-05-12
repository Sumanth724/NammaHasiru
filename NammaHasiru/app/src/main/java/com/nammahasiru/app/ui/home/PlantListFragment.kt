package com.nammahasiru.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.nammahasiru.app.R
import com.nammahasiru.app.data.Plant
import com.nammahasiru.app.viewmodel.PlantViewModel

class PlantListFragment : Fragment() {

    private val viewModel: PlantViewModel by activityViewModels()
    private lateinit var adapter: RecentPlantsAdapter

    private lateinit var rvPlants: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvListTitle: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plant_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvPlants   = view.findViewById(R.id.rv_plants)
        emptyState = view.findViewById(R.id.tv_empty_state)
        tvListTitle = view.findViewById(R.id.tv_list_title)

        // Add first plant button (shown in empty state)
        view.findViewById<MaterialButton>(R.id.btn_add_first_plant).setOnClickListener {
            findNavController().navigate(R.id.addPlantFragment)
        }

        val filterType = arguments?.getString("filterType") ?: "ALL"
        tvListTitle.text = when (filterType) {
            "ALIVE" -> "Alive plants"
            "DEAD"  -> "Lost plants"
            else    -> "Total plants"
        }

        setupRecyclerView()

        val btnAddPlantList = view.findViewById<View>(R.id.btn_add_plant_list)
        btnAddPlantList.setOnClickListener {
            findNavController().navigate(R.id.addPlantFragment)
        }

        val source = when (filterType) {
            "ALIVE" -> viewModel.alivePlants
            "DEAD"  -> viewModel.deadPlants
            else    -> viewModel.allPlants
        }

        source.observe(viewLifecycleOwner) { plants ->
            adapter.submitList(plants)
            val isEmpty = plants.isNullOrEmpty()
            emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            rvPlants.visibility   = if (isEmpty) View.GONE   else View.VISIBLE
            btnAddPlantList.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        adapter = RecentPlantsAdapter(
            onItemClick = { plant ->
                val args = Bundle().apply { putInt("plantId", plant.id.toInt()) }
                findNavController().navigate(R.id.plantDetailFragment, args)
            },
            onDeleteClick = { plant ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Plant")
                    .setMessage("Remove \"${plant.speciesName}\" from your list?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deletePlant(plant) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onEditClick = { plant -> showEditDialog(plant) }
        )
        rvPlants.layoutManager = LinearLayoutManager(requireContext())
        rvPlants.adapter = adapter
    }

    private fun showEditDialog(plant: Plant) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_plant, null)
        val etName  = dialogView.findViewById<EditText>(R.id.et_edit_name)
        val etNotes = dialogView.findViewById<EditText>(R.id.et_edit_notes)

        etName.setText(plant.speciesName)
        etNotes.setText(plant.notes)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Plant")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName  = etName.text.toString().trim()
                val newNotes = etNotes.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.updatePlantWithConfidence(plant.copy(speciesName = newName, notes = newNotes))
                    Toast.makeText(requireContext(), "Plant updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
