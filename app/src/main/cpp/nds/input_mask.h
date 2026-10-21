#pragma once

// DS KEYINPUT + EXTKEYIN: A B Select Start Right Left Up Down R L X Y.
// The Android/JNI side is pressed-high; melonDS SetKeyMask is pressed-low.
constexpr unsigned int pk_nds_pressed_keys(unsigned int keys) { return keys & 0xFFFu; }
constexpr unsigned int pk_nds_active_low_keys(unsigned int keys) { return (~keys) & 0xFFFu; }
