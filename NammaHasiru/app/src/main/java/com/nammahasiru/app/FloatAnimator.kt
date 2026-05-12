package com.nammahasiru.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * FloatAnimator — adds a gentle, continuous up-down floating effect to any View.
 * Call startFloat(view) from any Activity/Fragment.
 */
object FloatAnimator {

    /**
     * Gentle infinite float: moves view up by [amplitude]px and back, forever.
     * @param view      The view to animate
     * @param amplitude How many pixels to float up/down (default 14px)
     * @param duration  One full cycle in ms (default 2400ms — feels airy)
     * @param delay     Start delay in ms (stagger multiple elements)
     */
    fun startFloat(
        view: View,
        amplitude: Float = 14f,
        duration: Long = 2400L,
        delay: Long = 0L
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "translationY", 0f, -amplitude, 0f).apply {
            this.duration        = duration
            this.startDelay      = delay
            repeatCount          = ValueAnimator.INFINITE
            repeatMode           = ValueAnimator.RESTART
            interpolator         = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Staggered float for multiple views — each one starts slightly after the previous.
     * Gives a "waves on water" feel.
     */
    fun startFloatSet(vararg views: View, amplitude: Float = 14f, duration: Long = 2400L) {
        views.forEachIndexed { i, view ->
            startFloat(view, amplitude, duration, delay = i * 200L)
        }
    }

    /**
     * Soft pulse: subtle scale-up/down breathing effect, great for icons.
     */
    fun startPulse(view: View, scale: Float = 1.04f, duration: Long = 2000L): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, scale, 1f).apply {
            this.duration = duration
            repeatCount   = ValueAnimator.INFINITE
            interpolator  = AccelerateDecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, scale, 1f).apply {
            this.duration = duration
            repeatCount   = ValueAnimator.INFINITE
            interpolator  = AccelerateDecelerateInterpolator()
        }
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }
}
