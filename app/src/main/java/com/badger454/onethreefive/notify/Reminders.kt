package com.badger454.onethreefive.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.badger454.onethreefive.App
import com.badger454.onethreefive.MainActivity
import com.badger454.onethreefive.R
import com.badger454.onethreefive.domain.D
import com.badger454.onethreefive.domain.Planner
import java.time.ZoneId

/** Task reminders: one exact alarm per task, rescheduled from the data after every change. */
object Reminders {
    const val CHANNEL = "reminders"
    const val ACTION_FIRE = "com.badger454.onethreefive.REMIND"
    const val ACTION_DONE = "com.badger454.onethreefive.COMPLETE"
    const val ACTION_SNOOZE = "com.badger454.onethreefive.SNOOZE"
    const val EXTRA_ID = "taskId"
    const val EXTRA_DATE = "openDate"
    private const val PREFS = "alarms"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders you set on tasks"
                }
            )
        }
    }

    fun canPostNotifications(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(ctx: Context): Boolean = ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun intentFor(ctx: Context, action: String, id: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("onethreefive://task/" + Uri.encode(id)))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Brings the alarms in line with the data: schedules upcoming reminders, cancels removed ones. */
    fun sync(ctx: Context, planner: Planner) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        val zone = ZoneId.systemDefault()
        val offsetDays = planner.state.offset.toLong()
        val want = HashMap<String, Long>()
        for (t in planner.state.tasks) {
            if (t.done || t.fired) continue
            val at = t.reminderAt?.let { D.parseDT(it) } ?: continue
            // The simulated date (Settings) shifts "now"; shift the alarm back into real time.
            val real = at.minusDays(offsetDays).atZone(zone).toInstant().toEpochMilli()
            want[t.id] = real
        }
        for (id in old) if (id !in want) am.cancel(intentFor(ctx, ACTION_FIRE, id))
        val exact = am.canScheduleExactAlarms()
        val now = System.currentTimeMillis()
        for ((id, at) in want) {
            val pi = intentFor(ctx, ACTION_FIRE, id)
            val time = maxOf(at, now + 1000)
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            }
        }
        prefs.edit().putStringSet("ids", HashSet(want.keys)).apply()
    }

    fun show(ctx: Context, id: String, title: String, date: String?) {
        if (!canPostNotifications(ctx)) return
        val open = Intent(ctx, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .setData(Uri.parse("onethreefive://open/" + Uri.encode(id)))
            .putExtra(EXTRA_DATE, date)
        val openPi = PendingIntent.getActivity(ctx, id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title.ifBlank { "Untitled task" })
            .setContentText("Reminder")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, "Complete", intentFor(ctx, ACTION_DONE, id))
            .addAction(0, "Snooze 15 min", intentFor(ctx, ACTION_SNOOZE, id))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id.hashCode(), n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    fun dismiss(ctx: Context, id: String) {
        NotificationManagerCompat.from(ctx).cancel(id.hashCode())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
        val model = App.model(context)
        val p = model.planner
        val t = p.find(id)
        when (intent.action) {
            Reminders.ACTION_FIRE -> {
                if (t == null || t.done || t.reminderAt == null) return
                t.fired = true
                model.commitNow()
                Reminders.show(context, id, t.title, t.date)
            }
            Reminders.ACTION_DONE -> {
                Reminders.dismiss(context, id)
                if (t == null) return
                t.done = true
                t.completedAt = D.localDT(p.now())
                t.reminderAt = null
                model.commitNow()
            }
            Reminders.ACTION_SNOOZE -> {
                Reminders.dismiss(context, id)
                if (t == null) return
                t.reminderAt = D.localDT(p.now().plusMinutes(15))
                t.fired = false
                model.commitNow()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val model = App.model(context)
        model.rollover()
        Reminders.sync(context, model.planner)
        Nudges.schedule(context, model.planner)
        try { com.badger454.onethreefive.widget.DotsWidget.refresh(context) } catch (_: Exception) {}
    }
}
