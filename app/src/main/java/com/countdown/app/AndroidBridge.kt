package com.countdown.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.*

class AndroidBridge(private val context: Context) {

    // ── JS → scheduleNotification ─────────────────────────────────────────────
    @JavascriptInterface
    fun scheduleNotification(jsonParams: String) {
        try {
            val p        = JSONObject(jsonParams)
            val hour     = p.getInt("hour")
            val minute   = p.getInt("minute")
            val interval = p.getString("interval")          // daily / weekly / monthly
            val weekDay  = if (p.has("weekDay"))  p.getInt("weekDay")  else 1
            val monthDay = if (p.has("monthDay")) p.getInt("monthDay") else 1
            val title    = if (p.has("title"))    p.getString("title") else "📅 Відлік"
            val body     = if (p.has("body"))     p.getString("body")  else ""

            // Зберегти в SharedPreferences (для відновлення після перезавантаження)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("hour",      hour)
                .putInt("minute",    minute)
                .putString("interval",  interval)
                .putInt("weekDay",   weekDay)
                .putInt("monthDay",  monthDay)
                .putString("notifTitle", title)
                .putString("notifBody",  body)
                .putBoolean("enabled",   true)
                .apply()

            scheduleAlarm(hour, minute, interval, weekDay, monthDay, title, body)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── JS → sendTestNotification ─────────────────────────────────────────────
    @JavascriptInterface
    fun sendTestNotification(title: String, body: String) {
        showNotification(context, title, body, notifId = 1)
    }

    // ── JS → cancelNotification ───────────────────────────────────────────────
    @JavascriptInterface
    fun cancelNotification() {
        // Вимкнути в SharedPreferences
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", false)
            .apply()

        // Скасувати будильник
        val intent = Intent(context, NotificationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pending)
    }

    // ── JS → saveTargetDate ───────────────────────────────────────────────────
    @JavascriptInterface
    fun saveTargetDate(day: Int, month: Int, year: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("target_day",   day)
            .putInt("target_month", month)
            .putInt("target_year",  year)
            .apply()

        // Якщо нагадування активні — перепланувати з оновленим текстом
        if (prefs.getBoolean("enabled", false)) {
            scheduleAlarm(
                hour     = prefs.getInt("hour",     9),
                minute   = prefs.getInt("minute",   0),
                interval = prefs.getString("interval", "daily") ?: "daily",
                weekDay  = prefs.getInt("weekDay",  1),
                monthDay = prefs.getInt("monthDay", 1),
                title    = prefs.getString("notifTitle", "📅 Відлік") ?: "📅 Відлік",
                body     = prefs.getString("notifBody",  "") ?: ""
            )
        }
    }

    // ── Core: schedule exact alarm ────────────────────────────────────────────
    fun scheduleAlarm(
        hour: Int, minute: Int, interval: String,
        weekDay: Int, monthDay: Int,
        title: String, body: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // На Android 12+ перевірити дозвіл на точні будильники
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()) {
            // Без дозволу — нічого не робимо; дозвіл запитується в MainActivity
            return
        }

        // Intent з усіма параметрами для самоперепланування в Receiver
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("hour",     hour)
            putExtra("minute",   minute)
            putExtra("interval", interval)
            putExtra("weekDay",  weekDay)
            putExtra("monthDay", monthDay)
            putExtra("title",    title)
            putExtra("body",     body)
        }
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fireAt = nextFireTime(hour, minute, interval, weekDay, monthDay)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            fireAt,
            pending
        )
    }

    companion object {
        const val PREFS        = "countdown_prefs"
        const val REQUEST_CODE = 42
        const val CHANNEL_ID   = MainActivity.CHANNEL_ID

        // ── Розрахунок часу наступного спрацювання ────────────────────────────
        fun nextFireTime(
            hour: Int, minute: Int, interval: String,
            weekDay: Int, monthDay: Int
        ): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE,      minute)
            cal.set(Calendar.SECOND,      0)
            cal.set(Calendar.MILLISECOND, 0)

            val now = System.currentTimeMillis()

            when (interval) {
                "weekly" -> {
                    // weekDay: 1=Пн … 7=Нд (JS-формат), Calendar: 1=Нд, 2=Пн … 7=Сб
                    val calDay = if (weekDay == 0) Calendar.SUNDAY
                                 else weekDay + 1   // 1(Пн)→2, …, 6(Сб)→7
                    var diff = (calDay - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (diff == 0 && cal.timeInMillis <= now) diff = 7
                    cal.add(Calendar.DATE, diff)
                }
                "monthly" -> {
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, monthDay.coerceIn(1, maxDay))
                    if (cal.timeInMillis <= now) {
                        cal.add(Calendar.MONTH, 1)
                        val maxDay2 = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        cal.set(Calendar.DAY_OF_MONTH, monthDay.coerceIn(1, maxDay2))
                    }
                }
                else -> { // daily
                    if (cal.timeInMillis <= now) cal.add(Calendar.DATE, 1)
                }
            }

            return cal.timeInMillis
        }

        // ── Показати сповіщення ───────────────────────────────────────────────
        fun showNotification(context: Context, title: String, body: String, notifId: Int = 2) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager.notify(notifId, notif)
        }
    }
}
