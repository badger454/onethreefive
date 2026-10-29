package com.badger454.onethreefive.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PlannerTest {
    /** A clock we can move forward by hand. */
    private class TestClock(var t: LocalDateTime) : Clock {
        override fun now() = t
    }

    // Wednesday, Oct 21 2026, 9:00
    private fun planner(start: LocalDateTime = LocalDateTime.of(2026, 10, 21, 9, 0)): Pair<Planner, TestClock> {
        val c = TestClock(start)
        val p = Planner(AppState(), c)
        p.state.last = p.today()
        return p to c
    }

    private fun Planner.add(title: String, date: String?, sec: Section?, cat: String? = null): Task {
        val t = Task(Ids.new(), title, date, date, sec, order = if (date != null && sec != null) nextOrder(dayList(date, sec)) else 1, cat = cat)
        state.tasks.add(t)
        return t
    }

    @Test
    fun slotLimitsBlockAddingToFullSection() {
        val (p, _) = planner()
        val d = p.today()
        assertNotNull(p.addTask(Planner.ListKind.DAY, "big", d, Section.BIG, null))
        assertNull(p.addTask(Planner.ListKind.DAY, "big 2", d, Section.BIG, null))
        repeat(3) { assertNotNull(p.addTask(Planner.ListKind.DAY, "m$it", d, Section.MEDIUM, null)) }
        assertNull(p.addTask(Planner.ListKind.DAY, "m4", d, Section.MEDIUM, null))
        repeat(5) { assertNotNull(p.addTask(Planner.ListKind.DAY, "s$it", d, Section.SMALL, null)) }
        assertNull(p.addTask(Planner.ListKind.DAY, "s6", d, Section.SMALL, null))
        // the bucket has no limit
        repeat(20) { assertNotNull(p.addTask(Planner.ListKind.BUCKET, "b$it", null, null, null)) }
    }

    @Test
    fun moveIntoFullSectionIsBlocked() {
        val (p, _) = planner()
        val d = p.today()
        p.add("big", d, Section.BIG)
        val b = p.add("bucket", null, null)
        val r = p.applyDest(b, Planner.Mode.MOVE, d, Section.BIG)
        assertTrue(r is Planner.Outcome.Full)
        assertNull(b.date)
        val ok = p.applyDest(b, Planner.Mode.MOVE, d, Section.SMALL)
        assertTrue(ok is Planner.Outcome.Message)
        assertEquals(d, b.date)
        assertEquals(Section.SMALL, b.section)
    }

    @Test
    fun rolloverCarriesUnfinishedIntoSameSectionAndIsIdempotent() {
        val (p, c) = planner()
        val d0 = p.today()
        val done = p.add("done", d0, Section.MEDIUM).also { it.done = true }
        val open = p.add("open", d0, Section.MEDIUM)
        c.t = c.t.plusDays(1)
        assertTrue(p.rollover())
        val d1 = p.today()
        assertEquals(d1, open.date)
        assertEquals(Section.MEDIUM, open.section)
        assertFalse(open.tray)
        assertEquals(1, open.carry)
        assertEquals(d0, done.date)
        val snap = p.snapshot()
        p.rollover()
        assertEquals(snap, p.snapshot())
    }

    @Test
    fun carriedOverflowIsAppendedPastTheLimit() {
        val (p, c) = planner()
        val d0 = p.today()
        p.add("y1", d0, Section.MEDIUM); p.add("y2", d0, Section.MEDIUM)
        val d1 = D.addDays(d0, 1)
        repeat(3) { p.add("t$it", d1, Section.MEDIUM) }
        c.t = c.t.plusDays(1)
        p.rollover()
        val med = p.dayList(d1, Section.MEDIUM)
        assertEquals(5, med.size)
        assertEquals(listOf("t0", "t1", "t2", "y1", "y2"), med.map { it.title })
        assertTrue(p.trayList(d1).isEmpty())
    }

    @Test
    fun carryCountsDaysElapsed() {
        val (p, c) = planner()
        val t = p.add("x", p.today(), Section.SMALL)
        c.t = c.t.plusDays(3)
        p.rollover()
        assertEquals(3, t.carry)
        assertEquals(p.today(), t.date)
    }

    @Test
    fun workTasksSkipTheWeekend() {
        // Friday Oct 23 2026
        val (p, c) = planner(LocalDateTime.of(2026, 10, 23, 9, 0))
        val work = p.add("report", p.today(), Section.MEDIUM, cat = "c1")
        val home = p.add("lawn", p.today(), Section.MEDIUM, cat = "c2")
        assertEquals(listOf("lawn"), p.carryPlan("2026-10-24")!![Section.MEDIUM]!!.map { it.title })
        assertEquals(listOf("report"), p.carryPlan("2026-10-26")!![Section.MEDIUM]!!.map { it.title })
        c.t = c.t.plusDays(1) // Saturday
        p.rollover()
        assertEquals("2026-10-26", work.date)
        assertEquals(3, work.carry)
        assertEquals("2026-10-24", home.date)
    }

    @Test
    fun ongoingTaskCarriesEvenWhenChecked() {
        val (p, c) = planner()
        val d0 = p.today()
        val t = p.add("water plants", d0, Section.SMALL).also { it.ongoing = true; it.done = true }
        c.t = c.t.plusDays(1)
        p.rollover()
        assertEquals(p.today(), t.date)
        assertFalse(t.done)
        val copy = p.state.tasks.single { it.ongoingCopy }
        assertEquals(d0, copy.date)
        assertTrue(copy.done)
    }

    @Test
    fun uncheckingOnPastDayBringsItBackToToday() {
        val (p, c) = planner()
        val d0 = p.today()
        val t = p.add("x", d0, Section.BIG).also { it.done = true }
        c.t = c.t.plusDays(1)
        p.rollover()
        assertEquals(d0, t.date)
        val r = p.toggleDone(t)
        assertTrue(r is Planner.Outcome.Message)
        assertEquals(p.today(), t.date)
    }

    @Test
    fun markDoneOnCarriedDayUndoesTheCarry() {
        val (p, c) = planner()
        val d0 = p.today()
        val t = p.add("x", d0, Section.SMALL)
        c.t = c.t.plusDays(2)
        p.rollover()
        assertEquals(2, t.carry)
        assertEquals(1, p.carriedFrom(d0).size)
        p.markDoneOnCarriedDay(t, d0)
        assertEquals(d0, t.date)
        assertTrue(t.done)
        assertEquals(0, t.carry)
        assertTrue(p.carriedFrom(d0).isEmpty())
        // still done after another rollover
        p.rollover()
        assertEquals(d0, t.date)
    }

    @Test
    fun recurrencePatternsAndEdgeCases() {
        val (p, _) = planner()
        val daily = Rule("a", type = RepeatType.DAILY, interval = 2, start = "2026-10-01")
        assertTrue(p.matches(daily, "2026-10-03"))
        assertFalse(p.matches(daily, "2026-10-04"))
        val weekly = Rule("b", type = RepeatType.WEEKLY, interval = 2, weekdays = mutableListOf(1, 4), start = "2026-10-05")
        assertEquals(listOf("2026-10-05", "2026-10-08", "2026-10-19"), p.nextDates(weekly, "2026-10-05", 3))
        val mdate = Rule("c", type = RepeatType.MONTHLY_DATE, dom = 31, start = "2026-01-01")
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), p.nextDates(mdate, "2026-01-01", 4))
        val lastFri = Rule("d", type = RepeatType.MONTHLY_WEEKDAY, ord = -1, weekday = 5, start = "2026-10-01")
        assertEquals(listOf("2026-10-30", "2026-11-27"), p.nextDates(lastFri, "2026-10-01", 2))
        val secondTue = Rule("e", type = RepeatType.MONTHLY_WEEKDAY, ord = 2, weekday = 2, start = "2026-10-01")
        assertEquals(listOf("2026-10-13", "2026-11-10"), p.nextDates(secondTue, "2026-10-01", 2))
        val leap = Rule("f", type = RepeatType.ANNUAL, month = 2, day = 29, start = "2024-02-29")
        assertEquals(listOf("2024-02-29", "2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29"), p.nextDates(leap, "2024-01-01", 5))
        val ended = Rule("g", type = RepeatType.DAILY, start = "2026-10-01", end = "2026-10-02")
        assertEquals(listOf("2026-10-01", "2026-10-02"), p.nextDates(ended, "2026-09-01", 5))
    }

    @Test
    fun repeatingInstancesAreNotDuplicatedAndDoNotCarry() {
        val (p, c) = planner()
        val r = Rule("r1", "Plan", type = RepeatType.DAILY, start = p.today(), section = Section.SMALL)
        p.state.rules.add(r)
        p.materialize(p.today()); p.materialize(p.today())
        assertEquals(1, p.state.tasks.count { it.ruleId == "r1" })
        assertEquals("rr1_" + p.today(), p.state.tasks.single().id)
        val d0 = p.today()
        c.t = c.t.plusDays(1)
        p.rollover()
        assertEquals(1, p.state.tasks.count { it.date == d0 })
        assertEquals(1, p.state.tasks.count { it.date == p.today() })
    }

    @Test
    fun deletingOneInstanceRecordsASkip() {
        val (p, _) = planner()
        p.state.rules.add(Rule("r1", "Plan", type = RepeatType.DAILY, start = p.today()))
        p.materialize(p.today())
        val t = p.state.tasks.single()
        p.deleteWithScope(t, allFuture = false)
        p.materialize(p.today())
        assertTrue(p.state.tasks.isEmpty())
        p.materialize(p.tomorrow())
        assertEquals(1, p.state.tasks.size)
    }

    @Test
    fun deletingThisAndFutureEndsTheRule() {
        val (p, _) = planner()
        p.state.rules.add(Rule("r1", "Plan", type = RepeatType.DAILY, start = D.addDays(p.today(), -3)))
        p.materialize(p.today())
        p.deleteWithScope(p.state.tasks.single(), allFuture = true)
        assertEquals(D.addDays(p.today(), -1), p.state.rules.single().end)
        p.materialize(p.tomorrow())
        assertTrue(p.state.tasks.isEmpty())
    }

    @Test
    fun copyMakesAnIndependentTask() {
        val (p, _) = planner()
        val pr = p.addProject("Shop")!!
        val a = p.addTask(Planner.ListKind.PROJECT, "action", null, null, pr.id)!!
        a.carry = 4
        p.applyDest(a, Planner.Mode.COPY, p.today(), Section.SMALL)
        val copy = p.state.tasks.single { it !== a }
        assertNull(copy.proj)
        assertEquals(0, copy.carry)
        assertNull(copy.ruleId)
        // moving an action "to bucket" sends it back to its project, unscheduled
        p.applyDest(a, Planner.Mode.MOVE, p.today(), Section.MEDIUM)
        p.applyDest(a, Planner.Mode.MOVE, null, null)
        assertEquals(pr.id, a.proj)
        assertNull(a.date)
        assertTrue(p.bucketList().isEmpty())
    }

    @Test
    fun deletingProjectKeepsScheduledActions() {
        val (p, _) = planner()
        val pr = p.addProject("Shop")!!
        p.addTask(Planner.ListKind.PROJECT, "unscheduled", null, null, pr.id)
        val s = p.addTask(Planner.ListKind.PROJECT, "scheduled", null, null, pr.id)!!
        p.applyDest(s, Planner.Mode.MOVE, p.today(), Section.SMALL)
        p.deleteProject(pr)
        assertEquals(listOf("scheduled"), p.state.tasks.map { it.title })
        assertNull(s.proj)
    }

    @Test
    fun deletingCategoryClearsIt() {
        val (p, _) = planner()
        val t = p.add("x", p.today(), Section.SMALL, cat = "c1")
        p.deleteCategory(p.cat("c1")!!)
        assertNull(t.cat)
        assertEquals(listOf("Home", "Hobby"), p.state.cats.map { it.name })
    }

    @Test
    fun backupRoundTripsAndReadsPrototypeFormat() {
        val proto = """{"v":1,"next":10,"tasks":[{"id":"mvx1","title":"Say \"hi\"\n","date":"2026-10-21","origin":"2026-10-21","section":"medium","tray":false,"order":2,"done":false,"completedAt":null,"important":true,"carry":1,"reminderAt":"2026-10-21T13:30","fired":false,"ruleId":null,"cat":"k1","proj":null,"porder":0,"ongoing":true}],
          "rules":[{"id":"r1","title":"Plan","important":false,"type":"weekly","interval":1,"weekdays":[1,3],"dom":1,"ord":-1,"weekday":5,"month":1,"day":1,"section":"small","start":"2026-10-01","end":""}],
          "skips":["r1|2026-10-05"],"log":[{"date":"2026-10-20","title":"Say hi","id":"mvx1"}],"last":"2026-10-21","offset":0,"examples":false,
          "cats":[{"id":"k1","name":"Garage","color":"c_k1","hex":"#ff8800"},{"id":"c2","name":"Home","color":1}],"projects":[{"id":"p1","name":"Shop","archived":true}],"theme":"Lagoon","autoDark":false}"""
        val s = AppState.fromJson(proto)
        val t = s.tasks.single()
        assertEquals("Say \"hi\"\n", t.title)
        assertEquals(Section.MEDIUM, t.section)
        assertTrue(t.important && t.ongoing)
        assertEquals(RepeatType.WEEKLY, s.rules.single().type)
        assertEquals(listOf(1, 3), s.rules.single().weekdays)
        assertEquals("#ff8800", s.cats[0].hex)
        assertNull(s.cats[1].hex)
        assertEquals(Theme.LAGOON, s.theme)
        assertFalse(s.autoDark)
        assertTrue(s.projects.single().archived)
        val again = AppState.fromJson(s.toJson())
        assertEquals(s.toJson(), again.toJson())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBackupIsRejected() {
        AppState.fromJson("{\"hello\":1}")
    }

    @Test
    fun examplesSeedWithoutErrors() {
        val (p, _) = planner()
        p.seedExamples()
        assertTrue(p.state.examples)
        assertEquals(1, p.dayList(p.today(), Section.BIG).size)
        // the two unfinished tasks from yesterday carried over
        assertTrue(p.state.tasks.any { it.title.startsWith("Back up the photo library") && it.date == p.today() })
    }

    @Test
    fun dropInSectionRespectsLimit() {
        val (p, _) = planner()
        val d = p.today()
        p.add("big", d, Section.BIG)
        val s = p.add("small", d, Section.SMALL)
        assertTrue(p.dropInSection(s, Section.BIG, 0) is Planner.Outcome.Full)
        val s2 = p.add("small2", d, Section.SMALL)
        p.dropInSection(s2, Section.SMALL, 0)
        assertEquals(listOf("small2", "small"), p.dayList(d, Section.SMALL).map { it.title })
    }

    @Test
    fun dragToBucketAndProject() {
        val (p, _) = planner()
        val pr = p.addProject("Shop")!!
        p.addTask(Planner.ListKind.PROJECT, "first", null, null, pr.id)
        val t = p.add("x", p.today(), Section.SMALL)
        p.moveToProject(t, pr.id)
        assertNull(t.date)
        assertEquals(pr.id, t.proj)
        assertEquals(listOf("first", "x"), p.projList(pr.id).map { it.title })
        p.moveToBucket(t)
        assertNull(t.proj)
        assertEquals(listOf("x"), p.bucketList().map { it.title })
        // a repeating instance dropped in the bucket becomes a one-off and its day is skipped
        p.state.rules.add(Rule("r1", "Plan", type = RepeatType.DAILY, start = p.today()))
        p.materialize(p.today())
        val inst = p.state.tasks.single { it.ruleId == "r1" }
        p.moveToBucket(inst)
        p.materialize(p.today())
        assertEquals(0, p.state.tasks.count { it.ruleId == "r1" && it.date == p.today() })
    }

    @Test
    fun nudgesDescribeTheDay() {
        val (p, _) = planner()
        assertEquals("Pick today's Big Thing", p.morningNudge().first)
        assertNull(p.eveningNudge())
        p.add("Ship it", p.today(), Section.BIG)
        val s1 = p.add("a", p.today(), Section.SMALL)
        assertEquals("Your Big Thing: Ship it", p.morningNudge().first)
        assertEquals("2 of 2 tasks to do today.", p.morningNudge().second)
        s1.done = true
        assertEquals("1 of 2 done today", p.eveningNudge()!!.first)
        p.dayList(p.today(), Section.BIG).single().done = true
        assertNull(p.eveningNudge())
    }

    @Test
    fun nudgeSettingsSurviveBackupAndClearing() {
        val (p, _) = planner()
        p.state.nudges = false
        p.state.nudgeAm = "06:30"
        val again = AppState.fromJson(p.state.toJson())
        assertFalse(again.nudges)
        assertEquals("06:30", again.nudgeAm)
        assertEquals("20:00", AppState.fromJson("{\"tasks\":[]}").nudgePm)
        p.seedExamples()
        assertEquals("06:30", p.state.nudgeAm)
    }
}
