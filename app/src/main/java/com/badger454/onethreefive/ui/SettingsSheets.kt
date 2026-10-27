package com.badger454.onethreefive.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.badger454.onethreefive.AppModel
import com.badger454.onethreefive.BuildConfig
import com.badger454.onethreefive.Tab
import com.badger454.onethreefive.domain.AppState
import com.badger454.onethreefive.domain.Category
import com.badger454.onethreefive.domain.D
import com.badger454.onethreefive.domain.Theme
import com.badger454.onethreefive.notify.Reminders
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll

// ---------- categories ----------
@Composable
fun CategoriesSheet(model: AppModel, close: () -> Unit) {
    val p = LocalPal.current
    @Suppress("UNUSED_VARIABLE") val v = model.version
    val planner = model.planner
    val cats = planner.state.cats
    var picking by remember { mutableStateOf<String?>(null) }
    var armed by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf<Category?>(null) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 56.dp.toPx() }
    val finish = {
        cats.forEach { it.name = it.name.trim().ifEmpty { "Untitled" } }
        model.commit()
        close()
    }
    fun add() {
        if (planner.addCategory(newName) != null) { newName = ""; armed = null; model.commit() }
    }
    AppSheet(finish) {
        SheetTitle("Categories")
        Lead("Rename a category in place. Tap its color dot to choose a color. Drag the handle to set the order they appear in the task menu.", Modifier.padding(top = 4.dp))
        cats.forEachIndexed { i, c ->
            val isDragging = dragIndex == i
            Column(
                Modifier.zIndex(if (isDragging) 1f else 0f).graphicsLayer { if (isDragging) { translationY = dragY; shadowElevation = 12f } }
                    .background(p.surface),
            ) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(32.dp).height(48.dp).pointerInput(c.id) {
                            detectDragGestures(
                                onDragStart = { picking = null; dragIndex = cats.indexOf(c); dragY = 0f },
                                onDragEnd = {
                                    val from = dragIndex
                                    val to = (from + (dragY / rowPx).roundToInt()).coerceIn(0, cats.size - 1)
                                    dragIndex = -1; dragY = 0f
                                    if (from >= 0 && to != from) model.change { moveCategory(from, to) }
                                },
                                onDragCancel = { dragIndex = -1; dragY = 0f },
                                onDrag = { ch, amt -> ch.consume(); dragY += amt.y },
                            )
                        }.semantics { contentDescription = "Drag to reorder ${c.name}" },
                        contentAlignment = Alignment.Center,
                    ) { androidx.compose.material3.Icon(Ic.grip, null, Modifier.size(16.dp), tint = p.muted) }
                    Box(
                        Modifier.size(40.dp).clickable(role = Role.Button) { picking = if (picking == c.id) null else c.id }
                            .semantics { contentDescription = "Change color" },
                        contentAlignment = Alignment.Center,
                    ) { Dot(p.catColor(c), 30.dp) }
                    Spacer(Modifier.width(8.dp))
                    var name by remember(c.id) { mutableStateOf(c.name) }
                    val shape = RoundedCornerShape(9.dp)
                    BasicTextField(
                        value = name,
                        onValueChange = { s -> name = s.replace("\n", "").take(24); c.name = name; model.saveQuietly() },
                        singleLine = true,
                        modifier = Modifier.weight(1f).clip(shape).background(p.bg).border(1.dp, p.line, shape).padding(horizontal = 10.dp, vertical = 11.dp),
                        textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(p.accent),
                    )
                    Box(
                        Modifier.heightIn(min = 40.dp).width(52.dp).clip(RoundedCornerShape(10.dp)).clickable {
                            if (armed != c.id) armed = c.id
                            else { armed = null; picking = null; model.change { deleteCategory(c) } }
                        }.semantics { contentDescription = "Delete category" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (armed == c.id) Text("Sure?", color = p.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        else androidx.compose.material3.Icon(Ic.trash, null, Modifier.size(20.dp), tint = p.danger)
                    }
                }
                if (picking == c.id) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(p.bg).padding(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            for (k in 0..7) {
                                val on = !c.isCustom && c.color == k
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape)
                                        .then(if (on) Modifier.border(2.dp, p.ink, CircleShape) else Modifier)
                                        .padding(if (on) 4.dp else 0.dp).clip(CircleShape).background(p.k[k])
                                        .clickable { model.change { c.color = k; c.hex = null } }
                                        .semantics { contentDescription = "Color ${k + 1}" },
                                )
                            }
                            Box(
                                Modifier.size(28.dp).clip(CircleShape)
                                    .then(if (c.isCustom) Modifier.border(2.dp, p.ink, CircleShape).padding(4.dp).clip(CircleShape) else Modifier)
                                    .background(
                                        if (c.isCustom) SolidColor(p.catColor(c))
                                        else Brush.sweepGradient(listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935)))
                                    )
                                    .clickable { custom = c }
                                    .semantics { contentDescription = "Custom color" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(p.surface), contentAlignment = Alignment.Center) {
                                    Text("+", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = 13.sp)
                                }
                            }
                        }
                        Text("Pick a color, or tap + for any color.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(40.dp))
            val shape = RoundedCornerShape(9.dp)
            BasicTextField(
                value = newName,
                onValueChange = { newName = it.replace("\n", "").take(24) },
                singleLine = true,
                modifier = Modifier.weight(1f).clip(shape).background(p.bg).border(1.dp, p.line, shape).padding(horizontal = 10.dp, vertical = 11.dp),
                textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                decorationBox = { inner ->
                    Box {
                        if (newName.isEmpty()) Text("New category", color = p.muted.copy(alpha = 0.75f), fontSize = 16.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Btn("Add", enabled = newName.isNotBlank()) { add() }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            Btn("Done") { finish() }
        }
    }
    custom?.let { c ->
        CustomColorDialog(p.catColor(c), { custom = null }) { color ->
            model.change { c.hex = color.toHex() }
        }
    }
}

@Composable
private fun CustomColorDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val p = LocalPal.current
    val hsv = remember {
        val a = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.rgb((initial.red * 255).toInt(), (initial.green * 255).toInt(), (initial.blue * 255).toInt()), a)
        a
    }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.35f)) }
    var v by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.45f)) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text("Custom color", fontWeight = FontWeight.Bold, color = p.ink) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(color))
                Text(color.toHex().uppercase(), color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Label("Hue", Modifier.padding(top = 10.dp))
                Slider(h, { h = it }, valueRange = 0f..360f, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
                Label("Saturation")
                Slider(s, { s = it }, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
                Label("Brightness")
                Slider(v, { v = it }, valueRange = 0.15f..1f, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color); onDismiss() }) { Text("Use color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- settings ----------
@Composable
fun SettingsSheet(model: AppModel, close: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = model.version
    val planner = model.planner
    val st = planner.state
    var armed by remember { mutableStateOf<String?>(null) }
    var paste by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(planner.state.toJson().toByteArray(Charsets.UTF_8)) }
                model.say("Backup saved")
            } catch (e: Exception) {
                model.say("Couldn't save the backup file.")
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                AppState.fromJson(text)
                pendingImport = text
            } catch (e: Exception) {
                model.say("That file isn’t a 1-3-5 backup.")
            }
        }
    }

    fun doRestore(json: String) {
        try {
            AppState.fromJson(json)
        } catch (e: Exception) {
            model.say("That text isn’t a valid backup. Paste the full JSON from “Copy data as JSON”.")
            return
        }
        model.change(undoable = true) { this.restore(json); rollover() }
        model.openDay(planner.today())
        close()
        model.say("Backup restored", undo = true)
    }

    AppSheet(close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetTitle("Settings", Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).background(p.bg).clickable(onClick = close).semantics { contentDescription = "Close settings" }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Ic.close, null, Modifier.size(22.dp), tint = p.ink)
            }
        }

        Group("Appearance") {
            val themes = Theme.entries
            for (rowStart in themes.indices step 3) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in rowStart until rowStart + 3) {
                        if (i < themes.size) ThemeTile(themes[i], st.theme == themes[i], Modifier.weight(1f)) { model.change { state.theme = themes[i] } }
                        else Spacer(Modifier.weight(1f))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable { model.change { state.autoDark = !state.autoDark } }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Use Midnight in dark mode", fontWeight = FontWeight.Bold, color = p.ink)
                    Text("Switches automatically when your phone is in dark mode", fontSize = 12.sp, color = p.muted)
                }
                androidx.compose.material3.Switch(checked = st.autoDark, onCheckedChange = { on -> model.change { state.autoDark = on } })
            }
        }

        Group("Reminders") {
            val notif = Reminders.canPostNotifications(ctx)
            val exact = Reminders.canScheduleExact(ctx)
            Text(
                when {
                    notif && exact -> "Notifications and exact alarms are allowed."
                    !notif -> "Notifications are off for this app, so reminders can't alert you."
                    else -> "Exact alarms are off, so reminders may arrive a few minutes late."
                },
                fontSize = 14.sp, color = p.muted,
            )
            if (!notif || !exact) {
                Spacer(Modifier.height(8.dp))
                Btn(if (!notif) "Open notification settings" else "Allow exact alarms", BtnKind.GHOST) {
                    try {
                        val i = if (!notif) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        else Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                        ctx.startActivity(i)
                    } catch (_: Exception) {}
                }
            }
        }

        Group("Updates") { UpdateSection(model) }

        Group("Simulated date") {
            val off = st.offset
            Text(
                D.fmtShort(planner.today()) + if (off != 0) " (${if (off > 0) "+" else ""}$off day${if (kotlin.math.abs(off) == 1) "" else "s"})" else "",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.ink,
            )
            Text("Jump the clock to test the midnight rollover and repeating tasks.", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("− 1 day", BtnKind.GHOST, Modifier.weight(1f)) { model.change { state.offset -= 1; rollover() }; model.openDay(planner.today()) }
                Btn("+ 1 day", BtnKind.PRIMARY, Modifier.weight(1f)) { model.change { state.offset += 1; rollover() }; model.openDay(planner.today()) }
                if (off != 0) Btn("Real date", BtnKind.GHOST, Modifier.weight(1f)) { model.change { state.offset = 0; rollover() }; model.openDay(planner.today()) }
            }
        }

        Group("Back up") {
            Text("Save everything as a JSON file you keep yourself. The HTML prototype's backups work here too.", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Save backup file", BtnKind.GHOST, Modifier.weight(1f)) { saveFile.launch("1-3-5-backup-${planner.today()}.json") }
                Btn("Copy as JSON", BtnKind.GHOST, Modifier.weight(1f)) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("1-3-5 backup", planner.state.toJson()))
                    model.say("Copied backup to the clipboard")
                }
            }
        }

        Group("Restore") {
            Text("Replace everything with a backup, from a file or pasted JSON.", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(bottom = 8.dp))
            Btn("Open backup file", BtnKind.GHOST, Modifier.fillMaxWidth()) { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            Spacer(Modifier.height(10.dp))
            val shape = RoundedCornerShape(9.dp)
            BasicTextField(
                value = paste,
                onValueChange = { paste = it; armed = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape).background(p.bg).border(1.dp, p.line, shape).padding(10.dp),
                textStyle = TextStyle(color = p.ink, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                cursorBrush = SolidColor(p.accent),
                maxLines = 6,
                decorationBox = { inner ->
                    Box {
                        if (paste.isEmpty()) Text("Paste backup JSON", color = p.muted.copy(alpha = 0.75f), fontSize = 13.sp)
                        inner()
                    }
                },
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Btn(if (armed == "paste") "Tap again to replace" else "Replace with pasted data", if (armed == "paste") BtnKind.DANGER else BtnKind.GHOST, enabled = paste.isNotBlank()) {
                    if (armed != "paste") armed = "paste" else { armed = null; doRestore(paste.trim()) }
                }
            }
        }

        Group("Categories") {
            Text(st.cats.joinToString(" · ") { it.name }, fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(bottom = 8.dp))
            Btn("Edit categories", BtnKind.GHOST) { model.sheet = com.badger454.onethreefive.Sheet.Categories(back = com.badger454.onethreefive.Sheet.Settings) }
        }

        Group("Start over") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (armed == "seed") "Tap again to confirm" else "Reload examples", if (armed == "seed") BtnKind.DANGER else BtnKind.GHOST, Modifier.weight(1f)) {
                    if (armed != "seed") armed = "seed" else {
                        armed = null
                        model.change(undoable = true) { seedExamples() }
                        model.openDay(planner.today()); model.tab = Tab.DAY; close()
                        model.say("Examples reloaded", undo = true)
                    }
                }
                Btn(if (armed == "wipe") "Tap again to erase" else "Erase everything", if (armed == "wipe") BtnKind.DANGER else BtnKind.DANGER_GHOST, Modifier.weight(1f)) {
                    if (armed != "wipe") armed = "wipe" else {
                        armed = null
                        model.change(undoable = true) { eraseAll() }
                        model.openDay(planner.today()); model.tab = Tab.DAY; close()
                        model.say("All tasks erased", undo = true)
                    }
                }
            }
        }

        Text(
            "1-3-5 To-Do ${BuildConfig.VERSION_NAME} · your tasks stay on this phone",
            fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 18.dp),
        )
    }

    pendingImport?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = p.surface,
            title = { Text("Replace everything?", fontWeight = FontWeight.Bold, color = p.ink) },
            text = { Text("Your current tasks, projects and categories will be replaced with the backup. You can undo right after.", color = p.muted) },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { pendingImport = null; doRestore(json) }) { Text("Replace") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Divider()
        Label(title, Modifier.padding(top = 14.dp, bottom = 4.dp))
        content()
    }
}

