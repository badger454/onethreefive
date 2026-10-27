package com.badger454.onethreefive.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.badger454.onethreefive.AppModel
import com.badger454.onethreefive.Sheet
import com.badger454.onethreefive.domain.D
import com.badger454.onethreefive.domain.Planner
import com.badger454.onethreefive.domain.Section
import com.badger454.onethreefive.domain.Task
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val START = 100_000

/** Monday on or before [iso]. */
fun mondayOf(iso: String): String = D.addDays(iso, -((D.wd(iso) + 6) % 7).toLong())

@Composable
fun DayScreen(model: AppModel) {
    @Suppress("UNUSED_VARIABLE") val v = model.version
    val planner = model.planner
    val base = remember { planner.today() }
    fun pageOf(d: String) = START + (D.dn(d) - D.dn(base)).toInt()
    fun dateOf(pg: Int) = D.addDays(base, (pg - START).toLong())
    // Days are pages: a swipe drags the whole day with your finger and settles on the next one.
    val pager = rememberPagerState(initialPage = pageOf(model.viewDate)) { 2 * START }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { pg ->
            val d = dateOf(pg)
            if (d != model.viewDate) model.viewDate = d
        }
    }
    LaunchedEffect(model.viewDate) {
        val target = pageOf(model.viewDate)
        if (target != pager.currentPage && !pager.isScrollInProgress) {
            if (abs(target - pager.currentPage) <= 7) pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
    }
    val shown = dateOf(pager.currentPage)
    val drag = remember { DragState() }
    drag.onDrop = { id, at, zone -> dropOnDay(model, drag, id, at, zone) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DayHeader(model, shown)
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { it },
            ) { pg -> DayPage(model, dateOf(pg), drag) }
        }
        DropBar(drag, Modifier.align(Alignment.BottomCenter))
    }
}

private fun dropOnDay(model: AppModel, drag: DragState, id: String, at: Offset, zone: String?) {
    val planner = model.planner
    val t = planner.find(id) ?: return
    when (zone) {
        "bucket" -> {
            model.change(undoable = true) { moveToBucket(t) }
            model.say("Moved to the bucket", undo = true)
        }
        "project" -> model.sheet = Sheet.PickProject(t.id)
        else -> {
            val d = t.date ?: return
            val cy = at.y
            val target = Section.entries.minByOrNull { s ->
                val r = drag.lists["day:$d:${s.key}"] ?: return@minByOrNull Float.MAX_VALUE
                when {
                    cy < r.top -> r.top - cy
                    cy > r.bottom -> cy - r.bottom
                    else -> 0f
                }
            } ?: t.section ?: Section.SMALL
            val items = planner.dayList(d, target).filter { it.id != id }
            val idx = items.count { (drag.rows[it.id]?.center?.y ?: Float.MAX_VALUE) < cy }
            val o = planner.dropInSection(t, target, idx)
            if (o is Planner.Outcome.Full) model.say("${target.label} is full. Free a slot first.")
            model.commit()
        }
    }
}

/** Shown while a task is being dragged: drop it on Bucket, or on Project to pick which one. */
@Composable
private fun DropBar(drag: DragState, modifier: Modifier) {
    val p = LocalPal.current
    AnimatedVisibility(
        visible = drag.id != null,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        DisposableEffect(Unit) { onDispose { drag.zones.clear() } }
        val hover = drag.hoverZone()
        Row(
            Modifier.fillMaxWidth().background(p.bg.copy(alpha = 0.92f)).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for ((key, label, icon) in listOf(Triple("bucket", "Bucket", Ic.bucket), Triple("project", "Project…", Ic.folder))) {
                val on = hover == key
                Row(
                    Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) p.accent else p.accentSoft)
                        .border(2.dp, p.accent, RoundedCornerShape(16.dp))
                        .onGloballyPositioned { drag.zones[key] = it.boundsInRoot() },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = if (on) p.accentInk else p.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (on) p.accentInk else p.accent)
                }
            }
        }
    }
}

