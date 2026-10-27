package com.badger454.onethreefive.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.badger454.onethreefive.domain.Category
import com.badger454.onethreefive.domain.Theme

/** Color tokens, same names and values as the prototype's CSS variables. */
data class Pal(
    val dark: Boolean,
    val bg: Color, val onBg: Color, val surface: Color, val line: Color, val ink: Color, val muted: Color,
    val accent: Color, val accentInk: Color, val accentSoft: Color,
    val flag: Color, val done: Color, val danger: Color, val dangerSoft: Color,
    val hdr: Color, val hdrInk: Color, val hdrSub: Color, val hdrLine: Color, val segOn: Color,
    val nav: Color, val navInd: Color, val navInk: Color, val chk: Color, val pill: Color, val pillInk: Color,
    val k: List<Color>,
) {
    fun catColor(c: Category?): Color =
        if (c == null) muted else c.hex?.let { parseHex(it) } ?: k[c.color.coerceIn(0, 7)]

    /** The very light category wash used as a row background. */
    fun tint(c: Category?): Color? = c?.let { lerp(surface, catColor(it), 0.13f) }
}

fun parseHex(hex: String): Color? = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    null
}

fun Color.toHex(): String {
    val r = (red * 255).toInt(); val g = (green * 255).toInt(); val b = (blue * 255).toInt()
    return String.format("#%02x%02x%02x", r, g, b)
}

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val K_LIGHT = listOf(c(0x2F6FB0), c(0x3E8E5A), c(0xC2602B), c(0x8A5BB8), c(0xB8475E), c(0x2A8C8C), c(0x9A7B1C), c(0x6B7280))
private val K_DARK = listOf(c(0x74A8E0), c(0x6FC08A), c(0xE89463), c(0xB793E0), c(0xE57D93), c(0x5CC2C2), c(0xD4B04A), c(0xA3ABB8))

val CLASSIC_LIGHT = Pal(
    dark = false,
    bg = c(0xEDF1EF), onBg = c(0x17201D), surface = c(0xFFFFFF), line = c(0xD6DEDA), ink = c(0x17201D), muted = c(0x5A6762),
    accent = c(0x1C5D85), accentInk = c(0xFFFFFF), accentSoft = c(0xDDE9F1),
    flag = c(0xA86C08), done = c(0x2C7A54), danger = c(0xAD3A2D), dangerSoft = c(0xF6DFDA),
    hdr = c(0xEDF1EF), hdrInk = c(0x17201D), hdrSub = c(0x5A6762), hdrLine = c(0xD6DEDA), segOn = c(0x1C5D85),
    nav = c(0xFFFFFF), navInd = c(0xDDE9F1), navInk = c(0x1C5D85), chk = c(0x5A6762), pill = c(0x1C5D85), pillInk = c(0xFFFFFF),
    k = K_LIGHT,
)

val CLASSIC_DARK = Pal(
    dark = true,
    bg = c(0x111715), onBg = c(0xE3EAE6), surface = c(0x19201E), line = c(0x2A3431), ink = c(0xE3EAE6), muted = c(0x93A19B),
    accent = c(0x6DB1DD), accentInk = c(0x0D1A22), accentSoft = c(0x1B2C37),
    flag = c(0xE2A846), done = c(0x5DBB8A), danger = c(0xE5786A), dangerSoft = c(0x3A2220),
    hdr = c(0x111715), hdrInk = c(0xE3EAE6), hdrSub = c(0x93A19B), hdrLine = c(0x2A3431), segOn = c(0x6DB1DD),
    nav = c(0x19201E), navInd = c(0x1B2C37), navInk = c(0x6DB1DD), chk = c(0x93A19B), pill = c(0x6DB1DD), pillInk = c(0x0D1A22),
    k = K_DARK,
)

private fun colored(
    bg: Long, onBg: Long, surface: Long, line: Long, ink: Long, muted: Long, hdr: Long, hdrSub: Long,
    accent: Long, accentSoft: Long, navInk: Long,
) = Pal(
    dark = false,
    bg = c(bg), onBg = c(onBg), surface = c(surface), line = c(line), ink = c(ink), muted = c(muted),
    accent = c(accent), accentInk = c(0xFFFFFF), accentSoft = c(accentSoft),
    flag = c(0xA86C08), done = c(accent), danger = c(0xAD3A2D), dangerSoft = c(0xF6DFDA),
    hdr = c(hdr), hdrInk = c(0xFFFFFF), hdrSub = c(hdrSub), hdrLine = Color.Transparent, segOn = c(0xFFFFFF),
    nav = c(surface), navInd = c(accentSoft), navInk = c(navInk), chk = c(accent), pill = c(0xFFFFFF), pillInk = c(navInk),
    k = K_LIGHT,
)

val LAGOON = colored(0xD6EEE0, 0x123A2B, 0xFFFCF5, 0xEADFC9, 0x1C1A16, 0x5E5748, 0x2E7A5B, 0xD2EFDF, 0x2A7F5C, 0xD5EEDF, 0x235F47)
val SUNSET = colored(0xFADCCB, 0x3A1308, 0xFFFAF5, 0xF3DCCB, 0x2A1A12, 0x6B5244, 0xA6482F, 0xFBDCCF, 0xB5452C, 0xFADBCD, 0x8E3420)
val INDIGO = colored(0xDADFF8, 0x121A47, 0xFBFBFF, 0xDEE2F7, 0x161A2E, 0x4E5470, 0x34449A, 0xD3D9F8, 0x3346C8, 0xDDE2FF, 0x27347E)

