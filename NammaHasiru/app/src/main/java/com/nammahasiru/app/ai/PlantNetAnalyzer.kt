package com.nammahasiru.app.ai

import android.graphics.Bitmap
import android.util.Log
import com.nammahasiru.app.BuildConfig
import com.nammahasiru.app.data.PlantStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * PlantNetAnalyzer — PlantNet API v2  +  Gemini fallback
 *
 * Flow:
 *  1. POST to PlantNet /v2/identify/all?organs=auto
 *       - any recognised result (score > 0.02) → ALIVE
 *       - 0 results or score too low          → DEAD
 *  2. If PlantNet returns 4xx / 5xx / network error
 *       → fall back to Gemini (PlantHealthAnalyzer)
 *
 * Key bug-fixes vs previous version:
 *  • Added `organs=auto` query param  (PlantNet rejects requests without it)
 *  • Added `organs` as a multipart text field in the request body (API requirement)
 *  • Confidence threshold lowered to 0.02 (PlantNet rarely gives high scores)
 *  • Gemini fallback so the user always gets an answer
 */
object PlantNetAnalyzer {

    private const val TAG      = "PlantNetAnalyzer"
    private const val BASE_URL = "https://my-api.plantnet.org/v2"
    private const val BOUNDARY = "----PlantNetBoundary7MA4YWxkTrZu0gW"

    // ── Public entry point ────────────────────────────────────────────────────

    suspend fun analyze(bitmap: Bitmap): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting PlantNet analysis")

            // ── Stage 1: Plant presence gate (on-device, no network) ──────────
            val presence = PlantPresenceDetector.detect(bitmap)
            Log.d(TAG, "Presence gate → isPlant=${presence.isPlant}  conf=${presence.confidence}%")

            if (!presence.isPlant) {
                return@withContext AnalysisResult(
                    status     = PlantStatus.UNKNOWN,
                    message    = presence.message,
                    error      = null,
                    confidence = presence.confidence,
                    notAPlant  = true
                )
            }

            // ── Stage 2: PlantNet remote identification ───────────────────────
            val imageBytes   = bitmapToJpeg(bitmap)
            val plantNetResult = tryIdentifyEndpoint(imageBytes, presence.confidence)
            if (plantNetResult != null) {
                Log.d(TAG, "PlantNet succeeded: ${plantNetResult.status}")
                return@withContext plantNetResult
            }

