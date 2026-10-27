package com.badger454.onethreefive.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * All of the app's rules, as plain Kotlin over [AppState]. No Android dependencies.
 * Behavior follows the HTML prototype (the reference implementation).
 * Every method mutates [state] in place; callers persist and redraw afterwards.
 */
class Planner(var state: AppState, private val clock: Clock = SystemClock) {

    // ---------- time ----------
    fun now(): LocalDateTime = clock.now().plusDays(state.offset.toLong())
    fun todayDate(): LocalDate = now().toLocalDate()
    fun today(): String = todayDate().toString()
    fun tomorrow(): String = D.addDays(today(), 1)

    // ---------- queries ----------
    private val byOrder = compareBy<Task>({ it.order }, { it.id })

    fun find(id: String): Task? = state.tasks.firstOrNull { it.id == id }
    fun rule(id: String?): Rule? = if (id == null) null else state.rules.firstOrNull { it.id == id }
    fun cat(id: String?): Category? = if (id == null) null else state.cats.firstOrNull { it.id == id }
    fun proj(id: String?): Project? = if (id == null) null else state.projects.firstOrNull { it.id == id }

    fun dayList(date: String, sec: Section): List<Task> =
        state.tasks.filter { it.date == date && !it.tray && it.section == sec }.sortedWith(byOrder)

    fun trayList(date: String): List<Task> = state.tasks.filter { it.date == date && it.tray }.sortedWith(byOrder)
    fun bucketList(): List<Task> = state.tasks.filter { it.date == null && it.proj == null }.sortedWith(byOrder)
    fun projList(pid: String): List<Task> = state.tasks.filter { it.proj == pid }.sortedWith(compareBy({ it.porder }, { it.id }))

    fun nextOrder(list: List<Task>): Int = if (list.isEmpty()) 1 else list.maxOf { it.order } + 1
    private fun nextPorder(pid: String): Int = projList(pid).let { l -> if (l.isEmpty()) 1 else l.maxOf { it.porder } + 1 }
    private fun renumber(list: List<Task>) = list.forEachIndexed { i, t -> t.order = i + 1 }

    fun hasRoom(date: String, sec: Section, exceptId: String? = null): Boolean =
        dayList(date, sec).count { it.id != exceptId } < sec.max

    fun isWork(t: Task): Boolean = cat(t.cat)?.name?.trim()?.lowercase() == "work"

    private fun secIdx(s: Section?): Int = s?.ordinal ?: -1

    // ---------- recurrence ----------
    fun matches(r: Rule, iso: String): Boolean {
        if (r.start.isEmpty() || iso < r.start || (r.end.isNotEmpty() && iso > r.end)) return false
        val p = D.parse(iso)
        val s = D.parse(r.start)
        val n = r.interval.coerceAtLeast(1)
        return when (r.type) {
            RepeatType.DAILY -> (D.dn(iso) - D.dn(r.start)) % n == 0L
            RepeatType.WEEKLY -> {
                if (!r.weekdays.contains(D.wd(iso))) return false
                val w = Math.floorDiv((D.dn(iso) - D.wd(iso)) - (D.dn(r.start) - D.wd(r.start)), 7L)
                w % n == 0L
            }
            RepeatType.MONTHLY_DATE -> {
                val mb = (p.year - s.year) * 12 + (p.monthValue - s.monthValue)
                if (mb % n != 0) return false
                p.dayOfMonth == minOf(r.dom, D.lastDay(p.year, p.monthValue))
            }
            RepeatType.MONTHLY_WEEKDAY -> {
                val mb = (p.year - s.year) * 12 + (p.monthValue - s.monthValue)
                if (mb % n != 0) return false
                if (D.wd(iso) != r.weekday) return false
                if (r.ord == -1) p.dayOfMonth + 7 > D.lastDay(p.year, p.monthValue)
                else (p.dayOfMonth + 6) / 7 == r.ord
            }
            RepeatType.ANNUAL -> {
                if ((p.year - s.year) % n != 0) return false
                p.monthValue == r.month && p.dayOfMonth == minOf(r.day, D.lastDay(p.year, p.monthValue))
            }
        }
    }

