package com.homelab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.homelab.app.data.local.entity.ServiceStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {
    @Query("SELECT * FROM service_status")
    fun getAllStatuses(): Flow<List<ServiceStatusEntity>>

    @Query("SELECT * FROM service_status WHERE serviceId = :id")
    fun getStatusById(id: String): Flow<ServiceStatusEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: ServiceStatusEntity)

    @Query("DELETE FROM service_status")
    suspend fun clearAll()
}
