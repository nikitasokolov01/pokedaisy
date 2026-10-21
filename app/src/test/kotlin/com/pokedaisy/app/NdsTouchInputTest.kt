package com.pokedaisy.app

import org.junit.Assert.*
import org.junit.Test

class NdsTouchInputTest {
    private val events = mutableListOf<Triple<Boolean, Int, Int>>()
    private val touch = NdsTouchInput { down, x, y -> events += Triple(down, x, y) }
        .apply { rect = NdsTouchRect(100, 0, 1024, 768) }

    @Test fun cornersAndCentreMapToDsPixelsWithoutRoundingOutsideIntoTheImage() {
        val r = touch.rect
        assertEquals(0 to 0, r.point(100f, 0f))
        assertEquals(255 to 191, r.point(1123.99f, 767.99f))
        assertEquals(128 to 96, r.point(612f, 384f))
        for ((x, y) in listOf(99.99f to 0f, 100f to -0.01f, 1124f to 0f, 100f to 768f, Float.NaN to 0f))
            assertNull(r.point(x, y))
    }

    @Test fun switchBarNeverOverlapsTheImageAtThorFourByThreeWideAndPortraitSizes() {
        for ((w, h) in listOf(1240 to 1080, 1280 to 960, 1920 to 1080, 720 to 1280, 160 to 90)) {
            val content = NdsTouchRect.contentHeight(w, h, 189)
            val r = NdsTouchRect.fit(w, content)
            assertTrue(r.top + r.height <= content)
            assertTrue(content <= h)
            assertNull(r.point(w / 2f, content.toFloat()))
            if (r.width > 0) assertEquals(128 to 96, r.point(w / 2f, r.height / 2f))
        }
    }

    @Test fun letterboxDownCannotStartADragInsideTheImage() {
        assertFalse(touch.start(1, 99.9f, 0f))
        touch.move(1, 200f, 100f)
        assertTrue(events.isEmpty())
    }

    @Test fun secondFingerNeverStealsOrReleasesTheStylus() {
        assertTrue(touch.start(3, 100f, 0f))
        assertFalse(touch.start(7, 612f, 384f))
        touch.move(7, 612f, 384f); touch.end(7)
        assertEquals(listOf(Triple(true, 0, 0)), events)
        touch.end(3)
        touch.move(7, 612f, 384f)
        assertEquals(Triple(false, 0, 0), events.last())
        assertEquals(2, events.size)
    }

    @Test fun leavingImageReleasesAndReenteringContinuesOnlyTheOriginalGesture() {
        touch.start(1, 100f, 0f)
        touch.move(1, 90f, 20f)
        assertEquals(Triple(false, 0, 0), events.last())
        touch.move(1, 612f, 384f)
        assertEquals(Triple(true, 128, 96), events.last())
    }

    @Test fun hideSuspendResizeAndDetachCancelUntilANewDown() {
        repeat(4) { reason ->
            touch.start(1, 100f, 0f)
            if (reason == 2) touch.rect = touch.rect else touch.cancel()
            val count = events.size
            touch.move(1, 612f, 384f); touch.end(1); touch.cancel()
            assertEquals(count, events.size)
            assertEquals(Triple(false, 0, 0), events.last())
        }
    }
}