    fun nextDates(r: Rule, from: String, k: Int): List<String> {
        val out = ArrayList<String>()
        if (r.start.isEmpty()) return out
        var d = if (from < r.start) r.start else from
        var i = 0
        while (i < 1500 && out.size < k) {
            if (r.end.isNotEmpty() && d > r.end) break
            if (matches(r, d)) out.add(d)
            d = D.addDays(d, 1)
            i++
        }
        return out
    }

    fun describe(r: Rule?): String {
        if (r == null) return ""
        val n = r.interval.coerceAtLeast(1)
        return when (r.type) {
            RepeatType.DAILY -> if (n == 1) "Every day" else "Every $n days"
            RepeatType.WEEKLY -> {
                val ds = r.weekdays.sorted().joinToString(", ") { D.WD[it] }
                (if (n == 1) "Weekly" else "Every $n weeks") + " on " + ds.ifEmpty { "—" }
            }
            RepeatType.MONTHLY_DATE -> (if (n == 1) "Monthly" else "Every $n months") + " on the " + D.ordinal(r.dom)
            RepeatType.MONTHLY_WEEKDAY -> (if (n == 1) "Monthly" else "Every $n months") + " on the " + (D.ORD[r.ord] ?: "") + " " + D.WDL[r.weekday.coerceIn(0, 6)]
            RepeatType.ANNUAL -> (if (n == 1) "Every year" else "Every $n years") + " on " + D.MON[(r.month - 1).coerceIn(0, 11)] + " " + r.day
        }
    }

    private fun skipKey(ruleId: String, date: String) = "$ruleId|$date"

    /** Creates repeating-task instances for [date] (today or later). Deterministic ids prevent duplicates. */
    fun materialize(date: String): Boolean {
        if (date < today()) return false
        var ch = false
        for (r in state.rules) {
            if (!matches(r, date)) continue
            if (state.tasks.any { it.ruleId == r.id && it.date == date }) continue
            if (state.skips.contains(skipKey(r.id, date))) continue
            val id = "r" + r.id + "_" + date
            if (find(id) != null) continue
            state.tasks.add(
                Task(
                    id = id, title = r.title, date = date, origin = date, section = r.section,
                    important = r.important, cat = r.cat, ruleId = r.id, order = nextOrder(dayList(date, r.section)),
                )
            )
            ch = true
        }
        return ch
    }

    // ---------- carry-over ----------
    /** Anything left in the tray drops into its own section, even past the 1-3-5 limit. */
    fun autoSlot(date: String): Boolean {
        if (date < today()) return false
        var ch = false
        for (k in trayList(date)) {
            val sec = k.section ?: Section.SMALL
            k.section = sec
            k.tray = false
            k.order = nextOrder(dayList(date, sec))
            ch = true
        }
        return ch
    }

    /**
     * Computed midnight reset, safe to run any number of times.
     * Unfinished (and "ongoing") tasks from earlier days move to today and are appended to their own section.
     * A checked ongoing task leaves a done copy behind on its day. Work tasks skip Saturday and Sunday.
     */
    fun rollover(): Boolean {
        val t = today()
        var ch = false
        val due = state.tasks
            .filter { k -> k.date != null && k.date!! < t && (!k.done || k.ongoing) && k.ruleId == null }
            .sortedWith(compareBy<Task>({ it.date }, { if (it.tray) 1 else 0 }, { secIdx(it.section) }, { it.order }))
        for (k in due) {
            val from = k.date!!
            if (k.done) {
                val copy = k.copyAll()
                copy.id = Ids.new(); copy.ongoing = false; copy.ongoingCopy = true; copy.proj = null
                copy.reminderAt = null; copy.fired = false
                state.tasks.add(copy)
                k.done = false; k.completedAt = null; k.carry = 0
            } else {
                state.log.add(LogEntry(from, k.title, k.id))
                k.carry += (D.dn(t) - D.dn(from)).toInt()
            }
            if (isWork(k) && D.isWeekend(t)) {
                val mo = D.nextMonday(t)
                val sec = k.section ?: Section.SMALL
                k.carry += (D.dn(mo) - D.dn(t)).toInt()
                k.section = sec; k.date = mo; k.tray = false; k.order = nextOrder(dayList(mo, sec))
                ch = true
                continue
            }
            k.date = t; k.tray = true; k.order = nextOrder(trayList(t))
            ch = true
        }
        if (autoSlot(t)) ch = true
        if (state.last != t) {
            val cut = D.addDays(t, -120)
            state.log.removeAll { it.date < cut }
            val now = now()
            for (k in state.tasks) {
                val r = k.reminderAt?.let { D.parseDT(it) }
                if (r != null && r.isBefore(now)) { k.reminderAt = null; k.fired = false }
            }
            state.last = t
            ch = true
        }
        if (materialize(t)) ch = true
        return ch
    }

