package com.badger454.onethreefive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.badger454.onethreefive.AppModel
import com.badger454.onethreefive.Sheet
import com.badger454.onethreefive.domain.Planner

/** Plain header band for Bucket and Projects: small eyebrow line and a title. */
@Composable
fun ListHeader(eyebrow: String, title: String) {
    val p = LocalPal.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(p.hdr)
            .statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Text(eyebrow.uppercase(), fontSize = 12.sp, letterSpacing = 0.7.sp, color = p.hdrSub)
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.hdrInk, modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun BucketScreen(model: AppModel) {
    val p = LocalPal.current
    @Suppress("UNUSED_VARIABLE") val v = model.version
    val planner = model.planner
    val l = planner.bucketList()
    val drag = remember { DragState() }
    drag.onDrop = { id, at ->
        val cy = at.y
        planner.find(id)?.let { t ->
            val items = planner.bucketList().filter { it.id != id }
            val idx = items.count { (drag.rows[it.id]?.center?.y ?: Float.MAX_VALUE) < cy }
            planner.dropInBucket(t, idx)
            model.commit()
        }
    }
    Column(Modifier.fillMaxSize()) {
        ListHeader("Unplanned · ${l.count { !it.done }} open", "Bucket")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 24.dp)) {
            val listId = "bucket"
            val dragging = drag.id != null
            Column(
                Modifier.fillMaxWidth().zIndex(1f).let { if (dragging) it else it.clip(RoundedCornerShape(18.dp)) }
                    .background(p.surface, RoundedCornerShape(18.dp))
                    .onGloballyPositioned { drag.lists[listId] = it.boundsInRoot() },
            ) {
                // Tasks and the blank line share one call site so a new task keeps its text field.
                for (i in 0..l.size) {
                    if (i > 0) Divider()
                    val t = l.getOrNull(i)
                    key("$listId:$i") {
                        TaskLine(
                            model, "$listId:$i", listId, t, RowCtx.BUCKET, if (t == null) "Add to bucket…" else "", "$listId:${l.size}",
                            if (t == null) "$listId:${l.size + 1}" else null, if (i > 0) "$listId:${i - 1}" else null, drag = drag,
                        ) { text -> planner.addTask(Planner.ListKind.BUCKET, text, null, null, null) }
                    }
                }
            }
            if (l.any { it.done }) {
                LinkButton("Clear completed") {
                    model.change(undoable = true) { clearBucketDone() }
                    model.say("Cleared completed", undo = true)
                }
            }
        }
    }
}

@Composable
fun LinkButton(text: String, color: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp)) {
        Text(text, color = color ?: p.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun ProjectsScreen(model: AppModel) {
    val p = LocalPal.current
    @Suppress("UNUSED_VARIABLE") val v = model.version
    val planner = model.planner
    val active = planner.state.projects.filter { !it.archived }
    val archived = planner.state.projects.filter { it.archived }
    val open = planner.state.tasks.count { t -> t.proj != null && !t.done && active.any { it.id == t.proj } }
    val drag = remember { DragState() }
    drag.onDrop = { id, at ->
        val cy = at.y
        planner.find(id)?.let { t ->
            val pid = t.proj
            if (pid != null) {
                val items = planner.projList(pid).filter { it.id != id }
                val idx = items.count { (drag.rows[it.id]?.center?.y ?: Float.MAX_VALUE) < cy }
                planner.dropInProject(t, idx)
                model.commit()
            }
        }
    }
    var newName by remember { mutableStateOf("") }
    fun add() {
        val pr = planner.addProject(newName) ?: return
        newName = ""
        model.commit()
        model.focusKey = "proj:${pr.id}:0"
    }
    Column(Modifier.fillMaxSize()) {
        ListHeader("${active.size} active · $open open action${if (open == 1) "" else "s"}", "Projects")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            // new project
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).background(p.surface)
                    .border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = it.replace("\n", "").take(80) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    decorationBox = { inner ->
                        Box {
                            if (newName.isEmpty()) Text("New project name…", color = p.muted.copy(alpha = 0.75f), fontSize = 16.sp)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                Btn("Add", enabled = newName.isNotBlank()) { add() }
            }
            if (active.isEmpty()) {
                Text(
                    "Break a bigger goal into small actions here, then schedule each action into a day.",
                    Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp), color = p.muted, textAlign = TextAlign.Center,
                )
            }
            for (pr in active) {
                key(pr.id) {
                    val l = planner.projList(pr.id)
                    val listId = "proj:${pr.id}"
                    val draggingHere = drag.id != null && l.any { it.id == drag.id }
                    Column(Modifier.padding(top = 18.dp).zIndex(if (draggingHere) 1f else 0f)) {
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(pr.name, Modifier.weight(1f).semantics { heading() }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.onBg)
                            Text(planner.projStat(pr.id), fontSize = 13.sp, color = p.muted)
                            IconBtn(Ic.more, "Project options", p.muted) { model.sheet = Sheet.ProjectMenu(pr.id) }
                        }
                        Column(
                            Modifier.fillMaxWidth().let { if (draggingHere) it else it.clip(RoundedCornerShape(18.dp)) }
                                .background(p.surface, RoundedCornerShape(18.dp))
                                .onGloballyPositioned { drag.lists[listId] = it.boundsInRoot() },
                        ) {
                            for (i in 0..l.size) {
                                if (i > 0) Divider()
                                val t = l.getOrNull(i)
                                key("$listId:$i") {
                                    TaskLine(
                                        model, "$listId:$i", listId, t, RowCtx.PROJ, if (t == null) "Add an action…" else "", "$listId:${l.size}",
                                        if (t == null) "$listId:${l.size + 1}" else null, if (i > 0) "$listId:${i - 1}" else null, drag = drag,
                                    ) { text -> planner.addTask(Planner.ListKind.PROJECT, text, null, null, pr.id) }
                                }
                            }
                        }
                    }
                }
            }
            if (planner.state.tasks.any { t -> t.done && active.any { it.id == t.proj } }) {
                LinkButton("Clear checked actions") {
                    var n = 0
                    model.change(undoable = true) { n = clearCheckedActions() }
                    model.say("Cleared $n checked action${if (n == 1) "" else "s"}", undo = true)
                }
            }
            if (archived.isNotEmpty()) {
                var show by remember { mutableStateOf(false) }
                Box(Modifier.padding(top = 26.dp).clip(RoundedCornerShape(8.dp)).clickable { show = !show }.padding(6.dp)) {
                    Text((if (show) "▾ " else "▸ ") + "Completed projects (${archived.size})", color = p.muted, fontSize = 14.sp)
                }
                if (show) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface)) {
                        archived.forEachIndexed { i, pr ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(pr.name + " · " + planner.projStat(pr.id), Modifier.weight(1f), color = p.muted, fontSize = 15.sp)
                                Btn("Restore", BtnKind.GHOST) {
                                    model.change { pr.archived = false }
                                    model.say("Project restored")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
