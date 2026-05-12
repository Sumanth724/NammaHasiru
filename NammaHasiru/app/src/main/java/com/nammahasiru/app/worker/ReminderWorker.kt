package com.nammahasiru.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nammahasiru.app.MainActivity
import com.nammahasiru.app.R
import com.nammahasiru.app.data.Notification
import com.nammahasiru.app.data.PlantDatabase

class ReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "namma_hasiru_reminders"
        const val CHANNEL_NAME = "Plant Check Reminders"
    }

    override suspend fun doWork(): Result {
        val plantId = inputData.getLong("plant_id", -1L)
        val speciesName = inputData.getString("species_name") ?: "your plant"
        val ownerUsername = inputData.getString("owner_username")
        val type = inputData.getString("type") ?: "CHECK"

        val prefs = context.getSharedPreferences("namma_hasiru_auth", Context.MODE_PRIVATE)
        val loggedInUser = prefs.getString("logged_in_user", null)

        // Ensure notifications are only for the logged-in user's own plants
        if (ownerUsername != null && ownerUsername != loggedInUser) {
            return Result.success()
        }

        // Logic Update: Use exact requested strings and emojis
        val (title, message) = when (type) {
            "GEO" -> Pair(
                "Plant geo-tagged", 
                "🌱 $speciesName plant has been saved to the map."
            )
            else -> Pair(
                "🌱 90-day check due!", 
                "$speciesName plant — please verify survival."
            )
        }

        // Store notification history in database for the owner
        if (ownerUsername != null && type != "GEO") {
            // Note: GEO is inserted by ViewModel immediately upon planting.
            // CHECK is inserted here when the 90-day work triggers.
            val db = PlantDatabase.getDatabase(context, ownerUsername)
            db.notificationDao().insertNotification(
                Notification(
                    title = title,
                    message = message,
                    type = type
                )
            )
        }

        createNotificationChannel()
        sendNotification(plantId, title, message)
        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Eco-reminders for your planted trees"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(plantId: Long, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, plantId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tree_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(0x2E7D32) // Eco-themed green accent
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(plantId.toInt(), notification)
    }
}
