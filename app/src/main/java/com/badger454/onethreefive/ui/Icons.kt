package com.badger454.onethreefive.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The prototype's line icons (24×24 viewBox), drawn as vectors. Tint them with Icon(tint = …). */
object Ic {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(p).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun filled(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(pathData = PathParser().parsePathString(p).toNodes(), fill = SolidColor(Color.Black))
        }
        return b.build()
    }

    val check = stroke("check", 3.2f, "M5 12.5l4.5 4.5L19 7.5")
    val more = filled("more", circle(12f, 5f, 1.9f) + circle(12f, 12f, 1.9f) + circle(12f, 19f, 1.9f))
    val grip = filled("grip", circle(9f, 6f, 1.6f) + circle(15f, 6f, 1.6f) + circle(9f, 12f, 1.6f) + circle(15f, 12f, 1.6f) + circle(9f, 18f, 1.6f) + circle(15f, 18f, 1.6f))
    val left = stroke("left", 2.2f, "M15 5l-7 7 7 7")
    val right = stroke("right", 2.2f, "M9 5l7 7-7 7")
    val day = stroke("day", 2f, "M4 6h3M4 12h3M4 18h3M10 6h10M10 12h10M10 18h10")
    val bucket = stroke("bucket", 2f, "M4 7h16l-1.6 12.2a2 2 0 0 1-2 1.8H7.6a2 2 0 0 1-2-1.8z", "M8 7a4 4 0 0 1 8 0")
    val sliders = stroke("sliders", 2f, "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2.2f), circle(10f, 17f, 2.2f))
    val copy = stroke("copy", 2f, "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2")
    val move = stroke("move", 2f, "M4 12h14M13 6l6 6-6 6")
    val clock = stroke("clock", 2f, circle(12f, 12f, 8.5f), "M12 7.5V12l3 2")
    val bell = stroke("bell", 2f, "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
    val flag = filled("flag", "M5 21V4h11l-2 4 2 4H6v9z")
    val repeat = stroke("repeat", 2f, "M17 3l3 3-3 3", "M4 11V9a3 3 0 0 1 3-3h13M7 21l-3-3 3-3", "M20 13v2a3 3 0 0 1-3 3H4")
    val ongoing = stroke("ongoing", 1.9f, "M20 11a8 8 0 0 0-14.3-4.9L4 8", "M4 4v4h4", "M4 13a8 8 0 0 0 14.3 4.9L20 16", "M20 20v-4h-4")
    val trash = stroke("trash", 2f, "M4 7h16M9 7V4h6v3M6.5 7l1 13h9l1-13")
    val plus = stroke("plus", 2.4f, "M12 5v14M5 12h14")
    val arrow = stroke("arrow", 2.6f, "M4 12h14M13 6l6 6-6 6")
    val tag = stroke("tag", 2f, "M3.5 12.2V4.5a1 1 0 0 1 1-1h7.7l8.3 8.3a1.4 1.4 0 0 1 0 2l-6.7 6.7a1.4 1.4 0 0 1-2 0z", circle(8f, 8f, 1.5f))
    val folder = stroke("folder", 2f, "M3.5 7a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")
    val sched = stroke("sched", 2f, "M5.5 5h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z", "M3.5 9.5h13M7.5 3v3.5M12.5 3v3.5", "M15 15h6.5M18.5 12l3 3-3 3")
    val chev = stroke("chev", 2.2f, "M9 5l7 7-7 7")
    val chevDown = stroke("chevDown", 2.2f, "M5 9l7 7 7-7")
    val chevUp = stroke("chevUp", 2.2f, "M5 15l7-7 7 7")
    val close = stroke("close", 2.2f, "M6 6l12 12M18 6L6 18")
    val notes = stroke("notes", 2f, "M6 3.5h8.5L19 8v12.5H6z", "M14 3.5V8.5h5M9 12.5h7M9 16.5h5")
    val minus = stroke("minus", 2.4f, "M5 12h14")
}
