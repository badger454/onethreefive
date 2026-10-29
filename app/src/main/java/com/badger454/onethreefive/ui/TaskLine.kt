package com.badger454.onethreefive.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.badger454.onethreefive.AppModel
import com.badger454.onethreefive.Sheet
import com.badger454.onethreefive.domain.D
import com.badger454.onethreefive.domain.Task

enum class RowCtx { DAY, PAST, BUCKET, PROJ }

/** Drag-to-reorder bookkeeping for one screen. Rows report their bounds; the screen decides where a drop lands. */
class DragState {
    var id by mutableStateOf<String?>(null)
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)
    var start: Rect = Rect.Zero
    val rows = HashMap<String, Rect>()
    val lists = HashMap<String, Rect>()
    /** Extra drop targets (e.g. "bucket", "project") by bounds in root. */
    val zones = HashMap<String, Rect>()
    var onDrop: (taskId: String, at: Offset, zone: String?) -> Unit = { _, _, _ -> }

    /** Where the finger first touched the handle, in root coordinates. */
    var finger0: Offset = Offset.Zero

    fun center(): Offset = Offset(start.center.x + dx, start.center.y + dy)
    fun finger(): Offset = Offset(finger0.x + dx, finger0.y + dy)
    /** Drop targets follow the finger, not the row's middle (rows are full width). */
    fun zoneAt(p: Offset): String? = zones.entries.firstOrNull { it.value.contains(p) }?.key
    fun hoverZone(): String? = if (id == null) null else zoneAt(finger())
}

