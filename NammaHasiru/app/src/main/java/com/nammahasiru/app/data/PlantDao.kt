package com.nammahasiru.app.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface PlantDao {

    @Query("SELECT * FROM plants ORDER BY plantedDate DESC")
    fun getAllPlants(): LiveData<List<Plant>>

    @Query("SELECT * FROM plants WHERE id = :plantId")
    suspend fun getPlantById(plantId: Long): Plant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlant(plant: Plant): Long

    @Update
    suspend fun updatePlant(plant: Plant)

    @Delete
    suspend fun deletePlant(plant: Plant)

    @Query("SELECT COUNT(*) FROM plants")
    fun getTotalCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM plants WHERE status = 'ALIVE'")
    fun getAliveCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM plants WHERE status = 'DEAD'")
    fun getDeadCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM plants WHERE status = 'UNKNOWN'")
    fun getUnknownCount(): LiveData<Int>

    @Query("SELECT * FROM plants ORDER BY plantedDate DESC")
    fun getRecentPlants(): LiveData<List<Plant>>

    @Query("SELECT * FROM plants WHERE status = 'ALIVE' ORDER BY plantedDate DESC")
    fun getAlivePlants(): LiveData<List<Plant>>

    @Query("SELECT * FROM plants WHERE status = 'DEAD' ORDER BY plantedDate DESC")
    fun getDeadPlants(): LiveData<List<Plant>>
}
