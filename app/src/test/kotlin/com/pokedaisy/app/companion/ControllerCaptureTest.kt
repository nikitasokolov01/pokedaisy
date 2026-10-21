package com.pokedaisy.app.companion

import com.pokedaisy.app.InputCodes
import org.junit.Assert.*
import org.junit.Test

class ControllerCaptureTest {
    @Test fun analogAndDigitalTriggerReportsProduceOneBindingAfterBothRelease() {
        val result = mutableListOf<List<Int>>()
        val capture = KeyCapture().apply { start(KeyCapture.Request("FF", true, { result += it })) }
        capture.onPhysicalKey(1, 105, true); capture.onAxes(1, setOf(105))
        capture.onPhysicalKey(1, 105, false)
        assertTrue(capture.active)
        capture.onAxes(1, emptySet())
        assertEquals(listOf(listOf(105)), result)
    }

    @Test fun selectedDeviceAndFirstControllerOwnTheCapture() {
        val result = mutableListOf<List<Int>>()
        val capture = KeyCapture().apply { start(KeyCapture.Request("X", false, { result += it }, acceptsDevice = { it == 2 })) }
        capture.onPhysicalKey(1, 96, true); capture.onPhysicalKey(1, 96, false)
        assertTrue(capture.active); assertTrue(result.isEmpty())
        capture.onAxes(2, setOf(InputCodes.STICK_UP)); capture.onAxes(2, emptySet())
        assertEquals(listOf(listOf(InputCodes.STICK_UP)), result)
        result.clear()
        capture.start(KeyCapture.Request("SAVE", true, { result += it }))
        capture.onPhysicalKey(2, 109, true)
        capture.onPhysicalKey(3, 103, true); capture.onPhysicalKey(3, 103, false)
        capture.onPhysicalKey(2, 109, false)
        assertEquals(listOf(listOf(109)), result)
    }

    @Test fun stickHeldBeforeCaptureMustReturnToNeutralBeforeItCanBind() {
        val result = mutableListOf<List<Int>>()
        val capture = KeyCapture()
        capture.onAxes(1, setOf(InputCodes.STICK_LEFT))
        capture.start(KeyCapture.Request("LEFT", false, { result += it }))
        capture.onAxes(1, emptySet())
        assertTrue(capture.active); assertTrue(result.isEmpty())
        capture.onAxes(1, setOf(InputCodes.STICK_RIGHT)); capture.onAxes(1, emptySet())
        assertEquals(listOf(listOf(InputCodes.STICK_RIGHT)), result)
    }
}