@Composable
private fun ThemeTile(t: Theme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val (bg, hdr, card, acc) = previewOf(t)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).border(2.dp, if (selected) p.accent else p.line, shape).clickable(role = Role.RadioButton, onClick = onClick).padding(6.dp)
            .semantics { contentDescription = t.label + if (selected) ", selected" else "" },
    ) {
        Column(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(9.dp)).background(bg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(bottomStart = 7.dp, bottomEnd = 7.dp)).background(hdr))
            for (i in 0..2) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(card).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).border(1.5.dp, acc, RoundedCornerShape(2.dp)).background(if (i == 1) acc else Color.Transparent))
                }
            }
        }
        Text(t.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(top = 6.dp))
    }
}

@Suppress("unused")
private fun Modifier.square() = this.aspectRatio(1f)

/** Settings → Updates: check GitHub for a newer release, show what's new, download and install it. */
@Composable
private fun UpdateSection(model: AppModel) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<com.badger454.onethreefive.update.Updater.Release?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    Text("You have version ${BuildConfig.VERSION_NAME}.", fontSize = 14.sp, color = p.muted)
    if (status != null) Text(status!!, fontSize = 14.sp, color = p.ink, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(8.dp))
    Btn(if (busy) "Checking…" else "Check for updates", BtnKind.GHOST, enabled = !busy) {
        busy = true
        status = null
        scope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.badger454.onethreefive.update.Updater.check() }
            busy = false
            when (r) {
                is com.badger454.onethreefive.update.Updater.Check.Available -> found = r.release
                com.badger454.onethreefive.update.Updater.Check.UpToDate -> status = "You're up to date."
                is com.badger454.onethreefive.update.Updater.Check.Failed -> status = r.message
            }
        }
    }
    val rel = found
    if (rel != null) {
        AlertDialog(
            onDismissRequest = { if (progress == null) found = null },
            containerColor = p.surface,
            title = { Text("Version ${rel.version} is available", fontWeight = FontWeight.Bold, color = p.ink) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text("What's new", fontWeight = FontWeight.Bold, color = p.ink, fontSize = 14.sp)
                    Text(rel.notes.ifBlank { "No notes for this version." }, color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    val pr = progress
                    if (pr != null) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { pr }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), color = p.accent, trackColor = p.line,
                        )
                        Text("Downloading…", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { found = null; progress = null }, enabled = progress == null) { Text("Later") } },
            confirmButton = {
                TextButton(enabled = progress == null, onClick = {
                    if (!com.badger454.onethreefive.update.Updater.canInstall(ctx)) {
                        model.say("Allow \u201cInstall unknown apps\u201d for 1-3-5, then come back and tap Install again.")
                        com.badger454.onethreefive.update.Updater.openInstallPermission(ctx)
                        return@TextButton
                    }
                    progress = 0f
                    scope.launch {
                        try {
                            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.badger454.onethreefive.update.Updater.download(ctx, rel) { f -> progress = f }
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.badger454.onethreefive.update.Updater.install(ctx, file) }
                            found = null
                        } catch (e: Exception) {
                            status = "The download didn't finish. Try again."
                            found = null
                        } finally {
                            progress = null
                        }
                    }
                }) { Text("Install") }
            },
        )
    }
}
