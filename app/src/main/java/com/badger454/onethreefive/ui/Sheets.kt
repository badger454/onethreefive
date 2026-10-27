package com.badger454.onethreefive.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badger454.onethreefive.AppModel
import com.badger454.onethreefive.Sheet
import com.badger454.onethreefive.domain.D
import com.badger454.onethreefive.domain.Planner
import com.badger454.onethreefive.domain.RepeatType
import com.badger454.onethreefive.domain.Rule
import com.badger454.onethreefive.domain.Section
import com.badger454.onethreefive.notify.Reminders
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

fun trunc(s: String, n: Int) = if (s.length > n) s.take(n - 1) + "…" else s

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.surface,
        contentColor = p.ink,
        scrimColor = Color.Black.copy(alpha = 0.42f),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            content = content,
        )
    }
}

/** Routes model.sheet to the right sheet or dialog. */
@Composable
fun SheetHost(model: AppModel) {
    @Suppress("UNUSED_VARIABLE") val v = model.version
    val close = { model.sheet = null }
    when (val s = model.sheet) {
        null -> {}
        is Sheet.Dest -> DestSheet(model, s, close)
        is Sheet.TodayPick -> TodayPickSheet(model, s.taskId, close)
        is Sheet.PickProject -> PickProjectSheet(model, s.taskId, close)
        is Sheet.Remind -> RemindSheet(model, s.taskId, close)
        is Sheet.Repeat -> RepeatSheet(model, s.taskId, close)
        is Sheet.Notes -> NotesSheet(model, s.taskId, close)
        is Sheet.EditScope -> EditScopeDialog(model, s, close)
        is Sheet.DeleteScope -> DeleteScopeDialog(model, s.taskId, close)
        is Sheet.CarriedDone -> CarriedDoneDialog(model, s, close)
        is Sheet.Categories -> CategoriesSheet(model) { model.sheet = s.back }
        is Sheet.ProjectMenu -> ProjectMenuSheet(model, s.projectId, close)
        Sheet.Settings -> SettingsSheet(model, close)
    }
}

// ---------- date & time pickers ----------
private fun isoToMillis(iso: String): Long = D.parse(iso).toEpochDay() * 86_400_000L
private fun millisToIso(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: String, minIso: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val minMs = minIso?.let { isoToMillis(it) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = isoToMillis(initial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = minMs == null || utcTimeMillis >= minMs
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(millisToIso(it)) }; onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

// ---------- move / copy / schedule ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DestSheet(model: AppModel, s: Sheet.Dest, close: () -> Unit) {
    val planner = model.planner
    val t = planner.find(s.taskId) ?: return close()
    val today = planner.today()
    val fromBucket = t.date == null
    val pj = if (!s.copy && t.proj != null) planner.proj(t.proj) else null
    var tab by remember { mutableIntStateOf(0) }
    var date by remember {
        mutableStateOf(
            when {
                s.copy && t.date != null && t.date!! >= today -> t.date!!
                t.date != null && t.date!! > today -> D.addDays(t.date!!, 1)
                else -> D.addDays(today, if (fromBucket) 0 else 1)
            }
        )
    }
    var picker by remember { mutableStateOf(false) }
    val mode = if (s.copy) Planner.Mode.COPY else Planner.Mode.MOVE
    fun apply(d: String?, sec: Section?) {
        close()
        var o: Planner.Outcome = Planner.Outcome.None
        model.change(undoable = true) { o = applyDest(t, mode, d, sec) }
        model.outcome(o)
    }
    AppSheet(close) {
        SheetTitle(
            when {
                s.copy -> "Copy to…"
                s.project -> if (fromBucket) "Schedule action" else "Reschedule action"
                else -> "Move to…"
            }
        )
        Lead(trunc(t.title.ifBlank { "Untitled task" }, 80), Modifier.padding(top = 4.dp))
        if (!fromBucket) {
            Seg(listOf("A day", if (pj != null) "Project" else "Bucket"), tab) { tab = it }
            Spacer(Modifier.height(14.dp))
        }
        if (tab == 1) {
            Lead(
                if (pj != null) "The action leaves its day and goes back to “${pj.name}”, unscheduled."
                else "The task loses its day and section and waits in the bucket."
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Btn(if (pj != null) "Move back to project" else if (s.copy) "Copy to bucket" else "Move to bucket") { apply(null, null) }
            }
        } else {
            val tm = D.addDays(today, 1)
            val wk = D.addDays(today, 7)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Today", date == today) { date = today }
                ChoiceChip("Tomorrow", date == tm) { date = tm }
                ChoiceChip("In a week", date == wk) { date = wk }
            }
            Label("Date", Modifier.padding(top = 14.dp, bottom = 6.dp))
            FieldButton(D.fmtShort(date), Modifier.fillMaxWidth()) { picker = true }
            Label("Section on " + D.fmtShort(date), Modifier.padding(top = 14.dp, bottom = 8.dp))
            for (sec in Section.entries) {
                val here = !s.copy && !t.tray && t.date == date && t.section == sec
                val l = planner.dayList(date, sec).filter { !(!s.copy && it.id == t.id) }
                val room = l.size < sec.max
                SectionChoice(planner, date, sec, if (here) "Already here" else if (room) "${l.size}/${sec.max}" else "Full", room && !here) { apply(date, sec) }
            }
        }
        if (s.project) {
            LinkButton("Delete action", LocalPal.current.danger) {
                close()
                model.change(undoable = true) { remove(t) }
                model.say("Action deleted", undo = true)
            }
        }
    }
    if (picker) DatePickDialog(date, today, { picker = false }) { date = it }
}

