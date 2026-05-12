package com.nammahasiru.app.ui.profile

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.nammahasiru.app.LoginActivity
import com.nammahasiru.app.ProfileActivity
import com.nammahasiru.app.R
import com.nammahasiru.app.data.PlantDatabase
import com.nammahasiru.app.viewmodel.PlantViewModel

class ProfileFragment : Fragment() {

    private val viewModel: PlantViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<ImageView>(R.id.btn_edit_profile).setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        view.findViewById<Button>(R.id.btn_sign_out).setOnClickListener {
            val authPrefs = requireContext().getSharedPreferences("namma_hasiru_auth", MODE_PRIVATE)
            authPrefs.edit().remove("logged_in_user").apply()
            PlantDatabase.clearInstance()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        loadProfileData(view)
        observeStats(view)
        
        return view
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadProfileData(it) }
    }

    private fun loadProfileData(view: View) {
        val authPrefs = requireContext().getSharedPreferences("namma_hasiru_auth", MODE_PRIVATE)
        val username = authPrefs.getString("logged_in_user", "default") ?: "default"
        
        val profilePrefs = requireContext().getSharedPreferences("profile_$username", MODE_PRIVATE)
        val defaultName = authPrefs.getString("user_${username}_name", username) ?: username
        val name = profilePrefs.getString("name", defaultName) ?: defaultName
        val city = profilePrefs.getString("city", "City not set") ?: "City not set"

        view.findViewById<TextView>(R.id.tv_profile_name).text = name
        view.findViewById<TextView>(R.id.tv_profile_location).text = city
        
        val initials = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
        view.findViewById<TextView>(R.id.tv_avatar_initials).text = if (initials.isNotEmpty()) initials else "U"
    }

    private fun observeStats(view: View) {
        viewModel.totalCount.observe(viewLifecycleOwner) { total ->
            view.findViewById<TextView>(R.id.tv_stat_plants).text = total.toString()
            updateSurvivalRate(view)
        }
        viewModel.aliveCount.observe(viewLifecycleOwner) {
            updateSurvivalRate(view)
        }
    }

    private fun updateSurvivalRate(view: View) {
        val alive = viewModel.aliveCount.value ?: 0
        val total = viewModel.totalCount.value ?: 0
        val rate = if (total > 0) (alive * 100) / total else 0
        view.findViewById<TextView>(R.id.tv_stat_survival).text = "$rate%"
    }
}
