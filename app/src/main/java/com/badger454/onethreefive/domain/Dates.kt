package com.badger454.onethreefive.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Source of "now". The app uses the system clock; tests use a fixed one. */
fun interface Clock {
    fun now(): LocalDateTime
}

object SystemClock : Clock {
    override fun now(): LocalDateTime = LocalDateTime.now()
}

/** Date helpers on ISO strings (yyyy-MM-dd), mirroring the prototype. Weekday numbers: 0 = Sunday … 6 = Saturday. */
object D {
    val WD = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val WDL = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val MON = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val ORD = mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", -1 to "last")

    private val DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun parse(iso: String): LocalDate = LocalDate.parse(iso)
    fun iso(d: LocalDate): String = d.toString()
    fun dn(iso: String): Long = parse(iso).toEpochDay()
    fun addDays(iso: String, n: Long): String = parse(iso).plusDays(n).toString()
    fun wd(iso: String): Int = parse(iso).dayOfWeek.value % 7
    fun lastDay(y: Int, m: Int): Int = LocalDate.of(y, m, 1).lengthOfMonth()

    fun fmtShort(iso: String): String {
        val d = parse(iso)
        return WD[d.dayOfWeek.value % 7] + ", " + MON[d.monthValue - 1] + " " + d.dayOfMonth
    }

    fun fmtDate(iso: String): String {
        val d = parse(iso.take(10))
        return MON[d.monthValue - 1] + " " + d.dayOfMonth
    }

    fun fmtTime(dt: LocalDateTime): String {
        var h = dt.hour
        val ap = if (h < 12) "AM" else "PM"
        h %= 12
        if (h == 0) h = 12
        return h.toString() + ":" + dt.minute.toString().padStart(2, '0') + " " + ap
    }

    fun localDT(dt: LocalDateTime): String = dt.format(DT)

    fun parseDT(s: String): LocalDateTime? = try {
        LocalDateTime.parse(s.take(16), DT)
    } catch (e: Exception) {
        null
    }

    fun ordinal(n: Int): String {
        val v = n % 100
        val suf = when {
            v in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suf"
    }

    fun isWeekend(iso: String): Boolean = wd(iso).let { it == 0 || it == 6 }

    /** The next Monday strictly after [iso]. */
    fun nextMonday(iso: String): String {
        val w = wd(iso)
        var k = (8 - w) % 7
        if (k == 0) k = 7
        return addDays(iso, k.toLong())
    }
}
