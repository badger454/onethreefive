package com.badger454.onethreefive.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.badger454.onethreefive.App
import com.badger454.onethreefive.MainActivity
import com.badger454.onethreefive.R
import com.badger454.onethreefive.domain.Planner
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Morning and evening nudges: one alarm each, rescheduled for the next day every time one fires. */
object Nudges {
    const val CHANNEL = "nudges"
    private const val ACTION = "com.badger454.onethreefive.NUDGE"
    private const val EXTRA_KIND = "kind"
    private const val AM = "am"
    private const val PM = "pm"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Daily nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Morning plan and evening check-in"
                }
            )
        }
    }

    private fun pending(ctx: Context, kind: String): PendingIntent {
        val i = Intent(ctx, NudgeReceiver::class.java).setAction("$ACTION.$kind").putExtra(EXTRA_KIND, kind)
        return PendingIntent.getBroadcast(ctx, if (kind == AM) 7001 else 7002, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun parse(hhmm: String, def: LocalTime): LocalTime = try { LocalTime.parse(hhmm) } catch (e: Exception) { def }

    /** Next real-clock occurrence of [time] after now. */
    private fun nextAt(time: LocalTime): Long {
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(time)
        if (!at.isAfter(now.plusSeconds(30))) at = at.plusDays(1)
        return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** Sets (or clears) both alarms from the current settings. Safe to call often. */
    fun schedule(ctx: Context, planner: Planner) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val s = planner.state
        val am1 = pending(ctx, AM)
        val pm1 = pending(ctx, PM)
        if (!s.nudges) {
            am.cancel(am1)
            am.cancel(pm1)
            return
        }
        set(am, nextAt(parse(s.nudgeAm, LocalTime.of(7, 0))), am1)
        set(am, nextAt(parse(s.nudgePm, LocalTime.of(20, 0))), pm1)
    }

    private fun set(am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun fire(ctx: Context, kind: String) {
        val model = App.model(ctx)
        model.rollover()
        val p = model.planner
        val msg = if (kind == AM) p.morningNudge() else p.eveningNudge()
        if (msg != null && p.state.nudges && Reminders.canPostNotifications(ctx)) {
            val open = Intent(ctx, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Reminders.EXTRA_DATE, p.today())
            val openPi = PendingIntent.getActivity(ctx, 7010, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(msg.first)
                .setContentText(msg.second)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg.second))
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(if (kind == AM) 7001 else 7002, n)
            } catch (_: SecurityException) {}
        }
        schedule(ctx, p)
    }
}

class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Nudges.fire(context, intent.getStringExtra("kind") ?: return)
    }
}
