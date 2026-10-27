package com.badger454.onethreefive.domain

/**
 * Minimal JSON reader/writer with no Android or library dependencies, so the backup format
 * (the same shape the HTML prototype exports) can be unit tested on a plain JVM.
 * Values: Map<String, Any?>, List<Any?>, String, Double, Boolean, null.
 */
object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw IllegalArgumentException("Unexpected text at ${p.i}")
        return v
    }

    fun write(v: Any?): String = StringBuilder().also { write(v, it) }.toString()

    private fun write(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is String -> str(v, sb)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int -> sb.append(v)
            is Long -> sb.append(v)
            is Double -> {
                if (v.isNaN() || v.isInfinite()) sb.append("null")
                else if (v == Math.floor(v) && Math.abs(v) < 1e15) sb.append(v.toLong())
                else sb.append(v)
            }
            is Number -> write(v.toDouble(), sb)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    str(k.toString(), sb)
                    sb.append(':')
                    write(value, sb)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(x, sb)
                }
                sb.append(']')
            }
            else -> str(v.toString(), sb)
        }
    }

    private fun str(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(): Any? {
            if (i >= s.length) throw IllegalArgumentException("Unexpected end")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("Bad literal at $i")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw IllegalArgumentException("Expected key at $i")
                val k = string()
                ws()
                if (i >= s.length || s[i] != ':') throw IllegalArgumentException("Expected : at $i")
                i++
                ws()
                m[k] = value()
                ws()
                if (i >= s.length) throw IllegalArgumentException("Unexpected end")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return m }
                throw IllegalArgumentException("Expected , or } at $i")
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                ws()
                l.add(value())
                ws()
                if (i >= s.length) throw IllegalArgumentException("Unexpected end")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return l }
                throw IllegalArgumentException("Expected , or ] at $i")
            }
        }

        private fun string(): String {
            val sb = StringBuilder()
            i++
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw IllegalArgumentException("Bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("Bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
            if (start == i) throw IllegalArgumentException("Unexpected character '${s[i]}' at $i")
            return s.substring(start, i).toDouble()
        }
    }
}

/* Small typed accessors used by the model readers. */
internal fun Map<String, Any?>.str(k: String): String? = when (val v = this[k]) {
    null -> null
    is String -> v
    is Double -> if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    else -> v.toString()
}

internal fun Map<String, Any?>.strOrEmpty(k: String): String? = str(k)?.takeIf { it.isNotEmpty() }

internal fun Map<String, Any?>.int(k: String, def: Int = 0): Int = when (val v = this[k]) {
    is Double -> v.toInt()
    is Number -> v.toInt()
    is String -> v.toIntOrNull() ?: def
    is Boolean -> if (v) 1 else 0
    else -> def
}

internal fun Map<String, Any?>.bool(k: String, def: Boolean = false): Boolean = when (val v = this[k]) {
    is Boolean -> v
    is Double -> v != 0.0
    is String -> v == "true"
    null -> def
    else -> def
}

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.objs(k: String): List<Map<String, Any?>> =
    (this[k] as? List<Any?>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.list(k: String): List<Any?> = (this[k] as? List<Any?>) ?: emptyList()
