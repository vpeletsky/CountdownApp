package com.countdown.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.*

class AndroidBridge(private val context: Context) {

    @JavascriptInterface
    fun scheduleNotification(jsonParams: String) {
        try {
            val p = JSONObject(jsonParams)
            val hour     = p.getInt("hour")
            val minute   = p.getInt("minute")
            val interval = p.getString("interval")   // daily / weekly / monthly
            val weekDay  = if (p.has("weekDay"))  p.getInt("weekDay")  else 1
            val monthDay = if (p.has("monthDay")) p.getInt("monthDay") else 1
            val title    = if (p.has("title"))    p.getString("title") else "📅 Відлік"
            val body     = if (p.has("body"))     p.getString("body")  else ""

            // Save to prefs
            context.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE).edit()
                .putInt("hour", hour).putInt("minute", minute)
                .putString("interval", interval)
                .putInt("weekDay", weekDay).putInt("monthDay", monthDay)
                .putString("notifTitle", title).putString("notifBody", body)
                .putBoolean("enabled", true)
                .apply()

            scheduleAlarm(hour, minute, interval, weekDay, monthDay)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun sendTestNotification(title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(1, notif)
    }

    @JavascriptInterface
    fun saveTargetDate(day: Int, month: Int, year: Int) {
        context.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE).edit()
            .putInt("target_day", day)
            .putInt("target_month", month)
            .putInt("target_year", year)
            .apply()
        // Reschedule if notifications are enabled
        val prefs = context.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("enabled", false)) {
            scheduleAlarm(
                prefs.getInt("hour", 9),
                prefs.getInt("minute", 0),
                prefs.getString("interval", "daily") ?: "daily",
                prefs.getInt("weekDay", 1),
                prefs.getInt("monthDay", 1)
            )
        }
    }

    private fun scheduleAlarm(hour: Int, minute: Int, interval: String, weekDay: Int, monthDay: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        when (interval) {
            "weekly" -> {
                var diff = (weekDay - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (diff == 0 && cal.before(Calendar.getInstance())) diff = 7
                cal.add(Calendar.DATE, diff)
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                    AlarmManager.INTERVAL_DAY * 7, pending
                )
            }
            "monthly" -> {
                cal.set(Calendar.DAY_OF_MONTH, monthDay.coerceAtMost(
                    cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                ))
                if (cal.before(Calendar.getInstance())) cal.add(Calendar.MONTH, 1)
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                    AlarmManager.INTERVAL_DAY * 30, pending
                )
            }
            else -> { // daily
                if (cal.before(Calendar.getInstance())) cal.add(Calendar.DATE, 1)
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                    AlarmManager.INTERVAL_DAY, pending
                )
            }
        }
    }
}