@Composable
private fun DayPage(model: AppModel, d: String, drag: DragState) {
    val v = model.version
    val planner = model.planner
    val today = planner.today()
    LaunchedEffect(d, v) {
        if (d >= today) {
            var c = planner.materialize(d)
            if (planner.autoSlot(d)) c = true
            if (c) model.commit()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
    ) {
        if (d < today) PastDay(model, d) else PlanDay(model, d, drag)
    }
}

/**
 * Month name, the week as a strip of selectable days, progress, and a month calendar that unrolls
 * under your finger when you drag the header down.
 */
@Composable
private fun DayHeader(model: AppModel, shown: String) {
    val p = LocalPal.current
    val planner = model.planner
    val today = planner.today()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val shownDate = D.parse(shown)
    var ym by remember { mutableIntStateOf(shownDate.year * 12 + shownDate.monthValue - 1) }
    val y = ym / 12
    val m = ym % 12 + 1
    val first = java.time.LocalDate.of(y, m, 1).toString()
    val lead = (D.wd(first) + 6) % 7
    val weeks = (lead + D.lastDay(y, m) + 6) / 7
    val fullPx = with(density) { (40 + 22 + weeks * 44 + 10).dp.toPx() }
    val calH = remember { Animatable(0f) }
    val open = calH.value > 0f
    fun setOpen(o: Boolean) {
        if (o && calH.value == 0f) ym = shownDate.year * 12 + shownDate.monthValue - 1
        scope.launch { calH.animateTo(if (o) fullPx else 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) }
    }
    LaunchedEffect(fullPx) { if (calH.value > 0f && !calH.isRunning) calH.snapTo(fullPx) }
    val pullMod = Modifier.pointerInput(fullPx) {
        var last = 0f
        detectVerticalDragGestures(
            onDragStart = { if (calH.value == 0f) ym = shownDate.year * 12 + shownDate.monthValue - 1 },
            onDragEnd = { setOpen(if (last != 0f) last > 0f else calH.value > fullPx / 2) },
            onDragCancel = { setOpen(calH.value > fullPx / 2) },
            onVerticalDrag = { ch, delta ->
                ch.consume()
                last = delta
                scope.launch { calH.snapTo((calH.value + delta).coerceIn(0f, fullPx)) }
            },
        )
    }
    val shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(p.hdr)
            .drawBehind { if (p.hdrLine != Color.Transparent) drawLine(p.hdrLine, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f) }
            .then(pullMod)
            .statusBarsPadding()
            .padding(top = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = if (open) D.MONTHS[m - 1] + (if (y != D.parse(today).year) " $y" else "") else D.MONTHS[shownDate.monthValue - 1] + (if (shownDate.year != D.parse(today).year) " ${shownDate.year}" else "")
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { setOpen(!open) }.padding(horizontal = 6.dp, vertical = 6.dp)
                    .semantics { contentDescription = "$label. " + if (open) "Close the calendar" else "Open the calendar" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.hdrInk)
                Spacer(Modifier.width(4.dp))
                Icon(Ic.chevDown, null, Modifier.size(16.dp).graphicsLayer { rotationZ = 180f * (calH.value / fullPx).coerceIn(0f, 1f) }, tint = p.hdrInk)
            }
            Spacer(Modifier.weight(1f))
            Pill("Today", enabled = shown != today, height = 34.dp, fontSize = 15, padH = 14.dp) {
                model.viewDate = today
                if (open) setOpen(false)
            }
        }
        WeekStrip(model, shown)
        Box(Modifier.fillMaxWidth().height(with(density) { calH.value.toDp() }).clipToBounds()) {
            MonthGrid(
                model, y, m, shown,
                onMonth = { delta -> ym += delta },
                onPick = { iso -> model.viewDate = iso; setOpen(false) },
                modifier = Modifier.wrapContentHeight(align = Alignment.Bottom, unbounded = true),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(18.dp).clickable { setOpen(!open) }
                .semantics { contentDescription = if (open) "Close calendar" else "Open calendar" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.hdrSub.copy(alpha = 0.55f)))
        }
    }
}

