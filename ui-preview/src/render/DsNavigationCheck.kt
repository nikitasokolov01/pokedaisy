@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package render

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.pokedaisy.app.NdsTouchRect
import com.pokedaisy.app.companion.CompanionSettings
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.activeGame
import com.pokedaisy.app.companion.ui.*
import com.pokedaisy.app.companion.ui.theme.AppTheme
import java.io.File
import javax.imageio.ImageIO

/** The actual lower companion pieces at the native host's reserved content/bar sizes. */
@Composable
fun DsLowerPreview(keys: CompanionKeys, back: CompanionBack, focus: (MenuKey, Boolean, () -> Unit) -> Boolean,
    tab: String = "SETTINGS") {
    val settings = remember { object : CompanionSettings by FakeSettings() {
        override val dsControls = true
        override val rewindSupported = false
        override val gameName = "DS TEST GAME"
    } }
    AppTheme {
        BoxWithConstraints {
            val density = LocalDensity.current
            val h = NdsTouchRect.contentHeight(constraints.maxWidth, constraints.maxHeight, with(density) { 72.dp.toPx().toInt() })
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(with(density) { h.toDp() })) {
                    // Desktop has no Android View D-pad focus search. Use Compose's matching
                    // direction search here; activation still travels through real key events.
                    val manager = LocalFocusManager.current
                    DsCompanionNavigation(keys, { key, repeat, start ->
                        val direction = when (key) {
                            MenuKey.UP -> FocusDirection.Up; MenuKey.DOWN -> FocusDirection.Down
                            MenuKey.LEFT -> FocusDirection.Left; MenuKey.RIGHT -> FocusDirection.Right
                            else -> null
                        }
                        if (direction != null) { start(); manager.moveFocus(direction) } else focus(key, repeat, start)
                    }) {
                        CompanionScreen(snapshot().disguised(), FakeSlots(), settings, initialTab = tab, keys = keys, back = back)
                    }
                }
                DsScreenSwitch(true) {}
            }
        }
    }
}

/** Fail-fast interaction verification, unlike the optional actions in the broad screenshot gallery. */
fun main(args: Array<String>) {
    activeGame = GameKind.FIRERED
    OptionColors.inGame = true
    val out = File(args.single()).apply { mkdirs() }
    for ((name, size, density) in listOf(
        Triple("thor", Size(1240f,1080f), 2.625f), Triple("four-three", Size(1280f,960f), 2f),
        Triple("wide", Size(1920f,1080f), 2f), Triple("compact", Size(720f,640f), 2f))) {
        runSkikoComposeUiTest(size, Density(density)) {
            mainClock.autoAdvance = false
            val keys = CompanionKeys()
            val back = CompanionBack()
            var pending: MenuKey? = null
            setContent { DsLowerPreview(keys, back, { key, _, start -> start(); pending = key; true }) }
            mainClock.advanceTimeBy(500)
            fun press(key: MenuKey) {
                pending = null
                runOnIdle { if (key == MenuKey.B) back.back() else keys.key(key) }
                val native = when (pending) {
                    MenuKey.DOWN -> Key.DirectionDown; MenuKey.UP -> Key.DirectionUp
                    MenuKey.LEFT -> Key.DirectionLeft; MenuKey.RIGHT -> Key.DirectionRight
                    MenuKey.A -> Key.Enter; else -> null
                }
                if (native != null) onRoot().performKeyInput { pressKey(native) }
                mainClock.advanceTimeBy(250)
            }
            onNodeWithText("FF SPEED").assertExists()
            press(MenuKey.DOWN)
            onAllNodes(isFocused()).assertCountEquals(1)
            press(MenuKey.A)
            onNodeWithText("INFINITE").assertExists()
            press(MenuKey.B)
            ImageIO.write(captureAllRoots(), "png", File(out, "$name-focus.png"))
            // Touch and controller share the same selector and back stack.
            onNodeWithText("FF SPEED").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            mainClock.advanceTimeBy(250)
            onNodeWithText("INFINITE").assertExists()
            press(MenuKey.B)
            onNodeWithText("INFINITE").assertDoesNotExist()
            press(MenuKey.L)
            onNodeWithText("FF SPEED").assertDoesNotExist()
            press(MenuKey.R)
            onNodeWithText("FF SPEED").assertExists()
            println("PASS $name: focus, controller A, touch selector, B, L/R")
        }
    }
    runSkikoComposeUiTest(Size(1240f, 1080f), Density(2.625f)) {
        val back = CompanionBack()
        val keys = CompanionKeys()
        setContent {
            AppTheme { CompositionLocalProvider(LocalCompanionBack provides back) {
                DsCompanionNavigation(keys, { _, _, start -> start(); true }) {
                    Column {
                        OptionTextField("", {}, rememberGbaTextMetrics(), Modifier.focusStart(true), placeholder = "SEARCH")
                        OptionButton("NEXT", rememberGbaTextMetrics(), {})
                    }
                }
            } }
        }
        runOnIdle { keys.key(MenuKey.DOWN) }
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        onNodeWithText("SEARCH").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        onAllNodes(hasSetTextAction()).assertCountEquals(1)
        runOnIdle { check(back.back()) }
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        ImageIO.write(captureAllRoots(), "png", File(out, "text-entry.png"))
        println("PASS explicit text entry: focus does not edit, activation edits, B closes")
    }
}
