package com.nammahasiru.app

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nammahasiru.app.databinding.ActivityForgotPasswordBinding

/**
 * ForgotPasswordActivity — local password reset.
 *
 * Since the app uses on-device SharedPreferences auth (no Firebase),
 * password reset is done by verifying the username exists, then
 * overwriting the stored password with the new one chosen by the user.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvBackToLogin.setOnClickListener { finish() }

        binding.btnSendReset.setOnClickListener { attemptReset() }
    }

    private fun attemptReset() {
        // Clear previous errors
        binding.tilEmail.error           = null
        binding.tilNewPassword.error     = null
        binding.tilConfirmPassword.error = null

        val username   = binding.etEmail.text.toString().trim().lowercase()
        val newPass    = binding.etNewPassword.text.toString()
        val confirmPass = binding.etConfirmPassword.text.toString()

        // ── Validation ───────────────────────────────────────────────────────
        if (username.isEmpty()) {
            binding.tilEmail.error = "Please enter your username"
            return
        }
        if (newPass.length < 6) {
            binding.tilNewPassword.error = "Password must be at least 6 characters"
            return
        }
        if (newPass != confirmPass) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            return
        }

        // ── Look up username in local auth store ─────────────────────────────
        val prefs = getSharedPreferences("namma_hasiru_auth", MODE_PRIVATE)
        val storedPassword = prefs.getString("user_$username", null)

        if (storedPassword == null) {
            binding.tilEmail.error = "Username not found. Check spelling or register a new account."
            return
        }

        // ── Update password ──────────────────────────────────────────────────
        prefs.edit().putString("user_$username", newPass).apply()

        val name = prefs.getString("user_${username}_name", username) ?: username

        AlertDialog.Builder(this)
            .setTitle("✅ Password Reset!")
            .setMessage(
                "Hello $name! 👋\n\n" +
                "Your password has been successfully updated.\n\n" +
                "You can now sign in with your new password."
            )
            .setPositiveButton("Go to Sign In") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