    /** Tomorrow's preview of today's tasks that will roll over at midnight (Work tasks preview on Monday instead of the weekend). */
    fun carryPlan(d: String): Map<Section, List<Task>>? {
        val t0 = today()
        val tm = D.addDays(t0, 1)
        val skip = D.isWeekend(tm)
        val mo = if (skip) D.nextMonday(tm) else null
        if (d != tm && d != mo) return null
        val due = state.tasks
            .filter { k -> k.date == t0 && (!k.done || k.ongoing) && k.ruleId == null && ((if (skip && isWork(k)) mo else tm) == d) }
            .sortedWith(compareBy<Task>({ if (it.tray) 1 else 0 }, { secIdx(it.section) }, { it.order }))
        return Section.entries.associateWith { s -> due.filter { (it.section ?: Section.SMALL) == s } }
    }

    /** Distinct tasks carried off [date], newest log entries kept in log order. */
    fun carriedFrom(date: String): List<LogEntry> {
        val seen = HashSet<String>()
        return state.log.filter { it.date == date && seen.add(it.id) }
    }

    // ---------- checking off ----------
    sealed class Outcome {
        object None : Outcome()
        data class Message(val text: String, val undo: Boolean = false) : Outcome()
        data class Full(val section: Section) : Outcome()
    }

    fun toggleDone(t: Task): Outcome {
        t.done = !t.done
        t.completedAt = if (t.done) D.localDT(now()) else null
        if (!t.done && t.date != null && t.date!! < today() && t.ruleId == null) {
            rollover()
            return Outcome.Message("Unchecked, so it’s back on today’s list")
        }
        return Outcome.None
    }

    /** "Done on {from}?" → Delete from today: the carried task goes back to [from] as completed. */
    fun markDoneOnCarriedDay(t: Task, from: String) {
        val days = t.date?.let { maxOf(0L, D.dn(it) - D.dn(from)).toInt() } ?: 0
        detach(t)
        t.carry = maxOf(0, t.carry - days)
        state.log.removeAll { it.id == t.id && it.date >= from }
        t.date = from
        t.tray = false
        val sec = t.section ?: Section.SMALL
        t.section = sec
        t.order = nextOrder(dayList(from, sec).filter { it.id != t.id })
        t.done = true
        t.completedAt = from + "T" + D.localDT(now()).substring(11)
        autoSlot(today())
    }

    // ---------- adding, editing, deleting ----------
    enum class ListKind { DAY, BUCKET, PROJECT }

    /** Typing into a blank line. Returns null when the day's section is already full. */
    fun addTask(kind: ListKind, title: String, date: String?, sec: Section?, pid: String?): Task? {
        val d = if (kind == ListKind.DAY) date else null
        if (kind == ListKind.DAY && (d == null || sec == null || !hasRoom(d, sec))) return null
        val list = if (d == null) bucketList() else dayList(d, sec!!)
        val t = Task(
            id = Ids.new(), title = title, date = d, origin = d, section = if (kind == ListKind.DAY) sec else null,
            order = nextOrder(list), proj = if (kind == ListKind.PROJECT) pid else null,
            porder = if (kind == ListKind.PROJECT && pid != null) nextPorder(pid) else 0,
        )
        state.tasks.add(t)
        return t
    }

    fun remove(t: Task) {
        state.tasks.removeAll { it === t || it.id == t.id }
    }

    /** Turns a repeating instance into a one-off; that day won't regenerate. */
    fun detach(t: Task) {
        val r = t.ruleId
        val d = t.date
        if (r != null && d != null) state.skips.add(skipKey(r, d))
        t.ruleId = null
    }

    fun setCategory(t: Task, catId: String?) {
        t.cat = catId
        val r = rule(t.ruleId) ?: return
        r.cat = catId
        val t0 = today()
        state.tasks.forEach { x -> if (x.ruleId == r.id && x.date != null && x.date!! >= t0) x.cat = catId }
    }

