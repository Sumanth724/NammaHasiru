package com.nammahasiru.app.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.nammahasiru.app.data.PlantStatus
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * PlantHealthAnalyzer — two-stage, on-device classifier (no API key, no internet).
 *
 * Stage 1 — Plant presence gate (PlantPresenceDetector):
 *   If no plant is detected → return NOT_A_PLANT immediately.
 *   This prevents walls, selfies, bikes, blank ground from being accepted.
 *
 * Stage 2 — Health classification (ML Kit labels + center-weighted colour):
 *   Only reached when Stage 1 confirms a plant is present.
 *   Output: ALIVE or DEAD with a confidence %.
 */
object PlantHealthAnalyzer {

    private const val TAG = "PlantHealthAnalyzer"

    // ─ Labels that suggest the plant is still living ─────────────────────────
    private val ALIVE_KEYWORDS = setOf(
        "plant", "leaf", "flower", "tree", "grass", "shrub", "succulent",
        "cactus", "fern", "moss", "vegetation", "foliage", "flora",
        "herb", "bloom", "blossom", "sprout", "seedling", "houseplant",
        "garden", "botanical", "branch", "bush"
    )

    // ─ Labels that suggest the plant is dead / dying ──────────────────────────
    private val DEAD_KEYWORDS = setOf(
        "dead", "dry", "dried", "wilted", "decay",
        // Note: intentionally excludes "wood", "bark", "soil", "dirt" — ML Kit
        // returns these for ANY plant photo (pot, trunk, ground) and they must
        // NOT be treated as evidence of death.
        "twig", "stick", "sand", "mulch"
    )

    // ── Result type (extends with confidence + notAPlant flag) ────────────────
    data class AnalysisResult(
        val status: PlantStatus,
        val message: String,
        val error: String?,
        /** 0–100 confidence that the health label is correct */
        val confidence: Int = 0,
        /** True when Stage-1 determined no plant is visible in the image */
        val notAPlant: Boolean = false
    )

    // ── Public entry point ────────────────────────────────────────────────────

    suspend fun analyze(bitmap: Bitmap): AnalysisResult {
        // ── Stage 1: plant presence gate ──────────────────────────────────────
        val presence = PlantPresenceDetector.detect(bitmap)
        Log.d(TAG, "Presence gate → isPlant=${presence.isPlant}  conf=${presence.confidence}%")

        if (!presence.isPlant) {
            return AnalysisResult(
                status    = PlantStatus.UNKNOWN,
                message   = presence.message,   // user-friendly rejection message
                error     = null,
                confidence = presence.confidence,
                notAPlant  = true
            )
        }

        // ── Stage 2: health classification ───────────────────────────────────
        return runHealthClassification(bitmap, presence.confidence)
    }

    // ── ML Kit health classification ──────────────────────────────────────────

