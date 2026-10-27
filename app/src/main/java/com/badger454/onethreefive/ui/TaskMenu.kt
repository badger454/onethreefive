package com.badger454.onethreefive.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badger454.onethreefive.AppModel
import com.badger454.onethreefive.Sheet
import com.badger454.onethreefive.domain.Category
import com.badger454.onethreefive.domain.D
import com.badger454.onethreefive.domain.Task

/** Where a task lives, for menu headers: "Bucket", "Today · Medium", "Tue, Oct 27 · Small". */
fun whereLabel(model: AppModel, t: Task): String {
    val d = t.date ?: return if (t.proj != null) "Project" else "Bucket"
    return model.planner.relName(d) + (if (t.tray) " · Carried over" else t.section?.let { " · " + it.label } ?: "")
}

/** The ⋮ menu: a popover anchored to the button, matching the prototype. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskMenu(model: AppModel, t: Task, expanded: Boolean, onDismiss: () -> Unit) {
    val p = LocalPal.current
    val planner = model.planner
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(300.dp),
        containerColor = p.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        @Suppress("UNUSED_VARIABLE") val v = model.version
        val rule = planner.rule(t.ruleId)
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                t.title.ifBlank { "Untitled task" }, Modifier.padding(horizontal = 12.dp), fontSize = 16.sp,
                fontWeight = FontWeight.Bold, color = p.ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(whereLabel(model, t))
                planner.proj(t.proj)?.let { Tag(it.name, Ic.folder) }
            }
            MenuItem(Ic.copy, "Copy to another list") { onDismiss(); model.sheet = Sheet.Dest(t.id, copy = true) }
            Divider()
            MenuItem(Ic.move, "Move to another list") { onDismiss(); model.sheet = Sheet.Dest(t.id, copy = false) }
            Divider()
            val rem = t.reminderAt?.let { D.parseDT(it) }
            MenuItem(Ic.bell, if (rem != null) "Change reminder" else "Add reminder", hint = rem?.let { D.fmtTime(it) }) {
                onDismiss(); model.sheet = Sheet.Remind(t.id)
            }
            Divider()
            MenuItem(Ic.tag, "Category") { onDismiss(); model.sheet = Sheet.Categories() }
            CategoryChips(model, t, onDismiss)
            Divider()
            MenuItem(Ic.repeat, "Repeat", hint = if (rule != null) planner.describe(rule) else "Off") {
                onDismiss(); model.sheet = Sheet.Repeat(t.id)
            }
            if (rule == null) {
                Divider()
                MenuItem(Ic.ongoing, "Ongoing", trailing = { MiniToggle(t.ongoing) }) {
                    model.change { t.ongoing = !t.ongoing }
                    model.say(if (t.ongoing) "Ongoing: carries to the next day even when checked" else "No longer ongoing")
                }
            }
            Divider()
            MenuItem(Ic.trash, "Delete task", color = p.danger) {
                onDismiss()
                if (t.ruleId != null) model.sheet = Sheet.DeleteScope(t.id)
                else {
                    model.change(undoable = true) { remove(t) }
                    model.say("Task deleted", undo = true)
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val p = LocalPal.current
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(p.bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(12.dp), tint = p.muted)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One line of small category buttons under "Category". Tap to set, tap again to clear.
 * When there are more than fit, the rest sit behind "More"; "+" opens category editing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(model: AppModel, t: Task, onDismiss: () -> Unit) {
    val p = LocalPal.current
    val cats = model.planner.state.cats
    var open by remember { mutableStateOf(false) }
    val inline = if (cats.size <= 3) cats else cats.take(3)
    val rest = if (cats.size <= 3) emptyList() else cats.drop(3)
    val hiddenSelected = rest.firstOrNull { it.id == t.cat }
    fun pick(c: Category) = model.change { setCategory(t, if (t.cat == c.id) null else c.id) }

    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            for (c in inline) CatChip(c.name, p.catColor(c), selected = t.cat == c.id) { pick(c) }
            if (rest.isNotEmpty()) {
                MiniChip(selected = hiddenSelected != null, onClick = { open = !open }, description = "More categories (${rest.size})") {
                    if (hiddenSelected != null) {
                        Dot(p.catColor(hiddenSelected), 7.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("More", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (hiddenSelected != null) p.surface else p.ink)
                    Icon(if (open) Ic.chevUp else Ic.chevDown, null, Modifier.size(11.dp), tint = if (hiddenSelected != null) p.surface else p.ink)
                }
            } else {
                EditChip(model, onDismiss)
            }
        }
        if (open && rest.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (c in rest) CatChip(c.name, p.catColor(c), selected = t.cat == c.id) { pick(c) }
                EditChip(model, onDismiss)
            }
        }
    }
}

@Composable
private fun CatChip(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    MiniChip(selected, onClick) {
        Dot(color, 7.dp)
        Spacer(Modifier.width(4.dp))
        Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) p.surface else p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EditChip(model: AppModel, onDismiss: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier.size(22.dp).clip(shape).border(1.dp, p.muted.copy(alpha = 0.6f), shape)
            .clickable { onDismiss(); model.sheet = Sheet.Categories() }
            .semantics { contentDescription = "Edit categories" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ic.plus, null, Modifier.size(11.dp), tint = p.muted)
    }
}

@Composable
private fun MiniChip(selected: Boolean, onClick: () -> Unit, description: String? = null, content: @Composable () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(11.dp)
    var m = Modifier.height(22.dp).clip(shape)
        .background(if (selected) p.ink else Color.Transparent)
        .border(1.dp, if (selected) p.ink else p.line, shape)
        .clickable(onClick = onClick)
    if (description != null) m = m.semantics { contentDescription = description }
    Row(m.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}
