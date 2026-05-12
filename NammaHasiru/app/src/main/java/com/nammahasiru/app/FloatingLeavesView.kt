package com.nammahasiru.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class FloatingLeavesView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val leaves = mutableListOf<Leaf>()
    private val leafCount = 20 // Number of floating leaves
    private var isInitialized = false

    private data class Leaf(
        var x: Float,
        var y: Float,
        var size: Float,
        var speedY: Float,
        var speedX: Float,
        var angle: Float,
        var rotationSpeed: Float,
        var color: Int,
        var alpha: Int
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isInitialized) {
            initLeaves(w, h)
            isInitialized = true
        }
    }

    private fun initLeaves(w: Int, h: Int) {
        leaves.clear()
        val colors = listOf(
            Color.parseColor("#4DA5D6A7"),
            Color.parseColor("#3381C784"),
            Color.parseColor("#4D66BB6A"),
            Color.parseColor("#22FFFFFF")
        )
        for (i in 0 until leafCount) {
            leaves.add(
                Leaf(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    size = Random.nextFloat() * 30f + 20f, // Slightly larger leaves
                    // Falling downwards (gravity)
                    speedY = Random.nextFloat() * 2.5f + 1.0f,
                    speedX = (Random.nextFloat() - 0.5f) * 2.0f,
                    angle = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 1.5f,
                    color = colors.random(),
                    alpha = Random.nextInt(80, 180)
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isInitialized) return

        val w = width.toFloat()
        val h = height.toFloat()

        for (leaf in leaves) {
            // Update position (falling downwards)
            leaf.y += leaf.speedY
            leaf.x += leaf.speedX
            leaf.angle += leaf.rotationSpeed

            // Wrap around when it goes off screen (bottom to top)
            if (leaf.y - leaf.size > h) {
                leaf.y = -leaf.size
                leaf.x = Random.nextFloat() * w
            }
            if (leaf.x > w + leaf.size) leaf.x = -leaf.size
            if (leaf.x < -leaf.size) leaf.x = w + leaf.size

            canvas.save()
            canvas.translate(leaf.x, leaf.y)
            canvas.rotate(leaf.angle)
            
            leafPaint.color = leaf.color
            leafPaint.alpha = leaf.alpha
            
            // Draw a more natural, organic leaf shape
            val path = Path()
            // Tip of the leaf
            path.moveTo(0f, -leaf.size / 2)
            // Right edge (wider at bottom)
            path.cubicTo(
                leaf.size / 1.8f, -leaf.size / 6f,
                leaf.size / 2.2f, leaf.size / 3f,
                0f, leaf.size / 2
            )
            // Left edge
            path.cubicTo(
                -leaf.size / 2.2f, leaf.size / 3f,
                -leaf.size / 1.8f, -leaf.size / 6f,
                0f, -leaf.size / 2
            )
            canvas.drawPath(path, leafPaint)
            
            // Draw subtle center vein
            leafPaint.style = Paint.Style.STROKE
            leafPaint.strokeWidth = 2f
            leafPaint.alpha = leaf.alpha / 2
            val veinPath = Path()
            veinPath.moveTo(0f, -leaf.size / 2.5f)
            veinPath.quadTo(leaf.size / 8f, 0f, 0f, leaf.size / 2.2f)
            canvas.drawPath(veinPath, leafPaint)
            leafPaint.style = Paint.Style.FILL // Reset to fill for next leaf
            
            canvas.restore()
        }

        invalidate() // Continuous animation loop
    }
}
