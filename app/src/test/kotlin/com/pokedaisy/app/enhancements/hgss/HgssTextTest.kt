package com.pokedaisy.app.enhancements.hgss

import org.junit.Assert.*
import org.junit.Test

class HgssTextTest {
    private val dialogue = HgssTextContext(0, true, false, false)
    private val glyphs = List(30) { HgssTextToken.GLYPH }

    @Test fun originalSpeedLeavesTheNativeStepUntouched() {
        assertEquals(0, HgssText.glyphs(HgssTextSpeed.ORIGINAL, dialogue, glyphs))
    }
    @Test fun veryFastAndInstantHaveBoundedDifferentBudgets() {
        assertEquals(4, HgssText.glyphs(HgssTextSpeed.VERY_FAST, dialogue, glyphs))
        assertEquals(30, HgssText.glyphs(HgssTextSpeed.INSTANT, dialogue, glyphs))
        assertEquals(1024, HgssText.glyphs(HgssTextSpeed.INSTANT, dialogue, List(2000) { HgssTextToken.GLYPH }))
    }
    @Test fun everyNonGlyphBarrierEndsTheAcceleratedRunBeforeTheBarrier() {
        for (barrier in HgssTextToken.entries.filter { it != HgssTextToken.GLYPH }) {
            val stream = listOf(HgssTextToken.GLYPH, HgssTextToken.GLYPH, barrier, HgssTextToken.GLYPH)
            assertEquals(barrier.name, 2, HgssText.glyphs(HgssTextSpeed.INSTANT, dialogue, stream))
            assertEquals(0, HgssText.glyphs(HgssTextSpeed.INSTANT, dialogue, listOf(barrier)))
        }
    }
    @Test fun waitsScrollingPausesAndUnknownPrinterStatesCannotBeAccelerated() {
        // Native states 1..8 include input waits, page clear, scrolling and timed delay (6).
        for (state in (-1..10).filter { it != 0 }) {
            assertEquals(0, HgssText.glyphs(HgssTextSpeed.INSTANT, dialogue.copy(printerState = state), glyphs))
        }
    }
    @Test fun callbacksAutomaticTextAndUnidentifiedContextsKeepNativeTiming() {
        for (context in listOf(dialogue.copy(callbackPresent = true), dialogue.copy(automaticScrolling = true),
            dialogue.copy(interactiveDialogue = false))) {
            assertEquals(0, HgssText.glyphs(HgssTextSpeed.INSTANT, context, glyphs))
        }
        assertEquals(0, HgssText.glyphs(HgssTextSpeed.INSTANT, dialogue, emptyList()))
    }
    @Test fun planningNeverConsumesAChoiceOrChangesInputAcrossRepeatedFrames() {
        val text = mutableListOf(HgssTextToken.GLYPH, HgssTextToken.PAGE, HgssTextToken.GLYPH)
        val before = text.toList()
        repeat(3) { assertEquals(1, HgssText.glyphs(HgssTextSpeed.INSTANT, dialogue, text)) }
        assertEquals(before, text)
        assertEquals(0, HgssText.glyphs(HgssTextSpeed.INSTANT, dialogue.copy(printerState = 2), text))
    }
}
