package com.badger454.onethreefive.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Bundle
import android.widget.RemoteViews
import com.badger454.onethreefive.App
import com.badger454.onethreefive.MainActivity
import com.badger454.onethreefive.R
import com.badger454.onethreefive.domain.Planner
import com.badger454.onethreefive.domain.Section
import java.time.LocalDate
import java.time.ZoneId

/**
 * Home-screen widget that looks like the app icon: 1 big, 3 medium and 5 small white dots on blue.
 * A dot is a ring while its task is open, filled once it's checked off, and dashed when the slot is empty.
 */
class DotsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) draw(context, manager, id)
        scheduleMidnight(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        draw(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MIDNIGHT) {
            App.model(context).rollover()
            refresh(context)
            scheduleMidnight(context)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(midnightIntent(context))
    }

    companion object {
        private const val ACTION_MIDNIGHT = "com.badger454.onethreefive.WIDGET_MIDNIGHT"
        private const val ICON_BLUE = 0xFF1C5D85.toInt()

        /** Redraws every placed widget. Called after each change in the app. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DotsWidget::class.java))
            for (id in ids) draw(context, manager, id)
        }

        private fun midnightIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 7100, Intent(context, DotsWidget::class.java).setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** The day rolls over at midnight even if the app isn't opened; refresh the dots shortly after. */
        private fun scheduleMidnight(context: Context) {
            val at = LocalDate.now().plusDays(1).atTime(0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC, at, midnightIntent(context))
        }

        private fun draw(context: Context, manager: AppWidgetManager, id: Int) {
            val opts = manager.getAppWidgetOptions(id)
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).takeIf { it > 0 } ?: 110
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110).takeIf { it > 0 } ?: 110
            val density = context.resources.displayMetrics.density
            val sidePx = (minOf(wDp, hDp) * density).toInt().coerceIn(96, 720)
            val bmp = render(App.model(context).planner, sidePx)
            val views = RemoteViews(context.packageName, R.layout.widget_dots)
            views.setImageViewBitmap(R.id.dots, bmp)
            views.setContentDescription(R.id.dots, describe(App.model(context).planner))
            val open = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("openDate", App.model(context).planner.today())
            views.setOnClickPendingIntent(
                R.id.dots,
                PendingIntent.getActivity(context, 7101, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            manager.updateAppWidget(id, views)
        }

        private fun describe(p: Planner): String {
            val t = p.today()
            val all = Section.entries.flatMap { s -> p.dayList(t, s).take(s.max) }
            return "1-3-5 today: ${all.count { it.done }} of ${all.size} done"
        }

        /** Same geometry as the launcher icon (108-unit grid, visible circle of 72 units centred at 54). */
        fun render(p: Planner, side: Int): Bitmap {
            val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val k = side / 72f
            fun x(u: Float) = (u - 18f) * k
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ICON_BLUE }
            c.drawCircle(side / 2f, side / 2f, side / 2f, bg)
            val today = p.today()
            val rows = listOf(
                Triple(Section.BIG, 40f, listOf(54f) to 7.5f),
                Triple(Section.MEDIUM, 56f, listOf(40f, 54f, 68f) to 5f),
                Triple(Section.SMALL, 70f, listOf(34f, 44f, 54f, 64f, 74f) to 3.5f),
            )
            for ((sec, cy, spec) in rows) {
                val (xs, r) = spec
                val tasks = p.dayList(today, sec)
                val stroke = (r * 0.3f).coerceIn(1.1f, 1.8f) * k
                xs.forEachIndexed { i, cx ->
                    val t = tasks.getOrNull(i)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
                    val radius = r * k
                    when {
                        t == null -> {
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = stroke
                            paint.alpha = 170
                            val circ = (2 * Math.PI * (radius - stroke / 2)).toFloat()
                            val dashes = if (sec == Section.SMALL) 6 else 8
                            val seg = circ / (dashes * 2)
                            paint.pathEffect = DashPathEffect(floatArrayOf(seg, seg), 0f)
                            c.drawCircle(x(cx), x(cy), radius - stroke / 2, paint)
                        }
                        t.done -> c.drawCircle(x(cx), x(cy), radius, paint)
                        else -> {
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = stroke
                            c.drawCircle(x(cx), x(cy), radius - stroke / 2, paint)
                        }
                    }
                }
            }
            return bmp
        }
    }
}
