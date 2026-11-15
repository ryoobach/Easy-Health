package com.ryoo.easyhealth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AppointmentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hospital_appointments"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "병원 방문 알림", NotificationManager.IMPORTANCE_HIGH))
        }
        val hospital = intent.getStringExtra("hospital") ?: "병원"
        val memo = intent.getStringExtra("memo").orEmpty()
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("병원 방문 일정")
            .setContentText(if (memo.isBlank()) "$hospital 방문 시간입니다." else "$hospital · $memo")
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (memo.isBlank()) "$hospital 방문 시간입니다." else "$hospital · $memo"))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(intent.getIntExtra("appointment_id", 1), notification)
        }
    }
}