            // ── Stage 3: On-device fallback (PlantHealthAnalyzer) ────────────
            Log.w(TAG, "PlantNet failed — falling back to on-device AI")
            val fallback = PlantHealthAnalyzer.analyze(bitmap)
            return@withContext AnalysisResult(
                status     = fallback.status,
                message    = fallback.message,
                error      = fallback.error,
                confidence = fallback.confidence,
                notAPlant  = fallback.notAPlant
            )

        } catch (e: Exception) {
            val msg = friendlyError(e)
            Log.e(TAG, "Analysis FAILED: $msg", e)
            AnalysisResult(
                status  = PlantStatus.UNKNOWN,
                message = "⚠️ Analysis error: $msg",
                error   = msg
            )
        }
    }

    // ── PlantNet identify endpoint ────────────────────────────────────────────

    private fun tryIdentifyEndpoint(imageBytes: ByteArray, presenceConf: Int = 0): AnalysisResult? {
        return try {
            // 'organs=auto' is REQUIRED — PlantNet returns 400 without it
            val url = URL(
                "$BASE_URL/identify/all" +
                "?api-key=${BuildConfig.PLANTNET_API_KEY}" +
                "&lang=en" +
                "&organs=auto" +
                "&include-related-images=false" +
                "&no-reject=false"
            )

            val (code, body) = postMultipart(url, imageBytes)
            Log.d(TAG, "PlantNet identify → HTTP $code  body=${body.take(300)}")

            when {
                code == 404 -> {
                    // No match found at all (PlantNet returns 404 when it can't identify)
                    Log.w(TAG, "PlantNet 404 — plant not identified")
                    AnalysisResult(
                        status  = PlantStatus.DEAD,
                        message = "Plant not identified — may be dead, dried, or missing 🍂",
                        error   = null
                    )
                }
                code !in 200..299 -> {
                    Log.w(TAG, "PlantNet returned $code — delegating to Gemini")
                    null   // trigger Gemini fallback
                }
                else -> parseIdentifyResponse(JSONObject(body), presenceConf)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PlantNet exception: ${e.message}")
            null   // trigger on-device fallback
        }
    }

    private fun parseIdentifyResponse(json: JSONObject, presenceConf: Int = 0): AnalysisResult {
        val results = json.optJSONArray("results")

        // Empty results array → couldn't see any plant features → treat as unknown
        if (results == null || results.length() == 0) {
            return AnalysisResult(
                status     = PlantStatus.DEAD,
                message    = "No plant features recognised — plant may be dead or missing 🍂",
                error      = null,
                confidence = 0
            )
        }

        val topResult = results.getJSONObject(0)
        val score     = topResult.optDouble("score", 0.0)
        val name      = extractCommonName(topResult)
        // Blend PlantNet score with on-device presence confidence
        val blendedConf = ((pct(score) * 0.6f + presenceConf * 0.4f).toInt()).coerceIn(1, 99)

        Log.d(TAG, "Top result: $name  score=$score  blendedConf=$blendedConf")

        return if (score >= 0.02) {
            // Plant was recognisable → alive/visible features
            AnalysisResult(
                status     = PlantStatus.ALIVE,
                message    = "Plant identified as $name — alive 🌿 ($blendedConf% confidence)",
                error      = null,
                confidence = blendedConf
            )
        } else {
            // Effectively no match → plant may be dead / featureless
            AnalysisResult(
                status     = PlantStatus.DEAD,
                message    = "Plant barely recognisable — may be dead or damaged 🍂 (${pct(score)}% match)",
                error      = null,
                confidence = pct(score)
            )
        }
    }

    private fun extractCommonName(result: JSONObject): String {
        val species = result.optJSONObject("species") ?: return "Unknown plant"
        val names   = species.optJSONArray("commonNames")
        return names?.optString(0)?.takeIf { it.isNotBlank() }
            ?: species.optString("scientificNameWithoutAuthor").takeIf { it.isNotBlank() }
            ?: "Unknown plant"
    }

    // ── HTTP multipart POST ───────────────────────────────────────────────────

    /**
     * PlantNet requires BOTH:
     *   - `organs` as a query param (?organs=auto)
     *   - `organs` as a form field in the multipart body
     * Missing either one causes a 400 / unexpected response.
     */
    private fun postMultipart(url: URL, imageBytes: ByteArray): Pair<Int, String> {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
        conn.doOutput       = true
        conn.connectTimeout = 15_000
        conn.readTimeout    = 25_000

        DataOutputStream(conn.outputStream).use { out ->

            // ── organs field (required by PlantNet in the body) ───────────────
            out.writeBytes("--$BOUNDARY\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"organs\"\r\n\r\n")
            out.writeBytes("auto\r\n")

            // ── image field ───────────────────────────────────────────────────
            out.writeBytes("--$BOUNDARY\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"images\"; filename=\"plant.jpg\"\r\n")
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            out.write(imageBytes)
            out.writeBytes("\r\n")

            out.writeBytes("--$BOUNDARY--\r\n")
            out.flush()
        }

        val code = conn.responseCode
        val body = try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        conn.disconnect()

        return Pair(code, body)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun bitmapToJpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also {
            // Use higher quality so PlantNet can extract plant features
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }.toByteArray()

    private fun pct(score: Double) = (score * 100).toInt()

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("401") || msg.contains("403")                               -> "Invalid PlantNet API key"
            msg.contains("429") || msg.contains("quota", ignoreCase = true)          -> "API quota exceeded — try again later"
            msg.contains("UnknownHost") || msg.contains("connect", ignoreCase = true) -> "No internet connection"
            msg.length > 120 -> msg.take(120) + "…"
            else -> msg
        }
    }

    // ── Result type ───────────────────────────────────────────────────────────

    data class AnalysisResult(
        val status:     PlantStatus,
        val message:    String,
        val error:      String?,
        /** 0–100 confidence that the status label is correct */
        val confidence: Int = 0,
        /** True when Stage-1 (PlantPresenceDetector) found no plant in the image */
        val notAPlant:  Boolean = false
    )
}
