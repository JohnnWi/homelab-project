package com.homelab.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val CHANNEL_CRITICAL_ALERTS = "critical_alerts"
    const val CHANNEL_GENERAL_INFO = "general_info"
    
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL_ALERTS, 
                "Allarmi Critici", 
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche per server down o crash"
            }
            
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL_INFO, 
                "Informazioni Generali", 
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Aggiornamenti di status in background"
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(criticalChannel)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }
}