    private suspend fun runHealthClassification(
        bitmap: Bitmap,
        presenceConf: Int
    ): AnalysisResult = suspendCoroutine { cont ->
        try {
            val image   = InputImage.fromBitmap(bitmap, 0)
            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder().setConfidenceThreshold(0.35f).build()
            )

            labeler.process(image)
                .addOnSuccessListener { mlLabels ->
                    labeler.close()
                    val labelList = mlLabels.map { it.text.lowercase() to it.confidence }
                    Log.d(TAG, "Health labels: ${labelList.joinToString { "${it.first}(%.2f)".format(it.second) }}")

                    var mlAlive = 0f
                    var mlDead  = 0f
                    val names   = mutableListOf<String>()
                    for ((label, conf) in labelList) {
                        when {
                            ALIVE_KEYWORDS.any { label.contains(it) } -> { mlAlive += conf; names += label }
                            DEAD_KEYWORDS.any  { label.contains(it) } -> mlDead  += conf
                        }
                    }

                    val colors = centerWeightedColors(bitmap)
                    val result = decide(mlAlive, mlDead, colors, names, presenceConf)
                    Log.d(TAG, "Health result: ${result.status}  conf=${result.confidence}%")
                    cont.resume(result)
                }
                .addOnFailureListener { e ->
                    labeler.close()
                    Log.w(TAG, "ML Kit health labels failed: ${e.message}")
                    val colors = centerWeightedColors(bitmap)
                    cont.resume(colorOnlyDecide(colors, presenceConf))
                }
        } catch (e: Exception) {
            Log.e(TAG, "runHealthClassification() error: ${e.message}", e)
            cont.resume(
                AnalysisResult(
                    status     = PlantStatus.UNKNOWN,
                    message    = "⚠️ Analysis error. Please try again.",
                    error      = e.message,
                    confidence = 0
                )
            )
        }
    }

    // ── Decision: ALIVE or DEAD with confidence ───────────────────────────────

    private fun decide(
        mlAlive: Float,
        mlDead: Float,
        colors: ColorAnalysis,
        names: List<String>,
        presenceConf: Int
    ): AnalysisResult {
        val g     = colors.greenRatio
        val b     = colors.brownRatio
        val label = names.take(2).joinToString(", ").ifBlank { "plant" }

        fun healthConf(baseConf: Int) =
            ((presenceConf * 0.5f + baseConf * 0.5f).toInt()).coerceIn(1, 99)

        // ── ALIVE checks come FIRST — green wins over any dead labels ─────────
        // Rationale: soil/bark labels appear in every plant photo. If the image
        // has meaningful green, the plant is alive — end of story.

        // Strong green + ML confirmation
        if (g >= 0.15f && mlAlive > 0.25f) {
            val conf = healthConf(minOf(95, (g * 300 + mlAlive * 50).toInt()))
            return AnalysisResult(
                status     = PlantStatus.ALIVE,
                message    = "Plant detected with $conf% confidence — alive and healthy! 🌿 ($label)",
                error      = null,
                confidence = conf
            )
        }

        // Decent green alone
        if (g >= 0.12f) {
            val conf = healthConf((g * 300).toInt().coerceIn(40, 85))
            return AnalysisResult(
                status     = PlantStatus.ALIVE,
                message    = "Plant appears alive 🌿 ($conf% confidence)",
                error      = null,
                confidence = conf
            )
        }

        // Grey-green plants (cactus, succulent, dark-leaved) have lower ratios
        if (g >= 0.06f) {
            val conf = healthConf((g * 400).toInt().coerceIn(35, 78))
            return AnalysisResult(
                status     = PlantStatus.ALIVE,
                message    = "Plant appears alive 🌿 ($conf% confidence)",
                error      = null,
                confidence = conf
            )
        }

        // ML Kit alive labels + trace of green
        if (mlAlive > 0.25f && g >= 0.03f) {
            val conf = healthConf((mlAlive * 80).toInt())
            return AnalysisResult(
                status     = PlantStatus.ALIVE,
                message    = "Plant detected and alive 🌿 — $conf% confidence ($label)",
                error      = null,
                confidence = conf
            )
        }

        // ── DEAD checks — only when green is near-zero ────────────────────────
        // Any plant with even a trace of green should NOT be called dead.

        // Strong ML dead signal AND near-zero green
        if (mlDead > 0.6f && mlDead > mlAlive && g < 0.03f) {
            val conf = healthConf((mlDead * 100).toInt())
            return AnalysisResult(
                status     = PlantStatus.DEAD,
                message    = "Plant appears dead 🍂 ($conf% confidence)",
                error      = null,
                confidence = conf
            )
        }

        // Dominant brown AND near-zero green
        if (b >= 0.30f && g < 0.03f) {
            val conf = healthConf((b * 200).toInt().coerceIn(40, 80))
            return AnalysisResult(
                status     = PlantStatus.DEAD,
                message    = "Plant appears dead — dry/brown 🍂 ($conf% confidence)",
                error      = null,
                confidence = conf
            )
        }

        // ML Kit dead labels + near-zero green
        if (mlDead > 0.5f && g < 0.03f) {
            val conf = healthConf((mlDead * 80).toInt())
            return AnalysisResult(
                status     = PlantStatus.DEAD,
                message    = "Plant appears dead 🍂 ($conf% confidence)",
                error      = null,
                confidence = conf
            )
        }

        // ── Presence fallback ─────────────────────────────────────────────────
        // Stage 1 confirmed a plant. If we can't call it dead, assume ALIVE.
        if (presenceConf >= 40) {
            val conf = presenceConf.coerceIn(40, 70)
            return AnalysisResult(
                status     = PlantStatus.ALIVE,
                message    = "Plant appears alive 🌿 ($conf% confidence — clearer photo improves accuracy)",
                error      = null,
                confidence = conf
            )
        }

        // ── Truly unclear ─────────────────────────────────────────────────────
        return AnalysisResult(
            status     = PlantStatus.UNKNOWN,
            message    = "⚠️ Not sure — retake the photo in good light, focusing on the leaves",
            error      = null,
            confidence = 0
        )
    }

    private fun colorOnlyDecide(colors: ColorAnalysis, presenceConf: Int): AnalysisResult {
        val g = colors.greenRatio
        val b = colors.brownRatio
        fun healthConf(base: Int) = ((presenceConf * 0.5f + base * 0.5f).toInt()).coerceIn(1, 99)

        return when {
            // Any meaningful green → alive (grey-green plants included)
            g >= 0.06f -> {
                val conf = healthConf((g * 350).toInt().coerceIn(35, 85))
                AnalysisResult(PlantStatus.ALIVE, "Plant appears alive 🌿 ($conf% confidence)", null, conf)
            }
            g >= 0.03f && g * 2f > b -> {
                val conf = healthConf(45)
                AnalysisResult(PlantStatus.ALIVE, "Plant appears alive 🌿 ($conf% confidence)", null, conf)
            }
            // Dead only when near-zero green AND dominant brown
            b >= 0.30f && g < 0.03f -> {
                val conf = healthConf((b * 200).toInt().coerceIn(40, 80))
                AnalysisResult(PlantStatus.DEAD, "Plant appears dead 🍂 ($conf% confidence)", null, conf)
            }
            // Presence-based fallback — plant confirmed by Stage 1
            presenceConf >= 40 -> {
                val conf = presenceConf.coerceIn(40, 68)
                AnalysisResult(PlantStatus.ALIVE, "Plant appears alive 🌿 ($conf% confidence)", null, conf)
            }
            else ->
                AnalysisResult(PlantStatus.UNKNOWN, "⚠️ Not sure — retake in good light", null, 0)
        }
    }

    // ── Center-weighted colour analysis ───────────────────────────────────────

    private data class ColorAnalysis(val greenRatio: Float, val brownRatio: Float, val sampledPixels: Int)

    private fun centerWeightedColors(bitmap: Bitmap): ColorAnalysis {
        val w = bitmap.width
        val h = bitmap.height

        val coreX1 = (w * 0.30f).toInt(); val coreX2 = (w * 0.70f).toInt()
        val coreY1 = (h * 0.30f).toInt(); val coreY2 = (h * 0.70f).toInt()
        val midX1  = (w * 0.15f).toInt(); val midX2  = (w * 0.85f).toInt()
        val midY1  = (h * 0.15f).toInt(); val midY2  = (h * 0.85f).toInt()

        var wGreen = 0f; var wBrown = 0f; var wTotal = 0f; var pxCount = 0
        val step = 5

        var py = 0
        while (py < h) {
            var px = 0
            while (px < w) {
                val pixel      = bitmap.getPixel(px, py)
                val r          = Color.red(pixel)
                val g          = Color.green(pixel)
                val b          = Color.blue(pixel)
                val brightness = (r + g + b) / 3
                if (brightness < 28 || brightness > 228) { px += step; continue }

                val inCore = px in coreX1..coreX2 && py in coreY1..coreY2
                val inMid  = px in midX1..midX2   && py in midY1..midY2
                val wt     = if (inCore) 4f else if (inMid) 2f else 1f

                wTotal += wt
                pxCount++

                when {
                    // Plant-green: covers both bright-green and grey-green (cactus/succulent)
                    // Relaxed: G just needs to be above R and above B by small margins
                    (g > r + 2 && g > b + 5 && g > 45) ||
                    (g > r && g > b && g > 60) -> wGreen += wt
                    // Brown/dry: R dominant, B clearly lower than R
                    r > g + 8 && r > 75 && b < (r - 25) && g > 25 -> wBrown += wt
                }
                px += step
            }
            py += step
        }

        return ColorAnalysis(
            greenRatio    = if (wTotal > 0) wGreen / wTotal else 0f,
            brownRatio    = if (wTotal > 0) wBrown / wTotal else 0f,
            sampledPixels = pxCount
        )
    }
}
