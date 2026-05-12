package com.nammahasiru.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nammahasiru.app.R
import com.nammahasiru.app.data.Plant
import com.nammahasiru.app.data.PlantStatus
import com.nammahasiru.app.databinding.ItemPlantBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentPlantsAdapter(
    private val onItemClick: (Plant) -> Unit,
    private val onDeleteClick: (Plant) -> Unit,
    private val onEditClick: ((Plant) -> Unit)? = null
) : ListAdapter<Plant, RecentPlantsAdapter.PlantViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlantViewHolder {
        val binding = ItemPlantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlantViewHolder, position: Int) = holder.bind(getItem(position))

    inner class PlantViewHolder(private val binding: ItemPlantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(plant: Plant) {
            binding.tvSpeciesName.text = plant.speciesName
            val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val dateStr = fmt.format(Date(plant.plantedDate))

            val locStr = if (plant.locationName.isNotEmpty()) plant.locationName
                         else "%.4f, %.4f".format(plant.latitude, plant.longitude)
            
            binding.tvLocation.text = "📍 $locStr • $dateStr"

            // Status Pill
            val (statusText, statusTextColor, statusBg) = when (plant.status) {
                PlantStatus.ALIVE   -> Triple("Alive", R.color.status_alive, R.drawable.bg_status_alive)
                PlantStatus.DEAD    -> Triple("Lost", R.color.status_dead, R.drawable.bg_status_dead)
                PlantStatus.UNKNOWN -> Triple("Pending", R.color.status_unknown, R.drawable.bg_status_unknown)
            }
            binding.tvStatus.text = statusText
            binding.tvStatus.setTextColor(binding.root.context.getColor(statusTextColor))
            binding.tvStatus.setBackgroundResource(statusBg)

            // Always show the green leaf icon — photo is visible on plant detail
            binding.ivPlantPhoto.setImageResource(R.drawable.ic_onboard_plant)
            binding.ivPlantPhoto.imageTintList = android.content.res.ColorStateList.valueOf(
                binding.root.context.getColor(R.color.green_800)
            )

            binding.root.setOnClickListener { onItemClick(plant) }
        }
    }

    fun attachSwipeToDelete(recyclerView: RecyclerView) {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onDeleteClick(getItem(pos))
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Plant>() {
            override fun areItemsTheSame(a: Plant, b: Plant) = a.id == b.id
            override fun areContentsTheSame(a: Plant, b: Plant) = a == b
        }
    }
}
