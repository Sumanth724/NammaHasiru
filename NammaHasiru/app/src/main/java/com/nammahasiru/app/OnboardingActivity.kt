package com.nammahasiru.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.nammahasiru.app.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var currentStep = 0

    private data class Step(
        val iconRes: Int,
        val title: String,
        val desc: String,
        val nextLabel: String,
        val showSkip: Boolean
    )

    private val steps = listOf(
        Step(
            iconRes   = R.drawable.ic_onboard_plant,
            title     = "Track every plant you grow",
            desc      = "Photo + GPS tag each sapling. Know exactly where every plant lives in your village.",
            nextLabel = "Next →",
            showSkip  = true
        ),
        Step(
            iconRes   = R.drawable.ic_onboard_map,
            title     = "See survival on a live map",
            desc      = "Green pins = alive. Red = lost. Watch your village turn greener every season.",
            nextLabel = "Next →",
            showSkip  = true
        ),
        Step(
            iconRes   = R.drawable.ic_onboard_star,
            title     = "Compete as a green village",
            desc      = "Your village earns a survival score. 90-day reminders keep your plantation thriving.",
            nextLabel = "Get started",
            showSkip  = false
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderStep()
        startFloatingAnimations()

        binding.btnNext.setOnClickListener {
            if (currentStep < steps.lastIndex) {
                currentStep++
                renderStep()
                bounceIcon()
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener { finishOnboarding() }
    }

    private fun startFloatingAnimations() {
        // Icon rings float gently up/down — staggered for a wave effect
        FloatAnimator.startFloat(binding.ringOuter,   amplitude = 10f, duration = 2800L, delay = 0L)
        FloatAnimator.startFloat(binding.ringMiddle,  amplitude = 12f, duration = 2600L, delay = 150L)
        FloatAnimator.startFloat(binding.iconCircle,  amplitude = 14f, duration = 2400L, delay = 300L)
        // Title + desc float slightly slower (subtle)
        FloatAnimator.startFloat(binding.onboardingTitle, amplitude = 6f,  duration = 3000L, delay = 100L)
        FloatAnimator.startFloat(binding.onboardingDesc,  amplitude = 5f,  duration = 3200L, delay = 200L)
    }

    /** Small bounce when switching steps */
    private fun bounceIcon() {
        binding.iconCircle.animate()
            .scaleX(0.88f).scaleY(0.88f).setDuration(120)
            .withEndAction {
                binding.iconCircle.animate()
                    .scaleX(1f).scaleY(1f).setDuration(200).start()
            }.start()
    }

    private fun renderStep() {
        val s = steps[currentStep]
        binding.onboardingIcon.setImageResource(s.iconRes)
        binding.onboardingTitle.text = s.title
        binding.onboardingDesc.text  = s.desc
        binding.btnNext.text         = s.nextLabel
        binding.btnSkip.visibility   = if (s.showSkip) View.VISIBLE else View.GONE

        updateDot(binding.dot1, currentStep == 0)
        updateDot(binding.dot2, currentStep == 1)
        updateDot(binding.dot3, currentStep == 2)
    }

    private fun updateDot(dot: View, active: Boolean) {
        val lp = dot.layoutParams as LinearLayout.LayoutParams
        lp.width = if (active) dp(24) else dp(8)
        dot.layoutParams = lp
        dot.setBackgroundResource(
            if (active) R.drawable.bg_onboard_dot_active
            else        R.drawable.bg_onboard_dot_inactive
        )
    }

    private fun finishOnboarding() {
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", true).apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