    // ---------- move / copy ----------
    enum class Mode { MOVE, COPY }

    fun destLabel(t: Task, mode: Mode, date: String?, sec: Section?): String =
        if (date == null) (if (mode == Mode.MOVE && t.proj != null) "project" else "bucket")
        else relName(date) + " · " + (sec?.label ?: "")

    fun relName(date: String): String = when (date) {
        today() -> "Today"
        tomorrow() -> "Tomorrow"
        else -> D.fmtShort(date)
    }

    /** date == null means the bucket (or, for a project action being moved, back to its project). */
    fun applyDest(t: Task, mode: Mode, date: String?, sec: Section?): Outcome {
        val label = destLabel(t, mode, date, sec)
        if (mode == Mode.COPY) {
            val list = if (date == null) bucketList() else dayList(date, sec!!)
            state.tasks.add(
                Task(
                    id = Ids.new(), title = t.title, important = t.important, cat = t.cat, date = date, origin = date,
                    section = if (date == null) null else sec, order = nextOrder(list), notes = t.notes,
                )
            )
            return Outcome.Message("Copied to $label", undo = true)
        }
        if (date != null && sec != null && !hasRoom(date, sec, t.id)) return Outcome.Full(sec)
        val wasUnplanned = t.proj != null && t.date == null
        detach(t)
        val list = if (date == null) bucketList() else dayList(date, sec!!)
        t.order = nextOrder(list.filter { it.id != t.id })
        t.date = date
        t.section = if (date == null) null else sec
        t.tray = false
        if (date != null && t.origin == null) t.origin = date
        return Outcome.Message((if (wasUnplanned && date != null) "Scheduled for " else "Moved to ") + label, undo = true)
    }

    /** Dropped on "Bucket": becomes an undated bucket task (a project action leaves its project). */
    fun moveToBucket(t: Task) {
        detach(t)
        t.proj = null
        t.date = null
        t.section = null
        t.tray = false
        t.order = nextOrder(bucketList().filter { it.id != t.id })
    }

    /** Dropped on "Project": becomes an unscheduled action at the end of that project. */
    fun moveToProject(t: Task, pid: String) {
        detach(t)
        val same = t.proj == pid
        t.date = null
        t.section = null
        t.tray = false
        t.proj = pid
        if (!same) t.porder = projList(pid).filter { it.id != t.id }.let { l -> if (l.isEmpty()) 1 else l.maxOf { it.porder } + 1 }
    }

    /** Drag and drop on the Day screen: put [t] at [index] in [sec] of its own day. */
    fun dropInSection(t: Task, sec: Section, index: Int): Outcome {
        val date = t.date ?: return Outcome.None
        if ((t.tray || t.section != sec) && !hasRoom(date, sec, t.id)) return Outcome.Full(sec)
        val list = dayList(date, sec).filter { it.id != t.id }.toMutableList()
        list.add(index.coerceIn(0, list.size), t)
        t.section = sec
        t.tray = false
        renumber(list)
        return Outcome.None
    }

    fun dropInBucket(t: Task, index: Int) {
        val list = bucketList().filter { it.id != t.id }.toMutableList()
        list.add(index.coerceIn(0, list.size), t)
        renumber(list)
    }

    fun dropInProject(t: Task, index: Int) {
        val pid = t.proj ?: return
        val list = projList(pid).filter { it.id != t.id }.toMutableList()
        list.add(index.coerceIn(0, list.size), t)
        list.forEachIndexed { i, x -> x.porder = i + 1 }
    }

    // ---------- repeating ----------
    fun newRuleDraft(t: Task): Rule {
        val st = if (t.date != null && t.date!! >= today()) t.date!! else today()
        val p = D.parse(st)
        val ld = D.lastDay(p.year, p.monthValue)
        return Rule(
            id = "", type = RepeatType.WEEKLY, interval = 1, weekdays = mutableListOf(D.wd(st)), dom = p.dayOfMonth,
            ord = if (p.dayOfMonth + 7 > ld) -1 else minOf(4, (p.dayOfMonth + 6) / 7), weekday = D.wd(st),
            month = p.monthValue, day = p.dayOfMonth, section = t.section ?: Section.SMALL, start = st, end = "",
        )
    }

