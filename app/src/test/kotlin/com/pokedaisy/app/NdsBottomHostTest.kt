package com.pokedaisy.app

import android.view.MotionEvent
import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.pokedaisy.app.companion.ui.*
import com.pokedaisy.app.companion.ui.theme.AppTheme

/** The real Android host and MotionEvent dispatch, with a recording JNI sink and no ROM. */
class NdsBottomHostTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1240, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE),
        theme = "android:Theme.Material.NoActionBar")

    @Test fun visibleTouchOwnsStylusAndSwitchingCancelsItBeforeHiddenMoves() {
        val points = mutableListOf<Triple<Boolean,Int,Int>>()
        val modes = mutableListOf<Boolean>()
        var preferred = false
        val host = NdsBottomHost(paparazzi.context, { View(paparazzi.context) }, false,
            { on, remember -> modes += on; if (remember) preferred = on },
            { down, x, y -> points += Triple(down,x,y) })
        var checked = false
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!checked && host.width > 0) {
                checked = true
                val touch = host.getChildAt(0)
                val bar = host.getChildAt(2)
                assertTrue(touch.bottom <= bar.top)
                fun event(action: Int, x: Float, y: Float) = MotionEvent.obtain(0, 0, action, x, y, 0)
                    .also { host.dispatchTouchEvent(it); it.recycle() }
                event(MotionEvent.ACTION_DOWN, touch.width / 2f, touch.height / 2f)
                assertEquals(Triple(true,128,96), points.last())
                host.show(true)
                assertEquals(Triple(false,0,0), points.last())
                val count = points.size
                event(MotionEvent.ACTION_MOVE, touch.width / 2f, touch.height / 2f)
                assertEquals(count, points.size)
                event(MotionEvent.ACTION_CANCEL, 0f, 0f)
                host.show(false, remember = false)
                assertTrue("A temporary game action keeps the preferred companion mode", preferred)
                event(MotionEvent.ACTION_DOWN, touch.width / 2f, touch.height / 2f)
                assertEquals(Triple(true,128,96), points.last())
                event(MotionEvent.ACTION_UP, touch.width / 2f, touch.height / 2f)
                assertEquals(Triple(false,0,0), points.last())
            }
        }
        paparazzi.snapshot(host)
        assertTrue(checked)
        assertEquals(listOf(true,false), modes)
    }

    @Test fun androidDpadSearchAndActivationReachTheVisibleCompanion() {
        val keys = CompanionKeys()
        var selected = ""
        val companion = ComposeView(paparazzi.context).apply {
            setContent {
                AppTheme {
                    DsCompanionNavigation(keys, { key, repeat, start -> companionFocus(this, key, repeat, start) }) {
                        Column {
                            val m = rememberGbaTextMetrics()
                            OptionButton("FIRST", m, { selected = "FIRST" }, Modifier.focusStart(true))
                            OptionButton("SECOND", m, { selected = "SECOND" })
                        }
                    }
                }
            }
        }
        val host = NdsBottomHost(paparazzi.context, { companion }, true, { _, _ -> }, { _, _, _ -> })
        var checked = false
        host.viewTreeObserver.addOnPreDrawListener {
            if (!checked && companion.width > 0) {
                checked = true
                keys.key(MenuKey.DOWN)
                keys.key(MenuKey.A)
                assertEquals("SECOND", selected)
                host.show(false)
                assertFalse(companionFocus(companion, MenuKey.A, false) {})
            }
            true
        }
        paparazzi.snapshot(host)
        assertTrue(checked)
    }

}
