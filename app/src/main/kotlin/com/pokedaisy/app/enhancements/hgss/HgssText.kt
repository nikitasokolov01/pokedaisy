package com.pokedaisy.app.enhancements.hgss

internal enum class HgssTextSpeed { ORIGINAL, VERY_FAST, INSTANT }
internal enum class HgssTextToken { GLYPH, LINE_BREAK, CONTROL, PAGE, END, UNKNOWN }
internal data class HgssTextContext(
    val printerState: Int,
    val interactiveDialogue: Boolean,
    val callbackPresent: Boolean,
    val automaticScrolling: Boolean,
)

/**
 * A bounded glyph-only plan for a future verified renderer hook. The decoder must distinguish ordinary glyphs
 * from control-code payloads. Do not implement this by setting AddTextPrinter's synchronous instant speed:
 * that path loops over RenderFont and frees its printer, unlike the interactive task/callback path.
 */
internal object HgssText {
    const val MAX_GLYPHS = 1024

    /** Number of plain glyphs safe to accelerate now; zero leaves the complete native step untouched. */
    fun glyphs(speed: HgssTextSpeed, context: HgssTextContext, lookahead: List<HgssTextToken>): Int {
        if (speed == HgssTextSpeed.ORIGINAL || context.printerState != 0 || !context.interactiveDialogue ||
            context.callbackPresent || context.automaticScrolling) return 0
        val budget = if (speed == HgssTextSpeed.VERY_FAST) 4 else MAX_GLYPHS
        // Reaching any control/newline/page boundary yields back to the original task. Never synthesize A/B.
        return lookahead.asSequence().take(budget).takeWhile { it == HgssTextToken.GLYPH }.count()
    }
}
