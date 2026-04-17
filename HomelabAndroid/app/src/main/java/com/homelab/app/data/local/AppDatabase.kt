package com.homelab.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.homelab.app.data.local.dao.ServiceDao
import com.homelab.app.data.local.dao.ServiceInstanceDao
import com.homelab.app.data.local.entity.ServiceInstanceEntity
import com.homelab.app.data.local.entity.ServiceStatusEntity

@Database(
    entities = [ServiceStatusEntity::class, ServiceInstanceEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serviceDao(): ServiceDao
    abstract fun serviceInstanceDao(): ServiceInstanceDao
}
