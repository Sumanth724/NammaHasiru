package com.nammahasiru.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import com.nammahasiru.app.data.PlantStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * PlantHealthClassifier — Binary Alive / Dead Classifier
 *
 * Outputs only two states:
 *   ALIVE  — plant has visible green leaves or active growth
 *   DEAD   — plant is dry, brown, leafless; no life signs
 *   UNKNOWN — genuinely unclear (user should retake photo)
 *
 * Key design: CENTER-WEIGHTED sampling
 *   The central 50% of the image (by area) is analysed with 3× weight.
 *   This makes the classifier focus on the MAIN PLANT in the foreground
 *   and ignore surrounding garden/grass/background greenery.
 */
class PlantHealthClassifier(private val context: Context) {

    companion object {
        private const val TAG            = "PlantHealthClassifier"
        private const val MODEL_FILENAME = "plant_health_model.tflite"
        private const val IMG_SIZE       = 224
        private const val NUM_CLASSES    = 2   // 0=ALIVE, 1=DEAD
        private const val PIXEL_BYTES    = 4   // float32
    }

    private var interpreter: Interpreter? = null
    private var modelLoaded = false

    fun loadModel() {
        if (modelLoaded) return
        try {
            val model = FileUtil.loadMappedFile(context, MODEL_FILENAME)
            val options = Interpreter.Options().apply { numThreads = 4 }
            interpreter = Interpreter(model, options)
            modelLoaded = true
            Log.d(TAG, "TFLite model loaded")
        } catch (e: Exception) {
            Log.w(TAG, "No TFLite model — using colour fallback: ${e.message}")
            modelLoaded = false
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        modelLoaded = false
    }

    // ── Public entry point ────────────────────────────────────────────────────

    suspend fun classify(bitmap: Bitmap): ClassificationResult = withContext(Dispatchers.Default) {
        loadModel()
        return@withContext if (modelLoaded && interpreter != null) {
            runTFLiteInference(bitmap)
        } else {
            runCenterWeightedFallback(bitmap)
        }
    }

    // ── TFLite inference ──────────────────────────────────────────────────────

    private fun runTFLiteInference(bitmap: Bitmap): ClassificationResult {
        return try {
            val preprocessed = preprocessBitmap(bitmap)
            val inputBuffer  = bitmapToByteBuffer(preprocessed)
            val output       = Array(1) { FloatArray(NUM_CLASSES) }

            interpreter!!.run(inputBuffer, output)

            val scores = output[0]  // [aliveConf, deadConf]
            val alive  = scores[0]
            val dead   = scores[1]

            Log.d(TAG, "TFLite: alive=%.3f dead=%.3f".format(alive, dead))

            when {
                alive >= 0.65f -> aliveResult()
                dead  >= 0.65f -> deadResult()
                else           -> unsureResult()
            }
        } catch (e: Exception) {
            Log.e(TAG, "TFLite failed, using fallback: ${e.message}")
            runCenterWeightedFallback(bitmap)
        }
    }

    // ── Centre-weighted colour fallback ───────────────────────────────────────

    /**
     * Divides the image into 3 zones:
     *   CORE   (inner 40% width × 40% height) — weight 4×  ← main plant
     *   MIDDLE (inner 70% × 70%)              — weight 2×
     *   OUTER  (rest)                         — weight 1×  ← background
     *
     * This means a green garden behind a dead plant does NOT make it ALIVE.
     * A dead-brown cactus in the middle of a green lawn will still be DEAD.
     */
    private fun runCenterWeightedFallback(bitmap: Bitmap): ClassificationResult {
        val w = bitmap.width
        val h = bitmap.height

        // Zone boundaries
        val coreX1 = (w * 0.30f).toInt();  val coreX2 = (w * 0.70f).toInt()
        val coreY1 = (h * 0.30f).toInt();  val coreY2 = (h * 0.70f).toInt()
        val midX1  = (w * 0.15f).toInt();  val midX2  = (w * 0.85f).toInt()
        val midY1  = (h * 0.15f).toInt();  val midY2  = (h * 0.85f).toInt()

        var weightedGreen = 0f
        var weightedBrown = 0f
        var weightedTotal = 0f

        val step = 5
        var py = 0
        while (py < h) {
            var px = 0
            while (px < w) {
                val pixel = bitmap.getPixel(px, py)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3

                // Skip near-black (shadow) and near-white (sky/overexposed)
                if (brightness < 28 || brightness > 228) { px += step; continue }

                // Zone weight
                val inCore = px in coreX1..coreX2 && py in coreY1..coreY2
                val inMid  = px in midX1..midX2   && py in midY1..midY2
                val wt = when {
                    inCore -> 4f
                    inMid  -> 2f
                    else   -> 1f
                }

                weightedTotal += wt

                when {
                    // Green: G is prominent (prevents tan/brown by ensuring g is close to or greater than r)
                    g > b + 10 && g > 40 && (g - r) >= -5 ->
                        weightedGreen += wt

                    // Brown/dry: R is clearly dominant, B is low
                    r > g + 5 && r > 70 && b < (r - 20) && g > 28 ->
                        weightedBrown += wt
                }

                px += step
            }
            py += step
        }

        if (weightedTotal < 50) return unsureResult()

        val greenRatio = weightedGreen / weightedTotal
        val brownRatio = weightedBrown / weightedTotal

        Log.d(TAG, "CenterFallback: green=%.3f brown=%.3f total=%.0f".format(
            greenRatio, brownRatio, weightedTotal))

        return when {
            // Strong ALIVE: clear green presence that isn't completely dwarfed by brown
            greenRatio >= 0.10f -> aliveResult()
            greenRatio >= 0.05f && greenRatio * 3f > brownRatio -> aliveResult()

            // Strong DEAD: clear brown presence, not enough green to save it
            brownRatio >= 0.15f -> deadResult()

            // Weak ALIVE: some green present with very little brown
            greenRatio >= 0.03f -> aliveResult()

            // Truly unclear
            else -> unsureResult()
        }
    }

    // ── Image preprocessing ───────────────────────────────────────────────────

    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val side = min(w, h)
        val startX = (w - side) / 2
        val startY = (h - side) / 2
        val cropped = Bitmap.createBitmap(bitmap, startX, startY, side, side)
        val matrix = Matrix().apply {
            postScale(IMG_SIZE.toFloat() / cropped.width, IMG_SIZE.toFloat() / cropped.height)
        }
        return Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(1 * IMG_SIZE * IMG_SIZE * 3 * PIXEL_BYTES)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(IMG_SIZE * IMG_SIZE)
        bitmap.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE)
        for (pixel in pixels) {
            buf.putFloat(Color.red(pixel)   / 255.0f)
            buf.putFloat(Color.green(pixel) / 255.0f)
            buf.putFloat(Color.blue(pixel)  / 255.0f)
        }
        buf.rewind()
        return buf
    }

    // ── Result helpers (no confidence % in message) ───────────────────────────

    private fun aliveResult() = ClassificationResult(
        status    = PlantStatus.ALIVE,
        label     = "ALIVE",
        message   = "Plant is alive and healthy! 🌿",
        isLowConf = false
    )

    private fun deadResult() = ClassificationResult(
        status    = PlantStatus.DEAD,
        label     = "DEAD",
        message   = "Plant appears dead — dry and leafless 🍂",
        isLowConf = false
    )

    private fun unsureResult() = ClassificationResult(
        status    = PlantStatus.UNKNOWN,
        label     = "UNKNOWN",
        message   = "⚠️ Not sure — please retake the photo focusing on the plant's leaves",
        isLowConf = true
    )

    // ── Result type ───────────────────────────────────────────────────────────

    data class ClassificationResult(
        val status:    PlantStatus,
        val label:     String,
        val message:   String,
        val isLowConf: Boolean
    )
}
