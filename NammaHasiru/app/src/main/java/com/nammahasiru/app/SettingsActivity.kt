package com.nammahasiru.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.nammahasiru.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("namma_hasiru_settings", MODE_PRIVATE)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        binding.toolbar.setNavigationOnClickListener { finish() }

        // ── Restore saved states ─────────────────────────────────────────────
        val notifEnabled = prefs.getBoolean("notifications_enabled", true)
        val darkEnabled  = prefs.getBoolean("dark_mode_enabled",     false)

        binding.switchNotifications.isChecked = notifEnabled
        binding.switchDarkMode.isChecked      = darkEnabled

        // ── Notification toggle ──────────────────────────────────────────────
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            val msg = if (isChecked) "🔔 Reminders enabled" else "🔕 Reminders disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // ── Dark mode toggle — actually switches the app theme ───────────────
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode_enabled", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            // Recreate so Settings screen itself refreshes to new theme
            recreate()
        }
    }
}
