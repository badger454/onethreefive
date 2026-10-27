package com.badger454.onethreefive.domain

/**
 * App data. Field names and the JSON shape match the HTML prototype's backup ("Copy data as JSON"),
 * so a prototype backup imports directly and an export from the app can be read back by either.
 * Dates are ISO strings (yyyy-MM-dd); date-times are local "yyyy-MM-ddTHH:mm".
 */
enum class Section(val key: String, val max: Int, val label: String, val title: String) {
    BIG("big", 1, "Big", "1 Big Thing"),
    MEDIUM("medium", 3, "Medium", "3 Medium Things"),
    SMALL("small", 5, "Small", "5 Small Things");

    /** Header text without the leading number, shown next to the number badge. */
    val heading: String get() = title.substringAfter(' ')

    companion object {
        fun of(key: String?): Section? = entries.firstOrNull { it.key == key }
    }
}

class Task(
    var id: String,
    var title: String = "",
    var date: String? = null,
    var origin: String? = null,
    var section: Section? = null,
    var tray: Boolean = false,
    var order: Int = 1,
    var done: Boolean = false,
    var completedAt: String? = null,
    var important: Boolean = false,
    var carry: Int = 0,
    var reminderAt: String? = null,
    var fired: Boolean = false,
    var ruleId: String? = null,
    var cat: String? = null,
    var proj: String? = null,
    var porder: Int = 0,
    var notes: String? = null,
    var ongoing: Boolean = false,
    var ongoingCopy: Boolean = false,
) {
    fun copyAll(): Task = Task(
        id, title, date, origin, section, tray, order, done, completedAt, important, carry,
        reminderAt, fired, ruleId, cat, proj, porder, notes, ongoing, ongoingCopy,
    )

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "title" to title, "date" to date, "origin" to origin, "section" to section?.key,
        "tray" to tray, "order" to order, "done" to done, "completedAt" to completedAt,
        "important" to important, "carry" to carry, "reminderAt" to reminderAt, "fired" to fired,
        "ruleId" to ruleId, "cat" to cat, "proj" to proj, "porder" to porder,
        "notes" to notes, "ongoing" to ongoing, "ongoingCopy" to ongoingCopy,
    )

    companion object {
        fun from(m: Map<String, Any?>): Task = Task(
            id = m.str("id") ?: Ids.new(),
            title = m.str("title") ?: "",
            date = m.strOrEmpty("date"),
            origin = m.strOrEmpty("origin"),
            section = Section.of(m.str("section")),
            tray = m.bool("tray"),
            order = m.int("order", 1),
            done = m.bool("done"),
            completedAt = m.strOrEmpty("completedAt"),
            important = m.bool("important"),
            carry = m.int("carry"),
            reminderAt = m.strOrEmpty("reminderAt"),
            fired = m.bool("fired"),
            ruleId = m.strOrEmpty("ruleId"),
            cat = m.strOrEmpty("cat"),
            proj = m.strOrEmpty("proj"),
            porder = m.int("porder"),
            notes = m.str("notes"),
            ongoing = m.bool("ongoing"),
            ongoingCopy = m.bool("ongoingCopy"),
        )
    }
}

enum class RepeatType(val key: String, val unit: String, val label: String) {
    DAILY("daily", "days", "Daily"),
    WEEKLY("weekly", "weeks", "Weekly"),
    MONTHLY_DATE("monthly_date", "months", "Monthly, by date"),
    MONTHLY_WEEKDAY("monthly_weekday", "months", "Monthly, by weekday"),
    ANNUAL("annual", "years", "Annually");

    companion object {
        fun of(key: String?): RepeatType = entries.firstOrNull { it.key == key } ?: DAILY
    }
}

/** weekday/weekdays use 0 = Sunday … 6 = Saturday (prototype convention). ord: 1–4, or -1 = last. */
class Rule(
    var id: String,
    var title: String = "",
    var important: Boolean = false,
    var cat: String? = null,
    var type: RepeatType = RepeatType.WEEKLY,
    var interval: Int = 1,
    var weekdays: MutableList<Int> = mutableListOf(),
    var dom: Int = 1,
    var ord: Int = 1,
    var weekday: Int = 1,
    var month: Int = 1,
    var day: Int = 1,
    var section: Section = Section.SMALL,
    var start: String = "",
    var end: String = "",
) {
    fun copyAll(): Rule = Rule(id, title, important, cat, type, interval, weekdays.toMutableList(), dom, ord, weekday, month, day, section, start, end)

    fun setFrom(o: Rule) {
        type = o.type; interval = o.interval; weekdays = o.weekdays.toMutableList(); dom = o.dom; ord = o.ord
        weekday = o.weekday; month = o.month; day = o.day; section = o.section; start = o.start; end = o.end
    }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "title" to title, "important" to important, "cat" to cat, "type" to type.key,
        "interval" to interval, "weekdays" to weekdays.toList(), "dom" to dom, "ord" to ord,
        "weekday" to weekday, "month" to month, "day" to day, "section" to section.key,
        "start" to start, "end" to end,
    )

    companion object {
        fun from(m: Map<String, Any?>): Rule = Rule(
            id = m.str("id") ?: Ids.new(),
            title = m.str("title") ?: "",
            important = m.bool("important"),
            cat = m.strOrEmpty("cat"),
            type = RepeatType.of(m.str("type")),
            interval = m.int("interval", 1).coerceAtLeast(1),
            weekdays = m.list("weekdays").mapNotNull { (it as? Double)?.toInt() ?: (it as? String)?.toIntOrNull() }.toMutableList(),
            dom = m.int("dom", 1),
            ord = m.int("ord", 1),
            weekday = m.int("weekday", 1),
            month = m.int("month", 1),
            day = m.int("day", 1),
            section = Section.of(m.str("section")) ?: Section.SMALL,
            start = m.str("start") ?: "",
            end = m.str("end") ?: "",
        )
    }
}