    fun saveRule(t: Task, existing: Rule?, draft: Rule): Rule {
        val t0 = today()
        if (existing != null) {
            existing.setFrom(draft)
            existing.title = t.title; existing.important = t.important; existing.cat = t.cat
            state.tasks.removeAll { x -> x.ruleId == existing.id && x !== t && !x.done && x.date != null && x.date!! > t0 }
            if (t.date != null && !matches(existing, t.date!!)) t.ruleId = null
            materialize(t0)
            return existing
        }
        val r = draft.copyAll()
        r.id = Ids.new(); r.title = t.title; r.important = t.important; r.cat = t.cat
        state.rules.add(r)
        val d = t.date
        if (d == null) remove(t) else if (matches(r, d)) t.ruleId = r.id
        materialize(t0)
        return r
    }

    fun stopRepeat(t: Task, r: Rule) {
        val t0 = today()
        state.tasks.removeAll { x -> x.ruleId == r.id && x !== t && !x.done && x.date != null && x.date!! >= t0 }
        state.tasks.forEach { x -> if (x.ruleId == r.id && x.date != null && x.date!! >= t0) x.ruleId = null }
        state.rules.remove(r)
    }

    /** Title change on a repeating instance. */
    fun applyTitleScope(t: Task, allFuture: Boolean) {
        val r = rule(t.ruleId) ?: return
        if (!allFuture) { detach(t); return }
        r.title = t.title
        val from = t.date ?: today()
        state.tasks.forEach { x -> if (x.ruleId == r.id && x.date != null && x.date!! >= from) x.title = t.title }
    }

    fun deleteWithScope(t: Task, allFuture: Boolean) {
        val r = rule(t.ruleId)
        val d = t.date
        if (r == null || d == null) { remove(t); return }
        if (!allFuture) {
            state.skips.add(skipKey(r.id, d))
            remove(t)
            return
        }
        r.end = D.addDays(d, -1)
        state.tasks.removeAll { x -> x.ruleId == r.id && x.date != null && x.date!! >= d }
        if (r.end < r.start) state.rules.remove(r)
    }

    // ---------- categories ----------
    fun addCategory(name: String): Category? {
        val n = name.trim().take(24)
        if (n.isEmpty()) return null
        val used = state.cats.filter { !it.isCustom }.map { it.color }
        val col = (0..7).firstOrNull { it !in used } ?: (state.cats.size % 8)
        val c = Category("k" + Ids.new().take(8), n, col)
        state.cats.add(c)
        return c
    }

    fun deleteCategory(c: Category) {
        state.cats.remove(c)
        state.tasks.forEach { if (it.cat == c.id) it.cat = null }
        state.rules.forEach { if (it.cat == c.id) it.cat = null }
    }

    fun moveCategory(from: Int, to: Int) {
        if (from !in state.cats.indices) return
        val c = state.cats.removeAt(from)
        state.cats.add(to.coerceIn(0, state.cats.size), c)
    }

    // ---------- projects ----------
    fun addProject(name: String): Project? {
        val n = name.trim().take(80)
        if (n.isEmpty()) return null
        val p = Project("p" + Ids.new().take(8), n)
        state.projects.add(0, p)
        return p
    }

    fun deleteProject(p: Project) {
        state.tasks.removeAll { it.proj == p.id && it.date == null }
        state.tasks.forEach { if (it.proj == p.id) it.proj = null }
        state.projects.remove(p)
    }

    fun projStat(pid: String): String {
        val l = projList(pid)
        val d = l.count { it.done }
        val sc = l.count { it.date != null && !it.done }
        return "$d/${l.size} done" + if (sc > 0) " · $sc scheduled" else ""
    }

    /** Removes checked actions from active projects. Scheduled ones stay on their days without the label. */
    fun clearCheckedActions(): Int {
        val live = state.projects.filter { !it.archived }.map { it.id }.toSet()
        var n = 0
        val keep = ArrayList<Task>()
        for (x in state.tasks) {
            if (!(x.done && x.proj in live)) { keep.add(x); continue }
            n++
            if (x.date != null) { x.proj = null; keep.add(x) }
        }
        state.tasks = keep
        return n
    }

