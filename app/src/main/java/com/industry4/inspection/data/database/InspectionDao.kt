package com.industry4.inspection.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {

    @Query("SELECT * FROM inspections ORDER BY timestamp DESC")
    fun getAllInspections(): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentInspections(limit: Int = 10): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections WHERE id = :id LIMIT 1")
    suspend fun getInspectionById(id: String): InspectionEntity?

    @Query("SELECT COUNT(*) FROM inspections")
    fun getTotalInspectionsCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(defectCount), 0) FROM inspections")
    fun getTotalDefectsDetected(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspection(entity: InspectionEntity)

    @Delete
    suspend fun deleteInspection(entity: InspectionEntity)

    @Query("DELETE FROM inspections WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM inspections")
    suspend fun clearAll()
}
