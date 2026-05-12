package com.nammahasiru.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromStatus(status: PlantStatus): String = status.name

    /**
     * Safe converter — maps all legacy and intermediate values to ALIVE/DEAD/UNKNOWN.
     * HEALTHY / UNHEALTHY (from the 3-class experiment) → ALIVE
     */
    @TypeConverter
    fun toStatus(value: String): PlantStatus = when (value) {
        "ALIVE", "HEALTHY", "UNHEALTHY" -> PlantStatus.ALIVE
        "DEAD"                           -> PlantStatus.DEAD
        else                             -> PlantStatus.UNKNOWN
    }
}

/** v1→v2: added healthConfidence column */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE plants ADD COLUMN healthConfidence INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/** v2→v3: normalise HEALTHY/UNHEALTHY back to ALIVE */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "UPDATE plants SET status = 'ALIVE' WHERE status IN ('HEALTHY', 'UNHEALTHY')"
        )
    }
}

/** v3→v4: Added notifications table */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL, `type` TEXT NOT NULL)"
        )
    }
}

@Database(entities = [Plant::class, Notification::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class PlantDatabase : RoomDatabase() {

    abstract fun plantDao(): PlantDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: PlantDatabase? = null

        fun getDatabase(context: Context, username: String): PlantDatabase {
            val dbName = "nh_plants_${username.lowercase()}"
            val current = INSTANCE
            if (current != null && current.isOpen &&
                current.openHelper.databaseName == dbName) {
                return current
            }
            current?.close()
            INSTANCE = null

            return synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlantDatabase::class.java,
                    dbName
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun clearInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
