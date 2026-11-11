package com.engliziha.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.engliziha.app.MainActivity
import com.engliziha.app.R
import com.engliziha.app.data.DAILY_HOURS
import java.util.Calendar

/** Three daily reminders, one for each sentence of the day (morning, afternoon, 9 pm). */
object Reminders {
    private const val CHANNEL = "daily"
    private const val PREFS = "settings"
    private const val KEY = "reminders"

    private val TEXT = listOf(
        "🌅 جملة الصباح جاهزة" to "احفظها كتابةً ونطقاً في دقيقتين.",
        "🌤 جملة العصر جاهزة" to "جملة جديدة تنتظرك، ولا تنسَ مراجعة كلماتك.",
        "🌙 جملة المساء واختبار اليوم" to "احفظ الجملة الأخيرة ثم اختبر نفسك في جمل اليوم الثلاث.",
    )

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        schedule(context)
    }

    private fun pending(context: Context, slot: Int): PendingIntent = PendingIntent.getBroadcast(
        context, slot, Intent(context, ReminderReceiver::class.java).putExtra("slot", slot),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets (or cancels, when turned off) the repeating alarms. Safe to call on every start. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        DAILY_HOURS.forEachIndexed { slot, hour ->
            val intent = pending(context, slot)
            alarms.cancel(intent)
            if (!enabled(context)) return@forEachIndexed
            val at = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, at.timeInMillis, AlarmManager.INTERVAL_DAY, intent)
        }
    }

    fun show(context: Context, slot: Int) {
        if (!enabled(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "التذكير اليومي", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val (title, text) = TEXT.getOrElse(slot) { TEXT[0] }
        val open = PendingIntent.getActivity(
            context, 100, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            slot,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(text)
                .setContentIntent(open).setAutoCancel(true)
                .build(),
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.show(context, intent.getIntExtra("slot", 0))
}

/** Alarms are lost when the phone restarts, so set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.schedule(context)
    }
}
