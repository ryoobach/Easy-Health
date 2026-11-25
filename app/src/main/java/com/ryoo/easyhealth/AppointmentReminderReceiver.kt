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
        val notificationTitle = intent.getStringExtra("notification_title") ?: "병원 방문 일정"
        val customText = intent.getStringExtra("notification_text")
        val hospital = intent.getStringExtra("hospital") ?: "병원"
        val memo = intent.getStringExtra("memo").orEmpty()
        val channelId = if (notificationTitle == "메모 알림") "schedule_diary_memos" else "hospital_appointments"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = if (notificationTitle == "메모 알림") "간단 메모 알림" else "병원 방문 알림"
            manager.createNotificationChannel(NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH))
        }
        val notificationText = customText ?: if (memo.isBlank()) "$hospital 방문 시간입니다." else "$hospital · $memo"
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(intent.getIntExtra("appointment_id", 1), notification)
        }
    }
}
