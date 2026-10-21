package com.pokedaisy.app

import android.view.InputDevice

object ControllerDevices {
    private fun devices() = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).filter {
        !it.isVirtual && (it.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            it.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            it.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD)
    }
    fun key(id: Int): String? {
        val devices = devices()
        val d = devices.firstOrNull { it.id == id } ?: return null
        return InputProfiles.deviceKey(d.descriptor, d.isVirtual, devices.count { it.descriptor == d.descriptor })
    }
    fun choices(): List<InputProfileChoice> = listOf(InputProfileChoice(null, "ALL CONTROLLERS")) + devices().mapNotNull { d ->
        key(d.id)?.let { InputProfileChoice(it, "${d.name} · ${it.take(6)}") }
    }
}
