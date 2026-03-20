package com.countdown.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val hour     = prefs.getInt("hour", 9)
        val minute   = prefs.getInt("minute", 0)
        val interval = prefs.getString("interval", "daily") ?: "daily"
        val weekDay  = prefs.getInt("weekDay", 1)
        val monthDay = prefs.getInt("monthDay", 1)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent  = Intent(context, NotificationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (interval) {
            "weekly" -> {
                var diff = (weekDay - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (diff == 0 && cal.before(Calendar.getInstance())) diff = 7
                cal.add(Calendar.DATE, diff)
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY * 7, pending)
            }
            "monthly" -> {
                cal.set(Calendar.DAY_OF_MONTH, monthDay.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                if (cal.before(Calendar.getInstance())) cal.add(Calendar.MONTH, 1)
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY * 30, pending)
            }
            else -> {
                if (cal.before(Calendar.getInstance())) cal.add(Calendar.DATE, 1)
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
            }
        }
    }
}
