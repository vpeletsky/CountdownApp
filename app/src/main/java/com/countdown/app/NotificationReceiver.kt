package com.countdown.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // Читаємо параметри з Intent (передані AndroidBridge)
        val hour     = intent.getIntExtra("hour",     9)
        val minute   = intent.getIntExtra("minute",   0)
        val interval = intent.getStringExtra("interval") ?: "daily"
        val weekDay  = intent.getIntExtra("weekDay",  1)
        val monthDay = intent.getIntExtra("monthDay", 1)
        val title    = intent.getStringExtra("title") ?: "📅 Відлік"
        val body     = intent.getStringExtra("body")  ?: ""

        // Показати сповіщення
        AndroidBridge.showNotification(context, title, body, notifId = 2)

        // Запланувати НАСТУПНЕ спрацювання (setExactAndAllowWhileIdle — одноразовий)
        AndroidBridge(context).scheduleAlarm(
            hour, minute, interval, weekDay, monthDay, title, body
        )
    }
}
