package com.nammahasiru.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Simple binary classification: ALIVE or DEAD.
 * UNKNOWN is used only when the AI cannot make a confident decision.
 */
enum class PlantStatus {
    ALIVE,    // Plant has visible green leaves / active growth
    DEAD,     // Plant is dry, brown, leafless — no life signs
    UNKNOWN   // AI could not determine — user should retake photo
}

@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val speciesName: String,
    val latitude: Double,
    val longitude: Double,
    val photoPath: String?,
    val plantedDate: Long = System.currentTimeMillis(),
    val status: PlantStatus = PlantStatus.UNKNOWN,
    val healthConfidence: Int = 0,  // internal use only — not shown in UI
    val notes: String = "",
    val locationName: String = "",
    val reminderScheduled: Boolean = false
)