/** Mon–Sun for the shown week; swipe it sideways to see other weeks, tap a day to open it. */
@Composable
private fun WeekStrip(model: AppModel, shown: String) {
    val p = LocalPal.current
    val planner = model.planner
    val today = planner.today()
    val baseMon = remember { mondayOf(planner.today()) }
    fun weekPage(d: String) = START + Math.floorDiv(D.dn(d) - D.dn(baseMon), 7L).toInt()
    val wp = rememberPagerState(initialPage = weekPage(shown)) { 2 * START }
    LaunchedEffect(shown) {
        val t = weekPage(shown)
        if (t != wp.currentPage && !wp.isScrollInProgress) wp.animateScrollToPage(t)
    }
    val weekend = if (p.hdr.luminance() < 0.5f) Color(0xFFFFB74D) else p.flag
    HorizontalPager(state = wp, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), key = { it }) { pg ->
        val mon = D.addDays(baseMon, (pg - START) * 7L)
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                val d = D.addDays(mon, i.toLong())
                val sel = d == shown
                val isToday = d == today
                val has = planner.state.tasks.any { it.date == d && !it.tray }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { model.viewDate = d }
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = D.fmtShort(d) + if (sel) ", selected" else "" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(D.WD[(i + 1) % 7], fontSize = 12.sp, color = if (i >= 5) weekend else p.hdrSub)
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(if (sel) p.pill else Color.Transparent)
                            .let { if (isToday && !sel) it.border(1.5.dp, p.hdrInk.copy(alpha = 0.8f), CircleShape) else it },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            D.parse(d).dayOfMonth.toString(), fontSize = 16.sp,
                            fontWeight = if (sel || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) p.pillInk else if (d < today) p.hdrInk.copy(alpha = 0.7f) else p.hdrInk,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (has && !sel) p.hdrSub else Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(model: AppModel, y: Int, m: Int, shown: String, onMonth: (Int) -> Unit, onPick: (String) -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    val planner = model.planner
    val first = java.time.LocalDate.of(y, m, 1).toString()
    val lead = (D.wd(first) + 6) % 7
    val n = D.lastDay(y, m)
    val t0 = planner.today()
    Column(
        modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 6.dp).clip(RoundedCornerShape(16.dp)).background(p.surface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.left, "Previous month", p.muted) { onMonth(-1) }
            Text(D.MONTHS[m - 1] + " " + y, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconBtn(Ic.right, "Next month", p.muted) { onMonth(1) }
        }
        Row(Modifier.fillMaxWidth().height(22.dp)) {
            for (i in 0..6) Text(D.WD[(i + 1) % 7].take(2), Modifier.weight(1f), fontSize = 11.sp, color = p.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        val rows = (lead + n + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - lead + 1
                    if (day < 1 || day > n) {
                        Spacer(Modifier.weight(1f).height(44.dp))
                        continue
                    }
                    val iso = java.time.LocalDate.of(y, m, day).toString()
                    val has = planner.hasTasksOn(iso)
                    val rep = !has && planner.onlyRepeatsOn(iso)
                    val sel = iso == shown
                    val isToday = iso == t0
                    val fg = when { sel -> p.accentInk; iso < t0 -> p.muted; else -> p.ink }
                    Column(
                        Modifier.weight(1f).height(44.dp).padding(1.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) p.accent else Color.Transparent)
                            .let { if (isToday && !sel) it.border(2.dp, p.accent, RoundedCornerShape(10.dp)) else it }
                            .clickable { onPick(iso) }
                            .semantics { contentDescription = D.fmtShort(iso) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(day.toString(), fontSize = 15.sp, color = fg)
                        Spacer(Modifier.height(3.dp))
                        when {
                            has -> Box(Modifier.size(5.dp).clip(CircleShape).background(fg.copy(alpha = 0.75f)))
                            rep -> Box(Modifier.size(6.dp).border(1.3.dp, fg, CircleShape))
                            else -> Spacer(Modifier.size(5.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Notice(text: String, action: String, onAction: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(10.dp)).background(p.accentSoft).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), fontSize = 14.sp, color = p.ink)
        Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(10.dp)) {
            Text(action, fontWeight = FontWeight.Bold, color = p.accent, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SectionHead(s: Section, trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp).height(28.dp).semantics(mergeDescendants = true) {
            heading(); contentDescription = s.title
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumBadge(s.max)
        Spacer(Modifier.width(10.dp))
        Text(s.heading, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.onBg)
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun PlanDay(model: AppModel, d: String, drag: DragState) {
    val p = LocalPal.current
    val planner = model.planner
    if (planner.state.examples) {
        Notice("These are example tasks so you can try every feature.", "Clear examples") {
            model.change(undoable = true) { clearExamples() }
            model.say("Examples cleared", undo = true)
        }
    }
    val plan = planner.carryPlan(d)
    for (s in Section.entries) {
        val l = planner.dayList(d, s)
        val ghosts = plan?.get(s).orEmpty()
        val blanks = maxOf(0, s.max - l.size - ghosts.size)
        val listId = "day:$d:${s.key}"
        val overN = l.size + ghosts.size - s.max
        val draggingHere = drag.id != null && l.any { it.id == drag.id }
        Column(Modifier.padding(top = 12.dp).zIndex(if (draggingHere) 1f else 0f)) {
            SectionHead(s) {
                Pips(planner, d, s)
                Spacer(Modifier.weight(1f))
                if (overN > 0) {
                    Text(
                        "+$overN over", Modifier.clip(RoundedCornerShape(50)).background(Over.badge).padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            val panelShape = RoundedCornerShape(18.dp)
            Column(
                Modifier.fillMaxWidth()
                    .let { if (draggingHere) it else it.clip(panelShape) }
                    .background(p.surface, panelShape)
                    .onGloballyPositioned { drag.lists[listId] = it.boundsInRoot() },
            ) {
                val firstBlank = if (blanks > 0) "$listId:${l.size}" else null
                // One loop and one TaskLine call site for tasks and blank lines: when a blank becomes a task
                // it keeps the same key *and* the same place in the composition, so the text field survives.
                val rows = ArrayList<Triple<String, Task?, Int>>() // key, task, slot index (-1 = ghost preview)
                l.forEachIndexed { i, t -> rows.add(Triple("$listId:$i", t, i)) }
                ghosts.forEach { k -> rows.add(Triple("ghost:${k.id}", k, -1)) }
                for (j in 0 until blanks) rows.add(Triple("$listId:${l.size + j}", null, l.size + j))
                var ghostIdx = 0
                rows.forEachIndexed { n, (rk, t, idx) ->
                    if (n > 0) Divider()
                    key(rk) {
                        if (idx < 0) {
                            GhostRow(model, t!!, l.size + ghostIdx + 1 - s.max)
                            ghostIdx++
                        } else {
                            TaskLine(
                                model, rk, listId, t, RowCtx.DAY,
                                if (t == null) "Add a ${s.label.lowercase()} thing…" else "",
                                firstBlank,
                                nextKeyAfterCreate = if (t == null && blanks > 1) "$listId:${l.size + 1}" else null,
                                prevKey = if (idx > 0) "$listId:${idx - 1}" else null,
                                over = if (t != null) idx + 1 - s.max else 0,
                                drag = drag,
                            ) { text ->
                                val created = planner.addTask(Planner.ListKind.DAY, text, d, s, null)
                                if (created == null) model.fullMessage(s)
                                created
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tomorrow's preview of a task that will carry over at midnight if left unchecked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GhostRow(model: AppModel, k: Task, over: Int) {
    val p = LocalPal.current
    val planner = model.planner
    var menu by remember { mutableStateOf(false) }
    val c = planner.cat(k.cat)
    val bg = when {
        over == 1 -> Over.one
        over == 2 -> Over.two
        over == 3 -> Over.three
        over >= 4 -> Over.stripeYellow
        else -> p.tint(c) ?: p.surface
    }
    val ink = if (over > 0) Over.ink else p.muted
    Row(Modifier.fillMaxWidth().background(bg).padding(2.dp), verticalAlignment = Alignment.Top) {
        Spacer(Modifier.width(26.dp))
        CheckBox(
            done = false, important = k.important, dashed = true, borderOverride = if (over > 0) Over.ink else p.chk,
            description = "Mark done today",
        ) {
            model.change(undoable = true) { toggleDone(k) }
            model.say("Done today, so it won’t carry over", undo = true)
        }
        Row(
            Modifier.weight(1f).padding(top = 9.dp, bottom = 7.dp, start = 2.dp).semantics(mergeDescendants = true) {
                contentDescription = k.title + ", carries over from today if unchecked"
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.arrow, null, Modifier.size(13.dp), tint = if (over > 0) Over.ink else p.accent)
            Spacer(Modifier.width(5.dp))
            Text(
                k.title.ifBlank { "(untitled)" }, Modifier.weight(1f, fill = false), fontSize = 15.sp, lineHeight = 21.sp, color = ink,
                fontWeight = if (k.important) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (k.carry > 0) Text("  ×${k.carry}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (k.carry >= 3 && over <= 0) p.danger else ink)
        }
        Box {
            IconBtn(Ic.more, "Task options", ink) { menu = true }
            TaskMenu(model, k, menu) { menu = false }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PastDay(model: AppModel, d: String) {
    val p = LocalPal.current
    val planner = model.planner
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.clock, null, Modifier.size(18.dp), tint = p.onBg.copy(alpha = 0.8f))
        Spacer(Modifier.width(8.dp))
        Text("Past day · you can still check things off", fontSize = 14.sp, color = p.onBg.copy(alpha = 0.8f))
    }
    var any = false
    for (s in Section.entries) {
        val l = planner.dayList(d, s)
        if (l.isEmpty()) continue
        any = true
        val listId = "past:$d:${s.key}"
        Column(Modifier.padding(top = 12.dp)) {
            SectionHead(s) {
                Pips(planner, d, s)
                Spacer(Modifier.weight(1f))
                Text("${l.count { it.done }} of ${l.size} done", fontSize = 13.sp, color = p.muted)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface)) {
                l.forEachIndexed { i, t ->
                    if (i > 0) Divider()
                    key("$listId:$i") {
                        TaskLine(model, "$listId:$i", listId, t, RowCtx.PAST, "", null, null, null) { null }
                    }
                }
            }
        }
    }
    val log = planner.carriedFrom(d)
    if (log.isNotEmpty()) {
        any = true
        Column(Modifier.padding(top = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 0.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Carried forward", Modifier.weight(1f).semantics { heading() }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.onBg)
                Text(log.size.toString(), fontSize = 13.sp, color = p.muted)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface)) {
                log.forEachIndexed { i, e ->
                    if (i > 0) Divider()
                    val t = planner.find(e.id)
                    Row(Modifier.fillMaxWidth().padding(2.dp), verticalAlignment = Alignment.Top) {
                        Spacer(Modifier.width(26.dp))
                        if (t == null) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Ic.arrow, null, Modifier.size(14.dp), tint = p.muted) }
                            Column(Modifier.weight(1f).padding(top = 9.dp, bottom = 7.dp)) {
                                Text(e.title.ifBlank { "(untitled)" }, fontSize = 15.sp, color = p.muted)
                                Meta("Deleted", p.muted)
                            }
                        } else {
                            CheckBox(done = t.done, description = if (t.done) "Already done" else "Mark done on this day") {
                                if (t.done) model.say("Already marked done" + (t.completedAt?.let { " on " + D.fmtShort(it.take(10)) } ?: ""))
                                else model.sheet = Sheet.CarriedDone(t.id, d)
                            }
                            Column(Modifier.weight(1f).padding(top = 9.dp, bottom = 7.dp)) {
                                Text(
                                    t.title.ifBlank { "(untitled)" }, fontSize = 15.sp, lineHeight = 21.sp,
                                    color = if (t.done) p.muted else p.ink,
                                    textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                )
                                val where = t.date?.let { if (it == planner.today()) "Today" else D.fmtShort(it) } ?: "Bucket"
                                Meta(if (t.done) "Done " + D.fmtDate((t.completedAt ?: t.date ?: d).take(10)) else "Carried to $where", p.muted, icon = Ic.arrow)
                            }
                            Spacer(Modifier.width(40.dp))
                        }
                    }
                }
            }
        }
    }
    if (!any) {
        Text("Nothing was planned for this day.", Modifier.fillMaxWidth().padding(vertical = 28.dp), color = p.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
