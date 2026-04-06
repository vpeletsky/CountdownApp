package com.countdown.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // Читаємо параметри з Intent (передані AndroidBridge)
        val hour     = intent.getIntExtra("hour",     9)
        val minute   = intent.getIntExtra("minute",   0)
        val interval = intent.getStringExtra("interval") ?: "daily"
        val weekDay  = intent.getIntExtra("weekDay",  1)
        val monthDay = intent.getIntExtra("monthDay", 1)

        // Читаємо target дату з SharedPreferences
        val prefs = context.getSharedPreferences(AndroidBridge.PREFS, Context.MODE_PRIVATE)
        var targetDay   = prefs.getInt("target_day",   0)
        var targetMonth = prefs.getInt("target_month", 0)
        var targetYear  = prefs.getInt("target_year",  0)

        // РЕЗЕРВНИЙ ШЛЯХ: якщо SharedPrefs порожній — беремо з Intent extras
        // (AndroidBridge.scheduleAlarm вбудовує туди дату при кожному плануванні)
        if (targetYear == 0) {
            targetDay   = intent.getIntExtra("target_day",   0)
            targetMonth = intent.getIntExtra("target_month", 0)
            targetYear  = intent.getIntExtra("target_year",  0)
            // Відновлюємо SharedPreferences, якщо дані є в Intent
            if (targetYear > 0) {
                prefs.edit()
                    .putInt("target_day",   targetDay)
                    .putInt("target_month", targetMonth)
                    .putInt("target_year",  targetYear)
                    .apply()
            }
        }

        val (title, body) = if (targetYear > 0) {
            // Розрахунок кількості днів
            val target = Calendar.getInstance().apply {
                set(targetYear, targetMonth - 1, targetDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val today = Calendar.getInstance() // поточний момент, як у JS: Math.floor((target - now) / 864e5)
            val diffMs   = target.timeInMillis - today.timeInMillis
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)

            val titleStr = "📅 До $targetDay.${String.format("%02d", targetMonth)}.$targetYear"
            val bodyStr  = when {
                diffDays > 0  -> "Залишилось $diffDays ${daysWord(diffDays)}"
                diffDays == 0L -> "Сьогодні!"
                else          -> "Минуло ${-diffDays} ${daysWord(-diffDays)}"
            }
            Pair(titleStr, bodyStr)
        } else {
            // Крайній fallback: якщо дата взагалі невідома
            val fbTitle = intent.getStringExtra("title")
                ?: prefs.getString("notifTitle", null)
                ?: "📅 Відлік"
            val fbBody  = intent.getStringExtra("body")
                ?.takeIf { it.isNotBlank() }
                ?: prefs.getString("notifBody", null)
                    ?.takeIf { it.isNotBlank() }
                ?: "Залишилось рахувати"
            Pair(fbTitle, fbBody)
        }

        // Показати сповіщення
        AndroidBridge.showNotification(context, title, body, notifId = 2)

        // Запланувати НАСТУПНЕ спрацювання (setExactAndAllowWhileIdle — одноразовий)
        AndroidBridge(context).scheduleAlarm(
            hour, minute, interval, weekDay, monthDay, title, body
        )
    }

    private fun daysWord(days: Long): String {
        val n = Math.abs(days) % 100
        val n1 = n % 10
        return when {
            n in 11..19       -> "днів"
            n1 == 1L          -> "день"
            n1 in 2..4        -> "дні"
            else              -> "днів"
        }
    }
}
