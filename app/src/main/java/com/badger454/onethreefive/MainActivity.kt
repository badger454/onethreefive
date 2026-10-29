package com.badger454.onethreefive

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.badger454.onethreefive.notify.Reminders
import com.badger454.onethreefive.ui.AppTheme
import com.badger454.onethreefive.ui.BucketScreen
import com.badger454.onethreefive.ui.DayScreen
import com.badger454.onethreefive.ui.Ic
import com.badger454.onethreefive.ui.LocalPal
import com.badger454.onethreefive.ui.ProjectsScreen
import com.badger454.onethreefive.ui.SheetHost
import com.badger454.onethreefive.ui.palFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val model: AppModel get() = App.model(this)

    /** The date rolls over while the app is open: run the midnight reset right away. */
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            model.rollover()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { Root(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            // Share → 1-3-5: offer to add the shared text as a task.
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val combined = listOf(subject, text).filter { it.isNotEmpty() }.distinct().joinToString("\n")
            if (combined.isNotEmpty()) model.sheet = Sheet.ShareAdd(combined)
            return
        }
        val date = intent?.getStringExtra(Reminders.EXTRA_DATE) ?: return
        model.openDay(date)
    }

    /** Nudges are on by default, so ask for notification permission once (Android 13+ requires it). */
    private val notifPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private fun askForNotificationsOnce() {
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getBoolean("askedNotifications", false)) return
        if (!model.planner.state.nudges || Reminders.canPostNotifications(this)) return
        prefs.edit().putBoolean("askedNotifications", true).apply()
        notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        model.rollover()
        com.badger454.onethreefive.notify.Nudges.schedule(this, model.planner)
        askForNotificationsOnce()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, clockReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(clockReceiver) } catch (_: Exception) {}
    }
}

@Composable
fun Root(model: AppModel) {
    val systemDark = isSystemInDarkTheme()
    @Suppress("UNUSED_VARIABLE") val v = model.version
    val st = model.planner.state
    val pal = palFor(st.theme, st.autoDark, systemDark)
    AppTheme(pal) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = pal.hdr.luminance() > 0.5f
                c.isAppearanceLightNavigationBars = pal.nav.luminance() > 0.5f
            }
        }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            model.messages.collectLatest { m ->
                snack.currentSnackbarData?.dismiss()
                val r = snack.showSnackbar(m.text, actionLabel = if (m.undo) "Undo" else null, duration = if (m.undo) SnackbarDuration.Long else SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) model.undo()
            }
        }
        // Keep the midnight reset and reminders current while the app stays open.
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                model.rollover()
            }
        }
        BackHandler(enabled = model.tab != Tab.DAY) { model.tab = Tab.DAY }
        Scaffold(
            containerColor = pal.bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { NavBar(model) },
            snackbarHost = {
                SnackbarHost(snack) { data ->
                    Snackbar(data, containerColor = pal.ink, contentColor = pal.bg, actionColor = pal.bg, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()) {
                when (model.tab) {
                    Tab.DAY -> DayScreen(model)
                    Tab.BUCKET -> BucketScreen(model)
                    Tab.PROJECTS -> ProjectsScreen(model)
                }
            }
        }
        SheetHost(model)
    }
}

@Composable
private fun NavBar(model: AppModel) {
    val p = LocalPal.current
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = p.navInk, selectedTextColor = p.navInk, indicatorColor = p.navInd,
        unselectedIconColor = p.muted, unselectedTextColor = p.muted,
    )
    val open = model.planner.bucketList().count { !it.done }
    NavigationBar(containerColor = p.nav, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = model.tab == Tab.DAY,
            onClick = {
                if (model.tab == Tab.DAY) model.viewDate = model.planner.today()
                model.tab = Tab.DAY
            },
            icon = { Icon(Ic.day, null, Modifier.size(22.dp)) },
            label = { Text("Day", fontWeight = if (model.tab == Tab.DAY) FontWeight.Bold else FontWeight.Normal) },
            colors = colors,
        )
        NavigationBarItem(
            selected = model.tab == Tab.BUCKET,
            onClick = { model.tab = Tab.BUCKET },
            icon = {
                BadgedBox(badge = { if (open > 0) Badge(containerColor = p.accent, contentColor = p.accentInk) { Text(open.toString()) } }) {
                    Icon(Ic.bucket, null, Modifier.size(22.dp))
                }
            },
            label = { Text("Bucket", fontWeight = if (model.tab == Tab.BUCKET) FontWeight.Bold else FontWeight.Normal) },
            colors = colors,
        )
        NavigationBarItem(
            selected = model.tab == Tab.PROJECTS,
            onClick = { model.tab = Tab.PROJECTS },
            icon = { Icon(Ic.folder, null, Modifier.size(22.dp)) },
            label = { Text("Projects", fontWeight = if (model.tab == Tab.PROJECTS) FontWeight.Bold else FontWeight.Normal) },
            colors = colors,
        )
        NavigationBarItem(
            selected = false,
            onClick = { model.sheet = Sheet.Settings },
            icon = { Icon(Ic.sliders, null, Modifier.size(22.dp)) },
            label = { Text("Settings") },
            colors = colors,
        )
    }
}
