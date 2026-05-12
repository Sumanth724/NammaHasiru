package com.nammahasiru.app.ui.settings

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.nammahasiru.app.R

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        // Handle back button click
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        val prefs = requireContext().getSharedPreferences("namma_hasiru_settings", MODE_PRIVATE)

        // Find switches
        val switch90Day = view.findViewById<SwitchCompat>(R.id.switch_90_day)
        val switchVillageScore = view.findViewById<SwitchCompat>(R.id.switch_village_score)
        val switchSeasonAlerts = view.findViewById<SwitchCompat>(R.id.switch_season_alerts)
        val switchShareLocation = view.findViewById<SwitchCompat>(R.id.switch_share_location)
        val switchShowProfile = view.findViewById<SwitchCompat>(R.id.switch_show_profile)

        // Load states
        switch90Day.isChecked = prefs.getBoolean("settings_90_day", true)
        switchVillageScore.isChecked = prefs.getBoolean("settings_village_score", true)
        switchSeasonAlerts.isChecked = prefs.getBoolean("settings_season_alerts", false)
        switchShareLocation.isChecked = prefs.getBoolean("settings_share_location", true)
        switchShowProfile.isChecked = prefs.getBoolean("settings_show_profile", false)

        // Save states on change
        val listener = { key: String, isChecked: Boolean ->
            prefs.edit().putBoolean(key, isChecked).apply()
            val msg = if (isChecked) "Enabled" else "Disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        switch90Day.setOnCheckedChangeListener { _, isChecked -> listener("settings_90_day", isChecked) }
        switchVillageScore.setOnCheckedChangeListener { _, isChecked -> listener("settings_village_score", isChecked) }
        switchSeasonAlerts.setOnCheckedChangeListener { _, isChecked -> listener("settings_season_alerts", isChecked) }
        switchShareLocation.setOnCheckedChangeListener { _, isChecked -> listener("settings_share_location", isChecked) }
        switchShowProfile.setOnCheckedChangeListener { _, isChecked -> listener("settings_show_profile", isChecked) }

        // Delete account functionality
        view.findViewById<View>(R.id.btn_delete_account).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to permanently delete your account and all your plant data? This action cannot be undone.")
                .setPositiveButton("Delete", { _, _ ->
                    // Clear all data
                    val authPrefs = requireContext().getSharedPreferences("namma_hasiru_auth", MODE_PRIVATE)
                    authPrefs.edit().clear().apply()
                    prefs.edit().clear().apply()
                    
                    Toast.makeText(requireContext(), "Account deleted successfully", Toast.LENGTH_SHORT).show()
                    
                    // Redirect to login
                    val intent = android.content.Intent(requireContext(), com.nammahasiru.app.LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                })
                .setNegativeButton("Cancel", null)
                .show()
        }

        return view
    }
}
