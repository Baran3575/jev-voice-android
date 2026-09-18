package com.baran.jevvoice

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

object DeviceActions {
    const val CHANNEL_ID = "jev_voice"
    private const val PREFS_NOTES = "jev_notes"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Jev Voice", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }

    fun saveNote(context: Context, note: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NOTES, Context.MODE_PRIVATE)
        val nextId = prefs.getInt("next_id", 1)
        prefs.edit().putString("note_$nextId", note).putInt("next_id", nextId + 1).apply()
        notify(context, nextId, "Not kaydedildi", note)
        return nextId
    }

    fun scheduleReminder(context: Context, text: String, atMillis: Long) {
        ensureChannel(context)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("text", text)
        }
        val pi = PendingIntent.getBroadcast(
            context, text.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun dialIntent(phone: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))

    fun smsIntent(phone: String?, body: String): Intent {
        val uri = if (phone != null) Uri.parse("smsto:${phone.filter { it.isDigit() || it == '+' }}")
        else Uri.parse("smsto:")
        return Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", body)
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
