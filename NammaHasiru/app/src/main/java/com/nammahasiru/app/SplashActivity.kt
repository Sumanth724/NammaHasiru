package com.nammahasiru.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.nammahasiru.app.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive mode
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── 1. Entrance: entire content fades + scales in ────────────────────
        binding.splashContent.alpha  = 0f
        binding.splashContent.scaleX = 0.82f
        binding.splashContent.scaleY = 0.82f
        binding.splashContent.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(1000)
            .setStartDelay(200)
            .withEndAction {
                // ── 2. After entrance → start gentle infinite float ──────────
                FloatAnimator.startFloat(
                    view      = binding.splashContent,
                    amplitude = 12f,
                    duration  = 2600L
                )
            }
            .start()

        // ── 3. App name slides up independently ──────────────────────────────
        binding.tvAppName.alpha        = 0f
        binding.tvAppName.translationY = 32f
        binding.tvAppName.animate()
            .alpha(1f).translationY(0f)
            .setDuration(700).setStartDelay(700).start()

        // ── 4. Flash → navigate at 3.2 s (gives float time to be felt) ───────
        binding.flashOverlay.alpha = 0f

        Handler(Looper.getMainLooper()).postDelayed({
            binding.flashOverlay.animate()
                .alpha(1f)
                .setDuration(350)
                .withEndAction {
                    val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    val onboardingDone = false // FORCED FOR TESTING
                    val dest = if (!onboardingDone) OnboardingActivity::class.java
                               else                 LoginActivity::class.java
                    startActivity(Intent(this, dest))
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
                .start()
        }, 3200)
    }
}