    fun clearBucketDone() {
        state.tasks.removeAll { it.date == null && it.proj == null && it.done }
    }

    // ---------- reminders ----------
    fun dueReminders(): List<Task> {
        val now = now()
        return state.tasks.filter { t ->
            val r = t.reminderAt?.let { D.parseDT(it) }
            r != null && !t.fired && !t.done && !r.isAfter(now)
        }
    }

    // ---------- calendar ----------
    fun hasTasksOn(iso: String): Boolean = state.tasks.any { it.date == iso } || state.log.any { it.date == iso }

    fun onlyRepeatsOn(iso: String): Boolean =
        !hasTasksOn(iso) && iso >= today() && state.rules.any { matches(it, iso) && !state.skips.contains(skipKey(it.id, iso)) }

    // ---------- whole-state ----------
    fun snapshot(): String = state.toJson()

    fun restore(json: String) {
        state = AppState.fromJson(json)
    }

    fun clearExamples() {
        val off = state.offset
        val theme = state.theme
        val auto = state.autoDark
        state = AppState(offset = off, theme = theme, autoDark = auto)
        state.last = today()
    }

    fun eraseAll() = clearExamples()

    /** Sample data dated around today, so every feature can be tried. */
    fun seedExamples() {
        val off = state.offset
        val theme = state.theme
        val auto = state.autoDark
        state = AppState(offset = off, theme = theme, autoDark = auto)
        val t = today()
        val y = D.addDays(t, -1)
        val tm = D.addDays(t, 1)
        fun add(title: String, date: String? = null, sec: Section? = null, order: Int = 1, cat: String? = null,
                done: Boolean = false, completedAt: String? = null, important: Boolean = false,
                proj: String? = null, porder: Int = 0, reminderAt: String? = null) {
            state.tasks.add(Task(Ids.new(), title, date, date, sec, false, order, done, completedAt, important, 0, reminderAt,
                false, null, cat, proj, porder))
        }
        add("Finish the quarterly report draft", y, Section.BIG, 1, "c1", true, "${y}T16:40")
        add("Back up the photo library", y, Section.MEDIUM, 1, "c2")
        add("Order printer paper", y, Section.SMALL, 1, "c1", true, "${y}T09:12")
        add("Water the houseplants", y, Section.SMALL, 2, "c2")
        add("Plan the weekend trip", t, Section.BIG, 1, "c3", important = true)
        add("Compare three new routers", t, Section.MEDIUM, 1, "c2", proj = "pnet", porder = 1)
        add("Reply to the team about the schedule", t, Section.MEDIUM, 2, "c1", true, "${t}T08:05")
        add("Clean out the hall closet", t, Section.MEDIUM, 3, "c2")
        add("Sharpen the kitchen knives", t, Section.SMALL, 1, "c2")
        add("Call to book a car service", t, Section.SMALL, 2, "c2", reminderAt = "${t}T13:30")
        state.rules.add(Rule(Ids.new(), "Plan tomorrow’s 1-3-5", type = RepeatType.DAILY, interval = 1, section = Section.SMALL, start = t))
        add("Pack for the weekend", tm, Section.MEDIUM, 1, "c3")
        state.projects.add(Project("pgarage", "Organize the garage"))
        state.projects.add(Project("pnet", "Upgrade home Wi-Fi"))
        add("Clear off the workbench", cat = "c2", proj = "pgarage", porder = 1, done = true, completedAt = "${y}T18:20")
        add("Sort hardware into labeled bins", cat = "c2", proj = "pgarage", porder = 2)
        add("Put up a pegboard for tools", cat = "c2", proj = "pgarage", porder = 3)
        add("Hang the bike rack", tm, Section.MEDIUM, 2, "c2", proj = "pgarage", porder = 4)
        add("Order the router", cat = "c2", proj = "pnet", porder = 2)
        add("Move the TV and console to the new switch", cat = "c2", proj = "pnet", porder = 3)
        add("Research attic insulation", order = 1, cat = "c2")
        add("Replace the door weatherstrip", order = 2, cat = "c2")
        add("Try a new bread recipe", order = 3, cat = "c3", important = true)
        add("Restring the guitar", order = 4, cat = "c3")
        state.last = y
        state.examples = true
        rollover()
    }
}
