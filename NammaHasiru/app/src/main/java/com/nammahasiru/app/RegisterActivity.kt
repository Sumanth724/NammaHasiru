package com.nammahasiru.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nammahasiru.app.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvBackToLogin.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val name     = binding.etRegName.text.toString().trim()
        val village  = binding.etRegVillage.text.toString().trim()
        val username = binding.etRegUsername.text.toString().trim().lowercase()
        val mobile   = binding.etRegMobile.text.toString().trim()
        val password = binding.etRegPassword.text.toString()
        val confirm  = binding.etRegConfirm.text.toString()

        // ── Validation ──────────────────────────────────────────────────────
        binding.etRegName.error     = null
        binding.etRegVillage.error  = null
        binding.etRegUsername.error = null
        binding.etRegMobile.error   = null
        binding.etRegPassword.error = null
        binding.etRegConfirm.error  = null

        if (name.isEmpty()) {
            binding.etRegName.error = "Please enter your full name"; return
        }
        if (village.isEmpty()) {
            binding.etRegVillage.error = "Please enter your village/panchayat name"; return
        }
        if (username.length < 3) {
            binding.etRegUsername.error = "Username must be at least 3 characters"; return
        }
        if (mobile.isEmpty()) {
            binding.etRegMobile.error = "Please enter your mobile number"; return
        }
        if (password.length < 6) {
            binding.etRegPassword.error = "Password must be at least 6 characters"; return
        }
        if (password != confirm) {
            binding.etRegConfirm.error = "Passwords do not match"; return
        }

        val prefs = getSharedPreferences("namma_hasiru_auth", MODE_PRIVATE)

        // Check if username already exists
        if (prefs.contains("user_${username}")) {
            binding.etRegUsername.error = "Username already taken. Choose another."; return
        }

        // ── Save credentials ─────────────────────────────────────────────────
        prefs.edit()
            .putString("user_${username}", password)
            .putString("user_${username}_name", name)
            .putString("user_${username}_village", village)
            .putString("user_${username}_mobile", mobile)
            .apply()

        Toast.makeText(this,
            "✅ Account created! Welcome, $name 🌿",
            Toast.LENGTH_LONG).show()

        // Go straight to login with username pre-filled
        val intent = Intent(this, LoginActivity::class.java).apply {
            putExtra("prefill_username", username)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }
}
