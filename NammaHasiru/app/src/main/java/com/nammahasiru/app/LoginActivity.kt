package com.nammahasiru.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nammahasiru.app.data.PlantDatabase
import com.nammahasiru.app.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Floating logo animation ──────────────────────────────────────────
        FloatAnimator.startFloat(binding.ivLoginLogo, amplitude = 10f, duration = 2600L)
        FloatAnimator.startPulse(binding.ivLoginLogo, scale = 1.03f, duration = 2600L)

        // Pre-fill username if coming from RegisterActivity
        intent.getStringExtra("prefill_username")?.let {
            binding.etUsername.setText(it)
            binding.etPassword.requestFocus()
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val username = binding.etUsername.text.toString().trim().lowercase()
        val password = binding.etPassword.text.toString()

        binding.tilUsername.error = null
        binding.tilPassword.error = null

        if (username.isEmpty()) {
            binding.tilUsername.error = "Please enter your username"; return
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = "Please enter your password"; return
        }

        val prefs = getSharedPreferences("namma_hasiru_auth", MODE_PRIVATE)
        val storedPassword = prefs.getString("user_${username}", null)

        when {
            storedPassword == null -> {
                binding.tilUsername.error = "Account not found. Please register first."
            }
            storedPassword != password -> {
                binding.tilPassword.error = "Incorrect password. Please try again."
                binding.etPassword.text?.clear()
            }
            else -> {
                val previousUser = prefs.getString("logged_in_user", null)
                if (previousUser != null && previousUser != username) {
                    PlantDatabase.clearInstance()
                }
                prefs.edit().putString("logged_in_user", username).apply()

                val displayName = prefs.getString("user_${username}_name", username) ?: username
                Toast.makeText(this, "Welcome back, $displayName! 🌿", Toast.LENGTH_SHORT).show()

                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }
    }
}