@Composable
private fun TodayPickSheet(model: AppModel, id: String, close: () -> Unit) {
    val planner = model.planner
    val t = planner.find(id) ?: return close()
    val d = planner.today()
    AppSheet(close) {
        SheetTitle("Add to Today")
        Lead(trunc(t.title.ifBlank { "Untitled task" }, 80), Modifier.padding(top = 4.dp))
        if (Section.entries.none { planner.hasRoom(d, it) }) Lead("Today is full. Check off or move something to free a slot.")
        for (sec in Section.entries) {
            val l = planner.dayList(d, sec)
            val room = l.size < sec.max
            SectionChoice(planner, d, sec, if (room) "${l.size}/${sec.max}" else "Full", room) {
                close()
                var o: Planner.Outcome = Planner.Outcome.None
                model.change(undoable = true) { o = applyDest(t, Planner.Mode.MOVE, d, sec) }
                model.outcome(o)
            }
        }
    }
}

/** After dropping a task on "Project…": choose the project, or start a new one. */
@Composable
private fun PickProjectSheet(model: AppModel, id: String, close: () -> Unit) {
    val p = LocalPal.current
    val planner = model.planner
    val t = planner.find(id) ?: return close()
    var name by remember { mutableStateOf("") }
    fun moveTo(pid: String, pname: String) {
        close()
        model.change(undoable = true) { moveToProject(t, pid) }
        model.say("Moved to “$pname”", undo = true)
    }
    AppSheet(close) {
        SheetTitle("Move to a project")
        Lead(trunc(t.title.ifBlank { "Untitled task" }, 80) + ". It leaves its day and waits in the project, unscheduled.", Modifier.padding(top = 4.dp))
        val active = planner.state.projects.filter { !it.archived }
        active.forEachIndexed { i, pr ->
            if (i > 0) Divider()
            MenuItem(Ic.folder, pr.name, hint = planner.projStat(pr.id)) { moveTo(pr.id, pr.name) }
        }
        Label("New project", Modifier.padding(top = 16.dp, bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(9.dp)
            BasicTextField(
                value = name,
                onValueChange = { name = it.replace("\n", "").take(80) },
                singleLine = true,
                modifier = Modifier.weight(1f).clip(shape).background(p.bg).border(1.dp, p.line, shape).padding(horizontal = 12.dp, vertical = 12.dp),
                textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box {
                        if (name.isEmpty()) Text("Project name…", color = p.muted.copy(alpha = 0.75f), fontSize = 16.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Btn("Create", enabled = name.isNotBlank()) {
                val pr = planner.addProject(name)
                if (pr != null) moveTo(pr.id, pr.name)
            }
        }
    }
}

// ---------- reminder ----------
@Composable
private fun RemindSheet(model: AppModel, id: String, close: () -> Unit) {
    val ctx = LocalContext.current
    val planner = model.planner
    val t = planner.find(id) ?: return close()
    val initial = remember {
        t.reminderAt?.let { D.parseDT(it) } ?: run {
            val base = if (t.date != null && t.date!! > planner.today()) LocalDate.parse(t.date).atTime(9, 0) else planner.now().plusHours(1)
            val m = ((base.minute + 14) / 15) * 15
            base.withMinute(0).withSecond(0).withNano(0).plusMinutes(m.toLong())
        }
    }
    var date by remember { mutableStateOf(initial.toLocalDate().toString()) }
    var time by remember { mutableStateOf(initial.toLocalTime()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var exactOk by remember { mutableStateOf(Reminders.canScheduleExact(ctx)) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) model.say("Notifications are off, so the reminder can't alert you. Turn them on in Settings.")
        close()
    }
    AppSheet(close) {
        SheetTitle("Set reminder")
        Lead(trunc(t.title.ifBlank { "Untitled task" }, 80), Modifier.padding(top = 4.dp))
        Label("Remind me at", Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldButton(D.fmtShort(date), Modifier.weight(1f)) { pickDate = true }
            FieldButton(D.fmtTime(LocalDate.parse(date).atTime(time)), Modifier.weight(1f)) { pickTime = true }
        }
        Spacer(Modifier.height(10.dp))
        Lead("You'll get a notification with Complete and Snooze 15 min.")
        if (!exactOk) {
            Lead("Exact alarms are off for this app, so reminders may arrive a few minutes late.")
            Btn("Allow exact alarms", BtnKind.GHOST) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                } catch (_: Exception) {}
                exactOk = true
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (t.reminderAt != null) {
                Btn("Clear reminder", BtnKind.GHOST) {
                    model.change { t.reminderAt = null; t.fired = false }
                    close()
                }
            }
            Btn("Save") {
                model.change {
                    t.reminderAt = D.localDT(LocalDate.parse(date).atTime(time))
                    t.fired = false
                }
                if (!Reminders.canPostNotifications(ctx)) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else close()
            }
        }
    }
    if (pickDate) DatePickDialog(date, null, { pickDate = false }) { date = it }
    if (pickTime) TimePickDialog(time, { pickTime = false }) { time = it }
}

// ---------- repeat ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatSheet(model: AppModel, id: String, close: () -> Unit) {
    val p = LocalPal.current
    val planner = model.planner
    val t = planner.find(id) ?: return close()
    val existing = planner.rule(t.ruleId)
    var draft by remember { mutableStateOf(existing?.copyAll() ?: planner.newRuleDraft(t)) }
    fun upd(f: Rule.() -> Unit) { val c = draft.copyAll(); c.f(); draft = c }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val valid = !(draft.type == RepeatType.WEEKLY && draft.weekdays.isEmpty())
    AppSheet(close) {
        SheetTitle("Repeat")
        Lead(trunc(t.title.ifBlank { "Untitled task" }, 80), Modifier.padding(top = 4.dp))
        Label("Pattern", Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (ty in RepeatType.entries) ChoiceChip(ty.label, draft.type == ty) { upd { type = ty } }
        }
        Label("Every", Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(draft.interval, 1, 99) { v -> upd { interval = v } }
            Spacer(Modifier.width(10.dp))
            Text(draft.type.unit, color = p.muted)
        }
        when (draft.type) {
            RepeatType.WEEKLY -> {
                Label("On", Modifier.padding(top = 14.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0..6) {
                        ChoiceChip(D.WD[i].take(2), draft.weekdays.contains(i), Modifier.weight(1f)) {
                            upd { weekdays = if (weekdays.contains(i)) weekdays.filter { it != i }.toMutableList() else (weekdays + i).toMutableList() }
                        }
                    }
                }
            }
            RepeatType.MONTHLY_DATE -> {
                Label("Day of month", Modifier.padding(top = 14.dp, bottom = 6.dp))
                Stepper(draft.dom, 1, 31) { v -> upd { dom = v } }
                if (draft.dom > 28) Lead("In shorter months this lands on the last day.", Modifier.padding(top = 6.dp))
            }
            RepeatType.MONTHLY_WEEKDAY -> {
                Label("Which", Modifier.padding(top = 14.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (o in listOf(1, 2, 3, 4, -1)) ChoiceChip(D.ORD[o] ?: "", draft.ord == o) { upd { ord = o } }
                }
                Label("Weekday", Modifier.padding(top = 14.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0..6) ChoiceChip(D.WD[i].take(2), draft.weekday == i, Modifier.weight(1f)) { upd { weekday = i } }
                }
            }
            RepeatType.ANNUAL -> {
                Label("Month", Modifier.padding(top = 14.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (mo in 1..12) ChoiceChip(D.MON[mo - 1], draft.month == mo) { upd { month = mo } }
                }
                Label("Day", Modifier.padding(top = 14.dp, bottom = 6.dp))
                Stepper(draft.day, 1, 31) { v -> upd { day = v } }
            }
            RepeatType.DAILY -> {}
        }
        Label("Section", Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (sec in Section.entries) ChoiceChip(sec.label, draft.section == sec) { upd { section = sec } }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Label("Starts", Modifier.padding(bottom = 6.dp))
                FieldButton(if (draft.start.isEmpty()) "—" else D.fmtShort(draft.start), Modifier.fillMaxWidth()) { pickStart = true }
            }
            Column(Modifier.weight(1f)) {
                Label("Ends", Modifier.padding(bottom = 6.dp))
                FieldButton(if (draft.end.isEmpty()) "Never" else D.fmtShort(draft.end), Modifier.fillMaxWidth()) { pickEnd = true }
            }
        }
        if (draft.end.isNotEmpty()) LinkButton("Remove end date") { upd { end = "" } }
        val next = if (valid) planner.nextDates(draft, planner.today(), 3) else emptyList()
        Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(10.dp)).background(p.bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(if (valid) planner.describe(draft) else "Pick at least one weekday", fontWeight = FontWeight.Bold, color = p.ink, fontSize = 14.sp)
            Text(
                if (!valid) "" else if (next.isEmpty()) "No upcoming dates" else "Next: " + next.joinToString(" · ") { D.fmtShort(it) },
                color = p.muted, fontSize = 14.sp,
            )
        }
        Lead("Unfinished repeats expire at the end of their day instead of carrying over.", Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (existing != null) {
                Btn("Stop repeating", BtnKind.GHOST) {
                    close()
                    model.change(undoable = true) { stopRepeat(t, existing) }
                    model.say("Stopped repeating", undo = true)
                }
            }
            Btn("Save", enabled = valid) {
                close()
                var r: Rule? = null
                model.change(undoable = true) { r = saveRule(t, existing, draft) }
                model.say(planner.describe(r), undo = true)
            }
        }
    }
    if (pickStart) DatePickDialog(draft.start.ifEmpty { planner.today() }, null, { pickStart = false }) { v -> upd { start = v } }
    if (pickEnd) DatePickDialog(draft.end.ifEmpty { draft.start.ifEmpty { planner.today() } }, draft.start.ifEmpty { null }, { pickEnd = false }) { v -> upd { end = v } }
}

// ---------- notes ----------
@Composable
private fun NotesSheet(model: AppModel, id: String, close: () -> Unit) {
    val p = LocalPal.current
    val planner = model.planner
    val t = planner.find(id) ?: return close()
    var text by remember { mutableStateOf(t.notes ?: "") }
    val finish = {
        planner.find(id)?.let { it.notes = text.trimEnd().ifEmpty { null } }
        model.commit()
        close()
    }
    AppSheet(finish) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetTitle("Notes", Modifier.weight(1f))
            IconBtn(Ic.close, "Close notes", p.ink) { finish() }
        }
        Lead(trunc(t.title.ifBlank { "Untitled task" }, 90) + (planner.proj(t.proj)?.let { " · " + it.name } ?: ""))
        val shape = RoundedCornerShape(9.dp)
        BasicTextField(
            value = text,
            onValueChange = { v -> text = v.take(20000); t.notes = text; model.saveQuietly() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).clip(shape).background(p.bg).border(1.dp, p.line, shape).padding(12.dp),
            textStyle = TextStyle(color = p.ink, fontSize = 16.sp, lineHeight = 24.sp),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Type notes, links, measurements, part numbers…", color = p.muted.copy(alpha = 0.75f), fontSize = 16.sp)
                    inner()
                }
            },
        )
        Lead("Saved as you type.", Modifier.padding(top = 8.dp))
    }
}

