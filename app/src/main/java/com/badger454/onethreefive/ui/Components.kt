package com.badger454.onethreefive.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badger454.onethreefive.domain.Planner
import com.badger454.onethreefive.domain.Section

/** Rounded-square checkbox: outlined, filled with a check when done, dashed for blank lines. */
@Composable
fun CheckBox(
    done: Boolean,
    important: Boolean = false,
    dashed: Boolean = false,
    borderOverride: Color? = null,
    description: String,
    onClick: (() -> Unit)?,
) {
    val p = LocalPal.current
    val border = borderOverride ?: when {
        dashed -> p.line
        done -> p.done
        important -> p.flag
        else -> p.chk
    }
    val mod = Modifier
        .size(40.dp)
        .let { if (onClick != null) it.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Checkbox, onClick = onClick) else it }
        .semantics { contentDescription = description }
    Box(mod, contentAlignment = Alignment.Center) {
        val box = Modifier.size(22.dp)
        if (dashed) {
            Box(box.drawBehind {
                drawRoundRect(
                    color = border, cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
                )
            })
        } else {
            Box(
                box.clip(RoundedCornerShape(6.dp))
                    .background(if (done) p.done else Color.Transparent)
                    .border(2.dp, border, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Ic.check, null, Modifier.size(14.dp), tint = p.surface)
            }
        }
    }
}

@Composable
fun NumBadge(n: Int) {
    val p = LocalPal.current
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(p.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(n.toString(), color = p.accentInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Slot dots next to a section title: dashed = open, outlined = planned, filled = done. */
@Composable
fun Pips(planner: Planner, date: String, sec: Section) {
    val p = LocalPal.current
    val l = planner.dayList(date, sec)
    val size = when (sec) { Section.BIG -> 14.dp; Section.MEDIUM -> 11.dp; Section.SMALL -> 8.dp }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until sec.max) {
            val t = l.getOrNull(i)
            val m = Modifier.size(size)
            when {
                t == null -> Box(m.drawBehind {
                    drawCircle(p.line, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
                })
                t.done -> Box(m.clip(CircleShape).background(p.done))
                else -> Box(m.border(1.5.dp, p.chk, CircleShape))
            }
        }
    }
}

/** Small metadata item under a task title (12sp, optional leading icon). */
@Composable
fun Meta(text: String, color: Color, icon: ImageVector? = null, dot: Color? = null, bold: Boolean = false, onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick) else Modifier
    Row(m.heightIn(min = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(4.dp))
        }
        if (icon != null) {
            Icon(icon, null, Modifier.size(12.dp), tint = color)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Pill(text: String, enabled: Boolean, height: Dp, fontSize: Int, padH: Dp, description: String? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(50)
    var m = Modifier.height(height).clip(shape)
    m = if (enabled) m.background(p.pill).clickable(onClick = onClick) else m.border(1.dp, p.hdrSub.copy(alpha = 0.75f), shape)
    if (description != null) m = m.semantics { contentDescription = description }
    Box(m.padding(horizontal = padH), contentAlignment = Alignment.Center) {
        Text(
            text, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, lineHeight = fontSize.sp,
            color = if (enabled) p.pillInk else p.hdrSub.copy(alpha = 0.75f),
        )
    }
}

/** The compact ON/OFF switch used for "Ongoing". */
@Composable
fun MiniToggle(on: Boolean) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier.width(46.dp).height(22.dp).clip(shape)
            .background(if (on) Over.toggleOn else Color.Transparent)
            .border(2.dp, if (on) Over.toggleOn else p.ink, shape),
    ) {
        Box(
            Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 2.dp)
                .size(14.dp).clip(CircleShape).background(if (on) Color.White else p.ink),
        )
        Text(
            if (on) "ON" else "OFF", fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp,
            color = if (on) Color.White else p.ink,
            modifier = Modifier.align(if (on) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 5.dp),
        )
    }
}

@Composable
fun Btn(text: String, kind: BtnKind = BtnKind.PRIMARY, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(10.dp)
    val (bg, fg, border) = when (kind) {
        BtnKind.PRIMARY -> Triple(p.accent, p.accentInk, null)
        BtnKind.GHOST -> Triple(Color.Transparent, p.accent, BorderStroke(1.dp, p.line))
        BtnKind.DANGER -> Triple(p.danger, p.surface, null)
        BtnKind.DANGER_GHOST -> Triple(Color.Transparent, p.danger, BorderStroke(1.dp, p.line))
    }
    var m = modifier.heightIn(min = 44.dp).clip(shape).background(if (enabled) bg else bg.copy(alpha = 0.45f))
    if (border != null) m = m.border(border, shape)
    if (enabled) m = m.clickable(onClick = onClick)
    Box(m.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) fg else fg.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

enum class BtnKind { PRIMARY, GHOST, DANGER, DANGER_GHOST }

/** Two-option segmented control. */
@Composable
fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.bg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (on) p.surface else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(o, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) p.ink else p.muted)
            }
        }
    }
}

/** Rounded chip that can be toggled (quick dates, weekdays, pattern pickers). */
@Composable
fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier.height(34.dp).clip(shape)
            .background(if (selected) p.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) p.accent else p.line, shape)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontSize = 14.sp, maxLines = 1,
            color = if (!enabled) p.muted.copy(alpha = 0.5f) else if (selected) p.accent else p.ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier, fontSize = 12.sp, letterSpacing = 0.7.sp,
        color = LocalPal.current.muted,
    )
}

@Composable
fun Lead(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(bottom = 12.dp), fontSize = 14.sp, color = LocalPal.current.muted)
}

/** Section choice for move/copy/schedule: pips, title, and "2/3", "Full" or "Already here". */
@Composable
fun SectionChoice(planner: Planner, date: String, sec: Section, status: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).border(1.dp, p.line, shape)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val a = if (enabled) 1f else 0.45f
        Box(Modifier.width(84.dp)) { Pips(planner, date, sec) }
        Text(sec.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = p.ink.copy(alpha = a))
        Text(status, fontSize = 13.sp, color = p.muted.copy(alpha = a))
    }
}

/** A tappable value field (dates, times) styled like the prototype's inputs. */
@Composable
fun FieldButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.height(44.dp).clip(shape).background(p.bg).border(1.dp, p.line, shape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = p.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Number stepper (interval, day of month). */
@Composable
fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    Row(
        Modifier.height(44.dp).clip(shape).background(p.bg).border(1.dp, p.line, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clickable(enabled = value > min) { onChange(value - 1) }, contentAlignment = Alignment.Center) {
            Icon(Ic.minus, "Decrease", Modifier.size(18.dp), tint = if (value > min) p.ink else p.line)
        }
        Text(value.toString(), Modifier.width(36.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink, style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        Box(Modifier.size(44.dp).clickable(enabled = value < max) { onChange(value + 1) }, contentAlignment = Alignment.Center) {
            Icon(Ic.plus, "Increase", Modifier.size(18.dp), tint = if (value < max) p.ink else p.line)
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector?, text: String, color: Color? = null, hint: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = color ?: p.muted)
            Spacer(Modifier.width(14.dp))
        }
        Text(text, Modifier.weight(1f), fontSize = 15.sp, color = color ?: p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hint != null) Text(hint, fontSize = 13.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        if (trailing != null) trailing()
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPal.current.line))
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Suppress("unused")
private fun Modifier.nudge(y: Dp) = this.offset(y = y)

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = LocalPal.current.ink)
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LocalPal.current.onBg)
    }
}