/**
 * One line in a list: an existing task, or a blank line that becomes a task as soon as you type.
 * Lines are keyed by position ("<list>:<index>"), so when a blank turns into a task the same text field
 * stays on screen and the keyboard is undisturbed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskLine(
    model: AppModel,
    slotKey: String,
    listId: String,
    task: Task?,
    ctx: RowCtx,
    placeholder: String,
    firstBlankKey: String?,
    nextKeyAfterCreate: String?,
    prevKey: String?,
    over: Int = 0,
    drag: DragState? = null,
    onCreate: (String) -> Task?,
) {
    val p = LocalPal.current
    val planner = model.planner
    @Suppress("UNUSED_VARIABLE") val redraw = model.version // redraw whenever data changes
    val focusManager = LocalFocusManager.current
    val fr = remember { FocusRequester() }
    var tfv by remember { mutableStateOf(TextFieldValue(task?.title ?: "")) }
    var focused by remember { mutableStateOf(false) }
    var origTitle by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    // Day rows stay one line so a full day fits on screen; tap the text to edit, the chevron to read it all.
    val compact = ctx == RowCtx.DAY || ctx == RowCtx.PAST
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var overflowing by remember { mutableStateOf(false) }
    val dataTitle = task?.title ?: ""

    LaunchedEffect(task?.id, dataTitle, model.version) {
        if (!focused && tfv.text != dataTitle) tfv = TextFieldValue(dataTitle)
    }
    LaunchedEffect(model.focusKey) {
        if (model.focusKey == slotKey) {
            model.focusKey = null
            if (compact && task != null && !focused) { editing = true; return@LaunchedEffect }
            try {
                fr.requestFocus()
                tfv = tfv.copy(selection = TextRange(tfv.text.length))
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(editing) {
        if (editing && !focused) {
            try {
                fr.requestFocus()
                tfv = tfv.copy(selection = TextRange(tfv.text.length))
            } catch (_: Exception) {}
        }
    }

    fun goNext(justCreated: Boolean) {
        val target = if (justCreated) nextKeyAfterCreate else firstBlankKey
        if (target != null && target != slotKey) model.focusKey = target else focusManager.clearFocus()
    }

    // The task this blank line just created. Keystrokes can arrive before the screen redraws with the new
    // task, so later letters must edit that task instead of trying to create another one.
    var createdId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(task?.id) { if (task != null) createdId = null }
    fun current(): Task? = task ?: createdId?.let { planner.find(it) }

    fun onChange(v: TextFieldValue) {
        var nv = v
        var enter = false
        if (v.text.contains('\n')) {
            enter = true
            val clean = v.text.replace("\n", "")
            nv = TextFieldValue(clean, TextRange(minOf(v.selection.start, clean.length)))
        }
        if (nv.text.length > 500) nv = TextFieldValue(nv.text.take(500), TextRange(500))
        val t = current()
        if (t == null) {
            if (nv.text.isBlank()) { tfv = nv; return }
            val created = onCreate(nv.text)
            if (created == null) { tfv = TextFieldValue(""); return }
            createdId = created.id
            tfv = nv
            model.commit()
            if (enter) goNext(justCreated = true)
            return
        }
        tfv = nv
        if (t.title != nv.text) {
            t.title = nv.text
            model.saveQuietly()
        }
        if (enter && nv.text.isNotBlank()) goNext(justCreated = task == null)
    }

    fun onEnterKey() {
        if (current() == null) return
        if (tfv.text.isNotBlank()) goNext(justCreated = task == null)
    }

    fun onBlur() {
        val t = current() ?: return
        createdId = null
        val live = planner.find(t.id) ?: return
        if (live.title.isBlank()) {
            model.change { remove(live) }
            return
        }
        val trimmed = live.title.trim()
        val prev = origTitle
        if (trimmed != live.title) live.title = trimmed
        if (live.ruleId != null && prev != null && trimmed != prev) {
            model.sheet = Sheet.EditScope(live.id, prev)
        }
        if (prev == null || trimmed != prev) model.commit()
    }

    val dragging = drag != null && task != null && drag.id == task.id
    val cat = planner.cat(task?.cat)
    val overBg: Brush? = when {
        task == null || over <= 0 -> null
        over == 1 -> SolidColor(Over.one)
        over == 2 -> SolidColor(Over.two)
        over == 3 -> SolidColor(Over.three)
        else -> Brush.linearGradient(
            0f to Over.stripeRed, 0.5f to Over.stripeRed, 0.5f to Over.stripeYellow, 1f to Over.stripeYellow,
            start = Offset(0f, 0f), end = Offset(40f, 40f), tileMode = TileMode.Repeated,
        )
    }
    val pulse = if (task != null && over >= 3) {
        val tr = rememberInfiniteTransition(label = "over")
        tr.animateFloat(0f, 0.18f, infiniteRepeatable(tween(if (over >= 4) 450 else 800, easing = LinearEasing), RepeatMode.Reverse), label = "pulse").value
    } else 0f
    val ink = if (overBg != null) Over.ink else p.ink
    val muted = if (overBg != null) Over.ink else p.muted

    var rowMod = Modifier.fillMaxWidth()
    if (drag != null && task != null) {
        rowMod = rowMod
            .onGloballyPositioned { if (drag.id != task.id) drag.rows[task.id] = it.boundsInRoot() }
            .zIndex(if (dragging) 10f else 0f)
            .graphicsLayer {
                if (dragging) {
                    translationY = drag.dy
                    translationX = drag.dx
                    shadowElevation = 16f
                    shape = RoundedCornerShape(10.dp)
                    clip = true
                }
            }
    }
    rowMod = if (overBg != null) rowMod.background(overBg) else rowMod.background(p.tint(cat) ?: p.surface)
    if (pulse > 0f) rowMod = rowMod.background(Color.White.copy(alpha = pulse))
    if (task != null && over >= 3) rowMod = rowMod.border(3.dp, if (over >= 4) Over.fourBorder else Over.threeBorder)

    Row(rowMod.padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.Top) {
        // drag handle
        val canDrag = task != null && drag != null && ctx != RowCtx.PAST
        val dragId = task?.id ?: ""
        var gripOrigin by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.width(26.dp).height(40.dp).onGloballyPositioned { gripOrigin = it.positionInRoot() }.let { m ->
                val d = drag
                if (canDrag && d != null) m.pointerInput(dragId) {
                    detectDragGestures(
                        onDragStart = { touch ->
                            focusManager.clearFocus()
                            d.finger0 = gripOrigin + touch
                            d.start = d.rows[dragId] ?: Rect.Zero
                            d.dx = 0f
                            d.dy = 0f
                            d.id = dragId
                        },
                        onDragEnd = {
                            val id = d.id
                            val at = d.center()
                            val zone = d.zoneAt(d.finger())
                            d.id = null
                            d.dx = 0f
                            d.dy = 0f
                            if (id != null) d.onDrop(id, at, zone)
                        },
                        onDragCancel = { d.id = null; d.dx = 0f; d.dy = 0f },
                        onDrag = { change, amount -> change.consume(); d.dx += amount.x; d.dy += amount.y },
                    )
                }.semantics { contentDescription = "Drag to reorder" } else m
            },
            contentAlignment = Alignment.Center,
        ) {
            if (canDrag) Icon(Ic.grip, null, Modifier.size(16.dp), tint = muted)
        }
        CheckBox(
            done = task?.done == true,
            important = task?.important == true,
            dashed = task == null,
            borderOverride = if (overBg != null) Over.ink else null,
            description = if (task?.done == true) "Mark not done" else "Mark done",
            onClick = if (task == null) null else ({ model.outcome(planner.toggleDone(task)); model.commit() }),
        )
        Column(Modifier.weight(1f).padding(top = 9.dp, bottom = 7.dp, start = 2.dp)) {
            val done = task?.done == true
            val titleStyle = TextStyle(
                color = if (done) muted else ink,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = if (task?.important == true && !done) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (compact && task != null && !focused && !editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        Modifier.weight(1f, fill = false).clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) { editing = true },
                        style = titleStyle,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { r -> if (!expanded) overflowing = r.hasVisualOverflow },
                    )
                    CompactIcons(task, muted, p.accent) { expanded = !expanded }
                }
                if (expanded) MetaLine(model, task, ctx, ink = muted)
            } else BasicTextField(
                value = tfv,
                onValueChange = { onChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fr)
                    .onFocusChanged { st ->
                        if (st.isFocused && !focused) {
                            focused = true
                            origTitle = task?.title
                            if (task == null && firstBlankKey != null && firstBlankKey != slotKey) model.focusKey = firstBlankKey
                        } else if (!st.isFocused && focused) {
                            focused = false
                            editing = false
                            onBlur()
                        }
                    }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                                onEnterKey(); true
                            }
                            e.key == Key.Backspace && tfv.text.isEmpty() -> {
                                current()?.let { ct -> model.change { remove(ct) } }
                                if (prevKey != null) model.focusKey = prevKey
                                true
                            }
                            else -> false
                        }
                    },
                textStyle = titleStyle,
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onEnterKey() }),
                decorationBox = { inner ->
                    Box {
                        if (tfv.text.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = p.muted.copy(alpha = 0.75f), fontSize = 15.sp, lineHeight = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                },
            )
            if (task != null && (!compact || focused || editing)) MetaLine(model, task, ctx, ink = muted)
        }
        if (compact && task != null && (overflowing || expanded) && !focused && !editing) {
            Box(
                Modifier.width(28.dp).height(40.dp).clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }
                    .semantics { contentDescription = if (expanded) "Show less" else "Show full text" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (expanded) Ic.chevUp else Ic.chevDown, null, Modifier.size(16.dp), tint = muted)
            }
        }
        // trailing buttons
        if (ctx == RowCtx.PROJ) {
            if (task != null) {
                val hasNotes = !task.notes.isNullOrBlank()
                IconBtn(Ic.notes, if (hasNotes) "Open notes" else "Add notes", if (hasNotes) p.accent else muted) { model.sheet = Sheet.Notes(task.id) }
                IconBtn(Ic.sched, "Schedule into a day", p.accent) { model.sheet = Sheet.Dest(task.id, copy = false, project = true) }
            } else {
                Spacer(Modifier.width(80.dp))
            }
        } else {
            Box {
                if (task != null) {
                    IconBtn(Ic.more, "Task options", muted) { focusManager.clearFocus(); menuOpen = true }
                    TaskMenu(model, task, menuOpen) { menuOpen = false }
                } else {
                    Spacer(Modifier.width(40.dp))
                }
            }
        }
    }
}

@Composable
fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color, size: Int = 20, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size.dp), tint = tint)
    }
}

/** The single 12sp line of details under a task title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetaLine(model: AppModel, t: Task, ctx: RowCtx, ink: Color) {
    @Suppress("UNUSED_VARIABLE") val redraw = model.version // redraw whenever data changes
    val p = LocalPal.current
    val planner = model.planner
    val c = planner.cat(t.cat)
    val pj = planner.proj(t.proj)
    val rule = planner.rule(t.ruleId)
    val strong = if (ink == p.muted) null else ink
    val hasAny = c != null || (pj != null && ctx != RowCtx.PROJ) || t.important || !t.notes.isNullOrBlank() || t.ongoing ||
        t.carry > 0 || rule != null || t.reminderAt != null || ctx == RowCtx.BUCKET || ctx == RowCtx.PROJ
    if (!hasAny) return
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (c != null) Meta(c.name, ink, dot = p.catColor(c))
        if (pj != null && ctx != RowCtx.PROJ) Meta(pj.name, ink, icon = Ic.folder)
        if (ctx == RowCtx.PROJ && t.date != null && !t.done) {
            Meta("→ " + planner.relName(t.date!!) + (t.section?.let { " · " + it.label } ?: ""), strong ?: p.accent, bold = true)
        }
        if (ctx == RowCtx.PROJ && t.done && t.completedAt != null) Meta("Done " + D.fmtDate(t.completedAt!!), ink)
        if (t.important) Meta("Important", strong ?: p.flag, icon = Ic.flag, bold = true)
        if (!t.notes.isNullOrBlank() && ctx != RowCtx.PROJ) Meta("Notes", strong ?: p.accent, icon = Ic.notes, bold = true) { model.sheet = Sheet.Notes(t.id) }
        if (t.ongoing && t.ruleId == null) Meta("Ongoing", strong ?: p.accent, icon = Ic.ongoing, bold = true)
        if (t.carry > 0) Meta("Carried ×${t.carry}", if (t.carry >= 3) (strong ?: p.danger) else ink, bold = t.carry >= 3)
        if (rule != null) Meta(planner.describe(rule), ink, icon = Ic.repeat)
        val r = t.reminderAt?.let { D.parseDT(it) }
        if (r != null) {
            val same = t.date != null && r.toLocalDate().toString() == t.date
            Meta((if (same) "" else D.fmtDate(r.toLocalDate().toString()) + ", ") + D.fmtTime(r), ink, icon = Ic.bell)
        }
        if (ctx == RowCtx.BUCKET && t.done && t.completedAt != null) Meta("Done " + D.fmtDate(t.completedAt!!), ink)
        if (ctx == RowCtx.BUCKET && !t.done) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, p.accent, RoundedCornerShape(50))
                        .clickable { model.sheet = Sheet.TodayPick(t.id) }
                        .padding(start = 6.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.plus, null, Modifier.size(12.dp), tint = p.accent)
                        Spacer(Modifier.width(3.dp))
                        Text("Add to Today", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.accent)
                    }
                }
            }
        }
    }
}

/** Tiny inline markers for a one-line row: project, reminder, repeat, ongoing, notes, carried count. */
@Composable
fun CompactIcons(t: Task, tint: Color, accent: Color, onClick: () -> Unit) {
    val items = ArrayList<@Composable () -> Unit>()
    val m = Modifier.size(13.dp)
    if (t.proj != null) items.add { Icon(Ic.folder, null, m, tint = tint) }
    if (t.reminderAt != null) items.add { Icon(Ic.bell, null, m, tint = tint) }
    if (t.ruleId != null) items.add { Icon(Ic.repeat, null, m, tint = tint) }
    if (t.ongoing && t.ruleId == null) items.add { Icon(Ic.ongoing, null, m, tint = accent) }
    if (!t.notes.isNullOrBlank()) items.add { Icon(Ic.notes, null, m, tint = accent) }
    if (t.carry > 0) items.add {
        Text("×${t.carry}", fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, color = if (t.carry >= 3 && tint != Over.ink) LocalPal.current.danger else tint)
    }
    if (items.isEmpty()) return
    Row(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 3.dp)
            .semantics { contentDescription = "Show details" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { items.forEach { it() } }
}