// ---------- dialogs ----------
@Composable
private fun ChoiceDialog(title: String, text: String?, options: List<Pair<String, () -> Unit>>, onCancel: () -> Unit) {
    val p = LocalPal.current
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = p.surface,
        title = { Text(title, fontWeight = FontWeight.Bold, color = p.ink) },
        text = {
            Column {
                if (text != null) Text(text, color = p.muted, modifier = Modifier.padding(bottom = 8.dp))
                options.forEachIndexed { i, (label, action) ->
                    if (i > 0) Divider()
                    MenuItem(null, label) { action() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun EditScopeDialog(model: AppModel, s: Sheet.EditScope, close: () -> Unit) {
    val planner = model.planner
    val t = planner.find(s.taskId) ?: return close()
    val r = planner.rule(t.ruleId) ?: return close()
    val cancel = {
        close()
        model.change { t.title = s.previousTitle }
    }
    ChoiceDialog(
        "Apply this change to…", planner.describe(r),
        listOf(
            "This one only" to { close(); model.change(undoable = true) { applyTitleScope(t, allFuture = false) } },
            "This and all future" to { close(); model.change(undoable = true) { applyTitleScope(t, allFuture = true) } },
        ),
        cancel,
    )
}

@Composable
private fun DeleteScopeDialog(model: AppModel, id: String, close: () -> Unit) {
    val planner = model.planner
    val t = planner.find(id) ?: return close()
    val r = planner.rule(t.ruleId)
    ChoiceDialog(
        "Delete repeating task", planner.describe(r),
        listOf(
            "This one only" to {
                close(); model.change(undoable = true) { deleteWithScope(t, allFuture = false) }; model.say("Deleted this one", undo = true)
            },
            "This and all future" to {
                close(); model.change(undoable = true) { deleteWithScope(t, allFuture = true) }; model.say("Deleted this and all future", undo = true)
            },
        ),
        close,
    )
}

@Composable
private fun CarriedDoneDialog(model: AppModel, s: Sheet.CarriedDone, close: () -> Unit) {
    val p = LocalPal.current
    val planner = model.planner
    val t = planner.find(s.taskId) ?: return close()
    val wh = t.date?.let { if (it == planner.today()) "today" else D.fmtShort(it) } ?: "the bucket"
    val from = D.fmtShort(s.from)
    AlertDialog(
        onDismissRequest = close,
        containerColor = p.surface,
        title = { Text("Done on $from?", fontWeight = FontWeight.Bold, color = p.ink) },
        text = { Text("“${trunc(t.title.ifBlank { "Untitled task" }, 80)}” was carried over to $wh. Delete it from $wh and mark it done on $from?", color = p.muted) },
        dismissButton = { TextButton(onClick = close) { Text("Keep it on $wh") } },
        confirmButton = {
            TextButton(onClick = {
                close()
                model.change(undoable = true) { markDoneOnCarriedDay(t, s.from) }
                model.say("Marked done on $from and removed from $wh", undo = true)
            }) { Text("Delete from $wh") }
        },
    )
}

// ---------- project menu ----------
@Composable
private fun ProjectMenuSheet(model: AppModel, pid: String, close: () -> Unit) {
    val p = LocalPal.current
    val planner = model.planner
    val pr = planner.proj(pid) ?: return close()
    var name by remember { mutableStateOf(pr.name) }
    var armed by remember { mutableStateOf(false) }
    val finish = {
        pr.name = name.trim().ifEmpty { "Untitled project" }
        model.commit()
        close()
    }
    AppSheet(finish) {
        SheetTitle("Project")
        Label("Name", Modifier.padding(top = 12.dp, bottom = 6.dp))
        val shape = RoundedCornerShape(9.dp)
        BasicTextField(
            value = name,
            onValueChange = { name = it.replace("\n", "").take(80) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().clip(shape).background(p.bg).border(1.dp, p.line, shape).padding(horizontal = 12.dp, vertical = 12.dp),
            textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
            cursorBrush = SolidColor(p.accent),
        )
        Lead(planner.projStat(pid), Modifier.padding(top = 10.dp))
        MenuItem(Ic.check, "Mark project complete") {
            pr.name = name.trim().ifEmpty { "Untitled project" }
            close()
            model.change(undoable = true) { pr.archived = true }
            model.say("Project marked complete", undo = true)
        }
        Divider()
        MenuItem(Ic.trash, if (armed) "Tap again to delete" else "Delete project", color = p.danger, hint = if (armed) "Unscheduled actions go too" else null) {
            if (!armed) armed = true
            else {
                close()
                model.change(undoable = true) { deleteProject(pr) }
                model.say("Project deleted", undo = true)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Btn("Done") { finish() }
        }
    }
}