val MIDNIGHT = Pal(
    dark = true,
    bg = c(0x17212B), onBg = c(0xDCE6EE), surface = c(0x223040), line = c(0x2E3F52), ink = c(0xE6EDF2), muted = c(0xA3B1BE),
    accent = c(0x4FD1C5), accentInk = c(0x04201D), accentSoft = c(0x1E3B44),
    flag = c(0xE2A846), done = c(0x4FD1C5), danger = c(0xE5786A), dangerSoft = c(0x3A2220),
    hdr = c(0x0F171F), hdrInk = c(0xEAF2F8), hdrSub = c(0x8FA3B5), hdrLine = Color.Transparent, segOn = c(0x4FD1C5),
    nav = c(0x0F171F), navInd = c(0x27384A), navInk = c(0xEAF2F8), chk = c(0x4FD1C5), pill = c(0x4FD1C5), pillInk = c(0x04201D),
    k = K_DARK,
)

/** Preview colors for the theme tiles in Settings: background, header, card, accent. */
fun previewOf(t: Theme): List<Color> = when (t) {
    Theme.CLASSIC -> listOf(c(0xEDF1EF), c(0xCFDAD5), c(0xFFFFFF), c(0x1C5D85))
    Theme.LAGOON -> listOf(c(0xD6EEE0), c(0x2E7A5B), c(0xFFFCF5), c(0x2A7F5C))
    Theme.SUNSET -> listOf(c(0xFADCCB), c(0xA6482F), c(0xFFFAF5), c(0xB5452C))
    Theme.INDIGO -> listOf(c(0xDADFF8), c(0x34449A), c(0xFBFBFF), c(0x3346C8))
    Theme.MIDNIGHT -> listOf(c(0x17212B), c(0x0F171F), c(0x223040), c(0x4FD1C5))
}

/** Midnight replaces the chosen theme when the phone is in dark mode (unless turned off). Classic has its own dark variant. */
fun palFor(theme: Theme, autoDark: Boolean, systemDark: Boolean): Pal = when {
    autoDark && systemDark -> MIDNIGHT
    theme == Theme.CLASSIC -> if (systemDark) CLASSIC_DARK else CLASSIC_LIGHT
    theme == Theme.LAGOON -> LAGOON
    theme == Theme.SUNSET -> SUNSET
    theme == Theme.INDIGO -> INDIGO
    else -> MIDNIGHT
}

val LocalPal = staticCompositionLocalOf { CLASSIC_LIGHT }

@Composable
fun AppTheme(pal: Pal, content: @Composable () -> Unit) {
    val scheme = if (pal.dark) {
        darkColorScheme(
            primary = pal.accent, onPrimary = pal.accentInk, primaryContainer = pal.accentSoft, onPrimaryContainer = pal.ink,
            secondary = pal.accent, onSecondary = pal.accentInk, background = pal.bg, onBackground = pal.ink,
            surface = pal.surface, onSurface = pal.ink, surfaceVariant = pal.bg, onSurfaceVariant = pal.muted,
            surfaceContainerHigh = pal.surface, surfaceContainerHighest = pal.surface, surfaceContainer = pal.surface,
            surfaceContainerLow = pal.surface, outline = pal.line, outlineVariant = pal.line, error = pal.danger,
            inverseSurface = pal.ink, inverseOnSurface = pal.bg, inversePrimary = pal.accentSoft,
        )
    } else {
        lightColorScheme(
            primary = pal.accent, onPrimary = pal.accentInk, primaryContainer = pal.accentSoft, onPrimaryContainer = pal.ink,
            secondary = pal.accent, onSecondary = pal.accentInk, background = pal.bg, onBackground = pal.ink,
            surface = pal.surface, onSurface = pal.ink, surfaceVariant = pal.bg, onSurfaceVariant = pal.muted,
            surfaceContainerHigh = pal.surface, surfaceContainerHighest = pal.surface, surfaceContainer = pal.surface,
            surfaceContainerLow = pal.surface, outline = pal.line, outlineVariant = pal.line, error = pal.danger,
            inverseSurface = pal.ink, inverseOnSurface = pal.bg, inversePrimary = pal.accentSoft,
        )
    }
    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Fixed backgrounds for rows carried past the 1-3-5 limit: louder with each one. */
object Over {
    val ink = Color(0xFF2B1300)
    val one = Color(0xFFFFE36E)
    val two = Color(0xFFFFAE34)
    val three = Color(0xFFFF6A3D)
    val threeBorder = Color(0xFFC62828)
    val stripeRed = Color(0xFFFF2D2D)
    val stripeYellow = Color(0xFFFFD400)
    val fourBorder = Color(0xFF8B0000)
    val badge = Color(0xFFE53935)
    val toggleOn = Color(0xFF1FBF6B)
}
