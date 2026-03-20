package com.countdown.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.*

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
        val day   = prefs.getInt("target_day",   24)
        val month = prefs.getInt("target_month", 12)
        val year  = prefs.getInt("target_year",  2027)

        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val totalDays = ((target.timeInMillis - now.timeInMillis) / (1000L * 60 * 60 * 24)).toInt()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pad = { n: Int -> String.format("%02d", n) }
        val title = "📅 До ${pad(day)}.${pad(month)}.${year}"
        val body  = "Залишилось $totalDays днів"

        val notif = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(1001, notif)
    }
}
