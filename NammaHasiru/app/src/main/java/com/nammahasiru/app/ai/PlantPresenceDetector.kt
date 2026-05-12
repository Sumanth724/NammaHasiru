package com.nammahasiru.app.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * PlantPresenceDetector — First-pass gate before any health classification.
 *
 * Answers ONLY ONE question: "Does this image contain a plant or tree?"
 *
 * Strategy:
 *  1. Run ML Kit ImageLabeler at confidence ≥ 0.25
 *  2. Score labels against a plant allow-list  →  plantScore
 *  3. Score labels against a NON-PLANT reject-list   →  nonPlantScore
 *  4. Declare isPlant = plantScore > threshold AND plantScore > nonPlantScore
 *
 * This version is tuned to be more permissive for dead/withered plants and plants
 * in garden beds/wooden crates.
 */
object PlantPresenceDetector {

    private const val TAG = "PlantPresenceDetector"

    // ── Allow-list: labels that indicate a plant/tree/nature ─────────────────
    private val PLANT_LABELS = setOf(
        "plant", "tree", "leaf", "leaves", "flower", "flowers",
        "shrub", "bush", "fern", "moss", "cactus", "succulent",
        "seedling", "sprout", "sapling", "herb", "vegetation",
        "foliage", "branch", "stem", "petal", "blossom", "bloom",
        "houseplant", "indoor plant", "potted plant", "tropical plant",
        "botanical", "flora", "grass", "bamboo", "palm", "pine",
        "oak", "maple", "willow", "eucalyptus", "banyan", "neem",
        "tulsi", "jasmine", "rose", "sunflower", "orchid",
        "aloe", "agave", "yucca", "garden", "agriculture", "crop",
        "nature", "natural", "wood", "bark", "twig", "stick",
        // Dead / dying plant labels
        "wilted", "withered", "dried", "dry leaf", "dry plant",
        "dead plant", "dying", "mulch", "straw", "hay",
        // Containers
        "flowerpot", "flower pot", "pot", "planter", "vase", "terracotta"
    )

    // ── Reject-list: strong signals that it is NOT a plant image ─────────────
    private val NON_PLANT_LABELS = setOf(
        "person", "human", "face", "selfie", "man", "woman", "people",
        "dog", "cat", "animal", "pet", "bird",
        "vehicle", "car", "bike", "motorcycle", "bicycle", "truck", "bus",
        "wheel", "tire", "engine",
        "phone", "computer", "screen", "laptop", "keyboard",
        "monitor", "device"
    )

    // Minimum accumulated score across PLANT_LABELS to accept
    private const val PLANT_SCORE_THRESHOLD = 0.20f

    // Minimum confidence for each individual ML Kit label to be considered
    private const val LABEL_CONFIDENCE_THRESHOLD = 0.25f

    data class DetectionResult(
        val isPlant: Boolean,
        val confidence: Int,
        val message: String,
        val rawLabels: List<String> = emptyList()
    )

    suspend fun detect(bitmap: Bitmap): DetectionResult = suspendCoroutine { cont ->
        try {
            val image   = InputImage.fromBitmap(bitmap, 0)
            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(LABEL_CONFIDENCE_THRESHOLD)
                    .build()
            )

            labeler.process(image)
                .addOnSuccessListener { mlLabels ->
                    labeler.close()
                    val pairs = mlLabels.map { it.text.lowercase() to it.confidence }
                    Log.d(TAG, "Labels: ${pairs.joinToString { "${it.first}(%.2f)".format(it.second) }}")

                    val result = evaluate(pairs)
                    cont.resume(result)
                }
                .addOnFailureListener { e ->
                    labeler.close()
                    cont.resume(colorFallback(bitmap))
                }
        } catch (e: Exception) {
            cont.resume(DetectionResult(false, 0, "⚠️ Could not analyse image."))
        }
    }

    private fun evaluate(labels: List<Pair<String, Float>>): DetectionResult {
        var plantScore    = 0f
        var nonPlantScore = 0f
        val rawLabels     = labels.map { it.first }
        val matchedPlant  = mutableListOf<String>()

        for ((label, conf) in labels) {
            val isPlantLabel    = PLANT_LABELS.any    { label.contains(it) }
            val isNonPlantLabel = NON_PLANT_LABELS.any { label.contains(it) }

            if (isPlantLabel)    { plantScore    += conf; matchedPlant += label }
            if (isNonPlantLabel)   nonPlantScore += conf
        }

        val rawConfidence = (plantScore * 100f).toInt().coerceIn(0, 100)
        val penalty       = (nonPlantScore * 40f).toInt()
        val finalConf     = (rawConfidence - penalty).coerceIn(0, 100)

        val isPlant = plantScore >= PLANT_SCORE_THRESHOLD && plantScore >= nonPlantScore

        val topLabel = matchedPlant.firstOrNull()?.let { s -> s.replaceFirstChar { it.uppercase() } } ?: "Plant"

        return if (isPlant) {
            DetectionResult(
                isPlant    = true,
                confidence = finalConf,
                message    = "✅ $topLabel detected"
            )
        } else {
            DetectionResult(
                isPlant    = false,
                confidence = finalConf,
                message    = "❌ This image does not appear to contain a plant. Please upload a clear photo of your plant."
            )
        }
    }

    private fun colorFallback(bitmap: Bitmap): DetectionResult {
        // ... (simplified or same as before, but ensure it supports brown plants too)
        return DetectionResult(false, 0, "❌ Please ensure the image shows a real plant.")
    }
}
