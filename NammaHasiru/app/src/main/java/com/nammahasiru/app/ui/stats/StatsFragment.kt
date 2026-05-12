package com.nammahasiru.app.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nammahasiru.app.R
import com.nammahasiru.app.data.PlantStatus
import com.nammahasiru.app.databinding.FragmentStatsBinding
import com.nammahasiru.app.viewmodel.PlantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlantViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Total count
        viewModel.totalCount.observe(viewLifecycleOwner) { binding.tvStatTotal.text = it.toString() }

        // Alive count + bar
        viewModel.aliveCount.observe(viewLifecycleOwner) { alive ->
            binding.tvStatAlive.text = alive.toString()
            val total = viewModel.totalCount.value ?: 0
            val pct = if (total > 0) (alive * 100) / total else 0
            binding.progressAlive.progress = pct
            binding.tvStatAlivePct.text = "$pct%"
            updateSurvivalScore()
        }

        // Dead count + bar
        viewModel.deadCount.observe(viewLifecycleOwner) { dead ->
            binding.tvStatDead.text = dead.toString()
            val total = viewModel.totalCount.value ?: 0
            val pct = if (total > 0) (dead * 100) / total else 0
            binding.progressDead.progress = pct
            binding.tvStatDeadPct.text = "$pct%"
            updateSurvivalScore()
        }

        // Pending count + bar
        viewModel.unknownCount.observe(viewLifecycleOwner) { unknown ->
            val total = viewModel.totalCount.value ?: 0
            val pct = if (total > 0) (unknown * 100) / total else 0
            binding.progressUnknown.progress = pct
            binding.tvStatUnknown.text = "$pct%"
        }

        // All plants: monthly breakdown + species guide
        viewModel.allPlants.observe(viewLifecycleOwner) { plants ->

            // ── Monthly bars ────────────────────────────────────────────────
            binding.llMonthlyBars.removeAllViews()
            if (plants.isNotEmpty()) {
                val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())
                val monthMap = mutableMapOf<String, Int>()
                val monthOrder = mutableListOf<String>()

                plants.sortedBy { it.plantedDate }.forEach { plant ->
                    val month = monthFmt.format(Date(plant.plantedDate))
                    if (!monthMap.containsKey(month)) monthOrder.add(month)
                    monthMap[month] = (monthMap[month] ?: 0) + 1
                }

                val maxCount = monthMap.values.maxOrNull() ?: 1

                monthOrder.takeLast(6).forEach { month ->
                    val count = monthMap[month] ?: 0
                    val pct = (count * 100) / maxCount

                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        lp.bottomMargin = 10
                        layoutParams = lp
                    }

                    val label = TextView(requireContext()).apply {
                        text = month
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        layoutParams = LinearLayout.LayoutParams(56.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
                    }

                    val bar = ProgressBar(requireContext(),
                        null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = pct
                        progressTintList = ContextCompat.getColorStateList(requireContext(), R.color.green_600)
                        progressBackgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green_100)
                        layoutParams = LinearLayout.LayoutParams(0, 10.dp, 1f)
                    }

                    val value = TextView(requireContext()).apply {
                        text = count.toString()
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.green_700))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        val lp = LinearLayout.LayoutParams(32.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.marginStart = 8
                        layoutParams = lp
                    }

                    row.addView(label)
                    row.addView(bar)
                    row.addView(value)
                    binding.llMonthlyBars.addView(row)
                }
            } else {
                val empty = TextView(requireContext()).apply {
                    text = "No data yet — start planting! 🌱"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                }
                binding.llMonthlyBars.addView(empty)
            }

            // ── Species guide ────────────────────────────────────────────────
            if (plants.isEmpty()) {
                binding.tvSpeciesBreakdown.text = "No data yet. Start planting! 🌱"
                return@observe
            }
            val sb = StringBuilder()
            plants.groupBy { it.speciesName }
                .entries.sortedByDescending { it.value.size }.take(10)
                .forEach { (species, list) ->
                    val aliveCount = list.count { it.status == PlantStatus.ALIVE }
                    val rate = if (list.isNotEmpty()) (aliveCount * 100) / list.size else 0
                    sb.appendLine("🌳 $species — ${list.size} planted | $rate% survival")
                }
            binding.tvSpeciesBreakdown.text = sb.toString()
        }
    }

    private fun updateSurvivalScore() {
        val b = _binding ?: return
        val alive   = viewModel.aliveCount.value ?: 0
        val dead    = viewModel.deadCount.value ?: 0
        val checked = alive + dead
        val rate    = if (checked > 0) (alive * 100) / checked else 0
        b.tvSurvivalScore.text = "$rate%"
        b.progressSurvivalScore.progress = rate
        b.tvSurvivalMessage.text = when {
            rate >= 80  -> "🏆 Excellent! Your village is a green champion!"
            rate >= 60  -> "🌿 Great work! Keep nurturing your saplings."
            rate >= 40  -> "🌱 Good start! More care needed."
            checked == 0 -> "📊 Check your saplings to see survival data."
            else        -> "⚠️ Low survival rate. Try different species."
        }
    }

    /** dp → px extension */
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