/** A category color is one of 8 palette slots, or a custom hex color. */
class Category(
    var id: String,
    var name: String,
    var color: Int = 0,
    var hex: String? = null,
) {
    val isCustom: Boolean get() = hex != null

    fun copyAll() = Category(id, name, color, hex)

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "name" to name,
        // The prototype stores a custom color as the string "c_<id>" plus "hex".
        "color" to (if (hex != null) "c_" + id.replace(Regex("[^\\w-]"), "") else color),
        "hex" to hex,
    )

    companion object {
        fun from(m: Map<String, Any?>): Category {
            val hex = m.str("hex")?.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) }
            val c = m["color"]
            val idx = if (c is Double) c.toInt().coerceIn(0, 7) else 0
            return Category(m.str("id") ?: Ids.new(), m.str("name") ?: "Untitled", idx, if (c is String || hex != null) hex else null)
        }
    }
}

class Project(var id: String, var name: String, var archived: Boolean = false) {
    fun copyAll() = Project(id, name, archived)
    fun toMap(): Map<String, Any?> = linkedMapOf("id" to id, "name" to name, "archived" to archived)

    companion object {
        fun from(m: Map<String, Any?>) = Project(m.str("id") ?: Ids.new(), m.str("name") ?: "Untitled project", m.bool("archived"))
    }
}

/** One line of "carried forward" history: task [id] was carried off [date]. */
data class LogEntry(val date: String, val title: String, val id: String) {
    fun toMap(): Map<String, Any?> = linkedMapOf("date" to date, "title" to title, "id" to id)
}

enum class Theme(val label: String) {
    CLASSIC("Classic"), LAGOON("Lagoon"), SUNSET("Sunset"), INDIGO("Indigo"), MIDNIGHT("Midnight");

    companion object {
        fun of(label: String?): Theme = entries.firstOrNull { it.label == label } ?: CLASSIC
    }
}

class AppState(
    var tasks: MutableList<Task> = mutableListOf(),
    var rules: MutableList<Rule> = mutableListOf(),
    var skips: MutableList<String> = mutableListOf(),
    var log: MutableList<LogEntry> = mutableListOf(),
    var last: String? = null,
    var offset: Int = 0,
    var examples: Boolean = false,
    var cats: MutableList<Category> = defaultCategories(),
    var projects: MutableList<Project> = mutableListOf(),
    var theme: Theme = Theme.CLASSIC,
    var autoDark: Boolean = true,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "v" to 1,
        "tasks" to tasks.map { it.toMap() },
        "rules" to rules.map { it.toMap() },
        "skips" to skips.toList(),
        "log" to log.map { it.toMap() },
        "last" to last,
        "offset" to offset,
        "examples" to examples,
        "cats" to cats.map { it.toMap() },
        "projects" to projects.map { it.toMap() },
        "theme" to theme.label,
        "autoDark" to autoDark,
        "app" to "onethreefive-android",
    )

    fun toJson(): String = Json.write(toMap())

    companion object {
        fun defaultCategories(): MutableList<Category> = mutableListOf(
            Category("c1", "Work", 0), Category("c2", "Home", 1), Category("c3", "Hobby", 3),
        )

        /** Throws IllegalArgumentException if the text isn't a backup (must contain a "tasks" array). */
        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): AppState {
            val root = Json.parse(text) as? Map<String, Any?> ?: throw IllegalArgumentException("Not a backup")
            if (root["tasks"] !is List<*>) throw IllegalArgumentException("Not a backup")
            val s = AppState()
            s.tasks = root.objs("tasks").map { Task.from(it) }.toMutableList()
            s.rules = root.objs("rules").map { Rule.from(it) }.toMutableList()
            s.skips = root.list("skips").mapNotNull { it as? String }.toMutableList()
            s.log = root.objs("log").mapNotNull { e ->
                val d = e.str("date"); val id = e.str("id")
                if (d == null || id == null) null else LogEntry(d, e.str("title") ?: "", id)
            }.toMutableList()
            s.last = root.strOrEmpty("last")
            s.offset = root.int("offset")
            s.examples = root.bool("examples")
            s.cats = if (root["cats"] is List<*>) root.objs("cats").map { Category.from(it) }.toMutableList() else defaultCategories()
            s.projects = root.objs("projects").map { Project.from(it) }.toMutableList()
            s.theme = Theme.of(root.str("theme"))
            s.autoDark = root.bool("autoDark", true)
            // Keep ids unique even if a hand-edited backup repeats one.
            val seen = HashSet<String>()
            s.tasks = s.tasks.filter { seen.add(it.id) }.toMutableList()
            return s
        }
    }
}

object Ids {
    fun new(): String = java.util.UUID.randomUUID().toString()
}
